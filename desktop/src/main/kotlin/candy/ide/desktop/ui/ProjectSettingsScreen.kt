package candy.ide.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.DesktopEngine
import candy.ide.desktop.IdeState
import candy.ide.desktop.Workspace
import candy.ide.desktop.data.ProjectStore
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import java.awt.Desktop
import java.io.File

/**
 * 工程设置（与 Android 端 ProjectSettingsScreen 对齐的功能子集，去掉 APK 打包相关项）：
 * 工程信息 / 重命名 / 在文件管理器中打开 / 复制路径 / 远程仓库地址 / 删除工程（回主页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsScreen(state: IdeState) {
    val dir = state.projectSettingsTarget ?: return
    val typeLabel = remember(dir) { candy.ide.desktop.data.projectTypeLabel(dir) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf(ProjectStore.remoteOf(dir)) }
    val fileCount = remember(dir) { dir.walk().filter { it.isFile }.count() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工程设置") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = candy.ide.desktop.DesktopScreen.Projects }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
            )
        },
        containerColor = CandyBg,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("工程") {
                InfoRow("名称", dir.name)
                InfoRow("类型", typeLabel)
                InfoRow("路径", dir.absolutePath)
                InfoRow("文件数", fileCount.toString() + " 个")
                InfoRow("最近活跃", prettyTime(ProjectStore.lastEdited(dir)))
            }

            Section("操作") {
                RowAction(Icons.Filled.Edit, "重命名工程", dir.name) { renaming = true }
                RowAction(Icons.Filled.FolderOpen, "在文件管理器中打开", "") {
                    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir) }
                        .onFailure { state.toast = "打开失败：" + it.message }
                }
                RowAction(Icons.Filled.ContentCopy, "复制工程路径", "") {
                    copyToClipboard(dir.absolutePath)
                    state.toast = "已复制路径"
                }
                RowAction(Icons.Filled.Archive, "导出归档", "压缩工程为 .capr（zip）") {
                    val dlg = java.awt.FileDialog(java.awt.Frame(), "导出工程归档", java.awt.FileDialog.SAVE)
                    dlg.file = dir.name + ".capr"
                    dlg.isVisible = true
                    val target = dlg.file?.let { File(dlg.directory, it) }
                    if (target == null) return@RowAction
                    val ok = Workspace.zipTo(dir, target)
                    state.toast = if (ok) "已导出 " + target.name else "导出失败"
                }
                RowAction(Icons.Filled.Delete, "删除工程", "", danger = true) { confirmDelete = true }
            }

            // 插件贡献的工程设置分区（对齐安卓端 ProjectSettingsScreen：appliesTo 的工程才显示；
            // android-project 为 jsapk 工程提供「APK 打包（安卓端专属）」说明分区）
            for (section in candy.ide.desktop.plugin.PluginRegistry.projectSettingsSections()) {
                if (!runCatching { section.appliesTo(candy.ide.desktop.plugin.host.DesktopPluginHost(state), dir) }.getOrDefault(false)) continue
                Section(section.id) {
                    section.Content(candy.ide.desktop.plugin.host.DesktopPluginHost(state), dir)
                }
            }
            Section("远程仓库") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = remote,
                        onValueChange = { remote = it },
                        label = { Text("Git 远程地址（可留空）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CandyText),
                    )
                    Button(
                        onClick = {
                            ProjectStore.setRemote(dir, remote.trim())
                            state.toast = "远程地址已保存"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CandyPrimary),
                    ) { Text("保存") }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(dir.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("重命名工程") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && name != dir.name, onClick = {
                    val ok = runCatching { ProjectStore.rename(dir, name.trim()) }.getOrDefault(false)
                    renaming = false
                    if (ok) { state.toast = "已重命名"; state.refreshProjects(); state.screen = candy.ide.desktop.DesktopScreen.Projects }
                    else state.toast = "重命名失败：名称冲突或文件被占用"
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除工程") },
            text = { Text("将删除工程「" + dir.name + "」及其全部文件，不可恢复。", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    state.closeProjectWindow(dir)
                    runCatching { ProjectStore.deleteProject(dir) }
                    confirmDelete = false
                    state.refreshProjects()
                    state.screen = candy.ide.desktop.DesktopScreen.Projects
                    state.toast = "已删除工程"
                }) { Text("删除", color = CandyError) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = CandyPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        content()
    }
}

private fun prettyTime(ts: Long): String {
    if (ts <= 0L) return "未知"
    val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0)
    val min = diff / 60000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> min.toString() + " 分钟前"
        min < 60 * 24 -> (min / 60).toString() + " 小时前"
        min < 60 * 24 * 30 -> (min / 1440).toString() + " 天前"
        else -> java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(ts))
    }
}

private fun copyToClipboard(text: String) {
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        val clipboard = toolkit.systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}

@Composable
private fun InfoRow(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = CandyText, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
        Text(value, color = CandyTextDim, fontSize = 11.sp)
    }
}

@Composable
private fun RowAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sub: String, danger: Boolean = false, onClick: () -> Unit) {
    Surface(color = CandySurface, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, tint = if (danger) CandyError else CandySecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = if (danger) CandyError else CandyText, fontSize = 13.sp)
                if (sub.isNotEmpty()) Text(sub, color = CandyTextDim, fontSize = 11.sp)
            }
        }
    }
}
