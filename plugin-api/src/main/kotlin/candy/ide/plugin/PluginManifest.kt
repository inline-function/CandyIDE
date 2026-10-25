package candy.ide.plugin

/**
 * 插件清单：随插件资产发布，路径固定为 assets/candy-plugins/&lt;id&gt;/candy-plugin.json。
 * 清单负责「声明」：插件是谁、提供哪些贡献点、入口类是谁；
 * 实际注册在 [CandyPlugin.setup] 里按代码完成（声明字段用于展示与校验）。
 */
data class PluginManifest(
    /** 唯一 id（kebab-case），同时是资产目录名与注册键 */
    val id: String,
    /** 显示名 */
    val name: String,
    /** 插件版本 */
    val version: String = "1.0.0",
    /** 一句话描述 */
    val description: String = "",
    val author: String? = null,
    /** CandyPlugin 实现类全名。v1 由宿主内置索引（BuiltInPluginIndex）解析；v2 可走 DEX 动态加载 */
    val entryClass: String? = null,
    /** 声明的贡献点列表（形如 "project-type:jsapk"），仅用于展示/校验 */
    val contributions: List<String> = emptyList(),
    /** 要求的最低 IDE 版本（不满足则不装载） */
    val minIdeVersion: String? = null,
) {
    companion object {
        const val FILE_NAME = "candy-plugin.json"
        const val ASSET_ROOT = "candy-plugins"

        fun parse(json: String): PluginManifest {
            val o = org.json.JSONObject(json)
            return PluginManifest(
                id = o.optString("id").trim(),
                name = o.optString("name").ifBlank { o.optString("id").trim() },
                version = o.optString("version", "1.0.0"),
                description = o.optString("description"),
                author = o.optString("author").ifBlank { null },
                entryClass = o.optString("entryClass").ifBlank { null },
                contributions = runCatching {
                    val a = o.getJSONArray("contributions")
                    (0 until a.length()).map { a.getString(it) }
                }.getOrDefault(emptyList()),
                minIdeVersion = o.optString("minIdeVersion").ifBlank { null },
            )
        }
    }
}
