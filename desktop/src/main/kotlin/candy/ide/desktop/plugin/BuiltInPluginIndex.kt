package candy.ide.desktop.plugin

import candy.ide.desktop.plugin.android.DesktopAndroidProjectPlugin

/**
 * 内置插件索引（v1 加载机制；对齐安卓端 candy.ide.plugin.BuiltInPluginIndex）：
 * 把资源 candy-plugins/<id>/candy-plugin.json 的清单 id 绑定到随 desktop 编译的插件实例。
 * v2 可换成按 entryClass 动态加载的实现，PluginLoader 的 resolver 参数是唯一的替换点。
 *
 * desktop 当前内置：android-project（Windows 端裁剪版——jsapk 工程的编辑/运行/LSP/预览，
 * 不含 Shizuku 安装通道、LiteASDK 与 APK 打包链）。
 */
object BuiltInPluginIndex {

    val entries: Map<String, () -> CandyPlugin> = mapOf(
        "android-project" to { DesktopAndroidProjectPlugin() },
    )

    fun resolve(id: String): CandyPlugin? = entries[id]?.invoke()
}
