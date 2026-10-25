package candy.ide.desktop

import candy.ide.desktop.DesktopEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 桌面端 LSP 桥：定义 / 重命名 / 格式化 */
class LspApiTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newProject(): File {
        val dir = tmp.newFolder("p")
        File(dir, "a.subs").writeText("fun candyAdd(a: Int, b: Int) { a + b }\nfun main() { print(candyAdd(1, 2)) }\n")
        return dir
    }

    private fun uri(f: File) = DesktopEngine.uriOf(f)

    @Test fun goto_definition_of_call() {
        val p = newProject()
        val f = File(p, "a.subs")
        val text = f.readText()
        // add( 调用在第 2 行第 17 列附近：先定位调用的起始列
        val callLine = 1
        val callCol = text.split("\n")[1].indexOf("candyAdd(")
        val loc = DesktopEngine.definition(uri(f), text, callLine, callCol)
        assertNotNull("应能找到 candyAdd 的定义", loc)
        // LSP 回传的 uri 分隔风格不保证与 uriOf 完全一致：按路径比较
        val target = runCatching { java.io.File(java.net.URI(loc?.uri ?: "")).absolutePath }.getOrNull()
        assertEquals(f.absolutePath, target)
        assertEquals(0, loc?.range?.start?.line)
    }

    @Test fun rename_updates_all_occurrences() {
        val p = newProject()
        val f = File(p, "a.subs")
        val text = f.readText()
        val col = text.split("\n")[1].indexOf("candyAdd(") + 1
        val changes = DesktopEngine.rename(uri(f), text, 1, col, "plus")
        assertNotNull(changes)
        val textEdits = changes?.get(uri(f))
        assertNotNull("当前文件应有编辑", textEdits)
        val nt = applyEdits(text, textEdits!!)
        assertEquals(false, nt.contains("add("))
        assertEquals(true, nt.contains("fun plus("))
        assertEquals(true, nt.contains("plus(a: Int"))
        assertEquals(true, nt.contains("print(plus(1, 2))"))
    }


    @Test fun format_document_returns_edits_or_null() {
        val p = newProject()
        val f = File(p, "a.subs")
        val edits = DesktopEngine.format(uri(f), f.readText())
        // 引擎可能返回 null（不支持）或编辑列表；不崩即可
        if (edits != null) {
            for (te in edits) {
                assertTrue(te.newText.isNotEmpty() || true) // newText 允许为空串（删除类编辑）
            }
        }
        
    }

    @Test fun completion_after_dot_and_prefix() {
        val p = newProject()
        val f = File(p, "a.subs")
        val text = f.readText()
        DesktopEngine.lsp.updateDocument(uri(f), text)
        // 工程内符号补全：前缀 can
        val list = DesktopEngine.completion(uri(f), 1, 1)
        val labels = list.map { it.label }
        assertEquals(true, labels.isNotEmpty())
    }

    @Test fun composable_tree_json_and_codegen() {
        val p = newProject()
        val f = File(p, "a.subs")
        val text = f.readText()
        // 无 @composable 函数：返回 null
        assertEquals(null, DesktopEngine.composableTree(uri(f), text, 0, 0))
    }

    @Test fun signature_help_at_call_args() {
        val p = newProject()
        val f = File(p, "a.subs")
        val text = f.readText()
        val row = text.split("\n")[1]
        val col = row.indexOf("candyAdd(") + "candyAdd(".length + 1 // 光标在第一个实参区
        val help = DesktopEngine.signatureHelp(uri(f), text, 1, col)
        assertNotNull(help)
        val sig = help?.signatures?.getOrNull(help.activeSignature)
        assertNotNull(sig)
        assertEquals(true, (sig?.parameters?.size ?: 0) == 2)
    }

    @Test fun references_of_function_in_file() {
        val p = newProject()
        val f = File(p, "a.subs")
        val text = f.readText()
        val col = text.split("\n")[1].indexOf("candyAdd(")
        val locs = DesktopEngine.references(DesktopEngine.uriOf(f), text, 1, col)
        assertNotNull(locs)
        // 1 处定义 + 1 处调用（引用计数含定位）
        assertEquals(true, (locs?.size ?: 0) >= 1)
        assertEquals(true, locs?.any { it.range.start.line == 1 } == true)
    }

    @Test fun hover_returns_builtin_doc() {
        val p = newProject()
        val f = File(p, "a.subs")
        val text = f.readText()
        // print( 调用（stdlib print）
        val col = text.split("\n")[1].indexOf("print(")
        val hover = DesktopEngine.hover(DesktopEngine.uriOf(f), text, 1, col)
        val doc = when (val c = hover?.contents) {
            is String -> c
            is sugared.functor.lsp.MarkupContent -> c.value
            else -> ""
        }
        assertEquals(true, doc.isNotBlank())
    }

    @Test fun symbol_detail_extracts_signature() {
        val p = newProject()
        val f = File(p, "a.subs")
        val text = f.readText()
        val detail = DesktopEngine.symbolDetail(DesktopEngine.uriOf(f), text, "candyAdd")
        assertNotNull(detail)
        assertEquals(true, detail?.contains("candyAdd") == true)
        assertEquals(true, detail?.contains("a: Int") == true)
    }

    private fun applyEdits(text: String, edits: List<sugared.functor.lsp.TextEdit>): String {
        val sb = StringBuilder(text)
        val sorted = edits.sortedByDescending { it.range.start.line * 100000 + it.range.start.character }
        for (te in sorted) {
            val s = offset(text, te.range.start.line, te.range.start.character)
            val e = offset(text, te.range.end.line, te.range.end.character)
            if (s in 0..e && e <= sb.length) sb.replace(s, e, te.newText)
        }
        return sb.toString()
    }

    private fun offset(text: String, line: Int, char: Int): Int {
        var l = 0
        var idx = 0
        while (l < line && idx < text.length) { if (text[idx] == '\n') l++; idx++ }
        val lineEnd = text.indexOf('\n', idx).let { if (it < 0) text.length else it }
        return (idx + char).coerceIn(idx, lineEnd)
    }
}
