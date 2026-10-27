package candy.ide.desktop.plugin.android

import candy.ide.desktop.plugin.PluginHost
import java.io.File

/**
 * android-project 插件资产物化结果（对齐安卓端 candy.plugin.android.AndroidAssets，目录结构一致）：
 * %APPDATA%\CandyIDE\plugins\android-project\android
 *  - stdlib/：安卓标准库（@android 内建）
 *  - procs/：安卓注解处理器脚本（.subs）
 *
 * 资产来源：desktop 构建期 syncAndroidPluginAssets 任务自 :plugin-android 模块资产同步，
 * 经 classpath 资源 + assets-index.txt 由宿主 materializeAssets 物化（版本号与安卓端一致，4）。
 */
object AndroidAssets {
    const val PLUGIN_ID = "android-project"
    const val VERSION = 4   // 处理器/安卓标准库更新后递增（同安卓端 androidAssetVersion）

    lateinit var root: File
        private set

    /** 插件资产内的安卓运行时目录（classpath 资源根下的全路径，t50 后统一在 candy-plugins/<id>/ 下） */
    const val ASSET_PATH = "candy-plugins/" + PLUGIN_ID + "/android"

    fun setup(host: PluginHost) {
        root = host.materializeAssets(PLUGIN_ID, ASSET_PATH, VERSION)
    }

    fun stdlib(): File = File(root, "stdlib")
    fun procs(): File = File(root, "procs")
}
