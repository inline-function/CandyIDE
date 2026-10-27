package candy.ide.desktop.plugin.host

import candy.ide.desktop.DesktopEngine
import candy.ide.desktop.IdeState
import candy.ide.desktop.OutputLine
import candy.ide.desktop.data.ProjectStore
import candy.ide.desktop.plugin.PluginHost
import candy.ide.desktop.plugin.PluginRegistry
import candy.ide.desktop.plugin.contrib.AppInstaller
import candy.ide.desktop.plugin.contrib.EditorViewContribution
import candy.ide.desktop.plugin.contrib.ProjectSettingsSectionContribution
import candy.ide.desktop.plugin.contrib.ProjectTypeContribution
import candy.ide.desktop.plugin.contrib.SettingsSectionContribution
import sugared.functor.lsp.LspExtension
import java.io.File

/**
 * PluginHost 的桌面宿主实现（对齐安卓端 candy.ide.vm.CandyPluginHost）：
 * 把插件的服务调用映射到 IdeState 的能力（日志/输出/线程/工程类型/语言服务——插件不直接持有 state）。
 *
 * 桌面端为多窗口：每个 IdeState（主页 + 各编辑器窗口）各持一个本类实例；
 * 插件 setup 在启动时用主页实例注册全局贡献，运行期调用（如 runShort）由
 * 发起调用的窗口构造本实例传入，使日志/toast/输出落在正确的窗口。
 *
 * 声明为 open：单测可重定向 pluginDataDir 到临时目录，避免写真实 %APPDATA%（生产行为不变）。
 */
open class DesktopPluginHost(val state: IdeState) : PluginHost {

    override fun candyRoot(): File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "CandyIDE")

    override fun pluginDataDir(pluginId: String): File = File(File(candyRoot(), "plugins"), pluginId)

    override fun materializeAssets(pluginId: String, assetRoot: String, version: Int, destRel: String): File {
        // t50 对齐：插件资产统一在 candy-plugins/<id>/ 下，assetRoot/destRel 为「资源根下全路径」
        // （如 candy-plugins/android-project/android）。桌面端落盘时规范化掉该前缀，
        // 物化目录保持 plugins/<id>/<相对路径>（t16 布局，旧安装的 .version 标记继续有效）；
        // 前缀缺失（插件相对路径）的旧调用也照常工作。
        val assetPrefix = "candy-plugins/$pluginId/"
        val relAsset = assetRoot.removePrefix(assetPrefix)
        val relDest = destRel.removePrefix(assetPrefix)
        val target = File(pluginDataDir(pluginId), relDest)
        val marker = File(target.parentFile, target.name + ".version")
        val need = !marker.exists() || marker.readText().trim() != version.toString()
        if (need) {
            target.deleteRecursively(); target.mkdirs()
            // classpath 无法列目录：资产相对路径来自构建期生成的 assets-index.txt（插件目录内相对路径）
            val index = candy.ide.desktop.plugin.resourceText(
                PluginManifestAssetPath(pluginId)
            ) ?: return target
            for (line in index.lineSequence()) {
                val rel = line.trim()
                if (rel.isEmpty() || !rel.startsWith("$relAsset/")) continue
                val res = "candy-plugins/$pluginId/$rel"
                val stream = candy.ide.desktop.plugin.resourceStream(res) ?: continue
                val out = File(target, rel.removePrefix("$relAsset/"))
                out.parentFile?.mkdirs()
                runCatching { stream.use { it.copyTo(out.outputStream()) } }
            }
            marker.writeText(version.toString())
        }
        return target
    }

    // ---- 贡献注册（全局注册表去重） ----

    override fun registerProjectType(contrib: ProjectTypeContribution) = PluginRegistry.registerProjectType(contrib)
    override fun registerEditorView(contrib: EditorViewContribution) = PluginRegistry.registerEditorView(contrib)
    override fun registerLspExtension(extension: LspExtension) {
        PluginRegistry.registerLspExtension(extension)
        // 与 LspAnalysis 创建时序解耦：插件晚于首轮分析注册时直达实时分析器（t50 对齐）
        DesktopEngine.registerLiveLspExtension(extension)
    }
    override fun registerSettingsSection(contrib: SettingsSectionContribution) = PluginRegistry.registerSettingsSection(contrib)
    override fun registerProjectSettingsSection(contrib: ProjectSettingsSectionContribution) = PluginRegistry.registerProjectSettingsSection(contrib)
    override fun registerAppInstaller(installer: AppInstaller) = PluginRegistry.registerAppInstaller(installer)

    // ---- 运行期服务 ----

    override fun log(msg: String) = state.log(msg, OutputLine.Kind.Info)
    override fun toast(msg: String) { state.toast = msg }
    override fun postMain(action: () -> Unit) = action()   // Compose 快照状态可跨线程写，直接执行
    override fun runOnIo(action: () -> Unit) {
        kotlin.concurrent.thread(name = "candy-plugin-io", isDaemon = true) { runCatching(action) }
    }
    override fun setBusy(busy: Boolean) { state.running = busy }

    // ---- 工程 ----

    override fun currentProjectDir(): File? = state.currentProject
    override fun currentEditingFile(): File? = state.activeFile
    override fun projectTypeOf(dir: File): String = ProjectStore.typeOf(dir)
    override fun currentFileText(): String? = state.activeFile?.let { state.fileTexts[it.absolutePath] }

    // ---- 语言/编辑器能力 ----

    override fun composableTreeJson(line: Int, col: Int): String? {
        val f = state.activeFile ?: return null
        val text = state.fileTexts[f.absolutePath] ?: return null
        return DesktopEngine.composableTree(DesktopEngine.uriOf(f), text, line, col)
    }

    override fun compileToJs(script: File, onCompiled: ((Long) -> Unit)?): String =
        DesktopEngine.compileToJs(script, onCompiled)

    override fun runCurrentAsJsConsole() = state.runJsConsole()
}

/** 插件资产索引资源路径 */
private fun PluginManifestAssetPath(pluginId: String): String = "candy-plugins/$pluginId/assets-index.txt"
