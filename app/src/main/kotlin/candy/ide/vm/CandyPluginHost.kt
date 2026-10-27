package candy.ide.vm

import android.app.Application
import candy.ide.data.ProjectStore
import candy.ide.engine.Engine
import candy.ide.fs.CandyPath
import candy.ide.plugin.PluginHost
import candy.ide.plugin.PluginRegistry
import candy.ide.plugin.contrib.AppInstaller
import candy.ide.plugin.contrib.EditorViewContribution
import candy.ide.plugin.contrib.ProjectSettingsSectionContribution
import candy.ide.plugin.contrib.ProjectTypeContribution
import candy.ide.plugin.contrib.SettingsSectionContribution
import sugared.functor.lsp.LspExtension
import java.io.File

/**
 * PluginHost 的宿主实现：把插件的服务调用映射到 CandyViewModel 的能力
 * （日志/输出/线程/安装/工程类型/语言服务——插件不直接持有 VM）。
 */
class CandyPluginHost(private val vm: CandyViewModel) : PluginHost {

    override val context: Application get() = vm.getApplication()

    override fun candyRoot(): File = Engine.root(context)

    override fun pluginDataDir(pluginId: String): File = File(candyRoot(), "plugins/$pluginId")

    override fun materializeAssets(pluginId: String, assetRoot: String, version: Int, destRel: String): File {
        val ctx = context
        val target = File(pluginDataDir(pluginId), destRel)
        val marker = File(target.parentFile, target.name + ".version")
        val need = !marker.exists() || marker.readText().trim() != version.toString()
        if (need) {
            target.deleteRecursively(); target.mkdirs()
            fun copyAssetTree(assetPath: String, out: File) {
                val children = ctx.assets.list(assetPath)
                if (children == null || children.isEmpty()) {
                    out.parentFile?.mkdirs()
                    ctx.assets.open(assetPath).use { ins -> out.outputStream().use { o -> ins.copyTo(o) } }
                } else {
                    out.mkdirs()
                    for (c in children) copyAssetTree("$assetPath/$c", File(out, c))
                }
            }
            copyAssetTree(assetRoot, target)
            marker.writeText(version.toString())
        }
        return target
    }

    // ---- 贡献注册（全局注册表去重） ----

    override fun registerProjectType(contrib: ProjectTypeContribution) = PluginRegistry.registerProjectType(contrib)
    override fun registerEditorView(contrib: EditorViewContribution) = PluginRegistry.registerEditorView(contrib)
    override fun registerLspExtension(extension: LspExtension) {
        PluginRegistry.registerLspExtension(extension)
        // 与 LspAnalysis 创建时序解耦：插件晚于首轮分析注册时直达实时分析器
        vm.registerLiveLspExtension(extension)
    }
    override fun registerSettingsSection(contrib: SettingsSectionContribution) = PluginRegistry.registerSettingsSection(contrib)
    override fun registerProjectSettingsSection(contrib: ProjectSettingsSectionContribution) = PluginRegistry.registerProjectSettingsSection(contrib)
    override fun registerAppInstaller(installer: AppInstaller) = PluginRegistry.registerAppInstaller(installer)

    // ---- 运行期服务 ----

    override fun log(msg: String) = vm.mdLog(msg)
    override fun logUser(msg: String) = vm.mdLogUser(msg)
    override fun toast(msg: String) { vm.toast.value = msg }
    override fun postMain(action: () -> Unit) { vm.main.post(action) }
    override fun runOnIo(action: () -> Unit) = vm.io.execute(action)
    override fun setBusy(busy: Boolean) { vm.running.value = busy }
    override fun setRunFailed(failed: Boolean) { vm.runFailed.value = failed }
    override fun openBuildLog() = vm.openDrawerTab(1)
    override fun setUserOutput(line: String) { vm.main.post { vm.outputLines.add(line) } }

    override fun currentProjectDir(): CandyPath? = vm.currentProject.value
    override fun currentEditingFile(): CandyPath? = vm.currentFile.value
    override fun projectTypeOf(dir: CandyPath): String = ProjectStore.typeOf(dir)
    override fun currentFileText(): String? = vm.currentFile.value?.let { vm.textOf(it) }

    // ---- 语言/编辑器能力 ----

    override fun composableTreeJson(line: Int, col: Int): String? = vm.composableTreeJson(line, col)
    override fun compileToJs(script: candy.ide.fs.CandyPath, onCompiled: ((Long) -> Unit)?): String =
        Engine.compileToJs(context, script, onCompiled)

    override fun runCurrentAsJsConsole() = vm.runCurrentAsJsConsole()
    override fun openVirtEnvironment() = vm.openVirt()

    // ---- 安装能力 ----

    override fun requestInstall(apk: CandyPath, pkg: String?) = vm.installPkg(apk, pkg)
    override fun importCertInto(project: CandyPath, uri: android.net.Uri): String? = vm.importCertInto(project, uri)
}
