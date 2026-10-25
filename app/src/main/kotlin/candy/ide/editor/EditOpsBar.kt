package candy.ide.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.max
import kotlin.math.min

/** 辅助操作的选择状态：anchor = 选择锚点的字符偏移；sideLeft = 光标操作移动选择的左边 */
data class EditSel(val anchor: Int, val sideLeft: Boolean = false)

/** 操作执行后需要弹出文本操作栏的位置（编辑器内像素坐标 + 行列） */
data class OpPopup(val x: Float, val y: Float, val line: Int, val col: Int)

/** 操作结果：sel = null 表示退出选择态 */
data class OpResult(val sel: EditSel?, val popup: OpPopup?)

/**
 * 3×3 悬浮操作窗的操作码。
 * 两块面板：'-' 板为字符/行级移动（←→↑↓），'=' 板为大跳（⇐⇒ 行首行尾、⇑⇓ 文件首尾）。
 * 进入选择态后 ↩↪ 位置变成 ⇤⇥（选择基准边），☐ 变成 ☑。
 */
object EditOp {
    const val MODE = "MODE"              // - / = 面板切换（只切面板，不动编辑器）
    const val SELECT = "SELECT"          // ☐：进入 / 退出选择态（选择态显示 ☑）
    const val UNDO = "UNDO"              // ↩：撤销
    const val REDO = "REDO"              // ↪：恢复
    const val SIDE_LEFT = "⇤"            // 选择中：移动选择的左边（锚点边）
    const val SIDE_RIGHT = "⇥"           // 选择中：移动选择的右边
    const val INDENT = "~"               // 当前行缩进 4 格（选择中作用于所跨全部行）
    const val DEDENT = "≈"               // 当前行删 4 格缩进（选择中作用于所跨全部行）
    const val LEFT = "←"                 // 光标左移一格
    const val RIGHT = "→"                // 光标右移一格
    const val UP = "↑"                   // 上一行同列（列数不足取行尾）
    const val DOWN = "↓"                 // 下一行同列（列数不足取行尾）
    const val LINE_START = "⇐"           // 本行开始处
    const val LINE_END = "⇒"             // 本行结束处
    const val FILE_START = "⇑"           // 文件开头
    const val FILE_END = "⇓"             // 文件结尾
}

/** 悬浮窗的一个按键 */
private data class Cell(val label: String, val op: String, val on: Boolean)

/**
 * 编辑操作悬浮窗（3×3）：缩进 / 光标移动 / 撤销恢复 / 选择控制。
 * 九格从上到下、从左到右：
 *   [-/=] [↑/⇑] [☐/☑]
 *   [←/⇐] [~/≈] [→/⇒]
 *   [↩/⇤] [↓/⇓] [↪/⇥]
 * 选择态时 ↩↪ 变 ⇤⇥（哪边为基准哪边高亮）；mode = true 为 '=' 板。
 */
@Composable
fun EditOpsWindow(
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
    sideLeft: Boolean = false,
    mode: Boolean = false,
    onOp: (String) -> Unit,
) {
    // 悬浮窗自身位移：整窗可拖（点按钮是小幅点按，拖动超过 slop 后按钮点击自动取消）
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    val cells = listOf(
        Cell(if (mode) "=" else "-", EditOp.MODE, false),
        Cell(if (mode) "⇑" else "↑", if (mode) EditOp.FILE_START else EditOp.UP, false),
        Cell(if (selecting) "☑" else "☐", EditOp.SELECT, selecting),
        Cell(if (mode) "⇐" else "←", if (mode) EditOp.LINE_START else EditOp.LEFT, false),
        Cell(if (mode) "≈" else "~", if (mode) EditOp.DEDENT else EditOp.INDENT, false),
        Cell(if (mode) "⇒" else "→", if (mode) EditOp.LINE_END else EditOp.RIGHT, false),
        Cell(if (selecting) "⇤" else "↩", if (selecting) EditOp.SIDE_LEFT else EditOp.UNDO, selecting && sideLeft),
        Cell(if (mode) "⇓" else "↓", if (mode) EditOp.FILE_END else EditOp.DOWN, false),
        Cell(if (selecting) "⇥" else "↪", if (selecting) EditOp.SIDE_RIGHT else EditOp.REDO, selecting && !sideLeft),
    )
    Surface(
        modifier = modifier
            .offset { offset }
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    offset = IntOffset(
                        offset.x + dragAmount.x.roundToInt(),
                        offset.y + dragAmount.y.roundToInt(),
                    )
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = candy.ide.ui.theme.CandySurface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            for (row in 0..2) {
                Row(horizontalArrangement = Arrangement.Start) {
                    for (col in 0..2) {
                        val cell = cells[row * 3 + col]
                        OpCell(cell) { onOp(cell.op) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpCell(cell: Cell, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            cell.label,
            color = if (cell.on) CandyPrimary else CandyTextDim,
            fontSize = 16.sp,
            fontWeight = if (cell.on) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * 在编辑器上执行一个辅助操作；返回新的选择状态与可能的文本操作栏弹出位置。
 * MODE / UNDO / REDO 由调用方直接处理（ed.undo() / ed.redo()），不走这个引擎。
 */
fun applyEditOp(ed: CodeEditor, op: String, sel: EditSel?): OpResult {
    val content = ed.text
    val cur = ed.cursor
    // 选择态：sel 非空即在选择中（刚按 ☐ 选区还是 0 宽，下一个移动操作才撑开）
    val active = sel != null
    return when (op) {
        // 缩进：选择中作用于所选涉及的全部行，否则作用于光标所在行
        EditOp.INDENT -> { indentLines(ed, add = true); OpResult(if (active) sel else null, null) }
        EditOp.DEDENT -> { indentLines(ed, add = false); OpResult(if (active) sel else null, null) }
        // 开始选择：锚点 = 当前光标，同时弹文本操作栏；已在选择态则退出
        EditOp.SELECT ->
            if (active) OpResult(null, null) else OpResult(EditSel(cur.left), popupAt(ed, cur.left))
        // 切换选择边：⇤ 移动左边（锚点边），⇥ 移动右边
        EditOp.SIDE_LEFT -> if (active) OpResult(sel!!.copy(sideLeft = true), popupAt(ed, cur.right)) else OpResult(null, null)
        EditOp.SIDE_RIGHT -> if (active) OpResult(sel!!.copy(sideLeft = false), popupAt(ed, cur.right)) else OpResult(null, null)
        // 光标移动类
        else -> {
            if (!active) {
                // 未在选择：整体移动光标（折叠定位）
                val no = moveOffset(content, cur.left, op)
                applySel(ed, no, no)
                OpResult(null, null)
            } else {
                // 选择中：移动活动边（⇤ 模式移动锚点边，另一边固定）
                val s = sel!!
                val free = if (s.sideLeft) s.anchor else cur.right
                val fixed = if (s.sideLeft) cur.right else s.anchor
                val no = moveOffset(content, free, op)
                applySel(ed, no, fixed)
                OpResult(s, popupAt(ed, no))
            }
        }
    }
}

/** 偏移 → 像素位置（供文本操作栏定位） */
private fun popupAt(ed: CodeEditor, offset: Int): OpPopup? {
    val pos = ed.text.getIndexer().getCharPosition(offset)
    val lp = ed.layout?.getCharLayoutOffset(pos.line, pos.column) ?: return null
    if (lp.size < 2) return null
    return OpPopup(lp[0], lp[1], pos.line, pos.column)
}

/** 设置选区（两个偏移，顺序任意） */
private fun applySel(ed: CodeEditor, a: Int, b: Int) {
    val content = ed.text
    val l = content.getIndexer().getCharPosition(min(a, b))
    val r = content.getIndexer().getCharPosition(max(a, b))
    ed.setSelectionRegion(l.line, l.column, r.line, r.column)
}

/** 按操作移动一个偏移（上下移动保持列、列数不足取行尾） */
private fun moveOffset(content: Content, off: Int, op: String): Int {
    val len = content.length
    return when (op) {
        EditOp.LEFT -> (off - 1).coerceAtLeast(0)
        EditOp.RIGHT -> (off + 1).coerceAtMost(len)
        else -> {
            val p = content.getIndexer().getCharPosition(off)
            when (op) {
                EditOp.UP -> {
                    val nl = (p.line - 1).coerceAtLeast(0)
                    content.getCharIndex(nl, min(p.column, content.getColumnCount(nl)))
                }
                EditOp.DOWN -> {
                    val nl = (p.line + 1).coerceAtMost(content.lineCount - 1)
                    content.getCharIndex(nl, min(p.column, content.getColumnCount(nl)))
                }
                EditOp.LINE_START -> content.getCharIndex(p.line, 0)
                EditOp.LINE_END -> content.getCharIndex(p.line, content.getColumnCount(p.line))
                EditOp.FILE_START -> 0
                EditOp.FILE_END -> len
                else -> off
            }
        }
    }
}

/**
 * 缩进/反缩进：选区跨多行时作用于涉及的全部行（每行行首插/删 4 空格）；
 * 无选区时作用于光标所在行。编辑后按行首增减量平移选区边。
 */
private fun indentLines(ed: CodeEditor, add: Boolean) {
    val content = ed.text
    val cur = ed.cursor
    // 先把两边折算成行列，编辑后再按行恢复
    val a = content.getIndexer().getCharPosition(cur.left)
    val b = content.getIndexer().getCharPosition(cur.right)
    val sel = cur.left != cur.right
    val l1 = min(a.line, b.line)
    val l2 = max(a.line, b.line)
    // 编辑前每行行首空格数（用于反缩进时确定删除量、以及选区平移量）
    val leads = (l1..l2).map { line ->
        var n = 0
        val cols = content.getColumnCount(line)
        while (n < cols && content.charAt(line, n) == ' ') n++
        n
    }
    content.beginBatchEdit()
    for (i in l2 downTo l1) {
        if (add) {
            content.insert(i, 0, "    ")
        } else {
            val del = min(4, leads[i - l1])
            if (del > 0) content.delete(i, 0, i, del)
        }
    }
    content.endBatchEdit()
    if (sel) {
        // 平移选区：本批行内所有列 +4（或 -del）
        fun shift(line: Int, column: Int): Pair<Int, Int> {
            if (line !in l1..l2 || column == 0) return line to column
            val delta = if (add) 4 else -min(4, leads[line - l1])
            return line to (column + delta).coerceAtLeast(0)
        }
        val (al, ac) = shift(a.line, a.column)
        val (bl, bc) = shift(b.line, b.column)
        ed.setSelectionRegion(min(al, bl), min(ac, bc), max(al, bl), max(ac, bc))
    }
}
