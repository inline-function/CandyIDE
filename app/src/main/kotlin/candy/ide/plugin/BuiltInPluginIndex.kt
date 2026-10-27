package candy.ide.plugin

/**
 * 内置插件索引（v1 加载机制）：把 assets/candy-plugins/<id>/candy-plugin.json 的清单 id
 * 绑定到随 app 编译的插件实例。v2 可换成按 entryClass 从 DEX 动态加载的实现，
 * PluginLoader 的 resolver 参数是唯一的替换点。
 */
object BuiltInPluginIndex {

    val entries: Map<String, () -> CandyPlugin> = mapOf(
        "android-project" to { candy.plugin.android.AndroidProjectPlugin() },
        // 平台插件（系统级：SfA/SfG/SfW 实体，语言服务扩展提供方）
        "sublimation-android" to { candy.plugin.android.platform.SublimationAndroidPlugin() },
        "sublimation-game" to { candy.plugin.android.platform.SublimationGamePlugin() },
        "sublimation-wasm" to { candy.plugin.android.platform.SublimationWasmPlugin() },
    )

    fun resolve(id: String): CandyPlugin? = entries[id]?.invoke()
}
