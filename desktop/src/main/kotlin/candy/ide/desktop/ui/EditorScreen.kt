package candy.ide.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.data.DiagItem
import candy.ide.desktop.DesktopEngine
import candy.ide.desktop.IdeState
import candy.ide.desktop.OutputLine
import candy.ide.desktop.RunMode
import candy.ide.desktop.Settings
import candy.ide.desktop.Workspace
import candy.ide.desktop.wordWrap
import candy.ide.desktop.data.ProjectStore
import candy.ide.desktop.plugin.preview.ComposePreviewState
import candy.ide.desktop.plugin.preview.PreviewCodeGen
import candy.ide.md.MarkdownText
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.ui.theme.CandyTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 编辑器窗口（结构对齐 Android 端 EditorScreen）：
 * 左抽屉文件树 + 顶栏（保存/运行/输出抽屉/更多）+ 标签页 + 编辑区 + 右侧输出抽屉。
 * 每个工程在独立窗口中打开，主页不关闭。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(state: IdeState) {
    val project = state.currentProject
    val activeFile = state.activeFile
    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未打开工程——请先在主页选择或创建", color = CandyTextDim)
        }
        return
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var outputOpen by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var gotoLine by remember { mutableIntStateOf(0) }
    var gotoTick by remember { mutableIntStateOf(0) }
    // 查找栏
    var findOpen by remember { mutableStateOf(false) }
    var gitOpen by remember { mutableStateOf(false) }
    var agentOpen by remember { mutableStateOf(false) }
    val agentState = remember { candy.ide.desktop.agent.AgentState() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { agentState.dispose() } }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableIntStateOf(0) }
    val findMatches = remember(findQuery, activeFile, state.fileTexts[activeFile?.absolutePath ?: ""]) {
        val txt = state.fileTexts[activeFile?.absolutePath ?: ""] ?: ""
        if (findQuery.isBlank() || txt.isEmpty()) emptyList()
        else txt.split("\n").mapIndexedNotNull { i, line -> if (line.contains(findQuery, ignoreCase = true)) i else null }
    }
    val typeLabel = candy.ide.desktop.data.projectTypeLabel(project)

    // 光标位置（F2 重命名 / F12 转定义用）
    var caretLine by remember { mutableIntStateOf(0) }
    var caretChar by remember { mutableIntStateOf(0) }
    // 重命名对话框：三项 = 初始符号名 / 所在行 / 列
    var renameWord by remember { mutableStateOf<String?>(null) }
    var renameLine by remember { mutableIntStateOf(0) }
    var renameChar by remember { mutableIntStateOf(0) }
    // 签名提示：光标在实参区时底部显示被调函数签名（150ms 防抖）
    var sigLabel by remember { mutableStateOf<String?>(null) }
    var sigTick by remember { mutableIntStateOf(0) }
    // Unicode 符号栏（键盘端输入 ∀λ→ 等）
    var symBarOpen by remember { mutableStateOf(false) }
    var symTick by remember { mutableIntStateOf(0) }
    var symText by remember { mutableStateOf("") }
    // 布局预览（@composable 视图树，对齐安卓端 ComposePreview）
    var preview by remember { mutableStateOf<ComposePreviewState?>(null) }

    // 引擎状态 -> 日志页签
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            state.log("引擎目录：" + (DesktopEngine.coreDir?.absolutePath ?: "内置 stdlib"))
            state.log("运行模式：" + when (Settings.runMode) {
                RunMode.Interpreter -> "解释器"
                RunMode.Node -> "Node.js"
                else -> "自动"
            })
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = CandyBg) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = CandyPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(project.name, color = CandyText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        val count = remember(project) {
                            project.walkTopDown().count { it.isFile && !it.name.startsWith(".") }
                        }
                        Text(count.toString() + " 个文件", color = CandyTextDim, fontSize = 12.sp)
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭文件树（Esc）", tint = CandyTextDim, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = CandySurfaceVariant)
                    FileTreePanel(state, project)
                }
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when {
                    e.isCtrlPressed && e.isShiftPressed && e.key == Key.S -> { saveAll(state); true }
                    e.isCtrlPressed && e.key == Key.S -> { saveActive(state); true }
                    e.isCtrlPressed && e.key == Key.R -> { state.runTarget(); true }
                    e.isCtrlPressed && e.key == Key.B -> { state.compileOnly(); true }
                    e.isCtrlPressed && e.key == Key.F -> { findOpen = true; true }
                    e.key == Key.Escape && findOpen -> { findOpen = false; true }
                    e.isAltPressed && e.key == Key.One -> { scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() }; true }
                    e.isAltPressed && e.key == Key.Two -> { outputOpen = !outputOpen; true }
                    e.isAltPressed && e.key == Key.Three -> { gitOpen = !gitOpen; true }
                    e.isAltPressed && e.key == Key.Four -> { agentOpen = !agentOpen; true }
                    e.key == Key.F12 -> { gotoDefinition(state, scope, caretLine, caretChar) { l -> gotoLine = l; gotoTick++ }; true }
                    e.isShiftPressed && e.key == Key.F12 -> { findReferences(state, scope, caretLine, caretChar); true }
                    e.isCtrlPressed && e.key == Key.Q -> { showHoverDoc(state, scope, caretLine, caretChar); true }
                    e.isCtrlPressed && e.isShiftPressed && e.key == Key.A -> { namedCall(state, scope, caretLine, caretChar); true }
                    e.isCtrlPressed && e.isShiftPressed && e.key == Key.P -> { openComposePreview(state, scope, caretLine, caretChar) { pv -> preview = pv }; true }
                    e.key == Key.F2 -> { renameWord = wordAtLoc(state, caretLine, caretChar); renameLine = caretLine; renameChar = caretChar; true }
                    e.isCtrlPressed && e.isAltPressed && e.key == Key.L -> { formatDocument(state, scope); true }
                    e.key == Key.Escape && agentOpen -> { agentOpen = false; true }
                    e.key == Key.Escape && gitOpen -> { gitOpen = false; true }
                    e.key == Key.Escape && drawerState.isOpen -> { scope.launch { drawerState.close() }; true }
                    e.key == Key.Escape && outputOpen -> { outputOpen = false; true }
                    else -> false
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = CandyTextDim, maxLines = 1)
                                Text(project.name, style = MaterialTheme.typography.titleMedium, color = CandyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "文件树", tint = CandyText)
                            }
                        },
                        actions = {
                            // 保存：短按存当前文件，长按存整个工程
                            IconButton(onClick = { symBarOpen = !symBarOpen }) {
                                Text("Σ", color = if (symBarOpen) CandyPrimary else CandyText, style = MaterialTheme.typography.titleMedium)
                            }
                            Box(
                                modifier = Modifier.size(44.dp).combinedClickable(
                                    enabled = activeFile != null,
                                    onClick = { saveActive(state) },
                                    onLongClick = { saveAll(state) },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "保存（长按保存全部工程文件）", tint = CandyText)
                            }
                            // 运行：短按保存并运行，长按仅编译
                            Box(
                                modifier = Modifier.size(44.dp).combinedClickable(
                                    enabled = activeFile != null && !state.running,
                                    onClick = { state.runTarget() },
                                    onLongClick = { state.compileOnly() },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.running) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CandyPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "运行（长按仅编译）", tint = CandySecondary, modifier = Modifier.size(21.dp))
                                }
                            }
                            // 输出/日志/诊断抽屉
                            IconButton(onClick = { outputOpen = !outputOpen }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = "输出/日志/诊断",
                                    tint = if (outputOpen) CandyPrimary else CandyText,
                                )
                            }
                            // 更多操作
                            Box {
                                IconButton(onClick = { moreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "其他操作", tint = CandyText)
                                }
                                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                    DropdownMenuItem(text = { Text("保存全部文件") }, onClick = { moreMenu = false; saveAll(state) })
            DropdownMenuItem(text = { Text("格式化文档（Ctrl+Alt+L）") }, onClick = { moreMenu = false; formatDocument(state, scope) })
            DropdownMenuItem(text = { Text("重命名符号（F2）") }, onClick = { moreMenu = false; renameWord = wordAtLoc(state, caretLine, caretChar); renameLine = caretLine; renameChar = caretChar })
            DropdownMenuItem(text = { Text("查看文档（Ctrl+Q）") }, onClick = { moreMenu = false; showHoverDoc(state, scope, caretLine, caretChar) })
            DropdownMenuItem(text = { Text("查找引用（Shift+F12）") }, onClick = { moreMenu = false; findReferences(state, scope, caretLine, caretChar) })
            DropdownMenuItem(text = { Text("指名调用（Ctrl+Shift+A）") }, onClick = { moreMenu = false; namedCall(state, scope, caretLine, caretChar) })
            DropdownMenuItem(text = { Text("布局预览（Ctrl+Shift+P）") }, onClick = { moreMenu = false; openComposePreview(state, scope, caretLine, caretChar) { pv -> preview = pv } })
                                    DropdownMenuItem(text = { Text("在文件管理器中打开") }, onClick = { moreMenu = false; Platform.openInExplorer(project) })
                                    DropdownMenuItem(text = { Text("Git 面板（Alt+3）") }, onClick = { moreMenu = false; gitOpen = true })
                                    DropdownMenuItem(text = { Text("AI 助手（Alt+4）") }, onClick = { moreMenu = false; agentOpen = true })
                                    DropdownMenuItem(text = { Text("复制工程路径") }, onClick = { moreMenu = false; state.toast = project.absolutePath })
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
                    )
                },
                containerColor = CandyBg,
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    TabBar(state)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val f = activeFile
                        when {
                            f == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("从左侧文件树选择一个文件", color = CandyTextDim)
                            }
                            f.name.endsWith(".md", ignoreCase = true) && state.mdTextMode[f.absolutePath] != true -> {
                                val src = remember(f) { runCatching { f.readText() }.getOrDefault("") }
                                MarkdownText(
                                    src,
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                                )
                                SmallFloatingActionButton(
                                    onClick = { state.mdTextMode[f.absolutePath] = true },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 10.dp).size(40.dp),
                                    containerColor = CandySurface,
                                    contentColor = CandyPrimary,
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = "切换为文本编辑", modifier = Modifier.size(18.dp))
                                }
                            }
                            else -> CodeEditor(
                                file = f,
                                text = state.fileTexts[f.absolutePath] ?: "",
                                onTextChange = { state.editText(f, it) },
                                diagnostics = state.diagnostics[f.absolutePath] ?: emptyList(),
                                fontSize = Settings.fontSize,
                                gotoTick = gotoTick,
                                gotoLine = gotoLine,
                                wordWrap = state.wordWrap,
                                onCaret = { l, c -> caretLine = l; caretChar = c; sigTick++ },
                                insertSymbolTick = symTick,
                                insertSymbolText = symText,
                            )
                        }
                        // 查找栏（编辑区右上悬浮）
                        if (findOpen) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 10.dp),
                                color = CandySurface,
                                tonalElevation = 6.dp,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val hide = { findOpen = false }
                                    OutlinedTextField(
                                        value = findQuery,
                                        onValueChange = { findQuery = it; findIndex = 0 },
                                        singleLine = true,
                                        modifier = Modifier.width(180.dp),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CandyText),
                                        placeholder = { Text("查找", fontSize = 12.sp, color = CandyTextDim) },
                                    )
                                    Text(
                                        if (findMatches.isEmpty()) "0/0" else ((findIndex % findMatches.size) + 1).toString() + "/" + findMatches.size,
                                        color = CandyTextDim,
                                        fontSize = 11.sp,
                                    )
                                    IconButton(onClick = {
                                        if (findMatches.isNotEmpty()) {
                                            findIndex = ((findIndex - 1) + findMatches.size) % findMatches.size
                                            gotoLine = findMatches[findIndex]
                                            gotoTick++
                                            state.toast = "第 " + (findMatches[findIndex] + 1) + " 行"
                                        }
                                    }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个", tint = CandyText, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        if (findMatches.isNotEmpty()) {
                                            findIndex = (findIndex + 1) % findMatches.size
                                            gotoLine = findMatches[findIndex]
                                            gotoTick++
                                            state.toast = "第 " + (findMatches[findIndex] + 1) + " 行"
                                        }
                                    }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个", tint = CandyText, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = hide, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "关闭查找", tint = CandyTextDim, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        // md 文本模式切回渲染的按钮
                        val f2 = activeFile
                        if (f2 != null && f2.name.endsWith(".md", ignoreCase = true) && state.mdTextMode[f2.absolutePath] == true) {
                            SmallFloatingActionButton(
                                onClick = { state.mdTextMode[f2.absolutePath] = false },
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 10.dp).size(40.dp),
                                containerColor = CandySurface,
                                contentColor = CandyPrimary,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "切换为渲染预览", modifier = Modifier.size(18.dp))
                            }
                        }
                        // 右抽屉：输出 / 日志 / 诊断（盖在编辑区右侧）
                        if (outputOpen) {
                            OutputDrawer(
                                modifier = Modifier.fillMaxHeight().width(400.dp).align(Alignment.TopEnd),
                                state = state,
                                onGoto = { d ->
                                    gotoLine = d.line
                                    gotoTick++
                                    state.toast = "已定位到第 " + (d.line + 1) + " 行"
                                },
                                onOpenFileLine = { name, line ->
                                    val f = state.openFiles.firstOrNull { it.name == name }
                                        ?: runCatching {
                                            project.walkTopDown().firstOrNull { !it.isDirectory && it.name == name }
                                        }.getOrNull()
                                    if (f != null) {
                                        state.open(f)
                                        gotoLine = (line - 1).coerceAtLeast(0)
                                        gotoTick++
                                    } else {
                                        state.toast = "找不到文件：$name"
                                    }
                                },
                            )
                        }
                        // AI 助手（Alt+4，整页覆盖编辑区，返回回到编辑器）
                        if (agentOpen) {
                            AgentScreen(
                                state = state,
                                agent = agentState,
                                onBack = { agentOpen = false },
                                onTapRun = { state.runTarget() },
                                onGotoLine = { line ->
                                    gotoLine = (line - 1).coerceAtLeast(0)
                                    gotoTick++
                                },
                            )
                        }
                        // 布局预览（Ctrl+Shift+P，右侧侧栏：树/渲染/属性，应用写回源码）
                        // 面板内容由插件提供（EditorViewContribution，对齐安卓端）；
                        // 插件尚未装载时给占位提示（装载很快，由启动时后台线程完成）
                        preview?.let { pv ->
                            val editorExt = candy.ide.desktop.plugin.PluginRegistry.editorView()
                            if (editorExt == null) {
                                Text(
                                    "预览面板由插件提供（android-project 装载中…）",
                                    color = CandyTextDim,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                                )
                            } else {
                                editorExt.FloatingPanel(
                                    state = pv,
                                    modifier = Modifier.fillMaxHeight().width(560.dp).align(Alignment.TopEnd),
                                    onClose = { preview = null },
                                    onApply = { treeJson -> applyPreviewTree(state, pv, treeJson) },
                                )
                            }
                        }
                        // Git 抽屉（Alt+3，与 Git 远程仓库地址联动）
                        if (gitOpen) {
                            GitPanel(
                                state = state,
                                project = project,
                                onClose = { gitOpen = false },
                                modifier = Modifier.fillMaxHeight().width(380.dp).align(Alignment.TopEnd),
                            )
                        }
                    }
                    // Unicode 符号栏（顶栏 Σ 开关；点击在光标处插入）
                    if (symBarOpen) {
                        SymbolBarRow(
                            onSymbol = { sym ->
                                if (activeFile != null && activeFile.name.endsWith(".md", ignoreCase = true).not()) {
                                    symText = sym
                                    symTick++
                                } else {
                                    state.toast = "请先打开一个代码文件"
                                }
                            },
                            onClose = { symBarOpen = false },
                        )
                    }
                }
            }
            sigLabel?.let { label ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = CandySurface,
                    border = BorderStroke(1.dp, CandySurfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = CandyText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }

            ToastOverlay(state)
        }
    }

    // 自动同步云端（对齐安卓端 gitAutoSync：进入工程时云端领先则拉取）
    LaunchedEffect(Unit) {
        if (!Settings.gitAutoSync) return@LaunchedEffect
        withContext(Dispatchers.Default) { state.pullIfBehind(project) }
    }

    // 实时诊断：防抖 350ms，后台线程跑 LSP 分析
    val activeText = if (activeFile != null) state.fileTexts[activeFile.absolutePath] else null
    LaunchedEffect(activeFile, activeText) {
        val f = activeFile ?: return@LaunchedEffect
        val text = activeText ?: return@LaunchedEffect
        delay(350)
        val diags = withContext(Dispatchers.Default) { DesktopEngine.analyze(DesktopEngine.uriOf(f), text) }
        if (state.activeFile == f) state.diagnostics[f.absolutePath] = diags
    }

    // 布局预览刷新：编辑 1s 后，函数行范围内无 Error 诊断才重算视图树
    LaunchedEffect(preview, activeText) {
        val pv = preview ?: return@LaunchedEffect
        val f = activeFile ?: return@LaunchedEffect
        delay(1000)
        val hasErr = (state.diagnostics[f.absolutePath] ?: emptyList()).any {
            it.severity.toInt() == 0 && it.line + 1 in pv.start..pv.end
        }
        if (hasErr) return@LaunchedEffect
        val nt = state.fileTexts[f.absolutePath] ?: return@LaunchedEffect
        val json = DesktopEngine.composableTree(DesktopEngine.uriOf(f), nt, caretLine, caretChar)
        if (json != null && preview == pv) {
            val o = org.json.JSONObject(json)
            if (o.optString("name") == pv.fnName) preview = ComposePreviewState(pv.fnName, pv.start, pv.end, json)
        }
    }

    // 签名提示刷新
    LaunchedEffect(sigTick) {
        if (sigTick == 0) return@LaunchedEffect
        delay(150)
        val f = activeFile ?: return@LaunchedEffect
        val text = state.fileTexts[f.absolutePath] ?: return@LaunchedEffect
        if (f.extension == "md") { sigLabel = null; return@LaunchedEffect }
        sigLabel = withContext(Dispatchers.Default) {
            runCatching { signatureLabel(f, text, caretLine, caretChar) }.getOrNull()
        }
    }

    renameWord?.let { word ->
        var name by remember { mutableStateOf(word) }
        AlertDialog(
            onDismissRequest = { renameWord = null },
            title = { Text("重命名「" + word + "」") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("新名字") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = CandyText),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && name.trim() != word, onClick = {
                    val n = name.trim()
                    renameWord = null
                    doRename(state, scope, renameLine, renameChar, word, n)
                }) { Text("重命名") }
            },
            dismissButton = { TextButton(onClick = { renameWord = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TabBar(state: IdeState) {
    if (state.openFiles.isEmpty()) return
    // 钳制到有效区间：关标签页/切文件时索引可能瞬时越界
    val selectedTabIndex = state.openFiles.indexOfFirst { it.absolutePath == state.activeFile?.absolutePath }
        .coerceIn(0, state.openFiles.lastIndex)
    // 标签数量变化时整体重建 ScrollableTabRow：其内部 measure lambda 会捕获旧索引/旧列表，
    // 开/关标签页（尤其 1↔2）时滚动动画按旧索引取新列表 → IndexOutOfBoundsException。
    // key(size) 强制丢弃被污染的内部状态。
    var closing by remember { mutableStateOf<File?>(null) }
    key(state.openFiles.size, selectedTabIndex) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
        containerColor = CandySurfaceVariant,
        contentColor = CandyText,
        edgePadding = 0.dp,
    ) {
        state.openFiles.forEach { f ->
            val key = f.absolutePath
            val selected = state.activeFile?.absolutePath == key
            Tab(
                selected = selected,
                onClick = { state.activeFile = f },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (state.dirty[key] == true) "● " else "") + f.name, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.clickable {
                                if (state.dirty[key] == true) closing = f else state.close(f)
                            }.padding(3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "关闭", modifier = Modifier.size(13.dp), tint = CandyTextDim)
                        }
                    }
                },
            )
        }
        }
    }

    closing?.let { f ->
        AlertDialog(
            onDismissRequest = { closing = null },
            title = { Text("保存修改？") },
            text = { Text(f.name + " 有未保存的修改。", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    if (state.save(f)) state.toast = "已保存 " + f.name
                    state.close(f); closing = null
                }) { Text("保存并关闭", color = CandyPrimary) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { state.close(f); closing = null }) { Text("不保存", color = CandyError) }
                    TextButton(onClick = { closing = null }) { Text("取消") }
                }
            },
        )
    }
}

/** 签名提示：当前实参用【】包起来，其余按逗号拼接 */
private fun signatureLabel(f: File, text: String, line: Int, char: Int): String? {
    val help = DesktopEngine.signatureHelp(DesktopEngine.uriOf(f), text, line, char) ?: return null
    val sig = help.signatures.getOrNull(help.activeSignature) ?: return null
    if (sig.parameters.isEmpty()) return null
    val active = help.activeParameter.coerceIn(0, sig.parameters.lastIndex)
    val pre = sig.parameters.subList(0, active).joinToString(", ") { it.label }
    val cur = sig.parameters[active].label
    val post = sig.parameters.subList(active + 1, sig.parameters.size).joinToString(", ") { it.label }
    return buildString {
        append(sig.label.substringBefore('('))
        append("(")
        if (pre.isNotEmpty()) append(pre + ", ")
        append("【" + cur + "】")
        if (post.isNotEmpty()) append(", " + post)
        append(")")
    }
}
/** 打开布局预览：光标处/所在的 @composable 函数；找不到则兜底 layout(根组合函数)（与安卓端 openVirt 同构） */
private fun openComposePreview(
    state: IdeState,
    scope: CoroutineScope,
    caretLine: Int,
    caretChar: Int,
    onReady: (ComposePreviewState?) -> Unit,
) {
    val f = state.activeFile ?: run { state.toast = "没有打开的文件"; return }
    if (!f.name.endsWith(".subs") && !f.name.endsWith(".subl")) {
        state.toast = "布局预览仅支持 .subs/.subl"
        return
    }
    val text = state.fileTexts[f.absolutePath] ?: return
    scope.launch(Dispatchers.Default) {
        var json = DesktopEngine.composableTree(DesktopEngine.uriOf(f), text, caretLine, caretChar)
        if (json == null) {
            val rootName = Regex("layout\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)").find(text)?.groupValues?.getOrNull(1)
            if (rootName != null) {
                val decl = Regex("@composable\\s*\\n\\s*fun\\s+" + Regex.escape(rootName) + "\\b").find(text)
                    ?: Regex("@composable\\s+fun\\s+" + Regex.escape(rootName) + "\\b").find(text)
                if (decl != null) {
                    val funAt = text.indexOf("fun ", decl.range.first)
                    val head = text.substring(0, funAt)
                    val fnLine = head.split("\n").size - 1   // 0-based：fun 所在行
                    val lineStart = head.lastIndexOf("\n") + 1
                    val nameCol = funAt - lineStart + 4       // 跳过 "fun " 落在名字首字符
                    json = DesktopEngine.composableTree(DesktopEngine.uriOf(f), text, fnLine, nameCol)
                }
            }
        }
        val pv = json?.let {
            val o = org.json.JSONObject(it)
            ComposePreviewState(fnName = o.optString("name"), start = o.optInt("start"), end = o.optInt("end"), treeJson = it)
        }
        withContext(Dispatchers.Main) {
            if (pv == null) state.toast = "找不到 @composable 函数，或其视图树为空" else onReady(pv)
        }
    }
}

/** 预览写回：保留函数签名行（含 @composable），替换函数体（与安卓端 applyPreviewTree 同构，但不立即存盘） */
private fun applyPreviewTree(state: IdeState, pv: ComposePreviewState, treeJson: String) {
    val f = state.activeFile ?: return
    val content = state.fileTexts[f.absolutePath] ?: return
    runCatching {
        val tree = org.json.JSONObject(treeJson)
        val ls = content.split("\n").toMutableList()
        var sig = (pv.start - 1).coerceIn(0, ls.size - 1)
        while (sig < ls.size && !ls[sig].contains("{")) sig++
        if (sig >= ls.size) return@runCatching
        val endIdx = (pv.end - 1).coerceIn(sig, ls.size - 1)
        val head = ls.subList(0, sig + 1)
        val tail = if (endIdx + 1 < ls.size) ls.subList(endIdx + 1, ls.size) else emptyList()
        val out = ArrayList<String>(head)
        out += PreviewCodeGen.bodyLines(tree)
        out += "}"
        val newText = out.joinToString("\n")
        state.editText(f, newText)
        state.toast = "已写回 @" + pv.fnName + "（Ctrl+S 保存）"
        state.log("布局预览写回 " + f.name, OutputLine.Kind.Success)
    }.onFailure {
        state.toast = "写回失败：" + (it.message ?: it.javaClass.simpleName)
    }
}
// ───────── 跳转 / 重命名 / 格式化（LSP，与安卓端 EditorScreen 同构） ─────────

/** 一段 LSP 编辑列表应用到文本：按起始位置倒序替换，互不干扰 */
private fun applyTextEdits(text: String, edits: List<sugared.functor.lsp.TextEdit>): String {
    val sb = StringBuilder(text)
    val sorted = edits.sortedByDescending { it.range.start.line * 100000 + it.range.start.character }
    for (te in sorted) {
        val s = offsetInText(text, te.range.start.line, te.range.start.character)
        val e = offsetInText(text, te.range.end.line, te.range.end.character)
        if (s in 0..e && e <= sb.length) sb.replace(s, e, te.newText)
    }
    return sb.toString()
}

/** 行列 → 绝对偏移（钳在行内） */
private fun offsetInText(text: String, line: Int, char: Int): Int {
    var l = 0
    var idx = 0
    while (l < line && idx < text.length) {
        if (text[idx] == '\n') l++
        idx++
    }
    val lineEnd = text.indexOf('\n', idx).let { if (it < 0) text.length else it }
    return (idx + char).coerceIn(idx, lineEnd)
}

/** 取光标处的标识符（重命名对话框的初始名） */
private fun wordAt(text: String, line: Int, char: Int): String? {
    val s = offsetInText(text, line, 0)
    val e = text.indexOf('\n', s).let { if (it < 0) text.length else it }
    val row = text.substring(s, e)
    val ident = Regex("[A-Za-z0-9_]")
    var a = char.coerceIn(0, row.length)
    while (a > 0 && ident.matches(row[a - 1].toString())) a--
    var b = char.coerceIn(0, row.length)
    while (b < row.length && ident.matches(row[b].toString())) b++
    return if (b > a) row.substring(a, b) else null
}

/** 光标处的词（重命名对话框的初始名） */
private fun wordAtLoc(state: IdeState, line: Int, char: Int): String? {
    val f = state.activeFile ?: return null
    val text = state.fileTexts[f.absolutePath] ?: return null
    return wordAt(text, line, char)
}

/** 转到定义（F12）：同文件直接跳行；跨文件先打开再跳 */
private fun gotoDefinition(
    state: IdeState,
    scope: CoroutineScope,
    caretLine: Int,
    caretChar: Int,
    onJump: (Int) -> Unit,
) {
    val f = state.activeFile ?: run { state.toast = "没有打开的文件"; return }
    val text = state.fileTexts[f.absolutePath] ?: return
    scope.launch(Dispatchers.Default) {
        val loc = DesktopEngine.definition(DesktopEngine.uriOf(f), text, caretLine, caretChar)
        if (loc == null) {
            withContext(Dispatchers.Main) { state.toast = "此处找不到定义" }
            return@launch
        }
        val target = runCatching { File(java.net.URI(loc.uri)) }.getOrNull()
        if (target == null || !target.isFile) {
            withContext(Dispatchers.Main) { state.toast = "定义不在工程内（也许在标准库）" }
            return@launch
        }
        if (target.absolutePath != f.absolutePath) state.open(target)
        withContext(Dispatchers.Main) { onJump(loc.range.start.line) }
    }
}

/** 执行重命名：把语言服务给的改动写回打开的缓冲或磁盘 */
private fun doRename(
    state: IdeState,
    scope: CoroutineScope,
    line: Int,
    char: Int,
    oldName: String,
    newName: String,
) {
    val f = state.activeFile ?: return
    val text = state.fileTexts[f.absolutePath] ?: return
    scope.launch(Dispatchers.Default) {
        val changes = DesktopEngine.rename(DesktopEngine.uriOf(f), text, line, char, newName)
        if (changes == null || changes.isEmpty()) {
            withContext(Dispatchers.Main) { state.toast = "只能对标识符重命名" }
            return@launch
        }
        var touched = 0
        for ((uri, edits) in changes) {
            if (edits.isEmpty()) continue
            val target = runCatching { File(java.net.URI(uri)) }.getOrNull() ?: continue
            if (!target.isFile) continue
            val cur = state.fileTexts[target.absolutePath] ?: runCatching { target.readText() }.getOrNull() ?: continue
            val nt = applyTextEdits(cur, edits)
            if (state.isOpen(target)) state.editText(target, nt) else runCatching { target.writeText(nt) }
            touched++
        }
        withContext(Dispatchers.Main) {
            state.toast = if (touched > 0) "已重命名 $touched 个文件中的出现处" else "没有需要改的地方"
            state.log("重命名 $oldName -> $newName（$touched 个文件）", OutputLine.Kind.Success)
        }
    }
}

/** 格式化文档（Ctrl+Alt+L） */
private fun formatDocument(state: IdeState, scope: CoroutineScope) {
    val f = state.activeFile ?: run { state.toast = "没有打开的文件"; return }
    val text = state.fileTexts[f.absolutePath] ?: return
    scope.launch(Dispatchers.Default) {
        val edits = DesktopEngine.format(DesktopEngine.uriOf(f), text)
        if (edits.isNullOrEmpty()) {
            withContext(Dispatchers.Main) { state.toast = if (edits == null) "格式化不可用" else "文档已是格式化状态，无需调整" }
            return@launch
        }
        val nt = applyTextEdits(text, edits)
        withContext(Dispatchers.Main) {
            state.editText(f, nt)
            state.toast = "已格式化文档"
            state.log("格式化 " + f.name, OutputLine.Kind.Success)
        }
    }
}

/** 查找引用（Shift+F12）：引用位置打到日志页签（name:line 链接可点跳） */
private fun findReferences(state: IdeState, scope: CoroutineScope, caretLine: Int, caretChar: Int) {
    val f = state.activeFile ?: return
    val text = state.fileTexts[f.absolutePath] ?: return
    val word = wordAt(text, caretLine, caretChar) ?: run { state.toast = "光标处没有标识符"; return }
    scope.launch(Dispatchers.Default) {
        val locs = DesktopEngine.references(DesktopEngine.uriOf(f), text, caretLine, caretChar) ?: emptyList()
        if (locs.isEmpty()) {
            withContext(Dispatchers.Main) { state.log("**[查找引用]** `$word` 在本文件没有引用", OutputLine.Kind.Info) }
        } else {
        withContext(Dispatchers.Main) {
            state.log("**[查找引用]** `$word` — " + locs.size + " 处：", OutputLine.Kind.Info)
            locs.forEachIndexed { i, l ->
                state.log("${i + 1}. " + f.name + ":" + (l.range.start.line + 1), OutputLine.Kind.Info)
            }
        }
        }
    }
}

/** 悬停文档（Ctrl+Q）：LSP hoverFor 的文档打到日志页签 */
private fun showHoverDoc(state: IdeState, scope: CoroutineScope, caretLine: Int, caretChar: Int) {
    val f = state.activeFile ?: return
    val text = state.fileTexts[f.absolutePath] ?: return
    val word = wordAt(text, caretLine, caretChar) ?: run { state.toast = "光标处没有标识符"; return }
    scope.launch(Dispatchers.Default) {
        val hover = DesktopEngine.hover(DesktopEngine.uriOf(f), text, caretLine, caretChar)
        val doc = when (val c = hover?.contents) {
            is String -> c
            is sugared.functor.lsp.MarkupContent -> c.value
            else -> ""
        }
        withContext(Dispatchers.Main) {
            if (doc.isBlank()) state.log("**[文档]** `$word` 没有文档记录", OutputLine.Kind.Info)
            else state.log("**[文档]** `$word`\n" + doc.trim().take(600), OutputLine.Kind.Info)
        }
    }
}

/** 指名调用（Ctrl+Shift+A）：把光标处调用的实参改写成形参名 = 实参 */
private fun namedCall(state: IdeState, scope: CoroutineScope, caretLine: Int, caretChar: Int) {
    val f = state.activeFile ?: return
    val text = state.fileTexts[f.absolutePath] ?: return
    val word = wordAt(text, caretLine, caretChar) ?: run { state.toast = "光标处没有标识符"; return }
    scope.launch(Dispatchers.Default) {
        val msg: String
        var changed: String? = null
        // 光标偏移 → 调用括号范围：从标识符后向后找第一个 '('，配平到呼应的 ')'
        var p = offsetInText(text, caretLine, caretChar)
        while (p < text.length && text[p] != '(' && text[p] != '\n') p++
        if (p >= text.length || text[p] != '(') {
            msg = "**[指名调用]** 光标处不是函数调用"
        } else {
            val open = p
            var depth = 0
            var close = -1
            var j = open
            while (j < text.length) {
                when (text[j]) {
                    '(' -> depth++
                    ')' -> { depth--; if (depth == 0) { close = j; break } }
                }
                j++
            }
            if (close < 0) {
                msg = "**[指名调用]** 找不到调用的右括号"
            } else {
                val args = splitTopArgs(text.substring(open + 1, close).trim())
                val builtins = mapOf("print" to listOf("value"), "println" to listOf("value"), "printLn" to listOf("value"))
                when {
                    args.isEmpty() -> msg = "**[指名调用]** 无实参，无需指名"
                    else -> {
                        val params = paramsOf(DesktopEngine.symbolDetail(DesktopEngine.uriOf(f), text, word))
                        when {
                            params.isNotEmpty() && params.size == args.size -> {
                                val rw = params.zip(args).joinToString(", ") { (pn, av) -> pn + " = " + av }
                                msg = "**[指名调用]** " + word + "(" + rw + ")"
                                changed = text.replaceRange(open + 1, close, rw)
                            }
                            params.isEmpty() && builtins.containsKey(word) && args.size == 1 -> {
                                val rw = builtins[word]!![0] + " = " + args[0]
                                msg = "**[指名调用]** " + word + "(" + rw + ")"
                                changed = text.replaceRange(open + 1, close, rw)
                            }
                            params.isEmpty() -> msg = "**[指名调用]** 找不到 `$word` 的函数签名"
                            else -> msg = "**[指名调用]** 签名与实参数不符（" + params.size + " 形参 vs " + args.size + " 实参），未改写"
                        }
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            state.log(msg, OutputLine.Kind.Info)
            if (changed != null) {
                state.editText(f, changed!!)
                state.toast = "已改写为指名调用"
            }
        }
    }
}

/** 拆分顶层实参（逗号配平，跳过字符串与括号） */
private fun splitTopArgs(argsRaw: String): List<String> {
    if (argsRaw.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    var depth = 0
    var inStr = false
    val sb = StringBuilder()
    for (c in argsRaw) {
        when {
            c == '"' -> { inStr = !inStr; sb.append(c) }
            inStr -> sb.append(c)
            c == '(' || c == '[' -> { depth++; sb.append(c) }
            c == ')' || c == ']' -> { depth--; sb.append(c) }
            c == ',' && depth == 0 -> { out.add(sb.toString().trim()); sb.clear() }
            else -> sb.append(c)
        }
    }
    if (sb.isNotEmpty()) out.add(sb.toString().trim())
    return out
}

/** 从签名明细（fun name(a: Int, b: Int): Int）取形参名序列 */
private fun paramsOf(sig: String?): List<String> {
    if (sig.isNullOrBlank()) return emptyList()
    val inside = sig.substringAfter('(', missingDelimiterValue = "").substringBefore(')', missingDelimiterValue = "")
    if (inside.isEmpty()) return emptyList()
    return inside.split(',').mapNotNull { seg ->
        val body = seg.trim().substringBefore(':').trim()
        body.split(Regex("\\s+")).lastOrNull { it.isNotEmpty() && it.first().isLetter() }
    }
}
// ───────── 运行 / 编译 / 保存 ─────────

private fun saveActive(state: IdeState) {
    val f = state.activeFile ?: return run { state.toast = "没有打开的文件" }
    if (state.save(f)) {
        state.toast = "已保存 " + f.name
        state.log("保存 " + f.name, OutputLine.Kind.Success)
    } else state.toast = "保存失败"
}

private fun saveAll(state: IdeState) {
    var n = 0
    state.openFiles.forEach { f -> if (state.dirty[f.absolutePath] == true && state.save(f)) n++ }
    state.toast = if (n > 0) "已保存 $n 个文件" else "没有需要保存的文件"
    if (n > 0) state.log("保存全部：$n 个文件", OutputLine.Kind.Success)
}

// 运行/编译逻辑已迁入 IdeState（runTarget/compileOnly/runJsConsole/compileAndRun）：
// 插件工程类型经 PluginRegistry 路由（对齐安卓端 VM），快捷键与按钮直接调 state 方法。
