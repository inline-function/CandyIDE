package candy.ide.plugin.preview

import org.json.JSONObject
import candy.ide.plugin.preview.PreviewCodeGen
import java.util.regex.Pattern

/**
 * 预览页退出时把视图树写回源码 —— 只针对改动的节点做精确补丁，不重生成函数体
 * （重生成会丢 @mut var 声明与按钮 onClick 逻辑）。
 *
 * 节点里的 span 是 LSP 给的源码字符区间，写回策略：
 *  - 复用节点（签名相同）属性/文案变了 → 在节点源码片内就地改 key = value
 *  - 新增节点 → 生成源码插入父节点的尾 lambda / children=listOf(...) 内
 *  - 删除节点 → 删掉节点所在行；若属 var 声明的初值则整行删掉
 *
 * 同一 var 被内联到多处时它们共享同一 span：按区间去重，倒序应用。
 */
object PreviewPatch {

    fun apply(content: String, origJson: String, editedJson: String): String? {
        val orig = runCatching { JSONObject(origJson) }.getOrNull() ?: return null
        val edited = runCatching { JSONObject(editedJson) }.getOrNull() ?: return null
        val ops = ArrayList<Op>()
        diff(orig, edited, content, ops)
        if (ops.isEmpty()) return null
        val sb = StringBuilder(content)
        ops.distinctBy { it.start * 1000003L + it.end }
            .sortedByDescending { it.start }
            .forEach { op ->
                if (op.start in 0..op.end && op.end <= sb.length) {
                    sb.replace(op.start, op.end, op.text)
                }
            }
        return sb.toString()
    }

    private data class Op(val start: Int, val end: Int, val text: String)

    private fun children(n: JSONObject): List<JSONObject> =
        runCatching { n.getJSONArray("children") }.getOrNull()
            ?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } } ?: emptyList()

    private fun span(n: JSONObject): IntArray? =
        runCatching { n.getJSONArray("span") }.getOrNull()
            ?.let { intArrayOf(it.getInt(0), it.getInt(1)) }

    /** 名字（匹配用：属性/文案编辑不改名，就地补丁；改名只能删旧加新） */
    private fun sig(n: JSONObject): String {
        val props = runCatching { n.getJSONObject("props") }.getOrNull()?.let { p: JSONObject ->
            val keys = ArrayList<String>()
            p.keys().forEach { keys.add(it) }
            keys.sorted().joinToString(",") { k: String -> k + "=" + p.optString(k) }
        } ?: ""
        val args = runCatching { n.getJSONArray("args") }.getOrNull()?.let { a ->
            (0 until a.length()).joinToString(",") { i: Int -> a.optString(i) }
        } ?: ""
        return n.optString("name") + "|" + n.optString("text") + "|" + props + "|" + args
    }

    private fun diff(parentO: JSONObject, parentE: JSONObject, content: String, ops: ArrayList<Op>) {
        val os = children(parentO)
        val es = children(parentE)
        val used = HashSet<Int>()
        var j = 0
        for (e in es) {
            // 按名字就近匹配（窗口 5）：改属性/文案的节点名字不变，走就地补丁
            var oi = -1
            val win = Math.min(j + 5, os.size)
            for (k in j until win) {
                if (os[k].optString("name") == e.optString("name")) { oi = k; break }
            }
            if (oi >= 0) {
                used.add(oi)
                patchNode(os[oi], e, content, ops)
                diff(os[oi], e, content, ops)
                j = oi + 1
            } else if (span(parentO) != null) {
                ops.add(insertOp(parentO, e, content))
            }
        }
        for (i in os.indices) if (i !in used) {
            val op = deleteOp(os[i], content)
            if (op != null) ops.add(op)
        }
    }

    /** 属性/文案变化 → 就地改 span 内的 key = value */
    private fun patchNode(o: JSONObject, e: JSONObject, content: String, ops: ArrayList<Op>) {
        val sp = span(o) ?: return
        if (sp[1] > content.length || sp[0] >= sp[1]) return
        var text = content.substring(sp[0], sp[1])
        var changed = false
        // 文案（text/placeholder）：改对应的具名参数
        val tk = o.optString("textKey", "text")
        val ot = o.optString("text")
        val et = e.optString("text")
        if (ot != et && et.isNotEmpty()) {
            val nt = replaceArg(text, tk, ot, et)
            if (nt != null) { text = nt; changed = true }
        }
        // 属性
        val op = runCatching { o.getJSONObject("props") }.getOrNull() ?: JSONObject()
        val ep = runCatching { e.getJSONObject("props") }.getOrNull() ?: JSONObject()
        val keys = HashSet<String>().apply { op.keys().forEach { add(it) }; ep.keys().forEach { add(it) } }
        for (k in keys) {
            val ov = op.optString(k)
            val ev = ep.optString(k)
            if (ov == ev) continue
            val nt = when {
                ov.isNotEmpty() && ev.isNotEmpty() -> replaceArg(text, k, ov, ev)
                ev.isNotEmpty() -> addArg(text, k, ev)
                else -> removeArg(text, k, ov)
            }
            if (nt != null) { text = nt; changed = true }
        }
        if (changed) ops.add(Op(sp[0], sp[1], text))
    }

    /** 替换具名参数值：key = old → key = new */
    private fun replaceArg(src: String, key: String, oldV: String, newV: String): String? {
        val pat = Pattern.compile(Pattern.quote(key) + "\\s*=\\s*" + Pattern.quote(oldV))
        val m = pat.matcher(src)
        if (!m.find()) return null
        val sb = StringBuilder(src)
        sb.replace(m.start(), m.end(), key + " = " + newV)
        return sb.toString()
    }

    /** 新增具名参数：插到构造参数括号末尾 */
    private fun addArg(src: String, key: String, value: String): String? {
        val close = ctorCloseParen(src) ?: return null
        val sb = StringBuilder(src)
        val insert = if (close > 1 && src[close - 1] != '(') ", " + key + " = " + value else key + " = " + value
        sb.insert(close, insert)
        return sb.toString()
    }

    /** 删除具名参数（含其后的逗号） */
    private fun removeArg(src: String, key: String, oldV: String): String? {
        val pat = Pattern.compile(Pattern.quote(key) + "\\s*=\\s*" + Pattern.quote(oldV))
        val m = pat.matcher(src)
        if (!m.find()) return null
        var s = m.start()
        var e = m.end()
        // 吃掉一侧的逗号
        if (e < src.length && src[e] == ',') e++
        else if (s > 0 && src[s - 1] == ',') s--
        return src.substring(0, s) + src.substring(e)
    }

    /** 构造参数右括号在片内的位置（尾 lambda 的 { 在它之后） */
    private fun ctorCloseParen(src: String): Int? {
        val open = src.indexOf('(')
        if (open < 0) return null
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return i }
            }
        }
        return null
    }

    /** 删除节点：连同所在行删掉；若是 var 声明的初值（@mut var x = <节点>）整行删除 */
    private fun deleteOp(n: JSONObject, content: String): Op? {
        val sp = span(n) ?: return null
        if (sp[1] > content.length) return null
        var s = sp[0]
        var e = sp[1]
        // 行首缩进
        while (s > 0 && (content[s - 1] == ' ' || content[s - 1] == '\t')) s--
        // 行尾（含换行）
        while (e < content.length && content[e] != '\n') e++
        if (e < content.length) e++
        // 前文若是 var 声明（@mut var x = ）→ 从行首删（s 已经走到行首缩进，等价整行）
        return Op(s, e, "")
    }

    /** 新增节点：插进父节点的尾 lambda，或 children=listOf(...) 的实参里 */
    private fun insertOp(parent: JSONObject, node: JSONObject, content: String): Op {
        val sp = span(parent)!!
        val psrc = content.substring(sp[0], sp[1])
        val ci = psrc.lastIndexOf("listOf(")
        if (ci >= 0) {
            // children = listOf(...)：变成单行实参
            val close = matchingParen(psrc, ci + "listOf(".length - 1) ?: return Op(sp[0], sp[0], "")
            val inline = PreviewCodeGen.nodeInline(node)
            val comma = if (close > ci + 7 && psrc[close - 1] != '(' && psrc[close - 1] != ',') ", " else ""
            return Op(sp[0] + close, sp[0] + close, comma + inline)
        }
        // 尾 lambda：在最后 } 前插入（按父节点所在行缩进再深一级）
        val brace = psrc.lastIndexOf('}')
        if (brace < 0) return Op(sp[0], sp[0], "")
        // 父节点行缩进
        var ls = sp[0]
        while (ls > 0 && content[ls - 1] != '\n') ls--
        val lineStart = ls
        var indent = 0
        while (lineStart + indent < content.length && content[lineStart + indent] == ' ') indent++
        val pad = " ".repeat(indent + 4)
        val lines = PreviewCodeGen.nodeSource(node, 0)
        val sb = StringBuilder()
        for ((i, l) in lines.withIndex()) {
            sb.append("\n")
            sb.append(pad).append(l)
        }
        return Op(sp[0] + brace, sp[0] + brace, sb.toString())
    }

    private fun matchingParen(src: String, openIdx: Int): Int? {
        var depth = 0
        for (i in openIdx until src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return i }
            }
        }
        return null
    }
}
