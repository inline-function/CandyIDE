package candy.ide.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import candy.ide.ui.theme.CandyTheme

/** 桌面端主题：沿用 Android 端 Candy 配色；UI 字体 Noto Sans SC，整体字重比默认提一档（加粗） */
@Composable
fun CandyDesktopTheme(content: @Composable () -> Unit) {
    CandyTheme {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = MaterialTheme.typography.heavier(NotoSansSC),
            content = content,
        )
    }
}

/** 换字体 + 字重整体 +1 档：body/label → Medium(500)，title/headline/display → SemiBold(600) */
private fun Typography.heavier(f: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    displayMedium = displayMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    displaySmall = displaySmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    headlineLarge = headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    headlineMedium = headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    headlineSmall = headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleLarge = titleLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleSmall = titleSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    bodyLarge = bodyLarge.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    bodyMedium = bodyMedium.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    bodySmall = bodySmall.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    labelMedium = labelMedium.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    labelSmall = labelSmall.copy(fontFamily = f, fontWeight = FontWeight.Medium),
)
