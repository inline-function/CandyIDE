package candy.ide.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import candy.ide.data.ProjectStore
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: CandyViewModel) {
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { CandyTitleWithEgg() },
                actions = {
                    IconButton(onClick = { vm.goSettings() }) {
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
            if (vm.projects.isEmpty()) {
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
                    items(vm.projects) { dir ->
                        ProjectCard(
                            dir = dir,
                            onClick = { vm.openProject(dir) },
                            onSettings = { vm.goProjectSettings(dir) },
                            onDelete = { vm.deleteProject(dir) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        NewProjectDialog(
            onCreate = { name, type ->
                vm.createProject(name, type)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCard(dir: candy.ide.fs.CandyPath, onClick: () -> Unit, onSettings: () -> Unit, onDelete: () -> Unit) {
    val fileCount = remember(dir) { dir.walk().count { it.isFile() } }
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
                        when (type) {
                            ProjectStore.TYPE_JSAPK -> "JS-apk 工程"
                            ProjectStore.TYPE_SCRIPT -> "脚本工程"
                            ProjectStore.TYPE_MD -> "md 文档工程"
                            else -> "JS 平台工程"
                        },
                        color = if (type == ProjectStore.TYPE_JSAPK) CandyPrimary else if (type == ProjectStore.TYPE_JS) CandySecondary else CandyTextDim,
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
                Icon(Icons.Default.Settings, contentDescription = "工程设置", tint = CandyTextDim)
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
    // 0=JS 平台 1=脚本 2=JS-apk
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
                            label = { Text("JS 平台工程") }
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
                            label = { Text("JS-apk 工程") }
                        )
                        FilterChip(
                            selected = typeSel == 3,
                            onClick = { typeSel = 3 },
                            label = { Text("md 文档工程") }
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    when (typeSel) {
                        0 -> "编译成 JS 在 WebView 执行，需 fun main() 入口（语言服务已支持）"
                        1 -> "顶层语句直接解释执行（语言服务诊断待补）"
                        2 -> "编译成 JS 注进内置模板 APK，在本机直接安装（SDK 首版：固定包名 candy.jsapk.runtime）"
                        else -> "打包成只读文档 APK：含 md 渲染库与文档文件页，不可编辑"
                    },
                    color = CandyTextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), when (typeSel) { 1 -> ProjectStore.TYPE_SCRIPT; 2 -> ProjectStore.TYPE_JSAPK; 3 -> ProjectStore.TYPE_MD; else -> ProjectStore.TYPE_JS }) },
                enabled = name.isNotBlank() && error == null
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun RenameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val error = when {
        name.isBlank() -> null
        name.any { it in "\\/:*?\"<>|" } -> "名称包含非法字符"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("新名称") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && error == null
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
