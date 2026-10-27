package candy.plugin.android

import candy.ide.plugin.CandyPlugin
import candy.ide.plugin.PluginHost
import candy.ide.plugin.PluginManifest
import candy.plugin.android.install.ShizukuInstaller
import candy.plugin.android.litea.LiteASdk
import candy.plugin.android.project.AndroidProjectType
import candy.plugin.android.project.AndroidPackaging
import candy.plugin.android.ui.AndroidEditorView
import candy.plugin.android.ui.JsApkProjectSection
import candy.plugin.android.ui.LiteaSdkSection

/**
 * android-project 插件：CandyIDE 的内置安卓工程支持（自 :app 拆出）。
 * 提供贡献点：
 *  - project-type:jsapk（TYPE_JSAPK 工程类型 + 打包链路）
 *  - editor-view:compose-preview（布局预览三件套）
 *  - settings-section:litea-sdk（LiteASDK 工具链导入）
 *  - project-settings-section:jsapk（jsapk.json 打包配置）
 *  - app-installer:shizuku（静默安装通道）
 */
class AndroidProjectPlugin : CandyPlugin {

    override val manifest = PluginManifest(
        id = AndroidAssets.PLUGIN_ID,
        name = "安卓工程支持",
        version = "1.0.0",
        description = "安卓原生工程：@composable 视图 + @android 内建，编译为原生 APK；" +
            "含 jsapk 打包、LiteASDK 工具链导入、Shizuku 静默安装与布局预览虚拟桥接",
        author = "CandyIDE",
        entryClass = "candy.plugin.android.AndroidProjectPlugin",
        contributions = listOf(
            "project-type:jsapk",
            "editor-view:compose-preview",
                "settings-section:litea-sdk",
            "project-settings-section:jsapk",
            "app-installer:shizuku",
        ),
    )

    override fun setup(host: PluginHost) {
        // 1) 物化插件资产（android/stdlib + android/procs），版本 4 与旧 androidBootstrap 对齐
        AndroidAssets.setup(host)
        val stdlib = AndroidAssets.stdlib()
        val procs = AndroidAssets.procs()

        // 2) 贡献点（语言服务扩展由 SfA 平台插件提供，共用同一物化资产）
        host.registerProjectType(AndroidProjectType())
        host.registerEditorView(AndroidEditorView())
        host.registerSettingsSection(LiteaSdkSection())
        host.registerProjectSettingsSection(JsApkProjectSection())
        host.registerAppInstaller(ShizukuInstaller())
    }

}
