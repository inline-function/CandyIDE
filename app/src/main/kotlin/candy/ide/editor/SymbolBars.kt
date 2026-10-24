package candy.ide.editor

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.ui.theme.CandyPrimary

/**
 * 编辑器底部符号栏。
 * - full = false（默认）：两列符号各占一行，多了横向滑动才能看到
 * - full = true（完全显示）：首项 A/U 切换当前符号集，集合内符号自动换行展开（再多也开新行），
 *   不用滑动就能看到全部符号
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolBars(
    onSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    full: Boolean = false,
) {
    var unicodePage by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CandySurface)
    ) {
        if (!full) {
            // 精简模式：常规符号行 + Unicode 符号行，各自横向滚动
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NORMAL_SYMBOLS.forEach { sym ->
                    SymbolKey(sym) { onSymbol(sym) }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UNICODE_KEYS.forEach { sym ->
                    SymbolKey(sym, isUnicode = true) { onSymbol(sym) }
                }
            }
        } else {
            // 完全显示：A/U 切换 + 当前集合的符号全部列出，排不下自动换行（再开一列）
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                // 首项：A ↔ U 切换符号集（防止两列全展开太挤）
                SymbolKey(if (unicodePage) "U" else "A", isPage = true) { unicodePage = !unicodePage }
                val syms = if (unicodePage) UNICODE_KEYS else NORMAL_SYMBOLS
                syms.forEach { sym ->
                    SymbolKey(sym, isUnicode = unicodePage) { onSymbol(sym) }
                }
            }
        }
    }
}

@Composable
private fun SymbolKey(sym: String, isUnicode: Boolean = false, isPage: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            sym,
            color = when {
                isPage -> CandyPrimary
                isUnicode -> CandyText
                else -> CandyTextDim
            },
            fontSize = if (isPage) 14.sp else 15.sp,
            fontWeight = if (isPage) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 常规符号（点击直接插入字面文本） */
private val NORMAL_SYMBOLS = listOf(
    "(", ")", "{", "}", "[", "]", "<", ">",
    "=", "==", "!=", "<=", ">=", "->", "=>",
    "+", "-", "*", "/", "%", ":", ";", ",",
    ".", "\"", "\'", "|", "&", "!", "?", "@",
)

/** Unicode 符号（语言支持 \xxx 触发补全后的最终字符） */
private val UNICODE_KEYS = listOf(
    "∀", "∃", "∃!", "∈", "∧", "∨", "¬",
    "→", "↔", "≠", "≤", "≥", "≡", "⊢", "⊨",
    "∪", "∩", "⊂", "⊃", "∅", "∞", "λ",
    "×", "·", "∘", "Σ", "Π", "ℕ", "ℤ", "ℚ", "ℝ",
)
