package candy.ide.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.IdeState
import candy.ide.desktop.OutputLine
import candy.ide.desktop.Workspace
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import java.io.File

/**
 * 工程文件树（对齐 Android 端 FileTreePanel）：
 * 单击打开/展开；行尾 ⋮ 菜单 = 新建文件/目录、重命名、删除、
 * 解压 zip、用系统默认程序打开、在文件管理器中定位、复制路径。
 */
@Composable
fun FileTreePanel(state: IdeState, project: File) {
    var revision by remember { mutableIntStateOf(0) }
    var menuNode by remember { mutableStateOf<Workspace.TreeNode?>(null) }
    var newRequest by remember { mutableStateOf<Pair<Workspace.TreeNode, Boolean>?>(null) }
    var renaming by remember { mutableStateOf<Workspace.TreeNode?>(null) }
    var pendingDelete by remember { mutableStateOf<Workspace.TreeNode?>(null) }
    var pendingZip by remember { mutableStateOf<Workspace.TreeNode?>(null) }
    fun mutated(msg: String) {
        revision++
        menuNode = null
        state.log(msg, OutputLine.Kind.Success)
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        key(revision) {
            TreeNodes(state, project, 0) { menuNode = it }
        }
    }

    // 行内菜单
    menuNode?.let { node ->
        Box {
            DropdownMenu(expanded = true, onDismissRequest = { menuNode = null }) {
                if (node.isDir) {
                    DropdownMenuItem(text = { Text("新建文件") }, onClick = { newRequest = node to false })
                    DropdownMenuItem(text = { Text("新建目录") }, onClick = { newRequest = node to true })
                } else {
                    DropdownMenuItem(text = { Text("打开") }, onClick = {
                        menuNode = null
                        state.open(node.file)
                    })
                }
                if (node.file.extension.equals("zip", ignoreCase = true) || node.file.extension.equals("capr", ignoreCase = true)) {
                    DropdownMenuItem(text = { Text("解压到同名目录") }, onClick = { pendingZip = node; menuNode = null })
                }
                if (!node.isDir) {
                    DropdownMenuItem(text = { Text("用系统默认程序打开") }, onClick = {
                        menuNode = null
                        runCatching { java.awt.Desktop.getDesktop().open(node.file) }
                            .onFailure { state.toast = "无法打开：" + it.message }
                    })
                }
                if (node.isDir) {
                    DropdownMenuItem(text = { Text("在文件管理器中打开") }, onClick = {
                        menuNode = null
                        runCatching { java.awt.Desktop.getDesktop().open(node.file) }
                            .onFailure { state.toast = "无法打开：" + it.message }
                    })
                }
                DropdownMenuItem(text = { Text("重命名") }, onClick = { renaming = node; menuNode = null })
                DropdownMenuItem(text = { Text("复制路径") }, onClick = {
                    menuNode = null
                    runCatching {
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                            java.awt.datatransfer.StringSelection(node.file.absolutePath), null,
                        )
                    }
                    state.toast = "已复制路径"
                })
                DropdownMenuItem(text = { Text("删除") }, onClick = { pendingDelete = node; menuNode = null })
            }
        }
    }

    // 新建
    newRequest?.let { (parent, isDir) ->
        NodeNameDialog(
            title = if (isDir) "新建目录" else "新建文件",
            initial = if (isDir) "" else "new.subs",
            onConfirm = { name ->
                val target = File(parent.file, name)
                val ok = if (isDir) target.mkdirs() else runCatching { target.createNewFile() }.getOrDefault(false)
                if (ok) state.gitAutoCommitIfEnabled(if (isDir) "新建目录 " + name + " 文件" else "新建 " + name + " 文件")
                mutated(if (ok) "已创建 " + name else "创建失败 " + name)
                newRequest = null
            },
            onDismiss = { newRequest = null },
        )
    }

    // 重命名
    renaming?.let { node ->
        NodeNameDialog(
            title = "重命名「" + node.name + "」",
            initial = node.name,
            onConfirm = { name ->
                val target = File(node.file.parentFile, name)
                val ok = node.file.renameTo(target)
                if (ok && state.activeFile?.absolutePath == node.file.absolutePath) {
                    state.open(target)
                }
                mutated(if (ok) "已重命名为 " + name else "重命名失败")
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    // 删除确认
    pendingDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「" + node.name + "」？") },
            text = { Text("删除后不可恢复", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    val ok = if (node.isDir) node.file.deleteRecursively() else node.file.delete()
                    if (ok) state.gitAutoCommitIfEnabled("删除 " + node.name + " 文件")
                    mutated(if (ok) "已删除 " + node.name else "删除失败 " + node.name)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    // 解压
    pendingZip?.let { zf ->
        AlertDialog(
            onDismissRequest = { pendingZip = null },
            title = { Text("解压「" + zf.name + "」？") },
            text = { Text("解压到同名目录 " + zf.file.nameWithoutExtension + "（工程内）", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    val out = File(zf.file.parentFile, zf.file.nameWithoutExtension)
                    val ok = candy.ide.desktop.Workspace.extractArchive(zf.file, out)
                    mutated(if (ok) "已解压 " + zf.name else "解压失败：" + zf.name)
                    pendingZip = null
                }) { Text("解压") }
            },
            dismissButton = { TextButton(onClick = { pendingZip = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TreeNodes(state: IdeState, dir: File, depth: Int, onMenu: (Workspace.TreeNode) -> Unit) {
    for (node in Workspace.children(dir)) {
        val key = node.file.absolutePath
        val expanded = state.expandedDirs[key] ?: (depth == 0)
        val icon = when {
            node.isDir -> Icons.Default.Folder
            node.name.endsWith(".subs") || node.name.endsWith(".subl") -> Icons.Default.Code
            node.name.endsWith(".md") -> Icons.Default.Description
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { if (node.isDir) state.expandedDirs[key] = !expanded else state.open(node.file) }
                .background(if (!node.isDir && state.activeFile?.absolutePath == key) CandySurfaceVariant else Color.Transparent)
                .padding(start = (depth * 14 + 12).dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (node.isDir) CandyPrimary else CandyTextDim, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(node.name, color = CandyText, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            IconButton(onClick = { onMenu(node) }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "「" + node.name + "」操作", tint = CandyTextDim, modifier = Modifier.size(13.dp))
            }
        }
        if (node.isDir && expanded) TreeNodes(state, node.file, depth + 1, onMenu)
    }
}

@Composable
private fun NodeNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val error = when {
        name.isBlank() -> "名字不能为空"
        name.contains("/") || name.contains("\\") || name.contains(File.separator) -> "不能包含路径分隔符"
        name.trim() == "." || name.trim() == ".." -> "非法名字"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it, color = CandyTextDim) } },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = CandyText),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = error == null) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
