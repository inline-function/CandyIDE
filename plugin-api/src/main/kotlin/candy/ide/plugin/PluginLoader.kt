package candy.ide.plugin

import android.content.Context

/**
 * 插件装载器（加载机制）：
 *  1. 扫描 assets/candy-plugins/&lt;id&gt;/candy-plugin.json 得到清单；
 *  2. 经 [resolver] 把清单 id 绑定到插件实例（v1：宿主内置编译期索引；v2 可换 DEX 动态解析）；
 *  3. 清单 id 与实例清单 id 不一致的跳过并记日志（防止资产与代码错配）；
 *  4. 在 IO 线程逐个 setup；单个插件装配失败只记日志，不影响其它插件。
 */
object PluginLoader {

    fun loadAll(context: Context, host: PluginHost, resolver: (String) -> CandyPlugin?): List<LoadedPlugin> {
        val out = ArrayList<LoadedPlugin>()
        val dirs = runCatching { context.assets.list(PluginManifest.ASSET_ROOT) }.getOrNull() ?: emptyArray()
        for (id in dirs.sorted()) {
            val manifest = readManifest(context, id) ?: continue
            val instance = runCatching { resolver(manifest.id) }.getOrNull()
            if (instance != null && instance.manifest.id != manifest.id) {
                host.log("**[插件]** 装配跳过 " + manifest.id + "：清单 id 与实现不一致（" + instance.manifest.id + "）")
                out += LoadedPlugin(manifest, null)
                continue
            }
            out += LoadedPlugin(manifest, instance)
        }
        PluginRegistry.setLoaded(out)
        for (p in out) {
            val inst = p.instance ?: continue
            runCatching { inst.setup(host) }.onFailure {
                host.log("**[插件]** " + p.manifest.id + " 装配失败：" + (it.message ?: it.javaClass.simpleName))
            }
        }
        PluginRegistry.ready = true
        return out
    }

    private fun readManifest(context: Context, id: String): PluginManifest? {
        return runCatching {
            context.assets.open(PluginManifest.ASSET_ROOT + "/" + id + "/" + PluginManifest.FILE_NAME)
                .use { it.readBytes().toString(Charsets.UTF_8).trim() }
        }.getOrNull()?.let { txt -> if (txt.isEmpty()) null else runCatching { PluginManifest.parse(txt) }.getOrNull() }
            ?.takeIf { it.id.isNotEmpty() }
    }
}
