package candy.ide.md

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurfaceVariant
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
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Markdown 渲染：解析交给 commonmark-java（Apache-2.0），绘制用 Compose 自绘、糖果配色。
 * 用途：日志面板富文本、.md 文件预览。
 */

private val CodeBg = Color(0xFF2A2433)
private val CodeFg = Color(0xFFFF9DBE)
private const val LINK_TAG = "MD_LINK"

/** commonmark 0.30 的 Node 没有 children()，用 firstChild/next 遍历。 */
private fun nodeChildren(n: org.commonmark.node.Node): List<org.commonmark.node.Node> {
    val list = ArrayList<org.commonmark.node.Node>()
    var c = n.firstChild
    while (c != null) {
        list.add(c)
        c = c.next
    }
    return list
}

/** “goto://文件名:行:列” 链接标记：日志页用它做可点跳转 */
const val GOTO_LINK: String = "goto://"

@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier,
    color: Color = CandyText,
    fontSize: TextUnit = 13.sp,
    onLink: ((String) -> Unit)? = null,
) {
    val doc = remember(source) { runCatching { Parser.builder().build().parse(source) }.getOrNull() }
    Column(modifier = modifier) {
        if (doc != null) {
            for (child in nodeChildren(doc)) BlockView(child, color, fontSize, onLink)
        } else if (source.isNotEmpty()) {
            Text(source, color = color, fontSize = fontSize)
        }
    }
}

@Composable
private fun BlockView(node: org.commonmark.node.Node, color: Color, fontSize: TextUnit, onLink: ((String) -> Unit)?) {
    when (node) {
        is Heading -> LinkableText(inlineOf(node), CandyPrimary, FontWeight.SemiBold,
            (fontSize.value + when (node.level) { 1 -> 4f; 2 -> 2.5f; else -> 1f }).sp, onLink)
        is Paragraph -> LinkableText(inlineOf(node), color, FontWeight.Normal, fontSize, onLink)
        is BulletList -> for (item in nodeChildren(node)) {
            if (item is ListItem) {
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text("•", color = CandyPrimary, fontSize = fontSize)
                    Spacer(Modifier.size(4.dp))
                    Column { for (c in nodeChildren(item)) BlockView(c, color, fontSize, onLink) }
                }
            }
        }
        is OrderedList -> {
            var i = node.startNumber
            for (item in nodeChildren(node)) {
                if (item is ListItem) {
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("$i.", color = CandyPrimary, fontSize = fontSize)
                        Spacer(Modifier.size(4.dp))
                        Column { for (c in nodeChildren(item)) BlockView(c, color, fontSize, onLink) }
                    }
                }
                i++
            }
        }
        is FencedCodeBlock -> CodeBlockText(node.literal, node.info, fontSize)
        is IndentedCodeBlock -> CodeBlockText(node.literal, null, fontSize)
        is BlockQuote -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CandySurfaceVariant.copy(alpha = 0.35f))
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            for (c in nodeChildren(node)) BlockView(c, CandyTextDim, fontSize, onLink)
        }
        is ThematicBreak -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CandySurfaceVariant),
        )
        else -> {
            // HtmlBlock 等：取首层文本落袋
            val sb = StringBuilder()
            var c = node.firstChild
            while (c != null) {
                if (c is Text) sb.append(c.literal)
                c = c.next
            }
            val literal = sb.toString()
            if (literal.isNotBlank()) Text(literal, color = color, fontSize = fontSize)
        }
    }
}

@Composable
private fun CodeBlockText(text: String, lang: String?, fontSize: TextUnit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CodeBg),
    ) {
        if (!lang.isNullOrBlank()) {
            Text(lang, color = CandyTextDim, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        }
        Text(
            text.trimEnd().trimEnd(),
            color = CandyText,
            fontSize = (fontSize.value - 1f).sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** 支持 md 链接点击的 Text：带 MD_LINK 注解的点按触发回调 */
@Composable
private fun LinkableText(
    text: AnnotatedString,
    color: Color,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    onLink: ((String) -> Unit)?,
) {
    if (onLink == null) {
        Text(text, color = color, fontWeight = fontWeight, fontSize = fontSize)
        return
    }
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(text) {
            detectTapGestures { pos ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(pos)
                text.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { onLink(it.item) }
            }
        },
    )
}

/** 行内节点树 → AnnotatedString（粗体/斜体/行内代码/链接） */
private fun inlineOf(parent: org.commonmark.node.Node): AnnotatedString = buildAnnotatedString {
    parent.accept(object : AbstractVisitor() {
        override fun visit(text: Text) { append(text.literal) }
        override fun visit(emphasis: Emphasis) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { visitChildren(emphasis) }
        }
        override fun visit(strongEmphasis: StrongEmphasis) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { visitChildren(strongEmphasis) }
        }
        override fun visit(code: Code) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = CodeBg, color = CodeFg)) {
                append(code.literal)
            }
        }
        override fun visit(link: Link) {
            pushStringAnnotation(LINK_TAG, link.destination)
            withStyle(SpanStyle(color = CandyPrimary, textDecoration = TextDecoration.Underline)) {
                visitChildren(link)
            }
            pop()
        }
        override fun visit(softLineBreak: SoftLineBreak) { append('\n') }
        override fun visit(hardLineBreak: HardLineBreak) { append('\n') }
    })
}
