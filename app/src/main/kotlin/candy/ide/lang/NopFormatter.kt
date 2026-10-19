package candy.ide.lang

import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextRange

/** 空实现格式化器：MVP 不做格式化（语言侧 LSP 已有 formatting，后续接入） */
class NopFormatter : Formatter {
    override fun format(text: Content, cursorRange: TextRange) {
        // no-op
    }

    override fun formatRegion(text: Content, rangeToFormat: TextRange, cursorRange: TextRange) {
        // no-op
    }

    override fun setReceiver(receiver: Formatter.FormatResultReceiver?) {
        // no-op
    }

    override fun isRunning(): Boolean = false

    override fun destroy() {
        // no-op
    }
}
