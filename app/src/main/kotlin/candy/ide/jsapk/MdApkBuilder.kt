package candy.ide.jsapk

import android.content.Context
import candy.ide.engine.Engine
import java.io.File
import java.util.zip.ZipFile

/**
 * md 文档工程打包：
 *   收集工程内全部 .md → 文档数据（JSON）→ 注入阅读器 viewer.js（含文档数据）
 *   → 复用 jsapk 模板 APK 的 WebView 容器（assets/app.js）→ v1 签名 → <工程>/build/app.apk。
 *
 * viewer 在 WebView 里渲染文档列表 + 正文，文档只读不可编辑。
 */
object MdApkBuilder {

    private const val VIEWER_ASSET = "sdk/md/viewer.js"

    /** 打包 md 文档工程：返回产物 apk。无 .md 抛异常。 */
    fun build(ctx: Context, project: File, cfg: JsApkConfig, onCollected: ((Int) -> Unit)? = null): File {
        ApkSigner.attach(ctx.applicationContext)

        // 1) 收集工程内全部 .md（相对工程根的路径做键，排序保证多次打包条目稳定）
        val docs = LinkedHashMap<String, String>()
        project.walkTopDown().filter { it.isFile && it.name.endsWith(".md") }.sortedBy { it.relativeTo(project).path }
            .forEach { f ->
                runCatching { docs[f.relativeTo(project).path] = f.readText() }
            }
        if (docs.isEmpty()) throw RuntimeException("工程里没有 .md 文档")
        onCollected?.invoke(docs.size)

        // 2) 阅读器壳：viewer.js 模板 + 内联文档数据（避免运行期网络/文件访问）
        val viewer = ctx.assets.open(VIEWER_ASSET).bufferedReader().use { it.readText() }
        val js = viewer
            .replace("__DOCS_JSON__", jsonLiteral(docs))
            .replace("__INDEX_JSON__", jsonList(docs.keys))
            .toByteArray(Charsets.UTF_8)

        // 3) 读模板并注入（与 JsApkBuilder 同一路径，保持模板版本一致）
        val tmp = File(Engine.root(ctx), "sdk/jsapk/template-" + JsApkBuilder.TEMPLATE_VERSION + ".apk")
        tmp.parentFile?.mkdirs()
        ctx.assets.open(JsApkBuilder.ASSET_TEMPLATE).use { input -> tmp.outputStream().use { input.copyTo(it) } }

        val entries = ArrayList<Pair<String, ByteArray>>()
        ZipFile(tmp).use { zf ->
            val seen = HashSet<String>()
            val it = zf.entries()
            while (it.hasMoreElements()) {
                val ze = it.nextElement()
                val name = ze.name
                if (name.startsWith("META-INF/")) continue
                if (name == "assets/app.js") continue
                if (!seen.add(name)) continue
                entries.add(name to zf.getInputStream(ze).use { s -> s.readBytes() })
            }
        }
        entries.add("assets/app.js" to js)

        // 4) 按配置改写包名/应用名（与 jsapk 同一改写规则）
        val samePkg = cfg.pkg == JsApkConfig.DEFAULT_PACKAGE &&
            cfg.label == "Sublimation 应用" && cfg.versionCode == 1 && cfg.versionName == "1.0"
        if (!samePkg) {
            val edits = HashMap<String, String>()
            edits[JsApkConfig.DEFAULT_PACKAGE] = cfg.pkg
            edits["1.0"] = cfg.versionName
            edits["Sublimation 应用"] = cfg.label
            for (i in entries.indices) {
                val en = entries[i]
                if (en.first == "AndroidManifest.xml") {
                    entries[i] = en.first to AxmlEditor.patchManifest(en.second, edits, mapOf("versionCode" to cfg.versionCode))
                } else if (en.first == "resources.arsc") {
                    AxmlEditor.patchArscPackage(en.second, cfg.pkg)
                }
            }
        }

        // 5) 输出 + 签名
        val outDir = File(project, "build")
        if (!outDir.exists()) outDir.mkdirs()
        val apk = File(outDir, "app.apk")
        if (apk.exists()) apk.delete()
        tmp.copyTo(apk, overwrite = true)
        ApkSigner.signV1(apk, entries)
        return apk
    }

    /** Kotlin Map → JS 对象字面量（键含路径分隔符，统一用引号包裹） */
    private fun jsonLiteral(docs: Map<String, String>): String {
        val sb = StringBuilder("{")
        docs.entries.forEachIndexed { i, e ->
            if (i > 0) sb.append(',')
            sb.append(jsString(e.key)).append(':').append(jsString(e.value))
        }
        sb.append('}')
        return sb.toString()
    }

    private fun jsonList(keys: Set<String>): String =
        keys.joinToString(",", "[", "]") { jsString(it) }

    /** JS 字符串字面量：转义引号/反斜杠/换行/制表符，避免破坏 viewer 与文档内容 */
    private fun jsString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        sb.append('"')
        return sb.toString()
    }
}