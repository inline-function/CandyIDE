package candy.plugin.android

import candy.ide.plugin.PluginHost
import java.io.File

/**
 * 插件资产物化结果：files/candy/plugins/android-project/android
 *  - stdlib/：安卓标准库（@android 内建）
 *  - procs/：安卓注解处理器脚本（.subs）
 * 与旧 files/candy/android 同构，插件化后改存插件私有目录（namespace 隔离）。
 */
object AndroidAssets {
    const val PLUGIN_ID = "android-project"
    const val VERSION = 4   // 处理器/安卓标准库更新后递增（同旧 androidAssetVersion）

    lateinit var root: File
        private set

    /** 插件资产内的安卓运行时目录（assets 根下的全路径，t15 后插件资产统一在 candy-plugins/<id>/ 下） */
    const val ASSET_PATH = "candy-plugins/" + PLUGIN_ID + "/android"

    fun setup(host: PluginHost) {
        root = host.materializeAssets(PLUGIN_ID, ASSET_PATH, VERSION)
    }

    fun stdlib(): File = File(root, "stdlib")
    fun procs(): File = File(root, "procs")
}
