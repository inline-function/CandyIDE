package candy.ide.plugin

import android.content.Context
import candy.ide.fs.CandyPath
import candy.ide.plugin.contrib.AppInstaller
import candy.ide.plugin.contrib.EditorViewContribution
import candy.ide.plugin.contrib.ProjectSettingsSectionContribution
import candy.ide.plugin.contrib.ProjectTypeContribution
import candy.ide.plugin.contrib.SettingsSectionContribution
import sugared.functor.lsp.LspExtension
import java.io.File

/**
 * 宿主暴露给插件的服务面。插件只通过本接口接触宿主能力，不引用宿主内部类。
 */
interface PluginHost {

    val context: Context

    // ───────── 基础设施 ─────────

    /** IDE 私有数据根（files/candy）：引擎标准库与插件数据都在其下 */
    fun candyRoot(): File

    /** 插件私有数据目录（files/candy/plugins/&lt;id&gt;，插件自管） */
    fun pluginDataDir(pluginId: String): File

    /**
     * 释放 assets/&lt;assetRoot&gt; 到 pluginDataDir/&lt;destRel&gt;，带版本标记：
     * 版本号与磁盘标记不一致才全量重抽（与旧 androidBootstrap 同语义）。
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

    /** 构建日志页（输出抽屉「日志」页签） */
    fun log(msg: String)
    /** 用户可见日志（mdLogUser：markdown 化的重要操作记录） */
    fun logUser(msg: String)
    /** 轻量提示（2 秒自动消失的 toast） */
    fun toast(msg: String)
    /** 投递到主线程 */
    fun postMain(action: () -> Unit)
    /** 投递到 IDE 的单线程 IO 执行器 */
    fun runOnIo(action: () -> Unit)
    /** 「运行/打包」忙碌态（运行按钮转圈） */
    fun setBusy(busy: Boolean)
    /** 本次运行/打包失败标记（日志页标红、不跳输出页） */
    fun setRunFailed(failed: Boolean)
    /** 打开输出抽屉的「日志」页签 */
    fun openBuildLog()
    /** 输出页追加一行程序输出 */
    fun setUserOutput(line: String)

    /** 当前工程根目录 */
    fun currentProjectDir(): CandyPath?
    /** 当前编辑的文件 */
    fun currentEditingFile(): CandyPath?
    /** 工程类型（candy.project 的 type 值，与 ProjectStore.typeOf 一致） */
    fun projectTypeOf(dir: CandyPath): String
    /** 当前文件正文（编辑器缓存优先） */
    fun currentFileText(): String?

    // ───────── 语言/编辑器能力 ─────────

    /** LSP：@composable 函数的视图树 JSON（无结果的 null），行列为 0-based 编辑器坐标 */
    fun composableTreeJson(line: Int, col: Int): String?
    /** 把当前文件按 JS 控制台模式运行（编译 → JS → WebView） */
    /** 以当前 IDE 环境把 .subl 编译为 JS（复用宿主编译链；onCompiled 回调编译耗时 ms） */
    fun compileToJs(script: candy.ide.fs.CandyPath, onCompiled: ((Long) -> Unit)? = null): String

    fun runCurrentAsJsConsole()
    /** 进入虚拟桥接环境（宿主的 layout(根) 粘合） */
    fun openVirtEnvironment()

    // ───────── 安装能力 ─────────

    /** 走宿主安装 UI（静默设置开 → 立即安装；否则弹「是否安装」；安装通道来自插件贡献） */
    fun requestInstall(apk: CandyPath, pkg: String?)
    /** 把 SAF uri 的 .jks 证书导入工程 certificates/ 目录，成功返回相对路径 */
    fun importCertInto(project: CandyPath, uri: android.net.Uri): String?
}
