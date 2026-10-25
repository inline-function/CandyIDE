package candy.ide.desktop.plugin.preview

import org.json.JSONArray
import org.json.JSONObject

/**
 * @composable 布局预览状态（对齐安卓端 candy.ide.plugin.preview.ComposePreviewState）：
 * 函数名 + 声明行范围 + 视图树 JSON（来自 LSP composableTreeJson）。
 */
data class ComposePreviewState(
    val fnName: String,
    val start: Int,
    val end: Int,
    val treeJson: String,
)

/**
 * 视图树 -> Sublimation @composable 函数体（与安卓端 PreviewCodeGen 同构；自编辑器侧栏迁入插件 SDK）。
 */
object PreviewCodeGen {
    fun bodyLines(tree: JSONObject): List<String> {
        val kids = runCatching { tree.getJSONArray("children") }.getOrNull() ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until kids.length()) {
            out += nodeLines(kids.getJSONObject(i), 1)
        }
        return out
    }

    private fun nodeLines(node: JSONObject, indent: Int): List<String> {
        val pad = "    ".repeat(indent)
        val name = node.optString("name")
        val props = runCatching { node.getJSONObject("props") }.getOrNull()
        val kids = runCatching { node.getJSONArray("children") }.getOrNull()
        val kidsLines = if (kids != null) {
            (0 until kids.length()).flatMap { nodeLines(kids.getJSONObject(it), indent + 1) }
        } else emptyList()
        val args = mutableListOf<String>()
        when (name) {
            "Box", "Column", "Row" -> {
                args += "modifier = Plain()"
                val ori = props?.optString("orientation")?.trim('"')
                if (ori == "horizontal" || ori == "vertical") args += "orientation = \"" + ori + "\""
                val head = pad + name + "(" + args.joinToString(", ") + ")"
                return if (kidsLines.isEmpty()) listOf(head + " {{}}") else listOf(head + " {{}") + kidsLines + listOf(pad + "}")
            }
            "Text", "label" -> {
                val t = node.optString("text").ifEmpty { props?.optString("text") ?: "" }
                if (t.isNotEmpty()) args += "text = " + t
                appendProps(props, args, listOf("text"))
                if (args.isEmpty()) args += "text = \"\""
                return listOf(pad + "Text(" + args.joinToString(", ") + ")")
            }
            "TextField" -> {
                appendProps(props, args, listOf("text"))
                if (args.isEmpty()) args += "placeholder = \"\""
                val head = pad + "TextField(" + args.joinToString(", ") + ")"
                return if (kidsLines.isEmpty()) listOf(head) else listOf(head + " {{}") + kidsLines + listOf(pad + "}")
            }
            "Button" -> {
                val head = pad + "Button(onClick = {{}})"
                return if (kidsLines.isEmpty()) listOf(head) else listOf(head + " {{}") + kidsLines + listOf(pad + "}")
            }
            "Chart" -> {
                val kind = props?.optString("kind")?.ifEmpty { null }
                return listOf(pad + "Chart(points = emptyList(), labels = emptyList()" + (if (kind != null) ", kind = " + kind else "") + ")")
            }
            "Image" -> return listOf(pad + "Image(modifier = Plain())")
            else -> {
                val argArr = runCatching { node.getJSONArray("args") }.getOrNull()
                val argList = if (argArr != null) (0 until argArr.length()).map { argArr.getString(it) } else emptyList()
                return listOf(pad + name.removePrefix("@") + "(" + argList.joinToString(", ") + ")")
            }
        }
    }

    private fun appendProps(props: JSONObject?, args: MutableList<String>, skip: List<String>) {
        if (props == null) return
        for (key in props.keys()) {
            if (key in skip) continue
            if (key == "modifier") continue
            val v = props.optString(key)
            if (v.isNotEmpty()) args += key + " = " + v
        }
    }
}

/** 视图树 JSON 工具（预览面板树编辑用） */
internal fun JSONObject.deepCopy(): JSONObject = JSONObject(this.toString())

internal fun nodeAt(root: JSONObject, path: List<Int>): JSONObject? {
    var cur: JSONObject = root
    for (i in path) {
        val kids = runCatching { cur.getJSONArray("children") }.getOrNull() ?: return null
        if (i < 0 || i >= kids.length()) return null
        cur = kids.getJSONObject(i)
    }
    return cur
}

internal fun removeChild(parent: JSONObject, index: Int) {
    runCatching { parent.getJSONArray("children") }.getOrNull()?.let { arr ->
        if (index in 0 until arr.length()) {
            val kids = (0 until arr.length()).map { arr.getJSONObject(it) }.toMutableList()
            kids.removeAt(index)
            parent.put("children", JSONArray(kids))
        }
    }
}

internal fun addChild(parent: JSONObject, node: JSONObject) {
    val arr = runCatching { parent.getJSONArray("children") }.getOrNull() ?: JSONArray().also { parent.put("children", it) }
    arr.put(node)
}

internal fun mkBox(): JSONObject = JSONObject().apply {
    put("name", "Box")
    put("props", JSONObject().apply { put("modifier", "Plain()") })
}

internal fun mkText(t: String): JSONObject = JSONObject().apply {
    put("name", "Text")
    put("text", "\"" + t + "\"")
    put("props", JSONObject().apply { put("size", "14") })
}
