package candy.ide.desktop.plugin.contrib

import androidx.compose.runtime.Composable
import candy.ide.desktop.plugin.PluginHost

/** IDE 设置页分区贡献点（对齐安卓端；排序按 sortKey 升序插在设置页末段） */
interface SettingsSectionContribution {
    val id: String
    val sortKey: Int
    val title: String

    @Composable
    fun Content(host: PluginHost)
}
