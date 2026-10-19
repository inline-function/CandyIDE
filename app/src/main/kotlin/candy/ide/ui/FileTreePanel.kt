package candy.ide.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.data.TreeNode
import candy.ide.data.buildTree
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyTertiary
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel
import java.io.File

/** 左侧抽屉：IDEA 式工程文件树 + 新建/重命名/删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreePanel(
    vm: CandyViewModel,
    onFileClick: (File) -> Unit,
    drawerClose: () -> Unit = {},
) {
    val project = vm.currentProject.value
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 操作树版本：任何增删改后 +1 驱动整树重建
    val tv = vm.treeVersion.intValue
    var rootExpanded by remember { mutableStateOf(true) }
    var newNameDialog by remember { mutableStateOf<NewNodeRequest?>(null) }
    var renamingNode by remember { mutableStateOf<TreeNode?>(null) }
    var pendingDelete by remember { mutableStateOf<TreeNode?>(null) }
    var menuNode by remember { mutableStateOf<TreeNode?>(null) }
    var pendingDeleteMulti by remember { mutableStateOf<List<File>?>(null) }
    var pendingZip by remember { mutableStateOf<File?>(null) }
    var openWithNode by remember { mutableStateOf<TreeNode?>(null) }
    // 新建文件/目录的落地目录：点目录行即选中它（展开/收起照旧），默认工程根
    var selectedDir by remember { mutableStateOf<File?>(null) }
    // ---- 多选模式 ----
    var multiMode by remember { mutableStateOf(false) }
    val multiSet = remember { androidx.compose.runtime.mutableStateListOf<File>() }

    ModalDrawerSheet(modifier = Modifier.width(300.dp).background(CandySurface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = CandyPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (multiMode) "已选 " + multiSet.size + " 项" else (project?.name ?: "工程"),
                color = CandyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // 右上角多选切换按钮
            IconButton(onClick = {
                multiMode = !multiMode
                if (!multiMode) multiSet.clear()
            }) {
                Icon(
                    if (multiMode) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = "多选",
                    tint = if (multiMode) CandyPrimary else CandyTextDim,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // 多选操作栏：删除 / 复制 / 剪切 / 压缩为 zip
        if (multiMode) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.AssistChip(
                        onClick = { vm.copyMulti(multiSet.toList()); multiSet.clear(); multiMode = false },
                        label = { Text("复制", style = MaterialTheme.typography.labelSmall) },
                    )
                    androidx.compose.material3.AssistChip(
                        onClick = { vm.cutMulti(multiSet.toList()); multiSet.clear(); multiMode = false },
                        label = { Text("剪切", style = MaterialTheme.typography.labelSmall) },
                    )
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            val target = File(project, "selected-" + System.currentTimeMillis() + ".zip")
                            vm.zipMulti(multiSet.toList(), target)
                            multiSet.clear(); multiMode = false
                        },
                        label = { Text("压缩 zip", style = MaterialTheme.typography.labelSmall) },
                    )
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            if (multiSet.isNotEmpty()) {
                                pendingDeleteMulti = multiSet.toList()
                                multiSet.clear(); multiMode = false
                            }
                        },
                        label = { Text("删除", style = MaterialTheme.typography.labelSmall) },
                    )
                }
                if (vm.multiClip.value.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "剪贴板有 " + vm.multiClip.value.size + " 项",
                        color = CandyTextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(color = CandySurfaceVariant)
            }
        }
        HorizontalDivider(color = CandySurfaceVariant)
        if (project == null) {
            Text("未打开工程", color = CandyTextDim, modifier = Modifier.padding(16.dp))
        } else {
            val tree = remember(project, tv) { buildTree(project) }
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.verticalScroll(scrollState).padding(vertical = 8.dp).fillMaxSize()
            ) {
                // 工程根行：展开/收起整个树
                RootRow(
                    project = project,
                    expanded = rootExpanded,
                    selected = selectedDir == project,
                    onToggle = { rootExpanded = !rootExpanded },
                    onSelect = { selectedDir = it },
                    onMenu = { node -> menuNode = node },
                )
                if (rootExpanded) {
                    TreeNodes(tree, 0, vm, selectedDir, onFileClick,
                        onNodeMenu = { node -> menuNode = node },
                        onSelectDir = { f -> selectedDir = f },
                        onZipClick = { f -> pendingZip = f },
                        multiMode = multiMode,
                        multiSet = multiSet,
                    )
                }
            }
        }
    }

    menuNode?.let { node ->
        val items = if (node.isDir) listOf(
            "创建子文件" to { newNameDialog = NewNodeRequest(node.file, false); menuNode = null },
            "创建子目录" to { newNameDialog = NewNodeRequest(node.file, true); menuNode = null },
            if (vm.clipFile.value != null) "粘贴文件" to { vm.pasteInto(node.file); menuNode = null } else null,
            "重命名" to { renamingNode = node; menuNode = null },
            "复制目录" to { vm.copyNode(node.file); menuNode = null },
            "剪切目录" to { vm.cutNode(node.file); menuNode = null },
            "删除目录" to { pendingDelete = node; menuNode = null },
            "复制路径" to { vm.copyPath(ctx, node.file); menuNode = null },
        ).filterNotNull()
        else {
            val isApk = node.name.endsWith(".apk", ignoreCase = true)
            listOf(
                if (isApk) "安装 APK" to { vm.installApk(node.file); menuNode = null; drawerClose() } else null,
                "打开方式…" to { openWithNode = node; menuNode = null },
                "重命名" to { renamingNode = node; menuNode = null },
                "复制文件" to { vm.copyNode(node.file); menuNode = null },
                "剪切文件" to { vm.cutNode(node.file); menuNode = null },
                "删除文件" to { pendingDelete = node; menuNode = null },
                "复制路径" to { vm.copyPath(ctx, node.file); menuNode = null },
            ).filterNotNull()
        }
        AlertDialog(
            onDismissRequest = { menuNode = null },
            title = { Text(node.name) },
            text = {
                Column {
                    items.forEach { (label, action) ->
                        TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                            Text(label, color = if (label.startsWith("删除")) Color(0xFFFF5C8A) else CandyText)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    openWithNode?.let { node ->
        val isMd = node.name.endsWith(".md", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { openWithNode = null },
            title = { Text("打开方式：" + node.name) },
            text = {
                Column {
                    if (isMd) TextButton(onClick = {
                        vm.openAsText(node.file); openWithNode = null; drawerClose()
                    }, modifier = Modifier.fillMaxWidth()) { Text("以文本文件打开", color = CandyText) }
                    if (isMd) TextButton(onClick = {
                        vm.openAsMd(node.file); openWithNode = null; drawerClose()
                    }, modifier = Modifier.fillMaxWidth()) { Text("以 MD 渲染模式打开", color = CandyText) }
                    if (!isMd && !node.name.endsWith(".apk", ignoreCase = true)) TextButton(onClick = {
                        vm.openAsText(node.file); openWithNode = null; drawerClose()
                    }, modifier = Modifier.fillMaxWidth()) { Text("以文本文件打开", color = CandyText) }
                    TextButton(onClick = {
                        vm.openExternal(ctx, node.file); openWithNode = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("其他应用打开", color = CandyText) }
                }
            },
            confirmButton = {},
        )
    }

    newNameDialog?.let { req ->
        NodeNameDialog(
            title = if (req.isDirectory) "新建目录" else "新建文件",
            confirm = "创建",
            initial = if (req.isDirectory) "" else if (req.parent == project) "new.subl" else "new.subl",
            onConfirm = { name ->
                if (req.isDirectory) vm.createDir(req.parent, name) else vm.createFile(req.parent, name)
                newNameDialog = null
            },
            onDismiss = { newNameDialog = null }
        )
    }

    renamingNode?.let { node ->
        NodeNameDialog(
            title = "重命名「" + node.name + "」",
            confirm = "重命名",
            initial = node.name,
            onConfirm = { name ->
                vm.renameNode(node.file, name)
                renamingNode = null
            },
            onDismiss = { renamingNode = null }
        )
    }

    pendingDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「" + node.name + "」？") },
            text = { Text("删除后不可恢复", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNode(node.file)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    pendingZip?.let { zf ->
        AlertDialog(
            onDismissRequest = { pendingZip = null },
            title = { Text("解压「" + zf.name + "」？") },
            text = { Text("解压到同名目录 " + zf.nameWithoutExtension + "（工程内）", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    vm.extractArchive(zf)
                    pendingZip = null
                }) { Text("解压") }
            },
            dismissButton = { TextButton(onClick = { pendingZip = null }) { Text("取消") } },
        )
    }
    pendingDeleteMulti?.let { files ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMulti = null },
            title = { Text("删除已选 " + files.size + " 项？") },
            text = { Text("删除后不可恢复", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMulti(files)
                    pendingDeleteMulti = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteMulti = null }) { Text("取消") } },
        )
    }
}

private data class NewNodeRequest(val parent: File, val isDirectory: Boolean)

@Composable
private fun NodeNameDialog(title: String, confirm: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val error = when {
        name.isBlank() -> null
        name.any { it in charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|') } -> "名称包含非法字符"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank() && error == null) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RootRow(
    project: File,
    expanded: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: (File) -> Unit,
    onMenu: (TreeNode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 4.dp)
            .combinedClickable(
                onClick = { onToggle(); onSelect(project) },
                onLongClick = { onMenu(TreeNode(project, true)) },
            )
            .background(if (selected) CandyPrimary.copy(alpha = 0.16f) else CandySurface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (expanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
            contentDescription = null,
            tint = CandyTextDim,
            modifier = Modifier.size(18.dp)
        )
        Icon(Icons.Default.Folder, contentDescription = null, tint = CandyPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            project.name,
            color = CandyText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TreeNodes(
    nodes: List<TreeNode>,
    depth: Int,
    vm: CandyViewModel,
    selectedDir: File?,
    onFileClick: (File) -> Unit,
    onNodeMenu: (TreeNode) -> Unit,
    onSelectDir: (File) -> Unit,
    onZipClick: (File) -> Unit = {},
    multiMode: Boolean = false,
    multiSet: SnapshotStateList<File>? = null,
) {
    for (node in nodes) {
        TreeRow(node, depth, vm, selectedDir, onFileClick, onNodeMenu, onSelectDir, onZipClick, multiMode, multiSet)
        if (node.isDir && node.expanded) {
            TreeNodes(node.children, depth + 1, vm, selectedDir, onFileClick, onNodeMenu, onSelectDir, onZipClick, multiMode, multiSet)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    node: TreeNode,
    depth: Int,
    vm: CandyViewModel,
    selectedDir: File?,
    onFileClick: (File) -> Unit,
    onNodeMenu: (TreeNode) -> Unit,
    onSelectDir: (File) -> Unit,
    onZipClick: (File) -> Unit = {},
    multiMode: Boolean = false,
    multiSet: SnapshotStateList<File>? = null,
) {
    val selected = vm.currentFile.value == node.file
    val dirSelected = node.isDir && selectedDir == node.file
    val multiSelected = multiSet?.contains(node.file) == true
    val indent = 8 + (depth + 1) * 14
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(start = indent.dp)
            .background(if (multiSelected) CandyPrimary.copy(alpha = 0.24f) else if (selected) CandySurfaceVariant else if (dirSelected) CandyPrimary.copy(alpha = 0.16f) else CandySurface)
            .combinedClickable(
                onClick = {
                    if (multiMode && multiSet != null) {
                        // 多选模式：点任意行即勾选/取消勾选
                        if (multiSet.contains(node.file)) multiSet.remove(node.file) else multiSet.add(node.file)
                    } else if (node.isDir) {
                        node.expanded = !node.expanded
                        onSelectDir(node.file)
                    } else {
                        val n = node.name.lowercase()
                        when {
                            n.endsWith(".apk") -> vm.installApk(node.file)
                            // 点击 zip 类归档：弹确认框（在 FileTreePanel 持有状态）
                            n.endsWith(".zip") || n.endsWith(".capr") -> onZipClick(node.file)
                            else -> onFileClick(node.file)
                        }
                    }
                },
                onLongClick = { onNodeMenu(node) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDir) {
            Icon(
                if (node.expanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                contentDescription = null,
                tint = CandyTextDim,
                modifier = Modifier.size(16.dp)
            )
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = CandySecondary,
                modifier = Modifier.size(17.dp)
            )
        } else {
            Spacer(Modifier.width(16.dp))
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = fileTint(node.name),
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            node.name,
            color = if (selected) CandyPrimary else CandyText,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.Edit,
            contentDescription = "重命名",
            tint = CandyTextDim,
            modifier = Modifier.size(15.dp).padding(end = 8.dp).clickable { onNodeMenu(node) }
        )
    }
}

/** 按扩展名给文件图标上色：.subl 语法的粉、.subs 脚本绿、.md 蓝 */
private fun fileTint(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "subl" -> CandyPrimary
    "subs" -> CandyTertiary
    "md" -> CandySecondary
    else -> CandyTextDim
}
