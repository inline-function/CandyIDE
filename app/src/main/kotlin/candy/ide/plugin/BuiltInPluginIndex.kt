package candy.ide.plugin

/**
 * 内置插件索引（v1 加载机制）：把 assets/candy-plugins/<id>/candy-plugin.json 的清单 id
 * 绑定到随 app 编译的插件实例。v2 可换成按 entryClass 从 DEX 动态加载的实现，
 * PluginLoader 的 resolver 参数是唯一的替换点。
 */
object BuiltInPluginIndex {

    val entries: Map<String, () -> CandyPlugin> = mapOf(
        "android-project" to { candy.plugin.android.AndroidProjectPlugin() },
    )

    fun resolve(id: String): CandyPlugin? = entries[id]?.invoke()
}
