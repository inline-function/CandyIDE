package candy.ide.desktop.plugin

import candy.ide.desktop.plugin.contrib.AppInstaller
import candy.ide.desktop.plugin.contrib.EditorViewContribution
import candy.ide.desktop.plugin.contrib.ProjectSettingsSectionContribution
import candy.ide.desktop.plugin.contrib.ProjectTypeContribution
import candy.ide.desktop.plugin.contrib.SettingsSectionContribution
import sugared.functor.lsp.LspExtension
import java.io.File

/**
 * 宿主暴露给插件的服务面（对齐安卓端 candy.ide.plugin.PluginHost，裁剪 Android 专有能力）。
 * 插件只通过本接口接触宿主能力，不引用宿主内部类。
 *
 * 桌面端相对安卓端的裁剪（详见 README 差异表）：
 *  - 无 context（无 Android Context 概念）；
 *  - 无 openVirtEnvironment（桌面端无独立虚拟桥接全屏，布局预览并入编辑器右侧栏）；
 *  - 无 openBuildLog/setUserOutput/setRunFailed（桌面端输出/日志抽屉按窗口局部持有，
 *    插件经 log()/toast()/宿主运行方法间接驱动）；
 *  - 无 requestInstall/importCertInto（APK 安装与证书导入是安卓端打包链的能力，
 *    桌面端不做 APK 打包，相应通道不提供）。
 */
interface PluginHost {

    // ───────── 基础设施 ─────────

    /** IDE 数据根（%APPDATA%\CandyIDE；插件数据与引擎数据都在其下） */
    fun candyRoot(): File

    /** 插件私有数据目录（%APPDATA%\CandyIDE\plugins\<id>，插件自管） */
    fun pluginDataDir(pluginId: String): File

    /**
     * 释放插件资源 candy-plugins/<id>/<assetRoot>/ 下的全部文件 到 pluginDataDir/<destRel>，带版本标记：
     * 版本号与磁盘标记不一致才全量重抽（与安卓端 materializeAssets 同语义）。
     * 资产相对路径来自构建期生成的 assets-index.txt（classpath 无法列目录）。
     */
    fun materializeAssets(pluginId: String, assetRoot: String, version: Int, destRel: String = assetRoot): File

    // ───────── 贡献注册（注册表去重，重复 id 忽略） ─────────

    fun registerProjectType(contrib: ProjectTypeContribution)
    fun registerEditorView(contrib: EditorViewContribution)
    fun registerLspExtension(extension: LspExtension)
    fun registerSettingsSection(contrib: SettingsSectionContribution)
    fun registerProjectSettingsSection(contrib: ProjectSettingsSectionContribution)
    fun registerAppInstaller(installer: AppInstaller)

    // ───────── 运行期服务 ─────────

    /** IDE 日志页（输出抽屉「日志」页签） */
    fun log(msg: String)
    /** 轻量提示（自动消失的 toast） */
    fun toast(msg: String)
    /** 投递执行（桌面端 Compose 快照状态可跨线程写，直接执行） */
    fun postMain(action: () -> Unit)
    /** 投递到后台线程执行 */
    fun runOnIo(action: () -> Unit)
    /** 「运行」忙碌态（运行按钮转圈） */
    fun setBusy(busy: Boolean)

    // ───────── 工程 ─────────

    /** 当前工程根目录 */
    fun currentProjectDir(): File?
    /** 当前编辑的文件 */
    fun currentEditingFile(): File?
    /** 工程类型（candy.project 的 type 值，与 ProjectStore.typeOf 一致） */
    fun projectTypeOf(dir: File): String
    /** 当前文件正文（编辑器缓存优先） */
    fun currentFileText(): String?

    // ───────── 语言/编辑器能力 ─────────

    /** LSP：@composable 函数的视图树 JSON（无结果的 null），行列为 0-based 编辑器坐标 */
    fun composableTreeJson(line: Int, col: Int): String?
    /** 以当前 IDE 环境把 .subl 所属工程编译为 JS（复用宿主编译链；onCompiled 回调编译耗时 ms） */
    fun compileToJs(script: File, onCompiled: ((Long) -> Unit)? = null): String
    /** 把当前工程按 JS 控制台模式运行（桌面端 = 编译 + Node 执行；安卓端为 WebView） */
    fun runCurrentAsJsConsole()
}
