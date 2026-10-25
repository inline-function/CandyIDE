package candy.plugin.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import candy.ide.plugin.PluginHost
import candy.ide.plugin.contrib.SettingsSectionContribution
import candy.ide.plugin.ui.PluginColors
import candy.plugin.android.litea.LiteASdk

/** IDE 设置页「工具链」分区：LiteASDK 导入（自 SettingsScreen 迁入） */
class LiteaSdkSection : SettingsSectionContribution {
    override val id: String = "litea-sdk"
    override val sortKey: Int = 1000
    override val title: String = "工具链"

    @Composable
    override fun Content(host: PluginHost) = LiteaImportRow(host)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiteaImportRow(host: PluginHost) {
    var importing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(LiteASdk.ready(host)) }
    var showPathDialog by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("/sdcard/Download/LiteASDK.zip") }
    var importMsg by remember { mutableStateOf<String?>(null) }
    val pickSdk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            LiteASdk.importFromUri(host, uri) { msg -> importing = false; importMsg = msg; ready = LiteASdk.ready(host) }
        }
    }
    Surface(color = PluginColors.Surface) {
        Box(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (!importing) showPathDialog = true },
                    onLongClick = { if (!importing) pickSdk.launch(arrayOf("*/*")) },
                ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("导入 LiteASDK 工具链", color = if (ready) PluginColors.Text else PluginColors.Primary)
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = if (ready) "已导入 · 端上 subl→apk 构建链路可用（再点一次即覆盖重导）"
                        else "aarch64 精简安卓工具链：aapt2/d8/apksigner/zipalign + android.jar，解压到 IDE 私有目录",
                        color = PluginColors.TextDim, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (importing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PluginColors.Primary)
                } else {
                    Text(text = if (ready) "重新导入" else "未导入", color = PluginColors.TextDim)
                }
            }
        }
    }
    if (showPathDialog) {
        AlertDialog(
            onDismissRequest = { showPathDialog = false },
            title = { Text("导入 LiteASDK") },
            text = {
                Column {
                    Text(
                        "填 zip 包在手机上的完整路径（点按弹此对话框；长按导入行走系统文件选择器）。",
                        color = PluginColors.TextDim, style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPathDialog = false
                    importing = true
                    LiteASdk.importFromPath(host, pathInput.trim()) { msg -> importing = false; importMsg = msg; ready = LiteASdk.ready(host) }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showPathDialog = false }) { Text("取消") }
            },
        )
    }
    importMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { importMsg = null },
            title = { Text("LiteASDK") },
            text = { Text(msg, color = PluginColors.Text) },
            confirmButton = {
                TextButton(onClick = { importMsg = null }) { Text("知道了") }
            },
        )
    }
}
