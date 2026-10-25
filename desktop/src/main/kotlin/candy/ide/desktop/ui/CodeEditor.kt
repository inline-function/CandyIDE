@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package candy.ide.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.data.DiagItem
import candy.ide.desktop.DesktopEngine
import candy.ide.desktop.Schemes
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import sugared.functor.lexer.Kind
import sugared.functor.lexer.Lexer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
/**
 * 桌面端代码编辑器：Compose 原生实现，零 Android 依赖。
 * - 语法高亮：复用 sublimation 真实 Lexer，颜色/粗斜体来自当前配色方案（Schemes）
 * - 诊断：LSP 实时诊断下划线（错误/警告/提示三色）+ 行号按严重度着色
 * - 补全：DesktopEngine → LspAnalysis.completionFor，编辑区底部弹层，↑↓ 选择、Enter/Tab 插入、Esc 关闭
 * - 撤销/重做：Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y，时间分组避免每字一次
 * - 右键菜单：剪切/复制/粘贴/全选
 * - Tab = 4 空格
 */
@Composable
fun CodeEditor(
    file: File,
    text: String,
    onTextChange: (String) -> Unit,
    diagnostics: List<DiagItem>,
    fontSize: Int = 14,
    gotoTick: Int = 0,
    gotoLine: Int = 0,
    wordWrap: Boolean = false,
    onCaret: ((Int, Int) -> Unit)? = null,
    insertSymbolTick: Int = 0,
    insertSymbolText: String = "",
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val scroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var caretLine by remember { mutableIntStateOf(0) }
    var caretChar by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var completion by remember { mutableStateOf<List<sugared.functor.lsp.CompletionItem>?>(null) }
    // 快速修复（灯泡，Alt+Enter / Ctrl+.）：诊断驱动的 codeAction 列表
    var fixes by remember { mutableStateOf<List<sugared.functor.lsp.CodeAction>?>(null) }
    var fixSelected by remember { mutableIntStateOf(0) }
    var replaceStart by remember { mutableIntStateOf(0) }
    val worker = remember { Executors.newSingleThreadExecutor { r -> Thread(r, "candy-complete").apply { isDaemon = true } } }
    val token = remember { AtomicInteger(0) }

    // 当前配色方案：变化时整体重算并触发重组
    val scheme = Schemes.current
    val tokStyle = remember(scheme) {
        Schemes.TOKEN_LABELS.associate { (t, _) ->
            val flag = scheme.flags[t] ?: 0
            t to SpanStyle(
                color = Color(Schemes.colorOf(t)),
                fontWeight = if (flag and 1 != 0) FontWeight.Bold else null,
                fontStyle = if (flag and 2 != 0) FontStyle.Italic else null,
            )
        }
    }
    val bgColor = remember(scheme) { Color(Schemes.colorOf("background")) }
    val cursorColor = remember(scheme) { Color(Schemes.colorOf("selection")) }

    val mono = remember(fontSize) {
        TextStyle(fontFamily = JetBrainsMono, fontSize = fontSize.sp, lineHeight = (fontSize * 1.35).sp)
    }
    val lineStarts = remember(text) { lineStartsOf(text) }
    val totalLines = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1

    val density = androidx.compose.ui.platform.LocalDensity.current
    // 诊断跳转：滚到目标行
    LaunchedEffect(gotoTick) {
        if (gotoTick > 0 && gotoLine in 0 until totalLines) {
            val lh = with(density) { (fontSize.sp * 1.35f).toPx() }
            scroll.animateScrollTo((gotoLine * lh).toInt())
        }
    }

    // 编辑值：文本 + 真实选区（外层 text 为单一数据源，切换文件/保存后同步）
    var fieldState by remember(file) { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) {
        if (fieldState.text != text) fieldState = TextFieldValue(text)
    }

    // 撤销栈：每文件独立；时间分组（900ms 内连续编辑合并为一组）
    val undo = remember(file) { UndoStack() }
    var applyingUndo by remember { mutableStateOf(false) }
    LaunchedEffect(file) { undo.reset(text) }

    // 符号栏插入（符号栏点击 → 在光标处插入，进撤销栈）
    LaunchedEffect(insertSymbolTick) {
        if (insertSymbolTick == 0 || insertSymbolText.isEmpty()) return@LaunchedEffect
        val caret = caretAbs(lineStarts, caretLine, caretChar)
        if (caret in 0..text.length) {
            val nt = text.substring(0, caret) + insertSymbolText + text.substring(caret)
            undo.push(nt)
            onTextChange(nt)
            fieldState = TextFieldValue(nt, TextRange(caret + insertSymbolText.length))
        }
    }

    val clipboard = LocalClipboardManager.current
    var menuPos by remember { mutableStateOf(Offset.Zero) }
    var showMenu by remember { mutableStateOf(false) }
    fun dpMenuPos(): DpOffset = with(density) { DpOffset(menuPos.x.toDp(), menuPos.y.toDp()) }
fun triggerCompletion(src: String, caret: Int, line: Int, char: Int) {
        val before = src.substring(0, caret.coerceIn(0, src.length))
        val prefix = Regex("[A-Za-z0-9_\\\\@:.-]*$").find(before)?.value ?: ""
        val triggerChars = listOf(".", "::", "@", "\\\\")
        val want = prefix.isNotEmpty() && (prefix.any { it.isLetterOrDigit() || it == '_' } || triggerChars.any { prefix.endsWith(it) })
        if (!want) { completion = null; return }
        replaceStart = (caret - prefix.length).coerceAtLeast(0)
        val my = token.incrementAndGet()
        worker.execute {
            val uri = DesktopEngine.uriOf(file)
            val items = DesktopEngine.completion(uri, line, char).filter {
                val f = it.filterText ?: it.label
                f.startsWith(prefix) || it.label.startsWith(prefix) || prefix.endsWith(".") || prefix.endsWith("::") || prefix.endsWith("@") || prefix.endsWith("\\\\")
            }
            if (token.get() == my) { completion = items; selected = 0 }
        }
    }

    /** 拉取光标处的快速修复（复用补全线程池，带令牌防竞态） */
    fun triggerFixes() {
        val my = token.incrementAndGet()
        val uri = DesktopEngine.uriOf(file)
        worker.execute {
            val acts = DesktopEngine.codeActions(uri, text, caretLine, caretChar)
            if (token.get() == my) {
                fixes = acts
                fixSelected = 0
            }
        }
    }

    /** 应用一条 codeAction：按区间倒序替换（与安卓端 applyFix 同构） */
    fun applyFix(act: sugared.functor.lsp.CodeAction) {
        val edits = act.edit?.changes?.get(DesktopEngine.uriOf(file)) ?: emptyList()
        if (edits.isEmpty()) { fixes = null; return }
        val sb = StringBuilder(text)
        val sorted = edits.sortedByDescending { it.range.start.line * 100000 + it.range.start.character }
        for (te in sorted) {
            val s = offsetOf(lineStarts, te.range.start.line, te.range.start.character) ?: continue
            val en = offsetOf(lineStarts, te.range.end.line, te.range.end.character) ?: continue
            if (s in 0..en && en <= sb.length) sb.replace(s, en, te.newText)
        }
        fixes = null
        undo.push(sb.toString())
        onTextChange(sb.toString())
    }

    fun applyCompletion(item: sugared.functor.lsp.CompletionItem) {
        val te = item.textEdit
        val insert = cleanSnippet(item.insertText ?: item.label)
        val caret = caretAbs(lineStarts, caretLine, caretChar)
        val (start, end) = if (te != null) {
            val s = offsetOf(lineStarts, te.range.start.line, te.range.start.character) ?: replaceStart
            val e = offsetOf(lineStarts, te.range.end.line, te.range.end.character) ?: caret
            s to e
        } else replaceStart to caret
        if (start in 0..end && end <= text.length) {
            completion = null
            onTextChange(text.substring(0, start) + insert + text.substring(end))
        }
    }

    Box(
        modifier
            .background(bgColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.changedToDown() } ?: continue
                        if (event.button == PointerButton.Secondary) {
                            menuPos = change.position
                            showMenu = true
                        }
                    }
                }
            },
    ) {
        Row(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
            // 行号槽：诊断按严重度着色，当前行用选区色提亮
            Column(
                modifier = Modifier.wrapContentHeight().padding(top = 4.dp).width(gutterWidth(totalLines, fontSize)),
            ) {
                repeat(totalLines) { i ->
                    val worst = diagnostics.filter { it.line == i }.minOfOrNull { it.severity.toInt() } ?: -1
                    val color = when (worst) {
                        0 -> Color(Schemes.colorOf("problemError"))
                        1 -> Color(Schemes.colorOf("problemWarning"))
                        2, 3 -> Color(Schemes.colorOf("problemTypo"))
                        else -> if (i == caretLine) cursorColor else Color(Schemes.colorOf("lineNumber"))
                    }
                    Text(
                        "${i + 1}",
                        color = color,
                        style = mono.copy(fontSize = (fontSize - 1).sp),
                        modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                    )
                }
            }
            BasicTextField(
                value = fieldState,
                onValueChange = { v ->
                    val sameText = v.text == fieldState.text
                    fieldState = v
                    val caret = v.selection.end
                    caretLine = lineOf(lineStarts, caret)
                    caretChar = caret - lineStarts[caretLine]
                    onCaret?.invoke(caretLine, caretChar)
                    if (!sameText) {
                        if (!applyingUndo) undo.push(v.text)
                        onTextChange(v.text)
                        triggerCompletion(v.text, caret, caretLine, caretChar)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .then(if (wordWrap) Modifier else Modifier.horizontalScroll(hScroll))
                    .padding(start = 4.dp, top = 4.dp, end = 8.dp, bottom = 8.dp)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) false else when {
                            completion != null && e.key == Key.Enter -> { completion?.getOrNull(selected)?.let(::applyCompletion); true }
                            completion != null && e.key == Key.Tab -> { completion?.getOrNull(selected)?.let(::applyCompletion); true }
                            completion != null && e.key == Key.Escape -> { completion = null; true }
                    fixes != null && e.key == Key.Escape -> { fixes = null; true }
                    fixes != null && e.key == Key.Enter -> { fixes?.getOrNull(fixSelected)?.let(::applyFix); true }
                    fixes != null && e.key == Key.DirectionUp -> { fixSelected = (fixSelected - 1).coerceAtLeast(0); true }
                    fixes != null && e.key == Key.DirectionDown -> { fixSelected = (fixSelected + 1).coerceAtMost((fixes?.size ?: 1) - 1); true }
                    (e.isAltPressed && e.key == Key.Enter) || (e.isCtrlPressed && e.key == Key.Period) -> {
                        triggerFixes(); true
                    }
                            completion != null && e.key == Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                            completion != null && e.key == Key.DirectionDown -> { selected = (selected + 1).coerceAtMost((completion?.size ?: 1) - 1); true }
                            e.isCtrlPressed && !e.isShiftPressed && e.key == Key.Z -> {
                                applyingUndo = true
                                undo.undo()?.let { onTextChange(it) }
                                applyingUndo = false
                                true
                            }
                            e.isCtrlPressed && (e.key == Key.Y || (e.isShiftPressed && e.key == Key.Z)) -> {
                                applyingUndo = true
                                undo.redo()?.let { onTextChange(it) }
                                applyingUndo = false
                                true
                            }
                            e.key == Key.Tab && !e.isShiftPressed -> {
                                onTextChange(insertAt(text, caretAbs(lineStarts, caretLine, caretChar), "    ")); true
                            }
                            else -> false
                        }
                    },
                textStyle = mono.copy(color = Color(Schemes.colorOf("text"))),
                cursorBrush = SolidColor(cursorColor),
                visualTransformation = SublHighlight(diagnostics, tokStyle),
            )
        }

        // 右键菜单
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = dpMenuPos(),
        ) {
            DropdownMenuItem(
                text = { Text("剪切", color = CandyText) },
                onClick = {
                    showMenu = false
                    val sel = fieldState.selection
                    if (!sel.collapsed) {
                        clipboard.setText(AnnotatedString(fieldState.text.substring(sel.min, sel.max)))
                        onTextChange(fieldState.text.removeRange(sel.min, sel.max))
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("复制", color = CandyText) },
                onClick = {
                    showMenu = false
                    val sel = fieldState.selection
                    if (!sel.collapsed) clipboard.setText(AnnotatedString(fieldState.text.substring(sel.min, sel.max)))
                },
            )
            DropdownMenuItem(
                text = { Text("粘贴", color = CandyText) },
                onClick = {
                    showMenu = false
                    val sel = fieldState.selection
                    val clip = clipboard.getText()?.text ?: return@DropdownMenuItem
                    onTextChange(fieldState.text.substring(0, sel.min) + clip + fieldState.text.substring(sel.max))
                },
            )
            DropdownMenuItem(
                text = { Text("全选", color = CandyText) },
                onClick = {
                    showMenu = false
                    fieldState = fieldState.copy(selection = TextRange(0, fieldState.text.length))
                },
            )
        }

        // 快速修复弹层（灯泡，编辑区底部；与补全弹层错开）
        val acts = fixes
        if (!acts.isNullOrEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.7f).padding(8.dp),
                color = CandySurfaceVariant,
                tonalElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("快速修复", color = CandyTextDim, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    acts.take(9).forEachIndexed { i, act ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                                .background(if (i == fixSelected) CandySurface else Color.Transparent)
                                .clickable { applyFix(act) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("💡", fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(act.title, color = CandyText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 补全弹层（编辑区底部固定）
        val items = completion
        if (!items.isNullOrEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.65f).padding(8.dp),
                color = CandySurfaceVariant,
                tonalElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    items.take(9).forEachIndexed { i, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
                                .background(if (i == selected) CandySurface else Color.Transparent),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(item.label, color = CandyText, fontSize = 12.sp)
                            item.detail?.let { Text(it, color = CandyTextDim, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}
// ───────── 撤销栈 ─────────

/** 时间分组的文本撤销栈：900ms 内的连续编辑合并为一次撤销 */
private class UndoStack(private val cap: Int = 300, private val groupMs: Long = 900L) {
    private val entries = ArrayList<String>()
    private var pos = -1
    private var lastPush = 0L

    fun reset(initial: String) {
        entries.clear()
        entries.add(initial)
        pos = 0
        lastPush = System.currentTimeMillis()
    }

    fun push(s: String) {
        if (pos in entries.indices && entries[pos] == s) return
        val now = System.currentTimeMillis()
        if (now - lastPush < groupMs && pos in entries.indices) {
            // 合并进当前组：只更新组内终态
            entries[pos] = s
            lastPush = now
            return
        }
        if (pos < entries.lastIndex) entries.subList(pos + 1, entries.size).clear()
        entries.add(s)
        if (entries.size > cap) entries.removeAt(0) else pos++
        lastPush = now
    }

    fun undo(): String? {
        if (pos <= 0) return null
        pos--
        lastPush = System.currentTimeMillis() + groupMs
        return entries[pos]
    }

    fun redo(): String? {
        if (pos >= entries.lastIndex) return null
        pos++
        lastPush = System.currentTimeMillis() + groupMs
        return entries[pos]
    }
}

// ───────── 高亮变换 ─────────

/** token → 方案 token 名（与 Android 端 Schemes 语义一致） */
private val KIND_TOKEN: Map<Kind, String> = mapOf(
    Kind.FUN to "keyword", Kind.CLASS to "keyword", Kind.STRUCT to "keyword", Kind.ENUM to "keyword",
    Kind.VAR to "keyword", Kind.IMPL to "keyword", Kind.FOR to "keyword", Kind.IF to "keyword",
    Kind.ELSE to "keyword", Kind.UNCHECKED to "keyword", Kind.WHEN to "keyword", Kind.IS to "keyword",
    Kind.BY to "keyword", Kind.TYPE to "keyword", Kind.RET to "keyword", Kind.IN to "keyword", Kind.DEF to "keyword",
    Kind.TOP to "keyword", Kind.BOT to "keyword",
    Kind.INT to "number", Kind.FLOAT to "number",
    Kind.STR to "string",
    Kind.ARROW to "function", Kind.DARROW to "function",
    Kind.FORALL to "operator", Kind.EXISTS to "operator", Kind.EXISTS1 to "operator", Kind.LAND to "operator",
    Kind.LOR to "operator", Kind.LNOT to "operator", Kind.IMPLIES to "operator", Kind.EQUIV to "operator",
    Kind.BECAUSE to "operator", Kind.THEREFORE to "operator", Kind.PLUS_BECAUSE to "operator", Kind.IFF to "operator",
    Kind.SYMBOL to "punctuation", Kind.AT to "annotation",
)

/** 高亮 + 诊断下划线的 VisualTransformation（颜色/字形来自当前方案） */
private class SublHighlight(
    val diagnostics: List<DiagItem>,
    val tokStyle: Map<String, SpanStyle>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        val starts = lineStartsOf(src)
        val plain = tokStyle["text"] ?: SpanStyle()
        val errStyle = SpanStyle(
            textDecoration = TextDecoration.Underline,
            color = (tokStyle["problemError"] ?: plain).color,
        )
        val warnStyle = SpanStyle(
            textDecoration = TextDecoration.Underline,
            color = (tokStyle["problemWarning"] ?: plain).color,
        )
        val typoStyle = SpanStyle(
            textDecoration = TextDecoration.Underline,
            color = (tokStyle["problemTypo"] ?: plain).color,
        )
        val annotated = buildAnnotatedString {
            append(src)
            for ((start, end, style) in tokenSpans(src, tokStyle)) addStyle(style, start, end)
            val comment = tokStyle["comment"] ?: plain
            for ((start, end) in commentSpans(src)) {
                addStyle(SpanStyle(color = comment.color, fontStyle = FontStyle.Italic), start, end)
            }
            for (d in diagnostics) {
                val s = offsetOf(starts, d.line, d.char) ?: continue
                val en = offsetOf(starts, d.endLine, d.endChar) ?: (s + 1).coerceAtMost(src.length)
                if (s in 0..src.length && en > s && en <= src.length) {
                    addStyle(when (d.severity.toInt()) {
                        0 -> errStyle
                        1 -> warnStyle
                        else -> typoStyle
                    }, s, en)
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

private data class Span3(val start: Int, val end: Int, val style: SpanStyle)

/** 词法分析 → 区间样式；半截输入导致的失败走正则兜底 */
private fun tokenSpans(src: String, tokStyle: Map<String, SpanStyle>): List<Span3> {
    val tokens = runCatching { Lexer(src, "live").tokenize() }.getOrNull()
    if (tokens != null) {
        val starts = lineStartsOf(src)
        val out = ArrayList<Span3>(tokens.size)
        for (t in tokens) {
            if (t.kind == Kind.EOF) break
            val start = starts.getOrElse(t.line - 1) { 0 } + (t.col - 1)
            val end = (start + t.text.length).coerceAtMost(src.length)
            if (end <= start || start < 0 || start > src.length) continue
            val tok = if (t.kind == Kind.IDENT && t.text.firstOrNull()?.isUpperCase() == true) "type" else KIND_TOKEN[t.kind]
            out += Span3(start, end, tokStyle[tok] ?: tokStyle["text"] ?: SpanStyle())
        }
        return out
    }
    return keywordFallback(src, tokStyle)
}

private fun keywordFallback(src: String, tokStyle: Map<String, SpanStyle>): List<Span3> {
    val out = ArrayList<Span3>()
    val keys = setOf("fun", "class", "struct", "enum", "var", "impl", "for", "if", "else", "unchecked", "when", "is", "by", "type", "return", "def", "in", "import", "mount", "async", "impure", "mut", "root", "host", "axiom")
    val kw = tokStyle["keyword"] ?: SpanStyle()
    val num = tokStyle["number"] ?: SpanStyle()
    val str = tokStyle["string"] ?: SpanStyle()
    val typ = tokStyle["type"] ?: SpanStyle()
    val re = Regex("[A-Za-z_][A-Za-z0-9_]*|\\d+\\.\\d+|\\d+|'[^'\n]*'")
    for (m in re.findAll(src)) {
        val style = when {
            m.value in keys -> kw
            m.value.matches(Regex("\\d+(\\.\\d+)?")) -> num
            m.value.startsWith("'") -> str
            m.value.firstOrNull()?.isUpperCase() == true -> typ
            else -> continue
        }
        out += Span3(m.range.first, m.range.last + 1, style)
    }
    return out
}

/** // 注释区间（Lexer 不产出注释 token，需自行叠加；跳过字符串内部） */
private fun commentSpans(src: String): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    val tokens = runCatching { Lexer(src, "live").tokenize() }.getOrNull()
    val starts = lineStartsOf(src)
    val stringSpans = tokens?.filter { it.kind == Kind.STR }?.map { t ->
        val s = starts.getOrElse(t.line - 1) { 0 } + (t.col - 1)
        s until (s + t.text.length).coerceAtMost(src.length)
    } ?: emptyList()
    var i = 0
    while (i < src.length) {
        if (src[i] == '/' && i + 1 < src.length && src[i + 1] == '/' && stringSpans.none { i in it }) {
            val end = src.indexOf('\n', i).let { if (it < 0) src.length else it }
            out += i to end
            i = end
            continue
        }
        i++
    }
    return out
}

// ───────── 文本工具 ─────────

private fun lineStartsOf(src: String): IntArray {
    val out = ArrayList<Int>()
    out.add(0)
    for ((i, c) in src.withIndex()) if (c == '\n') out.add(i + 1)
    return out.toIntArray()
}

private fun lineOf(starts: IntArray, offset: Int): Int {
    var lo = 0; var hi = starts.lastIndex
    while (lo < hi) { val mid = (lo + hi + 1) ushr 1; if (starts[mid] <= offset) lo = mid else hi = mid - 1 }
    return lo
}

private fun offsetOf(starts: IntArray, line: Int, char: Int): Int? {
    if (line !in starts.indices) return null
    return starts[line] + char
}

private fun caretAbs(starts: IntArray, line: Int, char: Int): Int = starts.getOrElse(line) { 0 } + char

private fun insertAt(src: String, at: Int, s: String): String =
    src.substring(0, at.coerceIn(0, src.length)) + s + src.substring(at.coerceIn(0, src.length))

/** snippet 语法（$0 / ${'$'}{n:默认}）简化为纯文本 */
private fun cleanSnippet(s: String): String =
    Regex("\\${'$'}{\\d+:([^}]*)\\}").replace(s) { it.groupValues[1] }.replace("$0", "")

private fun gutterWidth(totalLines: Int, fontSize: Int) = (fontSize * 0.62 * (totalLines.toString().length + 2)).dp



