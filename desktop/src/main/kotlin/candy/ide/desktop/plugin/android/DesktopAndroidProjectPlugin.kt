package candy.ide.desktop.plugin.android

import candy.ide.desktop.plugin.CandyPlugin
import candy.ide.desktop.plugin.PluginHost
import candy.ide.desktop.plugin.PluginManifest
import java.io.File

/**
 * android-project 插件（Windows 端内置裁剪版；对齐安卓端 candy.plugin.android.AndroidProjectPlugin）。
 *
 * 提供贡献点：
 *  - project-type:jsapk（jsapk 工程类型：scaffold/claimsProject/runShort/packageProject）
 *  - lsp-extension:android-stdlib（@android 内建与处理器脚本的编辑期支持，启停按工程类型）
 *  - editor-view:compose-preview（编辑器右侧栏布局预览）
 *  - project-settings-section:jsapk-info（jsapk 工程的工程设置说明分区）
 *
 * 安卓端有而桌面端不提供的（平台差异，详见 README 差异表）：
 *  - app-installer:shizuku —— Shizuku 静默安装（Windows 无此通道）
 *  - settings-section:litea-sdk —— LiteASDK 端上工具链导入（安卓端打包链专用）
 *  - project-settings-section:jsapk —— jsapk.json 打包配置（桌面端不做打包）
 */
class DesktopAndroidProjectPlugin : CandyPlugin {

    override val manifest = PluginManifest(
        id = AndroidAssets.PLUGIN_ID,
        name = "安卓工程支持",
        version = "1.0.0",
        description = "安卓原生工程：@composable 视图 + @android 内建；桌面端提供编辑期诊断、" +
            "布局预览与 JS 控制台运行（APK 打包/Shizuku 安装为安卓端专属能力）",
        author = "CandyIDE",
        entryClass = "candy.ide.desktop.plugin.android.DesktopAndroidProjectPlugin",
        contributions = listOf(
            "project-type:jsapk",
            "editor-view:compose-preview",
            "lsp-extension:android-stdlib",
            "project-settings-section:jsapk-info",
        ),
    )

    private var lspExtension: DesktopAndroidLspExtension? = null

    override fun setup(host: PluginHost) {
        // 1) 物化插件资产（android/stdlib + android/procs），版本 4 与安卓端 androidBootstrap 对齐
        AndroidAssets.setup(host)
        val stdlib = AndroidAssets.stdlib()
        val procs = AndroidAssets.procs()

        // 2) 语言服务扩展：目录已固定，启停按工程类型实时切换
        val ext = DesktopAndroidLspExtension(stdlib, procs)
        lspExtension = ext
        host.registerLspExtension(ext)

        // 3) 贡献点
        host.registerProjectType(DesktopAndroidProjectType())
        host.registerEditorView(DesktopAndroidEditorView())
        host.registerProjectSettingsSection(JsApkInfoSection())
    }

    override fun onProjectTypeChanged(typeId: String, dir: File?) {
        // 安卓工程才启用 android stdlib/procs 的 LSP 接入（同安卓端 setAndroidStdlibEnabled）。
        // 注意：桌面端多窗口共享单例 LSP 分析器，启停跟随最近打开的工程（单窗口与安卓端一致）。
        lspExtension?.enabled = typeId == DesktopAndroidProjectType.TYPE_ID
    }
}
