# CandyIDE Desktop（Windows 端）

Sublimation（凝华）语言的 Windows 桌面 IDE，与 Android 端共用同一套语言引擎与 UI 设计语言。

## 架构定位

```
CandyIDE/desktop（Compose Multiplatform · JVM · Skia 渲染）
  ├─ 共享 Android 端 (:app) 的平台无关源码（构建期 sync，不改动 app 模块）
  │    ├─ ui/theme/Color.kt、Theme.kt —— Candy 主题色
  │    ├─ md/Markdown.kt           —— commonmark 文档渲染
  │    └─ data/DiagItem.kt          —— 诊断数据模型
  ├─ 共享 Android 端内置文档资产 assets/builtin（构建期 sync 进资源）
  └─ 进程内直连 sublimation（../sublimation 复合构建）
       ├─ frontend            Lexer（编辑器语法高亮直接复用真实词法分析）
       ├─ compiler            MultiModule 工程编译 / 诊断
       ├─ interpreter         脚本/工程直连执行（移动端同款）
       └─ language-service    LspAnalysis：实时诊断 / 补全 / 悬停（编辑器集成）
```

与 Android 端的差异（平台特性）：

| 能力 | Android | Windows |
|---|---|---|
| 编辑器组件 | sora-editor（LGPL-2.1） | Compose 原生编辑器（本模块内实现；自动换行/横向滚动可切） |
| 工程存储 | SAF 目录 | %APPDATA%\CandyIDE\projects（可打开任意目录） |
| 编译产物执行 | WebView | Node.js（require('fs')/Buffer 原生可用）；亦可解释器直连 |
| 终端/Git/APK 打包 | Shizuku + Termux | 桌面端内置 Git 面板（git 命令封装）；终端/APK 不做 |
| AI 助手 | ADK + OpenAI 兼容端点 | 内置 OpenAI 兼容客户端（java.net.http SSE 流式）+ 工具调用循环 |

## 插件框架（对齐安卓端 v2.0）

Windows 端照抄安卓端 CandyIDE 2.0 的插件框架（宿主 + 内置插件结构一致）：

```
desktop/src/main/kotlin/candy/ide/desktop/plugin/   ← 插件 SDK（桌面版，因 :plugin-api 是 Android 库无法跨端复用）
    ├─ CandyPlugin / PluginManifest / PluginHost / PluginLoader / PluginRegistry
    ├─ contrib/（6 类贡献点：工程类型 / 编辑器视图 / LSP 扩展 / 设置分区 / 工程设置分区 / 安装通道）
    ├─ preview/（ComposePreviewState + PreviewCodeGen 预览模型）
    ├─ android/（内置插件 android-project 的 Windows 端裁剪版）
    └─ platform/（三平台系统插件 SfA/SfG/SfW，t50 对齐）
desktop/src/main/resources/candy-plugins/  ← 清单（android-project + sublimation-android/game/wasm）；
    android/stdlib、android/procs 资产构建期自 :plugin-android 同源同步
```

- **装载**：启动时后台线程跑 `PluginBootstrap.load`（对齐安卓端 VM.bootstrap）：读 `plugin-index.txt` → 各插件清单 → `BuiltInPluginIndex` 绑定实现 → IO 线程 `setup`（注册贡献点 / 物化资产到 `%APPDATA%\CandyIDE\plugins\<id>`，版本标记 4）。单个插件装配失败只记日志，不阻断 IDE。装载完成后把当前工程类型再通知一遍插件（装载晚于 openProject 的时序补位，t50 对齐）。
- **贡献点**：与安卓端同构的 6 类注册表；UI 侧的渲染/路由位置与安卓端对齐——
  - `ProjectStore.typeOf` 先读标记（含插件类型）、无标记问插件 `claimsProject`；
  - 「运行」对插件工程类型路由 `runShort`（Ctrl+R / 顶栏按钮同）；
  - 布局预览面板由 `PluginRegistry.editorView()` 提供（Ctrl+Shift+P，容器为编辑器右侧栏）；
  - 设置页 / 工程设置页渲染插件分区（按 `sortKey`，`appliesTo` 过滤）；
  - LSP 扩展经 `registerLspExtension` 直达已创建的 `LspAnalysis`（与首轮分析时序解耦，t50 对齐），惰性初始化与装载完成时再全量补注册（t14 契约，`isEnabled()` 实时生效）。
- **三平台系统插件（t50 对齐，插件中心可见带「系统插件」徽章，不可停用/卸载）**：
  - `sublimation-android`（SfA · 安卓运行时，system）：接管 `lsp-extension:android-stdlib`——物化 android-project 的 stdlib/procs 资产后注册 `android-stdlib` 扩展，jsapk 工程打开时启用（工程类型通知），为编辑期补全/诊断注入 `@android` 内建与安卓标准库；
  - `sublimation-game`（SfG · 游戏库，system）：`lsp-extension:sublimation-game` 实体占位（stdlib/procs 为 null，符号库构建中）；
  - `sublimation-wasm`（SfW · WASM 运行时，system）：`lsp-extension:sublimation-wasm` 实体占位（同上）。
- **android-project 插件（Windows 端裁剪版）**：
  - 保留：`project-type:jsapk`（scaffold/认领/运行）、`editor-view:compose-preview`、`project-settings-section:jsapk-info`（jsapk 工程设置页的「APK 打包为安卓端专属能力」说明）。
  - 裁剪：`lsp-extension:android-stdlib`（移交 SfA 单一来源注册）、`app-installer:shizuku`（Windows 无 Shizuku）、`settings-section:litea-sdk`（LiteASDK 端上工具链为安卓打包链专用）、`project-settings-section:jsapk` 的打包配置（桌面不做打包）。
- **与安卓端的差异**（照抄基线的平台特化）：

| 项 | 安卓端 | 桌面端 |
|---|---|---|
| 插件 SDK | `:plugin-api`（Android 库，含 SAF 的 CandyPath/CandyFs） | desktop 内同构实现（路径统一 `java.io.File`，无 Context/SAF 分支） |
| 插件资产来源 | APK assets（可列目录） | classpath 资源 + 构建期 `plugin-index.txt`/`assets-index.txt` 索引 |
| 插件数据目录 | `files/candy/plugins/<id>` | `%APPDATA%\CandyIDE\plugins\<id>` |
| 插件宿主面 | 单 VM（context/安装/证书/输出重定向等） | 多窗口每 IdeState 一个 `DesktopPluginHost`（裁剪 context、requestInstall、Virt 环境、openBuildLog/setUserOutput 等安卓专有能力） |
| 编辑器视图容器 | 悬浮面板 + Virt 全屏 + 预览构建器三入口 | 编辑器右侧栏单面板（三入口合并） |
| 多窗口 LSP 扩展启停 | 单工程时生效 | 启停跟随最近打开的工程（单例分析器共享，单窗口与安卓端一致） |
| 安装通道 AppInstaller | Shizuku 静默安装 | 不提供（接口保留对齐，无注册） |
| 插件资产路径契约 | `assetRoot` = assets 根下全路径（`candy-plugins/<id>/android`） | 同契约（`AndroidAssets.ASSET_PATH`），宿主物化时规范化掉前缀，落盘仍 `plugins/<id>/android`（保持旧标记有效） |
| 资产路径守卫 | `:plugin-android:verifyPluginAssets`（preBuild） | `:desktop:verifyPluginAssets`（processResources 前，含 src 树与同步树双检查） |

## 功能对齐（v1.1）

- **多窗口**：主页常驻；点工程开独立编辑器窗口，互不影响；主页顶栏「导入归档」把 .capr/zip 解压成新工程
- **工程设置页**：重命名 / 删除 / 远程仓库地址 / 在文件管理器中打开 / 复制路径 / **导出归档（.capr = zip，与安卓端互通）**
- **配色方案**：内置「浮幻/默认」+ 自定义（token 级颜色 + 粗体/斜体标记，.properties 与 Android 端互换），随存随用、全编辑器热更新
- **标准库查看**：sublimation/core 源码双栏浏览（递归列出按模块分目录的全部 .subl/.settings）
- **关于页**：版本与开源依赖声明
- **编辑器**：Ctrl+Z/Y 撤销重做（时间分组）、Ctrl+F 查找栏、右键菜单（剪切/复制/粘贴/全选）、方案驱动的语法高亮、真实选区、**Alt+Enter / Ctrl+. 快速修复**（诊断驱动 codeAction 灯泡弹层）、**F12 转到定义**（跨文件先打开再跳；找不到时提示）、**F2 重命名符号**（LSP 全工程出现处一并改）、**Ctrl+Alt+L 格式化文档**、**Shift+F12 查找引用**（日志页签 name:line 链接可点跳）、**Ctrl+Q 查看文档**（LSP hover 文档打到日志页签）、**Ctrl+Shift+A 指名调用**（实参改写成形参名 = 实参）、**自动补全**（输入触发：过滤 / 上下方向键 / Enter 或 Tab 插入，Esc 关闭）、**签名提示**（实参区底部浮层，当前实参【】高亮，150ms 防抖）、顶栏 **Σ 符号栏**（Unicode 数学符号 ∀λ→ 一键插入光标处）、**Ctrl+Shift+P 布局预览**（对齐安卓端 ComposePreview：@composable 视图树右侧栏，树大纲增删改/缩略渲染点击选中/属性面板，「应用」把树再生成函数体写回源码；编辑 1s 后函数行无错则自动刷新，光标不在 composable 内时兜底 layout(根函数））
- **安全关闭**：关标签页/关窗口前有未保存修改时弹确认；运行/编译前自动保存脏文件
- **文件树**（对齐安卓端 FileTreePanel）：行尾 ⋮ 菜单 = 新建文件/目录、重命名、删除、解压 zip/.capr、用系统默认程序打开、在文件管理器中定位、复制路径
- **Git**（对齐安卓端 GitScreen）：未初始化一键 init（身分 + 远程）、云端领先提示 + 拉取、提交（默认名「第 n 次推送」）/ 推送 / 丢弃修改 / 回退历史提交；设置页「git 协同」区：提交署名 + **修改文件自动提交**（保存/删除/新建即提交）+ **自动同步云端**（进工程自动拉取，开自动提交时一并推送）+ git 可用性探测
- **输出抽屉**（对齐安卓端 OutputDrawer）：输出/日志/诊断三页签，日志与诊断支持四档等级过滤，日志里的「文件名:行」可点击跳转
- **AI 助手**（对齐安卓端 AgentScreen）：主页顶栏 💬 入口 + 编辑器窗口 Alt+4 全页覆盖；气泡聊天、SSE 流式输出、工具调用（读/写当前文件、工程树、诊断、run_shell、tap_run/tap_save/goto_line）、访问权分级（只读/工程/工作区/完全）、往期对话存档（JSON）、token 估算；设置页「AI 助手」区配端点/Key/模型/**温度滑块/流式下拉**/提示词
- **设置页**（对齐安卓端 SettingsScreen 分区与行风格：Surface 行 + Switch/Slider）：编辑器（配色方案/字号滑块/**自动换行**开关）、诊断（IDE 日志）、AI 助手、git 协同、引擎与路径、运行模式、结束（**关闭 IDE** / **重置 IDE** 两级确认——需输入 CandyIDE，同安卓端）；Shizuku/Termux/LiteASDK/无障碍/伴生软件等安卓特有项不移植

## 开发

```bat
gradlew.bat :desktop:run              :: 开发运行
gradlew.bat :desktop:test             :: 59 条测试（引擎/UI/git/工作区/助手/LSP/预览）
:: 并行开发注意：若另一个 Gradle 实例占用 .gradle 锁（本机双 agent 场景），
:: 加 --project-cache-dir F:/KotlinProject/CandyIDE/.gradle-dsh 用独立缓存目录避开
gradlew.bat :desktop:packageUberJarForCurrentOS  :: 打 uber jar
desktop\jpkg.ps1                        :: jlink（含 java.net.http）+ jpackage 生成 exe
                                        :: 产物 build\compose\binaries\main\CandyIDE\CandyIDE.exe
```

要求：JDK 17+；AppImage 打包走 desktop\jpkg.ps1（Gradle 官方打包任务强制下载 WiX，本机 SSL 环境拉不动；且默认 jlink 运行时不含 java.net.http，助手联网需要，故脚本自带 jlink）。

## 引擎定位

运行时按以下顺序定位 sublimation 仓库（供 stdlib 与 LSP prelude）：

1. 设置页「sublimation 根目录」（%APPDATA% 外的注册表式 prefs）
2. 工作目录向上自动探测（找含 frontend/compiler/interpreter/core 的目录）
3. classpath 内 stdlib（打包时从 ../sublimation/core 同步）

开发态从仓库根目录运行 Gradle 可直接命中 ../sublimation；打包发行后.SettingsMesh 设置里手动指定 sublimation 路径即可获得完整标准库补全。
