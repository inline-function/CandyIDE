package candy.ide.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.DesktopScreen
import candy.ide.desktop.IdeState
import candy.ide.desktop.data.ProjectStore
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(state: IdeState) {
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { CandyTitleWithEgg() },
                actions = {
                    IconButton(onClick = { state.screen = DesktopScreen.Docs }) {
                        Icon(Icons.Default.Description, contentDescription = "文档", tint = CandyText)
                    }
                    IconButton(onClick = {
                        val dir = Platform.pickDirectory("选择工程目录")
                        if (dir != null) state.openProjectWindow(dir)
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "打开任意目录", tint = CandyText)
                    }
                    IconButton(onClick = {
                        // 导入 .capr 归档：解压到工程根目录成为新工程（与 Android 端接收 .capr 同构）
                        val frame = java.awt.Frame()
                        val fd = java.awt.FileDialog(frame, "导入 .capr 归档", java.awt.FileDialog.LOAD)
                        fd.isMultipleMode = false
                        fd.setFilenameFilter { _, name -> name.endsWith(".capr") || name.endsWith(".zip") }
                        fd.isVisible = true
                        val picked = fd.files.firstOrNull()
                        frame.dispose()
                        if (picked == null) return@IconButton
                        val pname = picked.name.substringBeforeLast('.').ifBlank { "imported" }
                        val root = candy.ide.desktop.data.ProjectStore.projectsRoot()
                        var out = File(root, pname)
                        var n = 1
                        while (out.exists()) { out = File(root, pname + "-$n"); n++ }
                        val ok = out.mkdirs() && candy.ide.desktop.Workspace.extractArchive(picked, out)
                        if (ok) {
                            state.refreshProjects()
                            state.toast = "已导入工程：" + out.name
                            state.openProjectWindow(out)
                        } else {
                            state.toast = "导入失败：归档损坏或名字非法"
                        }
                    }) {
                        Icon(Icons.Filled.Archive, contentDescription = "导入归档", tint = CandyText)
                    }
                    if (candy.ide.desktop.Settings.agentEnabled) {
                        IconButton(onClick = { state.screen = DesktopScreen.Agent }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "AI 助手", tint = CandyText)
                        }
                    }
                    IconButton(onClick = { state.screen = DesktopScreen.Settings }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置", tint = CandyText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CandyBg,
                    titleContentColor = CandyPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建工程") },
                containerColor = CandyPrimary,
                contentColor = Color.White
            )
        },
        containerColor = CandyBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CandyBg),
            contentAlignment = Alignment.Center
        ) {
            if (state.projects.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有工程", color = CandyText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(4.dp))
                    Text("点击右下角「新建工程」开始", color = CandyTextDim)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.projects) { dir ->
                        ProjectCard(
                            dir = dir,
                            onClick = { state.openProjectWindow(dir) },
                            onSettings = {
                                state.projectSettingsTarget = dir
                                state.screen = candy.ide.desktop.DesktopScreen.ProjectSettings
                            },
                            onDelete = { state.deleteProject(dir) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        NewProjectDialog(
            onCreate = { name, type ->
                val p = state.createProject(name, type)
                if (p == null) state.toast = "创建失败：工程已存在或名字非法"
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCard(dir: File, onClick: () -> Unit, onSettings: () -> Unit, onDelete: () -> Unit) {
    val fileCount = remember(dir) { dir.walkTopDown().count { it.isFile() } }
    val type = remember(dir) { ProjectStore.typeOf(dir) }
    val edited = remember(dir) { ProjectStore.lastEdited(dir) }
    val editedText = remember(edited) { relativeTime(edited) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = CandySurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = CandyPrimary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dir.name, color = CandyText, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        candy.ide.desktop.data.projectTypeLabel(dir),
                        color = when (type) {
                            ProjectStore.TYPE_JSAPK -> CandyPrimary
                            ProjectStore.TYPE_JS -> CandySecondary
                            else -> CandyTextDim
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$fileCount 个文件", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                    if (edited > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("· $editedText", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "工程信息", tint = CandyTextDim)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除工程", tint = CandyTextDim)
            }
        }
    }
}

/** 相对时间：刚刚 / x 分钟前 / x 小时前 / x 天前 / 日期 */
private fun relativeTime(ts: Long): String {
    if (ts <= 0) return ""
    val diffMs = System.currentTimeMillis() - ts
    val min = diffMs / 60000
    return when {
        diffMs < 60_000 -> "刚刚"
        min < 60 -> min.toString() + " 分钟前"
        min < 1440 -> (min / 60).toString() + " 小时前"
        min < 1440 * 30 -> (min / 1440).toString() + " 天前"
        else -> {
            val fmt = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.CHINA)
            fmt.format(java.util.Date(ts))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectDialog(onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    // 0=JS 平台 1=脚本 2=JS-apk 3=MD
    var typeSel by remember { mutableIntStateOf(0) }
    val error = when {
        name.isBlank() -> null
        name.any { it in "\\/:*?\"<>|" } -> "名称包含非法字符"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建工程") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("工程名称") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "") }
                )
                Spacer(Modifier.size(12.dp))
                Text("工程类型", color = CandyTextDim, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.size(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = typeSel == 0,
                            onClick = { typeSel = 0 },
                            label = { Text("JS工程") }
                        )
                        FilterChip(
                            selected = typeSel == 1,
                            onClick = { typeSel = 1 },
                            label = { Text("脚本工程") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = typeSel == 2,
                            onClick = { typeSel = 2 },
                            label = { Text("安卓工程") }
                        )
                        FilterChip(
                            selected = typeSel == 3,
                            onClick = { typeSel = 3 },
                            label = { Text("MD工程") }
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    when (typeSel) {
                        0 -> "将 Subl 编译为 JS 代码，由 Node.js 执行"
                        1 -> "通过解释器直接执行 Subs 脚本"
                        2 -> "@composable 视图 + @android 内建；桌面端提供编辑诊断/布局预览并按 JS 运行（APK 打包请用安卓端）"
                        else -> "用于存放文档的工程"
                    },
                    color = CandyTextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(name.trim(), when (typeSel) {
                        1 -> ProjectStore.TYPE_SCRIPT
                        2 -> ProjectStore.TYPE_JSAPK
                        3 -> ProjectStore.TYPE_MD
                        else -> ProjectStore.TYPE_JS
                    })
                },
                enabled = name.isNotBlank() && error == null
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
