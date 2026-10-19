package candy.ide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CandyColors = darkColorScheme(
    primary = CandyPrimary,
    onPrimary = CandySurface,
    secondary = CandySecondary,
    onSecondary = CandySurface,
    tertiary = CandyTertiary,
    background = CandyBg,
    onBackground = CandyText,
    surface = CandySurface,
    onSurface = CandyText,
    surfaceVariant = CandySurfaceVariant,
    onSurfaceVariant = CandyTextDim,
    error = CandyError,
)

@Composable
fun CandyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CandyColors,
        content = content
    )
}
