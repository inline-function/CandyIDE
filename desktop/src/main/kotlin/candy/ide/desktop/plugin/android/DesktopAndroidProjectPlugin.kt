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
 *  - editor-view:compose-preview（编辑器右侧栏布局预览）
 *  - project-settings-section:jsapk-info（jsapk 工程的工程设置说明分区）
 *
 * 安卓端有而桌面端不提供的（平台差异，详见 README 差异表）：
 *  - app-installer:shizuku —— Shizuku 静默安装（Windows 无此通道）
 *  - settings-section:litea-sdk —— LiteASDK 端上工具链导入（安卓端打包链专用）
 *  - project-settings-section:jsapk —— jsapk.json 打包配置（桌面端不做打包）
 *
 * lsp-extension:android-stdlib（@android 内建与处理器脚本的编辑期支持）由系统插件
 * SfA（candy.ide.desktop.plugin.platform.SublimationAndroidPlugin，t50 对齐）接管——
 * 与安卓端同构：单一来源注册，避免双扩展竞争注册表。
 */
class DesktopAndroidProjectPlugin : CandyPlugin {

    override val manifest = PluginManifest(
        id = AndroidAssets.PLUGIN_ID,
        name = "安卓工程支持",
        version = "1.0.0",
        description = "安卓原生工程：@composable 视图 + @android 内建；桌面端提供编辑期诊断、" +
            "布局预览与 JS 控制台运行（APK 打包/Shizuku 安装为安卓端专属能力；@android 语言服务由 SfA 系统插件提供）",
        author = "CandyIDE",
        entryClass = "candy.ide.desktop.plugin.android.DesktopAndroidProjectPlugin",
        contributions = listOf(
            "project-type:jsapk",
            "editor-view:compose-preview",
            "project-settings-section:jsapk-info",
        ),
    )

    override fun setup(host: PluginHost) {
        // 1) 物化插件资产（android/stdlib + android/procs），版本 4 与安卓端 androidBootstrap 对齐；
        //    SfA 系统插件 setup 时也调它（幂等，版本标记命中即不重抽）
        AndroidAssets.setup(host)

        // 2) 贡献点（语言服务扩展由 SfA 接管）
        host.registerProjectType(DesktopAndroidProjectType())
        host.registerEditorView(DesktopAndroidEditorView())
        host.registerProjectSettingsSection(JsApkInfoSection())
    }
}
