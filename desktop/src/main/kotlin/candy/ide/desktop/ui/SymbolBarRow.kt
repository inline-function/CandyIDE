package candy.ide.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim

/** Unicode 符号栏（对齐安卓端 SymbolBars 的 Unicode 行）：横向滚动，点击插入 */
@Composable
fun SymbolBarRow(
    onSymbol: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(CandySurface)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Σ",
            color = CandyPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp),
        )
        UNICODE_SYMBOLS.forEach { sym ->
            Text(
                sym,
                color = CandyText,
                fontSize = 15.sp,
                modifier = Modifier
                    .width(38.dp)
                    .padding(2.dp)
                    .clickable { onSymbol(sym) },
            )
        }
        Text(
            "✕",
            color = CandyTextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp, end = 10.dp).clickable { onClose() },
        )
    }
}

/** Unicode 符号表（与安卓端 UNICODE_KEYS 同表） */
private val UNICODE_SYMBOLS = listOf(
    "∀", "∃", "∃!", "∈", "∧", "∨", "¬",
    "→", "↔", "≠", "≤", "≥", "≡", "⊢", "⊨",
    "∪", "∩", "⊂", "⊃", "∅", "∞", "λ",
    "×", "·", "∘", "Σ", "Π", "ℕ", "ℤ", "ℚ", "ℝ",
)
