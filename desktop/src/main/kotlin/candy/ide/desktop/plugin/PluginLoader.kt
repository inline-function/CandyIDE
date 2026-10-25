package candy.ide.desktop.plugin

/**
 * 插件装载器（加载机制；对齐安卓端 candy.ide.plugin.PluginLoader，资产来源由 assets 换成 classpath 资源）：
 *  1. 读资源 candy-plugins/plugin-index.txt（构建期生成：classpath 无法列目录）得到全部插件 id（排序后装载）；
 *  2. 逐插件读 candy-plugins/<id>/candy-plugin.json -> PluginManifest.parse（非法清单直接跳过，不影响其它插件）；
 *  3. [resolver] 把清单 id 绑定到插件实例（v1：宿主内置编译期索引；v2 可换动态解析）；
 *  4. 清单 id 与实例清单 id 不一致的跳过并记日志（防止资产与代码错配）；
 *  5. 在 IO 线程逐个 setup；单个插件装配失败只记日志，不影响其它插件。
 */
object PluginLoader {

    /** 插件索引资源路径（构建期 genPluginIndex 任务生成） */
    private const val INDEX_PATH = PluginManifest.ASSET_ROOT + "/plugin-index.txt"

    fun loadAll(host: PluginHost, resolver: (String) -> CandyPlugin?): List<LoadedPlugin> {
        val out = ArrayList<LoadedPlugin>()
        val ids = readPluginIndex()
        for (id in ids) {
            val manifest = readManifest(id) ?: continue
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

    private fun readPluginIndex(): List<String> {
        val txt = resourceText(INDEX_PATH) ?: return emptyList()
        return txt.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }

    private fun readManifest(id: String): PluginManifest? {
        val txt = resourceText(PluginManifest.ASSET_ROOT + "/" + id + "/" + PluginManifest.FILE_NAME)
            ?: return null
        if (txt.isEmpty()) return null
        return runCatching { PluginManifest.parse(txt) }.getOrNull()?.takeIf { it.id.isNotEmpty() }
    }
}
