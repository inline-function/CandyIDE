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
 * 单测替身宿主：贡献注册走真 PluginRegistry（与 CandyPluginHost 同动作），
 * materializeAssets 用 fixture 文件树代替 Android AssetManager
 * （AndroidAssets.setup 固定取其下 stdlib/ 与 procs/）。
 */
class FakePluginHost(val materializedRoot: File) : PluginHost {

    override val context: Context
        @Suppress("CAST_NEVER_SUCCEEDS")
        get() = null as Context

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
    override fun logUser(msg: String) {}
    override fun toast(msg: String) {}
    override fun postMain(action: () -> Unit) = action()
    override fun runOnIo(action: () -> Unit) = action()
    override fun setBusy(busy: Boolean) {}
    override fun setRunFailed(failed: Boolean) {}
    override fun openBuildLog() {}
    override fun setUserOutput(line: String) {}

    override fun currentProjectDir(): CandyPath? = null
    override fun currentEditingFile(): CandyPath? = null
    override fun projectTypeOf(dir: CandyPath): String = "jsapk"
    override fun currentFileText(): String? = null

    override fun composableTreeJson(line: Int, col: Int): String? = null
    override fun compileToJs(script: CandyPath, onCompiled: ((Long) -> Unit)?): String = ""
    override fun runCurrentAsJsConsole() {}
    override fun openVirtEnvironment() {}

    override fun requestInstall(apk: CandyPath, pkg: String?) {}
    override fun importCertInto(project: CandyPath, uri: android.net.Uri): String? = null
}
