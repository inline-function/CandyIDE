package candy.ide.desktop.plugin

import candy.ide.desktop.plugin.contrib.AppInstaller
import candy.ide.desktop.plugin.contrib.EditorViewContribution
import candy.ide.desktop.plugin.contrib.ProjectSettingsSectionContribution
import candy.ide.desktop.plugin.contrib.ProjectTypeContribution
import candy.ide.desktop.plugin.contrib.SettingsSectionContribution
import sugared.functor.lsp.LspExtension
import java.io.File

/**
 * 单测替身宿主（对齐安卓端 candy.ide.plugin.FakePluginHost，t50）：贡献注册走真 PluginRegistry
 *（与 DesktopPluginHost 同动作），materializeAssets 用 fixture 文件树代替 classpath 资源物化
 *（AndroidAssets.setup 固定取其下 stdlib/ 与 procs/）。
 */
class FakePluginHost(val materializedRoot: File) : PluginHost {

    override fun candyRoot(): File = materializedRoot.parentFile ?: materializedRoot
    override fun pluginDataDir(pluginId: String): File = materializedRoot
    override fun materializeAssets(pluginId: String, assetRoot: String, version: Int, destRel: String): File =
        materializedRoot

    override fun registerProjectType(contrib: ProjectTypeContribution) = PluginRegistry.registerProjectType(contrib)
    override fun registerEditorView(contrib: EditorViewContribution) = PluginRegistry.registerEditorView(contrib)
    override fun registerLspExtension(extension: LspExtension) = PluginRegistry.registerLspExtension(extension)
    override fun registerSettingsSection(contrib: SettingsSectionContribution) = PluginRegistry.registerSettingsSection(contrib)
    override fun registerProjectSettingsSection(contrib: ProjectSettingsSectionContribution) =
        PluginRegistry.registerProjectSettingsSection(contrib)
    override fun registerAppInstaller(installer: AppInstaller) = PluginRegistry.registerAppInstaller(installer)

    override fun log(msg: String) {}
    override fun toast(msg: String) {}
    override fun postMain(action: () -> Unit) = action()
    override fun runOnIo(action: () -> Unit) = action()
    override fun setBusy(busy: Boolean) {}

    override fun currentProjectDir(): File? = null
    override fun currentEditingFile(): File? = null
    override fun projectTypeOf(dir: File): String = "jsapk"
    override fun currentFileText(): String? = null

    override fun composableTreeJson(line: Int, col: Int): String? = null
    override fun compileToJs(script: File, onCompiled: ((Long) -> Unit)?): String = ""
    override fun runCurrentAsJsConsole() {}
}
