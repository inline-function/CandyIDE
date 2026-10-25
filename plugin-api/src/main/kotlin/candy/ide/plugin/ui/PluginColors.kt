package candy.ide.plugin.ui

import androidx.compose.ui.graphics.Color

/**
 * 插件 UI 配色契约：与宿主 candy.ide.ui.theme 的 Candy 色同值。
 * 插件不依赖宿主代码；宿主升级主题时须同步本契约（两处定义值保持一致）。
 */
object PluginColors {
    val Bg = Color(0xFF14111A)
    val Surface = Color(0xFF1D1926)
    val SurfaceVariant = Color(0xFF282333)
    val Primary = Color(0xFFFF5C8A)
    val Secondary = Color(0xFF6FE3C1)
    val Tertiary = Color(0xFFFFC46B)
    val Text = Color(0xFFEDE7F5)
    val TextDim = Color(0xFF9A93A8)
    val Error = Color(0xFFFF6B6B)
}
