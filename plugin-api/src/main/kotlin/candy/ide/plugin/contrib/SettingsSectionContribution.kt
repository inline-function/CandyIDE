package candy.ide.plugin.contrib

import androidx.compose.runtime.Composable

/** IDE 设置页分区贡献点（排序按 sortKey 升序插在「引擎与路径」分区附近） */
interface SettingsSectionContribution {
    val id: String
    val sortKey: Int
    val title: String

    @Composable
    fun Content(host: candy.ide.plugin.PluginHost)
}
