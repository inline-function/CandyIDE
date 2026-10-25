# CandyIDE 插件框架设计文档

> 版本：v1（随 CandyIDE 2.0 发布） · 日期：2026-10-25 · 负责：Android IDE（t15）
> 相关：t14 语言服务扩展点（sugared.functor.lsp.LspExtension，定义于 ../sublimation/language-service）

## 1. 设计目标

把 IDE 的能力壁垒拆成「宿主 + 插件」：

- **宿主（:app）**：编辑器、文件树、工程管理、输出/日志、运行/安装 UI、agent/终端/git 等系统能力；
- **插件（:plugin-android 等）**：按领域提供工程类型、打包链路、编辑器视图扩展、语言服务扩展、设置分区、安装通道。

第一个内置插件 android-project 就是原 CandyIDE 的安卓工程支持整体拆出：
TYPE_JSAPK 工程类型、jsapk/md/script 打包、LiteASDK 导入、Shizuku 静默安装、布局预览三件套、
安卓 stdlib/procs 资产。宿主代码里不再出现任何 ``candy.ide.jsapk``/安卓专属流程。

## 2. 模块结构

```
:plugin-api      ← candy.ide.plugin* / candy.ide.fs* / candy.ide.plugin.preview*  （插件 SDK）
    ↑ depends on sugared.functor:language-service（LspExtension 属于插件 API）
:plugin-android  ← candy.plugin.android.*  （android-project 插件本体 + 资产）
    ↑ depends on :plugin-api, compiler, annotationproc, shizuku
:app             ← 宿主：UI + VM + PluginHost 实现 + BuiltInPluginIndex
    ↑ depends on :plugin-api, :plugin-android, 引擎
```

- `plugin-api` 是插件唯一编译期依赖（含 `fs` 抽象 `CandyPath/CandyFs`、预览模型、配色契约 `PluginColors`）；
- 插件包名独立（`candy.plugin.*`），宿主包名（`candy.ide.*`）保持稳定；
- v1 的插件随 app 一同编译（built-in），v2 可按 manifest.entryClass 换 DEX 动态加载——`PluginLoader` 的 resolver 参数是唯一替换点。

## 3. 加载机制

### 3.1 清单 manifest

插件随资产发布，路径固定为 `assets/candy-plugins/<id>/candy-plugin.json`：

```json
{
  "id": "android-project",
  "name": "安卓工程支持",
  "version": "1.0.0",
  "description": "安卓原生工程：@composable 视图 + @android 内建 …",
  "author": "CandyIDE",
  "entryClass": "candy.plugin.android.AndroidProjectPlugin",
  "contributions": [
    "project-type:jsapk",
    "editor-view:compose-preview",
    "lsp-extension:android-stdlib",
    "settings-section:litea-sdk",
    "project-settings-section:jsapk",
    "app-installer:shizuku"
  ],
  "minIdeVersion": "2.0.0"
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| id | 是 | 唯一键、资产目录名、注册键（kebab-case），与插件实例 `manifest.id` 必须一致，否则跳过装配并记日志 |
| name | 否 | 显示名（空则回退 id） |
| version | 否 | 插件版本（默认 1.0.0） |
| description | 否 | 一句话描述 |
| author | 否 | 作者 |
| entryClass | 否 | `CandyPlugin` 实现类全名；v1 由宿主内置索引解析，字段信息性 |
| contributions | 否 | 声明的贡献点列表（`<类型>:<id>`），展示/校验用，实际注册在 setup() |
| minIdeVersion | 否 | 要求的最低 IDE 版本 |

### 3.2 装配流程（PluginLoader.loadAll，宿主 bootstrap 时 IO 线程执行）

1. 扫描 `assets.list("candy-plugins")` 得到全部插件 id（排序后装载）；
2. 逐目录读 `candy-plugin.json` → `PluginManifest.parse`（非法清单直接跳过，不影响其它插件）；
3. `resolver(manifest.id)` 绑定实例：宿主侧 `BuiltInPluginIndex`（`"android-project" → { AndroidProjectPlugin() }`）；找不到则为纯资产插件（LoadedPlugin(manifest, null)）；
4. 实例清单 id 与资产清单 id 不一致 → 记日志、按纯资产插件保留；
5. `PluginRegistry.setLoaded(...)` 后逐个 `setup(host)`；单个 setup 抛异常只记日志（`**[插件]** <id> 装配失败：…`），不阻断其它插件；
6. `PluginRegistry.ready = true`。

### 3.3 生命周期

```kotlin
interface CandyPlugin {
    val manifest: PluginManifest
    fun setup(host: PluginHost)                 // IO 线程：注册贡献点、物化资产
    fun onProjectTypeChanged(typeId: String, dir: CandyPath?)   // 打开工程时
    fun dispose(host: PluginHost)               // v1 预留
}
```

## 4. 贡献点清单

贡献点注册表 `PluginRegistry`（全局单例）按 id 去重；查询接口供宿主 UI/VM 使用。

### 4.1 工程类型 ProjectTypeContribution

接管一种 `candy.project` 的 type 取值（宿主内置 js/script/md）：

```kotlin
interface ProjectTypeContribution {
    val typeId: String                    // type= 值，如 "jsapk"
    fun displayName(): String             // 编辑器标题/工程卡
    fun description(): String             // 新建对话框描述
    fun scaffold(dir: CandyPath, name: String)          // 新建工程样板
    fun claimsProject(dir: CandyPath): Boolean          // 无类型标记时按内容认领
    fun runShort(host: PluginHost)                     // 短按「运行」
    fun packageProject(host: PluginHost, dir: CandyPath) // 打包 APK
}
```

宿主侧：
- `ProjectStore.typeOf` 先读标记（含插件注册的 typeId），无标记时再按注册顺序问 `claimsProject`；
- `CandyViewModel.runFile` 对插件类型调用 `runShort`；`buildApkProject` 路由给 `packageProject`；
- `createProject` 对插件类型用 `scaffold` 写样板（jsapk 的 @composable 骨架由 android-project 提供）。

### 4.2 语言服务扩展 LspExtension（t14 契约）

定义在语言服务包（不是 :plugin-api），`PluginHost.registerLspExtension` 在插件 setup 时注册：

```kotlin
interface LspExtension {
    val name: String
    val stdlibDir: File?
    val processorsDir: File?
    fun isEnabled(): Boolean
}
```

宿主 `CandyViewModel.analysis()` 创建 LspAnalysis 时把 `PluginRegistry.lspExtensions()` 全部 register；
`isEnabled()` 每次查询实时生效——插件在 `onProjectTypeChanged` 里翻转开关即可（如 jsapk 工程才并入 android/stdlib 与处理器脚本）。
`SimpleLspExtension` 适合简单场景；android-project 用自带的 `AndroidLspExtension`（目录固定、启停可控）。

### 4.3 编辑器视图 EditorViewContribution

```kotlin
interface EditorViewContribution {
    val id: String
    @Composable fun FloatingPanel(state: ComposePreviewState, onToggleFullscreen, onClose, onApply, onDrag)
    @Composable fun VirtScreen(state: ComposePreviewState?, onBack: () -> Unit)
    @Composable fun PreviewScreen(state, onBack, onApply: (treeJson, state) -> Unit)
}
```

宿主出容器与导航：悬浮窗 Surface/位移/拖动手势、`Screen.Virt`/`Screen.Preview` 页面与返回键。
预览数据模型 `ComposePreviewState(fnName, start, end, treeJson)` 由宿主经 `host.composableTreeJson(line, col)` 提供（LSP `composableTreeJson`），
插件只负责渲染与树编辑（写回经 onApply → 宿主 `applyPreviewTree` 精确补丁）。

### 4.4 设置分区 SettingsSectionContribution / ProjectSettingsSectionContribution

```kotlin
interface SettingsSectionContribution {
    val id: String; val sortKey: Int; val title: String
    @Composable fun Content(host: PluginHost)
}
interface ProjectSettingsSectionContribution {
    val id: String; val sortKey: Int
    fun appliesTo(host: PluginHost, dir: CandyPath): Boolean
    @Composable fun Content(host: PluginHost, dir: CandyPath)
}
```

宿主在设置页/工程设置页按 sortKey 渲染（android-project: 1000 工具链、100 jsapk 打包分区）。
插件 UI 用配色契约 `PluginColors`（与宿主 Candy 主题同值，宿主升级主题需同步契约）。

### 4.5 安装通道 AppInstaller

```kotlin
interface AppInstaller {
    val id: String
    fun tryInstall(apk: CandyPath, pkg: String?): Pair<Boolean, String>  // (通道是否可用, 结果/原因)
}
```

宿主安装 UI 按注册顺序尝试：via=false（通道不可用）即降级系统安装器（ACTION_VIEW apk）。
Shizuku 通道由 android-project 提供（`JsApkBuilder.installViaShizuku`）；无插件时直接降级。

## 5. PluginHost 服务面（插件访问宿主的唯一入口）

| 类别 | 方法 |
| --- | --- |
| 基础设施 | context / candyRoot() / pluginDataDir(id) / materializeAssets(id, assetRoot, version, destRel)（带版本标记的资产物化） |
| 注册 | registerProjectType / registerEditorView / registerLspExtension / registerSettingsSection / registerProjectSettingsSection / registerAppInstaller |
| 线程/日志 | postMain / runOnIo / log（日志页）/ logUser（mdLogUser）/ toast / setUserOutput / openBuildLog |
| 状态 | setBusy（运行按钮转圈）/ setRunFailed（日志标红） |
| 工程 | currentProjectDir / currentEditingFile / projectTypeOf(dir) / currentFileText |
| 语言/编辑器 | composableTreeJson(line, col) / runCurrentAsJsConsole / openVirtEnvironment |
| 安装 | requestInstall(apk, pkg)（静默设置由宿主编排）/ importCertInto(project, uri) |

宿主实现 `CandyPluginHost`（:app，candy.ide.vm）：把服务调用映射到 CandyViewModel 的能力，
插件不直接持有 VM。原有安卓专属目录全部改存插件私有 namespace：
`files/candy/android/{stdlib,procs}` → `files/candy/plugins/android-project/android/{stdlib,procs}`（版本标记沿用 4），
`files/candy/litea-sdk` → `files/candy/plugins/android-project/litea-sdk`。

## 6. 示例：android-project 插件（内置）

```kotlin
class AndroidProjectPlugin : CandyPlugin {
    override val manifest = PluginManifest(id = "android-project", name = "安卓工程支持", …)
    private var lspExtension: AndroidLspExtension? = null

    override fun setup(host: PluginHost) {
        AndroidAssets.setup(host)                       // 物化 android/{stdlib,procs}（版本 4）
        val stdlib = AndroidAssets.stdlib()
        val procs = AndroidAssets.procs()
        val ext = AndroidLspExtension(stdlib, procs)
        lspExtension = ext
        host.registerLspExtension(ext)                  // LSP 扩展（启停按工程类型）
        host.registerProjectType(AndroidProjectType())  // jsapk：scaffold/claimsProject/runShort/packageProject
        host.registerEditorView(AndroidEditorView())    // 预览三件套
        host.registerSettingsSection(LiteaSdkSection()) // 设置页「工具链」
        host.registerProjectSettingsSection(JsApkProjectSection())  // 工程设置页「打包（APK）」
        host.registerAppInstaller(ShizukuInstaller())   // Shizuku 静默安装通道
    }

    override fun onProjectTypeChanged(typeId: String, dir: CandyPath?) {
        lspExtension?.enabled = typeId == AndroidProjectType.TYPE_ID   // 同旧 setAndroidStdlibEnabled
    }
}
```

打包链路（AndroidPackaging，自 VM 迁入）经 PluginHost 取得宿主能力：
`host.setBusy/openBuildLog/log/logUser/requestInstall` 一一对应旧 VM 的 running/openDrawerTab/mdLog/mdLogUser/installPkg。
编译链（compileAndroidJs）的编译根 = core + 插件物化的 stdlib + 工程源，处理器来自插件资产的 procs/。

## 7. 如何写一个新插件（骨架）

1. 新建安卓 library 模块，依赖 `:plugin-api`（compose 按需）；
2. 资产放 `assets/candy-plugins/<id>/`：`candy-plugin.json` + 插件私有资产；
3. 实现 `CandyPlugin`，在 setup() 里按需注册贡献点（清单字段的 contributions 与之对应）；
4. 在 :app 的 `BuiltInPluginIndex.entries` 加映射（v1）；v2 将改成按 entryClass 加载 DEX；
5. 资产物化用 `host.materializeAssets(id, assetRoot, 版本号)`，版本号变化才重抽；
6. UI 用 `PluginColors`，不要 import 宿主的 candy.ide.ui.theme；
7. 约定：抛异常只影响自身装配（PluginLoader 捕获并记日志），不得拖死宿主。

## 8. 现状与演进

- v1：插件编译期内置（:plugin-android 随 app 一同打包），资产随插件模块发布（AGP 库资产合并）；
- 兼容性：`ShizukuShell/ShizukuManager`、终端与 git 等系统能力仍在宿主（它们是 IDE 平台能力而非工程类型能力）；
- v2（未实现）：清单 entryClass → DEX 动态加载、插件启用/禁用与热更新、贡献点 id 冲突仲裁表。
