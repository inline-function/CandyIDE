package candy.ide.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim

/** 编辑器底部符号栏：上方一行常规符号，下方一行 Unicode 符号 */
@Composable
fun SymbolBars(
    onSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var unicodePage by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CandySurface)
    ) {
        // 常规符号行
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
        // Unicode 符号行
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
    }
}

@Composable
private fun SymbolKey(sym: String, isUnicode: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            sym,
            color = if (isUnicode) CandyText else CandyTextDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
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
