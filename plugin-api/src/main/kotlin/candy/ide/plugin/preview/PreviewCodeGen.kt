package candy.ide.plugin.preview

import org.json.JSONObject

/**
 * 布局预览视图树 → Sublimation 源码（控制台函数体）：
 *  - 与 ComposePreviewPanel 的渲染树同构：Box/Text/TextField/Button/Chart/Image 与 @composable 调用；
 *  - 属性值按树里保存的字面量原样回写（"文本" / 14 / true / "horizontal"）；
 *  - Chart 需要必填 points/labels，统一再生成为 emptyList() 兜底；
 *  - @composable 调用按参数摘要回写（rowsView(meds)）；复杂实参摘要不了的以无参形式生成，
 *    由诊断页提示补全（编辑器撤销可整体回退）。
 */
object PreviewCodeGen {

    /** 视图树 → @composable 函数体（不含签名行）：下调一个缩进层次 */
    fun bodyLines(tree: JSONObject): List<String> {
        val kids = runCatching { tree.getJSONArray("children") }.getOrNull() ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until kids.length()) {
            out += nodeLines(kids.getJSONObject(i), 1)
        }
        return out
    }

    /** 单节点源码（插入用）：行集合，容器节点带子节点 */
    fun nodeSource(node: JSONObject, indent: Int): List<String> = nodeLines(node, indent)

    /** 单行形式（插进 children = listOf(...) 实参用） */
    fun nodeInline(node: JSONObject): String {
        val lines = nodeLines(node, 0).joinToString(" ")
        return lines.replace("\n", " ")
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
                val ori = props?.optString("orientation")?.trim('\u0022')
                if (ori == "horizontal" || ori == "vertical") args += "orientation = \"" + ori + "\""
                val head = pad + name + "(" + args.joinToString(", ") + ")"
                return if (kidsLines.isEmpty()) listOf(head + " {}") else listOf(head + " {") + kidsLines + listOf(pad + "}")
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
                return if (kidsLines.isEmpty()) listOf(head) else listOf(head + " {") + kidsLines + listOf(pad + "}")
            }
            "Button" -> {
                val head = pad + "Button(onClick = {})"
                return if (kidsLines.isEmpty()) listOf(head) else listOf(head + " {") + kidsLines + listOf(pad + "}")
            }
            "Chart" -> {
                val kind = props?.optString("kind")?.ifEmpty { null }
                return listOf(pad + "Chart(points = emptyList(), labels = emptyList()" + (if (kind != null) ", kind = " + kind else "") + ")")
            }
            "Image" -> return listOf(pad + "Image(modifier = Plain())")
            else -> {
                // @composable 调用：实参摘要原样回写（rowsView(meds)）
                val argArr = runCatching { node.getJSONArray("args") }.getOrNull()
                val argList = if (argArr != null) (0 until argArr.length()).map { argArr.getString(it) } else emptyList()
                return listOf(pad + name.removePrefix("@") + "(" + argList.joinToString(", ") + ")")
            }
        }
    }

    /** props 里可回写的字面量属性（props 的值已是 "x"/14/true 形式） */
    private fun appendProps(props: JSONObject?, args: MutableList<String>, skip: List<String>) {
        if (props == null) return
        for (key in props.keys()) {
            if (key in skip) continue
            if (key == "modifier") continue   // Plain() 是预览默认，不覆盖用户布局
            val v = props.optString(key)
            if (v.isNotEmpty()) args += key + " = " + v
        }
    }
}
