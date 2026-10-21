package candy.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser

/**
 * 轻量 Markdown 渲染：把 MODEL 气泡的正文按常见 markdown 排版。
 *
 * 支持：标题（缩小字号加粗）、粗体/斜体、行内代码、有序/无序列表、代码块（等宽+底色）、
 * 引用块、软换行。不支持的节点退化为纯文本——agent 输出以中文短句为主，够用。
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val parsed = remember(markdown) {
        runCatching { Parser.builder().build().parse(markdown) }.getOrNull()
    }
    if (parsed == null) {
        Text(markdown, color = CandyText, fontSize = 13.sp, modifier = modifier)
        return
    }
    val blocks = remember(parsed) { collectBlocks(parsed) }
    Column(modifier = modifier) {
        blocks.forEach { b -> b.Render() }
    }
}

/** 顶层块：一个段落 / 一个代码块 / 一个标题 / 列表 / 引用 */
private sealed class MdBlock {
    @Composable
    abstract fun Render()

    class Paragraph(val parts: List<MdSpan>) : MdBlock() {
        @Composable
        override fun Render() {
            Text(buildSpans(parts), color = CandyText, fontSize = 13.sp)
        }
    }

    class CodeBlock(val code: String) : MdBlock() {
        @Composable
        override fun Render() {
            Text(
                code,
                color = CandyTextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CandyBg)
                    .padding(8.dp),
            )
        }
    }

    class Heading(val level: Int, val parts: List<MdSpan>) : MdBlock() {
        @Composable
        override fun Render() {
            Text(
                buildSpans(parts),
                color = CandyPrimary,
                fontSize = (15 - (level - 1).coerceAtMost(2)).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    class ListItem(val ordered: Boolean, val order: String, val parts: List<MdSpan>) : MdBlock() {
        @Composable
        override fun Render() {
            Row {
                Text(if (ordered) "$order. " else "•  ", color = CandyPrimary, fontSize = 13.sp)
                Text(buildSpans(parts), color = CandyText, fontSize = 13.sp)
            }
        }
    }
}

/** 行内片段：普通文本 / 加粗 / 斜体 / 行内代码 */
private sealed class MdSpan {
    class Plain(val text: String) : MdSpan()
    class Bold(val text: String) : MdSpan()
    class Italic(val text: String) : MdSpan()
    class Code(val text: String) : MdSpan()
}

private fun buildSpans(parts: List<MdSpan>): AnnotatedString = buildAnnotatedString {
    parts.forEach { p ->
        when (p) {
            is MdSpan.Plain -> append(p.text)
            is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = CandyPrimary)) { append(p.text) }
            is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(p.text) }
            is MdSpan.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, color = CandyPrimary, background = CandyBg),
            ) { append(" " + p.text + " ") }
        }
    }
}

/** 遍历把文档拆成顶层块 */
private fun collectBlocks(doc: org.commonmark.node.Node): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    var orderCounter = 0
    var ordered = false
    doc.accept(object : AbstractVisitor() {
        override fun visit(paragraph: Paragraph) {
            out.add(MdBlock.Paragraph(collectSpans(paragraph)))
        }

        override fun visit(heading: Heading) {
            out.add(MdBlock.Heading(heading.level, collectSpans(heading)))
        }

        override fun visit(fencedCodeBlock: FencedCodeBlock) {
            out.add(MdBlock.CodeBlock(fencedCodeBlock.literal.trimEnd('\n')))
        }

        override fun visit(indentedCodeBlock: IndentedCodeBlock) {
            out.add(MdBlock.CodeBlock(indentedCodeBlock.literal.trimEnd('\n')))
        }

        override fun visit(bulletList: BulletList) {
            ordered = false
            orderCounter = 0
            visitChildren(bulletList)
        }

        override fun visit(orderedList: OrderedList) {
            ordered = true
            orderCounter = orderedList.markerStartNumber
            visitChildren(orderedList)
        }

        override fun visit(listItem: ListItem) {
            val marker = if (ordered) (orderCounter++).toString() else "-"
            // 列表项里的段落：合并行内片段
            val spans = mutableListOf<MdSpan>()
            listItem.firstChild?.let { child ->
                if (child is Paragraph) spans.addAll(collectSpans(child))
                else spans.add(MdSpan.Plain(textOf(child)))
            }
            out.add(MdBlock.ListItem(ordered, marker, spans))
        }

        override fun visit(blockQuote: BlockQuote) {
            // 引用块：子段落前缀「」并降亮
            val sb = StringBuilder()
            blockQuote.firstChild?.let { c -> if (c is Paragraph) sb.append(textOf(c)) }
            if (sb.isNotEmpty()) out.add(MdBlock.Paragraph(listOf(MdSpan.Plain(sb.toString()))))
        }
    })
    return out
}

/** 段落内行内片段收集 */
private fun collectSpans(node: org.commonmark.node.Node): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is Text -> spans.add(MdSpan.Plain(child.literal))
            is StrongEmphasis -> spans.add(MdSpan.Bold(textOf(child)))
            is Emphasis -> spans.add(MdSpan.Italic(textOf(child)))
            is Code -> spans.add(MdSpan.Code(child.literal))
            is SoftLineBreak -> spans.add(MdSpan.Plain("\n"))
            is HardLineBreak -> spans.add(MdSpan.Plain("\n"))
            else -> spans.add(MdSpan.Plain(textOf(child)))
        }
        child = child.next
    }
    return spans
}

private fun textOf(node: org.commonmark.node.Node): String {
    val sb = StringBuilder()
    var c = node.firstChild
    while (c != null) {
        if (c is Text) sb.append(c.literal) else sb.append(textOf(c))
        c = c.next
    }
    return sb.toString()
}
