package candy.ide.plugin.contrib

import androidx.compose.runtime.Composable
import candy.ide.fs.CandyPath

/** 工程设置页分区贡献点（appliesTo 为 true 的工程才显示） */
interface ProjectSettingsSectionContribution {
    val id: String
    val sortKey: Int

    fun appliesTo(host: candy.ide.plugin.PluginHost, dir: CandyPath): Boolean

    @Composable
    fun Content(host: candy.ide.plugin.PluginHost, dir: CandyPath)
}
