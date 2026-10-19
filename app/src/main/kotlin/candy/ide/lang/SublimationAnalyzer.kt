package candy.ide.lang

import candy.ide.editor.Schemes
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import sugared.functor.lexer.Kind
import sugared.functor.lexer.Lexer

/**
 * 用 Sublimation 自己的 Lexer 做 SoraEditor 的基础语法高亮，
 * 再叠加 LSP 的 semanticTokens：类型 / 变量 / 形参 / 注解 / 枚举成员等
 * 同一列上语义覆盖词法（普通标识符获得语义身份）。
 *
 * SimpleAnalyzeManager 会在文本变化时整篇重跑；Lexer + 复用 LSP 已分析的
 * 语义 token 都足够快。Lexer 行列从 1 开始，编辑器从 0 开始，各减一。
 * onAnalyzed 返回 LSP 的 semanticTokens（扁平 5 元组），同时把最新正文同步给 LSP。
 */
class SublimationAnalyzer(
    private val onAnalyzed: ((String) -> List<Int>?)? = null,
) : SimpleAnalyzeManager<Any?>() {

    override fun analyze(text: StringBuilder, delegate: SimpleAnalyzeManager<Any?>.Delegate<Any?>): Styles {
        val styles = Styles()
        val builder = MappedSpans.Builder()
        val src = text.toString()

        // 每行的 span：列为键；后加入的同列 span 覆盖先加入的（语义压词法）
        val perLine = HashMap<Int, MutableList<Pair<Int, Long>>>()
        // 词法层认定的函数列（fun foo / foo( 调用）：语义层不得用 variable(4) 再压回去
        val lexicalFunction = HashSet<Long>()
        try {
            val tokens = Lexer(src, "candy").tokenize()
            for (i in tokens.indices) {
                val tk = tokens[i]
                if (tk.kind == Kind.EOF) continue
                val isFun = tk.kind == Kind.IDENT && (
                    (i > 0 && tokens[i - 1].kind == Kind.FUN) ||
                    (i + 1 < tokens.size && tokens[i + 1].kind == Kind.LPAREN)
                )
                val style = if (isFun) Schemes.styleOfToken("function") else styleOf(tk.kind)
                val line0 = tk.line - 1
                val col0 = (tk.col - 1).coerceAtLeast(0)
                if (isFun) lexicalFunction.add((line0.toLong() shl 32) or col0.toLong())
                addSpan(perLine, line0, col0, style)
            }
        } catch (e: Exception) {
            // 编辑中途态常见词法失败：降级为纯语义着色，不阻断其它能力
        }

        // 语义 token（LSP semanticTokens）：5 元组 [deltaLine, deltaStart, len, type, mods]
        val semantic = onAnalyzed?.invoke(src)
        if (!semantic.isNullOrEmpty() && semantic.size % 5 == 0) {
            var line = 0
            var col = 0
            var k = 0
            while (k + 5 <= semantic.size) {
                val dl = semantic[k]
                val dc = semantic[k + 1]
                val type = semantic[k + 3]
                k += 5
                if (dl == 0) col += dc else { line += dl; col = dc }
                if (line < 0 || col < 0) continue
                val style = semanticStyle(type) ?: continue
                // 语义层把内置调用（print 等）fallback 成 variable(4)，会盖掉词法层的函数色：
                // 同列已被词法认定为函数、且语义只是普通变量时，保留词法结果
                val key = (line.toLong() shl 32) or col.toLong()
                if (type == 4 && key in lexicalFunction) continue
                addSpan(perLine, line, col, style)
            }
        }

        val lineCount = src.count { it == '\n' }
        for (line in 0..lineCount) {
            perLine[line]?.sortedBy { it.first }?.forEach { (c, style) ->
                builder.add(line, SpanFactory.obtain(c, style))
            }
        }
        builder.determine(lineCount)
        styles.spans = builder.build()
        return styles
    }

    private fun addSpan(map: HashMap<Int, MutableList<Pair<Int, Long>>>, line: Int, col: Int, style: Long) {
        val list = map.getOrPut(line) { ArrayList() }
        list.removeAll { it.first == col }
        list.add(col to style)
    }

    /** semantic token type 索引（LSP 的 SEMANTIC_TOKEN_TYPES legend）→ 配色 key */
    private fun semanticStyle(type: Int): Long? = when (type) {
        0 -> Schemes.styleOfToken("keyword")
        1, 10, 11, 12 -> Schemes.styleOfToken("type")       // type / class / enum / struct
        2 -> Schemes.styleOfToken("function")
        3, 5 -> Schemes.styleOfToken("parameter")           // parameter / property
        4 -> Schemes.styleOfToken("identifier")             // variable
        6 -> Schemes.styleOfToken("type")                 // enumMember（构造子 Ok/Err/Some/None）
        7 -> Schemes.styleOfToken("number")                  // number
        8 -> Schemes.styleOfToken("string")
        9 -> Schemes.styleOfToken("operator")
        13 -> Schemes.styleOfToken("annotation")            // decorator
        else -> null
    }

    // 走 Schemes：颜色与粗体由当前配色方案决定（设置页可换可改）
    private fun styleOf(kind: Kind): Long = when (kind) {
        Kind.FUN, Kind.CLASS, Kind.STRUCT, Kind.ENUM, Kind.VAR, Kind.IMPL, Kind.FOR, Kind.IF, Kind.ELSE,
        Kind.UNCHECKED, Kind.WHEN, Kind.IS, Kind.BY, Kind.TYPE, Kind.RET, Kind.IN, Kind.DEF,
        Kind.FORALL, Kind.EXISTS, Kind.EXISTS1, Kind.LAND, Kind.LOR, Kind.LNOT, Kind.IMPLIES, Kind.EQUIV,
        Kind.TOP, Kind.BOT, Kind.BECAUSE, Kind.THEREFORE, Kind.PLUS_BECAUSE ->
            Schemes.styleOfToken("keyword")

        Kind.INT, Kind.FLOAT -> Schemes.styleOfToken("number")
        Kind.STR -> Schemes.styleOfToken("string")
        Kind.IDENT -> Schemes.styleOfToken("identifier")
        Kind.AT -> Schemes.styleOfToken("annotation")
        Kind.COLON2, Kind.ARROW, Kind.DARROW, Kind.IFF -> Schemes.styleOfToken("function")
        Kind.PLUS, Kind.MINUS, Kind.STAR, Kind.SLASH, Kind.PERCENT, Kind.POW,
        Kind.ASSIGN, Kind.EQ, Kind.NEQ, Kind.LE, Kind.GE, Kind.LT, Kind.GT, Kind.RANGE,
        Kind.AMP, Kind.PIPE, Kind.BANG ->
            Schemes.styleOfToken("operator")

        Kind.LPAREN, Kind.RPAREN, Kind.LBRACKET, Kind.RBRACKET, Kind.LBRACE, Kind.RBRACE,
        Kind.COMMA, Kind.SEMI, Kind.COLON, Kind.DOT, Kind.BACKSLASH, Kind.DOLLAR, Kind.HASH, Kind.SYMBOL ->
            Schemes.styleOfToken("punctuation")

        Kind.EOF -> Schemes.styleOfToken("text")
    }
}
