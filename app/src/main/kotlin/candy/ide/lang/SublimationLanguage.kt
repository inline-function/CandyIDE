package candy.ide.lang

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import sugared.functor.lsp.CompletionItemKind
import sugared.functor.lsp.LspAnalysis
import sugared.functor.lsp.Position

/**
 * Sublimation 语言接入 SoraEditor。
 * - 高亮：SublimationAnalyzer（真 Lexer）
 * - 补全：优先 LspAnalysis 的真符号（函数/类型/成员/带签名描述），失败再关键字兜底。
 *   LSP 的关键字补全是 snippet 语法（形如 ${1:name}），sora 的 SimpleCompletionItem 不支持，
 *   一律退回插入 label，避免把字面量 ${...} 写进正文。
 */
class SublimationLanguage(
    private val uri: () -> String? = { null },
    private val lsp: () -> LspAnalysis? = { null },
    private val latestText: () -> String = { "" },
    analyzer: SublimationAnalyzer? = null,
) : Language {

    private val analyzeManager = analyzer ?: SublimationAnalyzer()

    private val symbolPairs = SymbolPairMatch().apply {
        putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
    }

    private val formatter = NopFormatter()

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_SLIGHT

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        publisher.checkCancelled()
        val prefix = buildPrefix(content.getLine(position.line), position.column)
        if (prefix.isEmpty()) return

        // sora 默认按自己的规则重排补全项，会忽略 LSP 的 sortText：显式按 sortText 比较
        publisher.setComparator { a, b ->
            (a.sortText ?: "~").compareTo(b.sortText ?: "~")
        }

        val items = ArrayList<CompletionItem>()
        val name = uri()
        val analysis = if (name != null) lsp() else null
        if (analysis != null && name != null) {
            try {
                // 编辑器最新正文：补全前同步给 LSP，避免拿到空文档/旧文档的空表
                runCatching { analysis.updateDocument(name, latestText()) }
                val list = analysis.completionFor(name, Position(position.line, position.column))
                // 三级排序：本文件/工程声明符号(0_) > stdlib prelude(1_) > 关键字(2_)
                // LSP 的 sortText 前两位标记来源（00/10 声明、02/12 stdlib、01/11 关键字），按此分桶；
                // 用户反馈的「补全词被关键字挤到后面」= 关键字桶排在符号桶之前。
                fun tierOf(item: sugared.functor.lsp.CompletionItem): String {
                    val raw = item.sortText ?: ""
                    val head = if (raw.length >= 2) raw.substring(0, 2) else ""
                    val isKw = item.kind == CompletionItemKind.Keyword || head == "01" || head == "11"
                    val isStd = head == "02" || head == "12"
                    return when {
                        isKw -> "2_kw_"
                        isStd -> "1_std_"
                        else -> "0_sig_"
                    }
                }
                val relabeled = list.items.map { it ->
                    it.copy(sortText = tierOf(it) + (it.sortText ?: "") + "_" + it.label)
                }.sortedBy { it.sortText }
                // sora 开了 comparator 后不再替我们做前缀过滤，必须自己按 prefix 筛选
                // （setComparator 后未匹配项也会被显示出来，不筛就是满屏符号）
                val pfx = prefix.lowercase()
                for (item in relabeled) {
                    val raw = item.insertText
                    val commit = if (raw != null && raw.indexOf('$') >= 0) item.label else (raw ?: item.label)
                    val matched = item.label.lowercase().startsWith(pfx) || commit.lowercase().startsWith(pfx)
                    if (!matched) continue
                    // 副标题规则：按 kind 给用户可读的类型信息（detail 为空时兜底）
                    val sub = subtitleOf(item, kindHint = item.kind)
                    val sci = SimpleCompletionItem(item.label, sub, prefix.length, commit)
                    sci.sortText = item.sortText
                    items.add(sci)
                }
            } catch (e: Throwable) {
                // 语言服务异常（如文档尚未分析完）：记录后降级到关键字，不再静默吞掉
                android.util.Log.w("CandyComplete", "completionFor 失败，降级关键字补全", e)
            }
        }
        if (items.isEmpty()) {
            for (kw in KEYWORDS) {
                if (kw.length > prefix.length && kw.startsWith(prefix, ignoreCase = true)) {
                    val sci = SimpleCompletionItem(kw, prefix.length, kw)
                    sci.sortText = "2_kw_" + kw
                    items.add(sci)
                }
            }
        }
        if (items.isNotEmpty()) publisher.addItems(items)
    }

    /**
     * 补全条目副标题：
     * 变量/参数 → 推导或标注的类型；函数 → 完整签名；结构体 → 签名；
     * 枚举 → "枚举"；类型类 → "类型类"；类型别名 → 实际类型；关键字 → "关键词"。
     * detail 里 LSP 已按 variety 填好（如 "fun add(a: Nat, b: Nat): Nat"），这里只做归类展示。
     */
    private fun subtitleOf(
        item: sugared.functor.lsp.CompletionItem,
        kindHint: CompletionItemKind?,
    ): String {
        val detail = item.detail?.trim().orEmpty()
        return when (kindHint) {
            CompletionItemKind.Keyword -> "关键词"
            CompletionItemKind.Enum -> if (detail.isNotEmpty()) detail else "枚举"
            CompletionItemKind.Class -> if (detail.isNotEmpty()) detail else "类型类"
            CompletionItemKind.Struct, CompletionItemKind.Interface -> detail
            CompletionItemKind.Function, CompletionItemKind.Method -> detail
            CompletionItemKind.Field, CompletionItemKind.Variable, CompletionItemKind.Property -> {
                // detail 形如 "var st: Result[Str, Str]" 或推导注记 "var st 推导为 Result[Str, Str]"
                val cleaned = detail.replace("`", "'")
                if (cleaned.isEmpty()) "变量" else cleaned
            }
            else -> detail
        }
    }

    /** 取光标前的标识符前缀（倒序收集再反转） */
    private fun buildPrefix(lineText: String, column: Int): String {
        val sb = StringBuilder()
        var i = column - 1
        while (i >= 0) {
            val c = lineText[i]
            if (c.isLetterOrDigit() || c == '_') {
                sb.append(c)
                i--
            } else break
        }
        return sb.reverse().toString()
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int = 0

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = formatter

    override fun getSymbolPairs(): SymbolPairMatch = symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler>? = null

    override fun destroy() {
        analyzeManager.destroy()
    }

    companion object {
        private val KEYWORDS = listOf(
            "fun", "class", "struct", "enum", "var", "impl", "for", "if", "else",
            "unchecked", "when", "is", "by", "type", "return", "def", "in",
            "true", "false", "null", "print", "listOf", "emptyList", "map", "filter",
            "forEach", "fold", "size", "isEmpty", "Optional", "Some", "None",
            "List", "Map", "Set", "Nat", "Int", "Rat", "Str", "Bool", "Null",
            "Task", "Channel", "Flow"
        )
    }
}
