package candy.ide.desktop.plugin.contrib

import androidx.compose.runtime.Composable
import candy.ide.desktop.plugin.PluginHost
import java.io.File

/** 工程设置页分区贡献点（对齐安卓端；appliesTo 为 true 的工程才显示） */
interface ProjectSettingsSectionContribution {
    val id: String
    val sortKey: Int

    fun appliesTo(host: PluginHost, dir: File): Boolean

    @Composable
    fun Content(host: PluginHost, dir: File)
}
