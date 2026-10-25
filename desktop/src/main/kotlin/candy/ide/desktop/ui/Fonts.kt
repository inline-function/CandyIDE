package candy.ide.desktop.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** 代码字体：JetBrains Mono（随包内置，来自 tools/jbmex，OFL） */
val JetBrainsMono = FontFamily(
    Font(resource = "fonts/JetBrainsMono-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/JetBrainsMono-Bold.ttf", weight = FontWeight.Bold),
    Font(resource = "fonts/JetBrainsMono-Italic.ttf", weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(resource = "fonts/JetBrainsMono-BoldItalic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic),
)

/**
 * UI 字体：Noto Sans SC（随包内置，OFL；由 NotoSansSC-VF 实例化的静态字重）。
 * 桌面 Skia 对变量字体渲染不佳（默认实例又细又糊），静态 TTF 才能保证清晰。
 */
val NotoSansSC = FontFamily(
    Font(resource = "fonts/NotoSansSC-400.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/NotoSansSC-500.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/NotoSansSC-600.ttf", weight = FontWeight.SemiBold),
    Font(resource = "fonts/NotoSansSC-700.ttf", weight = FontWeight.Bold),
)
