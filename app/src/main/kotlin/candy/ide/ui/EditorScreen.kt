package candy.ide.ui

import android.widget.Toast

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.sp
import candy.ide.data.ProjectStore
import candy.ide.editor.SoraEditorView
import candy.ide.md.MarkdownText
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.material3.Slider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

private data class LongPressMenu(val x: Float, val y: Float, val line: Int, val col: Int, val preview: candy.ide.ui.ComposePreviewState? = null)

private data class RenameRequest(val word: String, val line: Int, val col: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(vm: CandyViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentProject by vm.currentProject
    val currentFile by vm.currentFile
    val logOpen by vm.logOpen
    val running by vm.running
    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    val ctx = LocalContext.current
    val safe = currentFile
    // 内置工程（语言文档/IDE 文档）：只读，不可编辑
    val isMdProject = remember(currentProject) {
        currentProject?.let { ProjectStore.isBuiltin(it) } == true
    }
    val isJsApk = remember(currentProject) { currentProject?.let { ProjectStore.typeOf(it) } == ProjectStore.TYPE_JSAPK }
    // md 预览渲染时编辑器实例是上个标签页的：保存/运行要挡住；md 文本模式下可编辑
    val isEditableFile = safe != null && !isMdProject &&
        (!safe.name.endsWith(".md") || vm.mdTextMode[safe.key] == true)
    var longMenu by remember { mutableStateOf<LongPressMenu?>(null) }
    var sigTick by remember { mutableIntStateOf(0) }
    var sigLabel by remember { mutableStateOf<String?>(null) }
    var quickMenu by remember { mutableStateOf<LongPressMenu?>(null) }
    var diagInfo by remember { mutableStateOf<candy.ide.data.DiagItem?>(null) }
    var quickWord by remember { mutableStateOf<String?>(null) }
    // @composable 布局预览悬浮窗：长按 composable 函数弹出；编辑该函数 1s 无错自动刷新
    var composePreview by remember { mutableStateOf<candy.ide.ui.ComposePreviewState?>(null) }
    // 预览悬浮窗：顶栏拖动的位移（顶栏 onDrag 累加）
    var previewOffset by remember { mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) }
    val previewScope = androidx.compose.runtime.rememberCoroutineScope()
    var previewFullscreen by remember { mutableStateOf(false) }
    // 编辑节拍：onTextChange 计数，防抖用于预览刷新
    var textEditTick by remember { mutableIntStateOf(0) }
    var renameReq by remember { mutableStateOf<RenameRequest?>(null) }
    // 光标位置（SelectionChangeEvent 回调写入）：供诊断悬浮窗定位
    var cursorLine by remember { mutableIntStateOf(-1) }
    var cursorCol by remember { mutableIntStateOf(-1) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var fixPopupAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 外部修改检测：文件签（黄条）—— 文件被外部改动时提示同步或覆盖
    var externalChange by remember { mutableStateOf<candy.ide.fs.CandyPath?>(null) }
    // 记录每个打开文件最近一次我们这边写盘/载入时的 mtime，用于判断外部改动
    val knownMtimes = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
    var topBarHeightPx by remember { mutableIntStateOf(0) }

    // 全屏预览时返回键 = 退出全屏（像 AI 助手页那样）；否则退编辑器
    BackHandler { if (previewFullscreen) previewFullscreen = false else vm.backFromEditor() }

    // 外部修改检测：活动恢复 / agent 回复完毕 / 切换当前文件时，比对磁盘 mtime
    LaunchedEffect(vm.resumeNonce.intValue, safe) {
        val f = safe
        if (f == null) { externalChange = null; return@LaunchedEffect }
        val known = knownMtimes[f.key]
        val disk = runCatching { f.lastModified() }.getOrDefault(0L)
        when {
            // 切换文件：先清旧标记（这个 effect 因 safe 变化而重跑）
            known == null -> { knownMtimes[f.key] = disk; externalChange = null }
            // 已知 mtime 且磁盘更新（agent/git/termux 或其它外部改动）：直接以磁盘为准重载，不再询问
            disk > known && disk > 0 -> {
                knownMtimes[f.key] = disk
                externalChange = null
                vm.reloadCurrentFromDisk()
            }
        }
    }
    // 每次 sora 里保存/载入后同步 mtime（由 SoraEditorView 的保存回调驱动）
    LaunchedEffect(vm.savedTextsVersion.intValue, safe) {
        val f = safe ?: return@LaunchedEffect
        runCatching { knownMtimes[f.key] = f.lastModified() }.getOrNull()
        externalChange = null
    }

    // 诊断 -> 编辑器下划线：DiagItem 的行列换算成正文偏移，交给 sora 画红/黄波浪线。
    // 只有 Error/Warning 画波浪线；补充级（Hint/Info）只进悬浮窗与诊断页，不画线。
    LaunchedEffect(vm.diagnostics.size, vm.diagnostics.firstOrNull()) {
        val ed = editor ?: return@LaunchedEffect
        val content = ed.text
        val lineCount = content.lineCount
        val container = DiagnosticsContainer()
        vm.diagnostics.forEachIndexed { i, d ->
            if (d.line >= lineCount) return@forEachIndexed
            if (d.severity < DiagnosticRegion.SEVERITY_WARNING) return@forEachIndexed
            runCatching {
                val start = content.getCharIndex(d.line, d.char)
                val end = content.getCharIndex(d.endLine, d.endChar)
                if (end > start) container.addDiagnostic(DiagnosticRegion(start, end, d.severity, i.toLong()))
            }
        }
        ed.setDiagnostics(container)
    }

    // 文件树抽屉：不用 ModalNavigationDrawer（其遮罩点击不受理），自绘遮罩 + 面板，点遮罩即关
    Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    when (currentProject?.let { ProjectStore.typeOf(it) }) {
                                        ProjectStore.TYPE_JSAPK -> "安卓工程"
                                        ProjectStore.TYPE_SCRIPT -> "脚本工程"
                        ProjectStore.TYPE_MD -> "MD工程"
                                        else -> "JS工程"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CandyTextDim,
                                    maxLines = 1
                                )
                                Text(
                                    currentProject?.name ?: "工程",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CandyText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "文件树", tint = CandyText)
                            }
                        },
                        actions = {
                            Box(
                                modifier = Modifier.size(44.dp).combinedClickable(
                                    enabled = isEditableFile && editor != null,
                                    onClick = {
                                        editor?.let { vm.saveCurrent(it.text.toString()) }
                                        Toast.makeText(ctx, "文件保存完成", Toast.LENGTH_SHORT).show()
                                    },
                                    onLongClick = {
                                        editor?.let { vm.saveCurrent(it.text.toString()) }
                                        vm.saveProject()
                                        Toast.makeText(ctx, "工程保存完成", Toast.LENGTH_SHORT).show()
                                    },
                                ),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "保存（长按保存全部工程文件）", tint = CandyText)
                            }
                            // 撤销 / 恢复：短按撤销一步；长按弹出拖动条，向左拖撤销、向右拖恢复
                            var undoBar by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .combinedClickable(
                                        enabled = isEditableFile && editor != null,
                                        onClick = { editor?.undo() },
                                        onLongClick = { undoBar = true },
                                    ),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "撤销（长按拖动撤销/恢复）", tint = CandyText, modifier = Modifier.size(20.dp))
                            }
                            // 撤销/恢复拖动条：中点为基线，左侧拖动撤销、右侧拖动恢复
                            if (undoBar) {
                                var sliderPos by remember { mutableStateOf(0f) }
                                var appliedPos by remember { mutableStateOf(0f) }
                                Dialog(onDismissRequest = {
                                    undoBar = false
                                }) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = CandySurface,
                                        modifier = Modifier.width(300.dp),
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                "拖动撤销 / 恢复",
                                                color = CandyText,
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Spacer(Modifier.size(6.dp))
                                            Text(
                                                if (sliderPos < -0.05f) "撤销中…" else if (sliderPos > 0.05f) "恢复中…" else "松手关闭",
                                                color = CandyTextDim,
                                                fontSize = 12.sp,
                                            )
                                            Spacer(Modifier.size(10.dp))
                                            Slider(
                                                value = sliderPos,
                                                onValueChange = { v ->
                                                    val delta = v - appliedPos
                                                    val ed = editor
                                                    if (ed != null && delta < -0.02f) {
                                                        val steps = (-delta * 12f).toInt().coerceAtLeast(1)
                                                        repeat(steps) { ed.undo() }
                                                    } else if (ed != null && delta > 0.02f) {
                                                        val steps = (delta * 12f).toInt().coerceAtLeast(1)
                                                        repeat(steps) { ed.redo() }
                                                    }
                                                    appliedPos = v
                                                    sliderPos = v
                                                },
                                                onValueChangeFinished = {
                                                    sliderPos = 0f
                                                    appliedPos = 0f
                                                },
                                                valueRange = -1f..1f,
                                            )
                                            Spacer(Modifier.size(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                            ) {
                                                Text("撤销", color = CandyTextDim, fontSize = 11.sp)
                                                Text("恢复", color = CandyTextDim, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .combinedClickable(
                                        // 运行/打包只需要工程与文件存在：md 渲染模式下没有编辑器实例，
                                        // 但长按打包必须可用（md 文档工程的主要操作）
                                        enabled = safe != null && !running,
                                        onClick = { vm.saveAndRun(editor?.text?.toString()) },
                                        onLongClick = { vm.buildApk() },
                                    ),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                if (running) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = CandyPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "运行（短按虚拟桥接环境 / 长按打包 APK）", tint = CandySecondary, modifier = Modifier.size(21.dp))
                                }
                            }
                            // 第 4 按钮：短按 = 输出/日志抽屉；长按 = git 页（未装 git 提示安装）
                            Box(
                                modifier = Modifier.size(44.dp).combinedClickable(
                                    onClick = { vm.openDrawerTab(vm.drawerTab) },
                                    onLongClick = { vm.openGitPage(ctx) },
                                ),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = "输出/日志/诊断（长按打开 git 页）",
                                    tint = if (logOpen) CandyPrimary else CandyText)
                            }
                            var moreMenu by remember { mutableStateOf(false) }
                            Box {
                                Box(
                                    modifier = Modifier.size(44.dp).combinedClickable(
                                        onClick = { moreMenu = true },
                                        onLongClick = {
                                            val proj = currentProject
                                            if (proj != null) vm.goProjectSettings(proj)
                                            else Toast.makeText(ctx, "未打开工程", Toast.LENGTH_SHORT).show()
                                        },
                                    ),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "其他操作（长按工程设置）", tint = CandyText)
                                }
                                DropdownMenu(
                                    expanded = moreMenu,
                                    onDismissRequest = { moreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("AI 助手") },
                                        onClick = {
                                            moreMenu = false
                                            vm.goAgent()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("格式化文档") },
                                        onClick = {
                                            moreMenu = false
                                            val ed = editor
                                            if (ed == null) return@DropdownMenuItem
                                            val ok = vm.formatDocument()
                                            if (!ok) {
                                                Toast.makeText(ctx, "文档已是格式化状态，无需调整", Toast.LENGTH_SHORT).show()
                                                return@DropdownMenuItem
                                            }
                                            // 应用 pendingFormatEdits：批量编辑合并成一次撤销
                                            val content = ed.text
                                            val edits = vm.pendingFormatEdits.toList()
                                            vm.pendingFormatEdits.clear()
                                            var ok2 = false
                                            try { ok2 = content.beginBatchEdit() } catch (_: Throwable) { ok2 = false }
                                            try {
                                                edits
                                                    .sortedByDescending { it.first.start.line * 100000 + it.first.start.character }
                                                    .forEach { (range, text) ->
                                                        runCatching {
                                                            val s = content.getCharIndex(range.start.line, range.start.character)
                                                            val e = content.getCharIndex(range.end.line, range.end.character)
                                                            if (s in 0..e) content.replace(s, e, text)
                                                        }
                                                    }
                                            } finally { if (ok2) content.endBatchEdit() }
                                            vm.onEditorTextChanged(content.toString())
                                            vm.saveCurrent(content.toString())
                                        },
                                    )
                                }
                            }
                        },
                        modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg)
                    )
                },
                containerColor = CandyBg
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(CandyBg)
                ) {
                    if (vm.openFiles.isNotEmpty()) {
                        ScrollableTabRow(
                            // 不用默认指示器：Material3 的指示器 lambda 在页签增删帧里会越界（已知缺陷），用下面的点状选中态代替
                            indicator = {},
                            selectedTabIndex = vm.activeIndex.intValue.coerceIn(0, (vm.openFiles.size - 1).coerceAtLeast(0)),
                            containerColor = CandySurface,
                            contentColor = CandyPrimary,
                            edgePadding = 0.dp,
                        ) {
                            vm.openFiles.forEachIndexed { i, f ->
                                Tab(
                                    selected = i == vm.activeIndex.intValue,
                                    onClick = { vm.selectTab(i) },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val dirtyStar = vm.dirtyTexts.containsKey(f.key)
                                            if (dirtyStar) {
                                                Text(
                                                    "*",
                                                    color = CandySecondary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                                Spacer(Modifier.size(2.dp))
                                            }
                                            Text(
                                                f.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            IconButton(
                                                onClick = { vm.closeTabChecked(i) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "关闭",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = CandyTextDim
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { editorSize = it }) {
                        val active = currentFile
                        // Markdown 文件：右上角悬浮小按钮，渲染 / 文本模式互切
                        if (active == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("从左侧文件树选择一个文件", color = CandyTextDim)
                            }
                        } else if (active.name.endsWith(".md") && vm.mdTextMode[active.key] != true) {
                            val src = remember(active) { runCatching { active.readText() }.getOrDefault("") }
                            MarkdownText(
                                src,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            )
                        } else {
                            SoraEditorView(
                                modifier = Modifier.fillMaxSize(),
                                file = active,
                                vm = vm,
                                textProvider = vm::loadTextFor,
                                readOnly = isMdProject,
                                onEditorReady = { editor = it },
                                onLongPress = { x, y, line, col ->
                                    // 菜单先弹（不阻塞）；命中 @composable 函数时后台算视图树，
                                    // 算好把「预览布局」选项填进菜单（首次分析慢，不在主线程等）
                                    longMenu = LongPressMenu(x, y, line, col, null)
                                    previewScope.launch {
                                        val pv = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                            runCatching { vm.composePreviewState(line, col) }.getOrNull()
                                        }
                                        val cur = longMenu
                                        if (cur != null && cur.line == line && cur.col == col) {
                                            longMenu = cur.copy(preview = pv)
                                        }
                                    }
                                },
                                completionLongClick = { label, kind ->
                                    val ed = editor ?: return@SoraEditorView
                                    if (kind == io.github.rosemoe.sora.lang.completion.CompletionItemKind.Keyword || label in KEYWORD_TEMPLATES) {
                                        // 关键词：插入完整语句骨架（snippet 展开）
                                        val tpl = KEYWORD_TEMPLATES[label]
                                        if (tpl != null) {
                                            val content = ed.text
                                            val cursor = ed.cursor
                                            runCatching {
                                                // 先把已输入的关键词删掉，否则展开后会「ifif…」叠词
                                                val left = cursor.left
                                                var ws = left
                                                while (ws > 0 && content[ws - 1].let { it == ' ' || it == '\t' }) ws--
                                                var we = ws
                                                while (we > 0 && content[we - 1].let { it.isLetterOrDigit() || it == '_' }) we--
                                                val word = content.subSequence(we, ws).toString()
                                                // word==label 全删；label 以 word 为前缀（输入 v 选 var）也删掉，避免 vvar
                                                val start = if (word.equals(label, ignoreCase = true) ||
                                                    (word.isNotEmpty() && label.startsWith(word, ignoreCase = true))) we else left
                                                // 模板随缩进：除首行外每行补上当前行的行首缩进，
                                                // 否则模板永远顶在第一列
                                                var ls = start
                                                while (ls > 0 && content[ls - 1] != '\n') ls--
                                                var iIndent = ls
                                                val indBuilder = StringBuilder()
                                                while (iIndent < start && (content[iIndent] == ' ' || content[iIndent] == '\t')) {
                                                    indBuilder.append(content[iIndent])
                                                    iIndent++
                                                }
                                                val ind = indBuilder.toString()
                                                val tplIndented = if (ind.isEmpty()) tpl else {
                                                    val parts = tpl.split("\n")
                                                    (listOf(parts.first()) + parts.drop(1).map { ind + it }).joinToString("\n")
                                                }
                                                val batch = try { content.beginBatchEdit(); true } catch (_: Throwable) { false }
                                                try { content.replace(start, cursor.right, tplIndented) }
                                                finally { if (batch) content.endBatchEdit() }
                                                vm.onEditorTextChanged(content.toString())
                                                vm.saveCurrent(content.toString())
                                            }
                                            Toast.makeText(ctx, "已展开「" + label + "」模板", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(ctx, "尚无该关键词的模板", Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (label.isNotEmpty()) {
                                        // 符号：跳转定义
                                        val cur = ed.cursor
                                        goToDefinitionAt(vm, ed, cur.leftLine, cur.leftColumn, label)
                                    }
                                },
                                onDoubleClick = { x, y, line, col ->
                                    longMenu = null
                                    // 优先用选区文本（双击选中的就是目标词）；选区折叠时才按位置反推
                                    val ed0 = editor
                                    val w = if (ed0 != null && ed0.cursor.left != ed0.cursor.right) {
                                        ed0.text.substring(ed0.cursor.left, ed0.cursor.right).ifBlank { null }
                                    } else ed0?.let { wordAt(it, line, col) }
                                    quickMenu = LongPressMenu(x, y, line, col)
                                    quickWord = w
                                },
                                onEditorClick = { longMenu = null; quickMenu = null },
                                onCursorChange = { line, col ->
                                    cursorLine = line
                                    cursorCol = col
                                    sigTick++  // 触发签名提示重算（防抖在下面的 effect 里）
                                },
                                onTextChange = { s -> textEditTick++; vm.onEditorTextChanged(s) },
                            )
                            // 转到定义的跳转消费
                            LaunchedEffect(active, vm.pendingGotoLine) {
                                val target = vm.pendingGotoLine
                                if (target >= 0) {
                                    val ed = editor
                                    if (ed != null) runCatching {
                                        val line = target.coerceIn(0, (ed.text.lineCount - 1).coerceAtLeast(0))
                                        ed.setSelection(line, 0, true)
                                    }
                                    vm.consumeGoto()
                                }
                            }
                        }
                        // 签名提示：光标在实参区时编辑器底部显示被调函数签名（150ms 防抖）
                        LaunchedEffect(sigTick) {
                            if (sigTick == 0) return@LaunchedEffect
                            kotlinx.coroutines.delay(150)
                            val (ln, c) = cursorLine to cursorCol
                            sigLabel = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                runCatching { vm.computeSignatureHelp(ln, c) }.getOrNull()
                            }
                        }
                        // 布局预览刷新：编辑（textEditTick 变化）后 1s 防抖；
                        // 函数声明行范围内无 Error 诊断才刷新（用户规则：修改没错才更新）
                        LaunchedEffect(textEditTick, composePreview?.fnName) {
                            val pv = composePreview ?: return@LaunchedEffect
                            if (textEditTick == 0) return@LaunchedEffect
                            kotlinx.coroutines.delay(1000)
                            val hasErr = vm.diagnostics.any { d ->
                                d.severity >= DiagnosticRegion.SEVERITY_ERROR &&
                                    d.line in pv.start..pv.end
                            }
                            if (hasErr) return@LaunchedEffect
                            val next = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                runCatching { vm.composePreviewState(pv.start, 0) }.getOrNull()
                            }
                            if (next != null && next.fnName == pv.fnName) composePreview = next
                        }
                        // 悬浮窗本体：约为屏幕 1/5（比 1/4 略小）；全屏时填满
                        composePreview?.let { pv ->
                            Surface(
                                modifier = Modifier
                                    .align(androidx.compose.ui.Alignment.TopEnd)
                                    .then(
                                        if (previewFullscreen) Modifier.fillMaxSize()
                                        else Modifier.fillMaxWidth(0.42f)
                                    )
                                    .heightIn(max = if (previewFullscreen) 4000.dp else 300.dp)
                                    .offset { if (previewFullscreen) androidx.compose.ui.unit.IntOffset.Zero else previewOffset }
                                    .then(if (previewFullscreen) Modifier else Modifier.padding(end = 8.dp, top = 4.dp)),
                                shape = if (previewFullscreen) androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                                        else androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                color = candy.ide.ui.theme.CandySurface,
                                tonalElevation = if (previewFullscreen) 0.dp else 4.dp,
                                shadowElevation = if (previewFullscreen) 0.dp else 10.dp,
                            ) {
                                candy.ide.ui.ComposePreviewPanel(
                                    state = pv,
                                    fullscreen = previewFullscreen,
                                    onToggleFullscreen = { previewFullscreen = !previewFullscreen },
                                    onClose = { composePreview = null; previewFullscreen = false; previewOffset = androidx.compose.ui.unit.IntOffset.Zero },
                                    onApply = { treeJson ->
                                        // 视图树 → 函数体写回源码：保留签名行（含 @composable），替换体；编辑器可撤销
                                        runCatching {
                                            val tree = org.json.JSONObject(treeJson)
                                            val content = editor?.text?.toString() ?: ""
                                            if (content.isEmpty()) return@runCatching
                                            val lines = content.split("\n").toMutableList()
                                            var sig = (pv.start - 1).coerceIn(0, lines.size - 1)
                                            while (sig < lines.size && !lines[sig].contains("{")) sig++
                                            if (sig >= lines.size) return@runCatching
                                            val endIdx = (pv.end - 1).coerceIn(sig, lines.size - 1)
                                            val head = lines.subList(0, sig + 1)
                                            val tail = if (endIdx + 1 < lines.size) lines.subList(endIdx + 1, lines.size) else emptyList()
                                            val out = ArrayList<String>(head)
                                            out += candy.ide.ui.PreviewCodeGen.bodyLines(tree)
                                            out += "}"
                                            out += tail
                                            val newText = out.joinToString("\n")
                                            editor?.text?.replace(0, editor!!.text.length, newText)
                                        }.onFailure {
                                            android.widget.Toast.makeText(ctx, "写回失败：" + (it.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onDrag = { delta -> previewOffset = androidx.compose.ui.unit.IntOffset(previewOffset.x + delta.x, previewOffset.y + delta.y) },
                                )
                            }
                        }
                        sigLabel?.let { label ->
                            Surface(
                                modifier = Modifier
                                    .align(androidx.compose.ui.Alignment.BottomStart)
                                    .fillMaxWidth(),
                                color = CandySurfaceVariant.copy(alpha = 0.95f),
                                tonalElevation = 3.dp,
                            ) {
                                Text(
                                    text = label,
                                    color = CandyText,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }
                        if (active != null && active.name.endsWith(".md", ignoreCase = true)) {
                            val rendering = vm.mdTextMode[active.key] != true
                            androidx.compose.material3.SmallFloatingActionButton(
                                onClick = { vm.mdTextMode[active.key] = rendering },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 12.dp, top = 10.dp)
                                    .size(40.dp),
                                containerColor = candy.ide.ui.theme.CandySurface,
                                contentColor = candy.ide.ui.theme.CandyPrimary,
                            ) {
                                androidx.compose.material3.Icon(
                                    if (rendering) androidx.compose.material.icons.Icons.Default.Description
                                    else androidx.compose.material.icons.Icons.Default.MenuBook,
                                    contentDescription = if (rendering) "切换为文本编辑" else "切换为渲染预览",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }


                        // 双击快捷操作菜单：词级上下文（函数调用 → 指名调用；标识符 → 转定义/重命名）
                        quickMenu?.let { menu ->
                            val ed3 = editor
                            val word = quickWord
                            Surface(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (menu.x.roundToInt() - 8).coerceAtLeast(0),
                                            (menu.y.roundToInt() - 8).coerceAtLeast(0),
                                        )
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = CandySurface,
                                shadowElevation = 8.dp,
                            ) {
                                // 一排图标（只读工程不含剪切/粘贴）
                                val ed3 = editor
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { quickMenu = null; ed3?.selectAll() }) {
                                        Icon(Icons.Default.SelectAll, contentDescription = "全选", tint = CandyText, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { quickMenu = null; ed3?.copyText() }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = CandyText, modifier = Modifier.size(18.dp))
                                    }
                                    if (!isMdProject) {
                                        IconButton(onClick = { quickMenu = null; ed3?.cutText() }) {
                                            Icon(Icons.Default.ContentCut, contentDescription = "剪切", tint = CandyText, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { quickMenu = null; ed3?.pasteText() }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴", tint = CandyText, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // 长按菜单（快捷操作：长按 = 转定义/重命名/指名调用；双击 = 文本操作）
                        longMenu?.let { menu ->
                            // 长按位置现场取词：选区优先（长按选词已选中），否则按行列反推
                            val ed0 = editor
                            val word = if (ed0 != null && ed0.cursor.left != ed0.cursor.right) {
                                runCatching { ed0.text.substring(ed0.cursor.left, ed0.cursor.right) }.getOrNull()
                            } else ed0?.let { wordAt(it, menu.line, menu.col) }
                            Surface(
                                modifier = Modifier
                                    .width(180.dp)
                                    .offset {
                                        IntOffset(
                                            (menu.x.roundToInt() - 8).coerceAtLeast(0),
                                            (menu.y.roundToInt() - 8).coerceAtLeast(0),
                                        )
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = CandySurface,
                                shadowElevation = 8.dp,
                            ) {
                                Column {
                                    val ed = editor
                                    // 命中 @composable 函数：菜单首项「预览布局」弹悬浮窗
                                    if (menu.preview != null) {
                                        LongMenuRow("▶ 预览布局") {
                                            longMenu = null
                                            composePreview = menu.preview
                                            previewFullscreen = false
                                        }
                                    }
                                    if (word != null) {
                                        LongMenuRow("使用指名调用法") {
                                            longMenu = null
                                            ed?.let { namedCallOf(it, vm, menu.line, menu.col, word) }
                                        }
                                        LongMenuRow("转到定义") {
                                            longMenu = null
                                            ed?.let { goToDefinition(vm, it, menu.line, menu.col, word) }
                                        }
                                        LongMenuRow("查看文档") {
                                            longMenu = null
                                            ed?.let { showHoverDoc(vm, it, menu.line, menu.col, word) }
                                        }
                                        LongMenuRow("查找引用") {
                                            longMenu = null
                                            ed?.let { findReferences(vm, it, menu.line, menu.col, word) }
                                        }
                                        LongMenuRow("重命名") {
                                            longMenu = null
                                            renameReq = RenameRequest(word, menu.line, menu.col)
                                        }
                                    } else {
                                        LongMenuRow("转到定义") {
                                            longMenu = null
                                            ed?.let { goToDefinition(vm, it, menu.line, menu.col) }
                                        }
                                    }
                                }
                            }
                        }

                        // 光标处诊断悬浮窗：光标停在诊断区间内时出现，点条目弹快速修正
                        val ed2 = editor
                        if (ed2 != null && !logOpen && cursorLine >= 0) {
                            val here = vm.diagnostics.filter { d ->
                                cursorLine in d.line..d.endLine &&
                                    (cursorLine != d.line || cursorCol >= d.char)
                            }
                            if (here.isNotEmpty()) {
                                                                val layoutPos = runCatching {
                                    ed2.layout?.getCharLayoutOffset(cursorLine, cursorCol)
                                }.getOrNull()
                                val rh = ed2.rowHeight
                                if (layoutPos != null && layoutPos.size >= 2 && rh > 0) {
                                    Surface(
                                        modifier = Modifier
                                            .widthIn(max = 300.dp)
                                            .offset {
                                                val maxX = (editorSize.width - 420).coerceAtLeast(0)
                                                val ox = (layoutPos[0] - 8f).coerceIn(0f, maxX.toFloat())
                                                var oy = layoutPos[1] - rh * 2.8f
                                                if (oy < 4f) oy = layoutPos[1] + rh * 1.1f
                                                IntOffset(ox.roundToInt(), oy.roundToInt())
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = CandySurface,
                                        shadowElevation = 6.dp,
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            here.take(3).forEach { d ->
                                                val bad = d.severity >= DiagnosticRegion.SEVERITY_ERROR
                                                Row(
                                                    modifier = Modifier
                                                        .padding(vertical = 2.dp)
                                                        .combinedClickable(
                                                            onClick = {
                                                                // 短按：诊断含义说明
                                                                runCatching {
                                                                    val content = ed2.text
                                                                    val line = d.line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
                                                                    val col = d.char.coerceIn(0, content.getColumnCount(line))
                                                                    ed2.setSelection(line, col, true)
                                                                }
                                                                diagInfo = d
                                                            },
                                                            onLongClick = {
                                                                // 长按：快速修正列表（fixPopup 读当前光标诊断）
                                                                runCatching {
                                                                    val content = ed2.text
                                                                    val line = d.line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
                                                                    val col = d.char.coerceIn(0, content.getColumnCount(line))
                                                                    ed2.setSelection(line, col, true)
                                                                }
                                                                fixPopupAt = Pair(d.line, d.char)
                                                            },
                                                        ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (bad) Color(0xFFFF5370) else Color(0xFFE0AF68)))
                                                    Spacer(Modifier.size(6.dp))
                                                    Text(
                                                        d.message,
                                                        color = CandyText,
                                                        fontSize = 11.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                
                    // 底部符号栏：常规 + Unicode 两行；imePadding 让它始终浮在输入法上方
                    if (isEditableFile && currentFile != null) {
                        candy.ide.editor.SymbolBars(
                            modifier = Modifier.imePadding(),
                            onSymbol = { sym ->
                                editor?.let { ed ->
                                    runCatching {
                                        val cursor = ed.cursor
                                        ed.insertText(sym, 1)
                                        ed.setSelection(cursor.leftLine, cursor.leftColumn + sym.length, true)
                                    }
                                }
                            },
                        )
                    }
}
            }

            // 输出/日志抽屉：盖在整屏之上（含标签栏），左侧压暗，点遮罩收回
            AnimatedVisibility(visible = logOpen, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f))
                    .clickable { vm.toggleLog() })
            }
            AnimatedVisibility(
                visible = logOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(220)) { fullWidth -> fullWidth },
                exit = slideOutHorizontally(tween(220)) { fullWidth -> fullWidth }
            ) {
                val topPad = with(androidx.compose.ui.platform.LocalDensity.current) { topBarHeightPx.toDp() }
                OutputDrawer(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.85f).padding(top = topPad),
                    vm = vm,
                    onGoto = { d ->
                        editor?.let { ed ->
                            runCatching {
                                val content = ed.text
                                val line = d.line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
                                val col = d.char.coerceIn(0, content.getColumnCount(line))
                                ed.setSelection(line, col, true)
                            }
                            ed.requestFocus()
                        }
                        vm.toggleLog()
                    }
                )
            }

            // 左侧文件树抽屉：自绘遮罩（点遮罩关闭）+ 从左滑出的面板，手势拉开仍然关着
            AnimatedVisibility(
                visible = drawerState.isOpen,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f))
                        .clickable { scope.launch { drawerState.close() } },
                )
            }
            AnimatedVisibility(
                visible = drawerState.isOpen,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = slideInHorizontally(tween(220)) { fullWidth -> -fullWidth },
                exit = slideOutHorizontally(tween(220)) { fullWidth -> -fullWidth },
            ) {
                FileTreePanel(
                    vm = vm,
                    onFileClick = { file ->
                        vm.openFile(file)
                        scope.launch { drawerState.close() }
                    },
                    drawerClose = { scope.launch { drawerState.close() } },
                )
            }
            // git 面板抽屉：自绘遮罩 + 从右滑出（与日志抽屉同款）
            AnimatedVisibility(
                visible = vm.gitDrawerOpen.value,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f))
                        .clickable { vm.gitDrawerOpen.value = false },
                )
            }
            AnimatedVisibility(
                visible = vm.gitDrawerOpen.value,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(220)) { fullWidth -> fullWidth },
                exit = slideOutHorizontally(tween(220)) { fullWidth -> fullWidth }
            ) {
                val topPad = with(androidx.compose.ui.platform.LocalDensity.current) { topBarHeightPx.toDp() }
                Surface(color = CandyBg, modifier = Modifier.fillMaxHeight().fillMaxWidth(0.85f).padding(top = topPad)) {
                    GitPanel(vm)
                }
            }
        // AI agent 悬浮球（设置里 agentEnabled=true 才出现；Box 内最后声明 → 最上层）
        AgentFloatingBall(vm)
                    }

    // 控制台输入框：程序 readLine 时 JS 线程在等这个结果
    if (vm.showInput) {
        AlertDialog(
            onDismissRequest = { vm.cancelInput() },
            title = { Text("程序请求输入") },
            text = {
                var inputFocused by remember { mutableStateOf(false) }
                val focusRequester = remember { FocusRequester() }
                OutlinedTextField(
                    value = vm.inputText,
                    onValueChange = { vm.inputText = it },
                    label = { Text("输入内容") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { inputFocused = it.isFocused },
                )
                LaunchedEffect(Unit) {
                    // 对话框出现即聚焦输入框并弹键盘，省得手动点
                    runCatching { focusRequester.requestFocus() }
                }
            },
            confirmButton = { TextButton(onClick = { vm.submitInput() }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { vm.cancelInput() }) { Text("取消") } },
        )
    }

    // 诊断说明：短按悬浮窗条目的信息对话框
    diagInfo?.let { d ->
        AlertDialog(
            onDismissRequest = { diagInfo = null },
            title = { Text("诊断说明") },
            text = {
                Column {
                    Text("位置：第 " + (d.line + 1) + " 行 第 " + (d.char + 1) + " 列", color = CandyTextDim, fontSize = 12.sp)
                    Spacer(Modifier.size(6.dp))
                    Text(d.message, color = CandyText, fontSize = 13.sp)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        explainDiag(d.message),
                        color = CandyTextDim,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { diagInfo = null }) { Text("知道") }
            },
            dismissButton = {
                TextButton(onClick = {
                    diagInfo = null
                    runCatching {
                        val content = editor?.text ?: return@TextButton
                        val line = d.line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
                        val col = d.char.coerceIn(0, content.getColumnCount(line))
                        editor?.setSelection(line, col, true)
                    }
                }) { Text("定位") }
            },
        )
    }
    // 灯泡 → 快速修正列表（LSP codeActions）
    if (fixPopupAt != null) {
        val fixes = remember(fixPopupAt) { runCatching { vm.quickFixes(fixPopupAt!!.first, fixPopupAt!!.second) }.getOrDefault(emptyList()) }
        AlertDialog(
            onDismissRequest = { fixPopupAt = null },
            title = { Text("快速修正") },
            text = {
                if (fixes.isEmpty()) {
                    Text("当前没有可用的快速修正\n（codeActions 覆盖：全角符号替换、纯调用加 unchecked/@impure、缺 @async、多传实参、impl 补存根、声明加 @mut —— 视光标处的诊断而定）", color = CandyTextDim)
                } else {
                    Column {
                        fixes.forEach { fix ->
                            TextButton(
                                onClick = {
                                    val ed = editor
                                    if (ed != null && fix.command != null) {
                                        // LSP command 型快捷操作：重命名 / 转到定义
                                        val cur = ed.cursor
                                        when (fix.command) {
                                            "editor.action.rename" -> renameReq =
                                                RenameRequest(
                                                    wordAtCursorOf(ed, cur.leftLine, cur.leftColumn),
                                                    cur.leftLine,
                                                    cur.leftColumn,
                                                )
                                            "editor.action.revealDefinition" ->
                                                goToDefinition(vm, ed, cur.leftLine, cur.leftColumn)
                                        }
                                        vm.mdLogUser("**[快捷操作]** " + fix.title)
                                        fixPopupAt = null
                                        return@TextButton
                                    }
                                    if (ed != null) {
                                        val content = ed.text
                                        fix.edits
                                            .sortedByDescending { it.first.start.line * 100000 + it.first.start.character }
                                            .forEach { (range, text) ->
                                                runCatching {
                                                    val s = content.getCharIndex(range.start.line, range.start.character)
                                                    val e = content.getCharIndex(range.end.line, range.end.character)
                                                    if (s in 0..e) content.replace(s, e, text)
                                                }
                                            }
                                        vm.mdLogUser("**[快速修正]** " + fix.title)
                                        runCatching { vm.saveCurrent(content.toString()) }
                                    }
                                    fixPopupAt = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(fix.title, color = CandyText) }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    // 退出工程：有未保存修改时确认
    if (vm.exitGuard.value) {
        AlertDialog(
            onDismissRequest = { vm.cancelExit() },
            title = { Text("有未保存的修改") },
            text = { Text("「" + (currentProject?.name ?: "工程") + "」还有修改没保存，要保存后再退出吗？", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    val text = editor?.text?.toString()
                    if (text != null) vm.saveAndExit(text) else vm.discardChangesAndExit()
                }) { Text("保存并退出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.discardChangesAndExit() }) { Text("不保存退出") }
                    TextButton(onClick = { vm.cancelExit() }) { Text("取消") }
                }
            },
        )
    }

    // 安装中：Shizuku 静默安装的等待框
    if (vm.installing.value) {
        Dialog(onDismissRequest = { /* 不可取消 */ }) {
            Surface(shape = RoundedCornerShape(12.dp), color = CandySurface) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CandyPrimary, strokeWidth = 3.dp)
                    Spacer(Modifier.size(12.dp))
                    Text("正在静默安装…", color = CandyText, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    // 安装完成：是否打开
    vm.installedPkg.value?.let { pkg ->
        AlertDialog(
            onDismissRequest = { vm.installedPkg.value = null },
            title = { Text("安装完成") },
            text = { Text("是否立即打开？", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    vm.installedPkg.value = null
                    vm.launchInstalled(pkg)
                }) { Text("打开") }
            },
            dismissButton = {
                TextButton(onClick = { vm.installedPkg.value = null }) { Text("稍后") }
            },
        )
    }

    // 安装失败提示
    vm.installError.value?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.installError.value = null },
            title = { Text("安装失败") },
            text = { Text(msg, color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = { vm.installError.value = null }) { Text("知道") }
            },
        )
    }

    // 打包完成：是否安装
    vm.pendingApk.value?.let { apk ->
        AlertDialog(
            onDismissRequest = { vm.cancelInstallApk() },
            title = { Text("打包完成") },
            text = { Text("app.apk（" + apk.length() / 1024 + " KB）已生成在工程 build/ 目录，立即安装吗？", color = CandyTextDim) },
            confirmButton = { TextButton(onClick = { vm.confirmInstallApk() }) { Text("安装") } },
            dismissButton = { TextButton(onClick = { vm.cancelInstallApk() }) { Text("稍后") } },
        )
    }

    renameReq?.let { req ->
        var newName by remember(req) { mutableStateOf(req.word) }
        AlertDialog(
            onDismissRequest = { renameReq = null },
            title = { Text("重命名「" + req.word + "」") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ed = editor
                        val target = newName.trim()
                        if (ed != null && target.isNotEmpty() && target != req.word) {
                            val res = vm.renameAt(ed.text.toString(), req.line, req.col, target)
                            when {
                                res == null -> Toast.makeText(ctx, "语言服务不可用", Toast.LENGTH_SHORT).show()
                                !res.isSymbol || res.edits.isEmpty() -> Toast.makeText(ctx, "只能对标识符重命名", Toast.LENGTH_SHORT).show()
                                else -> {
                                    val content = ed.text
                                    // 从后往前应用，保证前面的偏移不失效；
                                    // beginBatchEdit 把所有 replace 合并成单个撤销条目（点一下撤销即可全部回滚）
                                    val batch = try { content.beginBatchEdit(); true } catch (e: Exception) { false }
                                    try {
                                        res.edits
                                            .sortedByDescending { it.first.start.line * 100000 + it.first.start.character }
                                            .forEach { (range, text) ->
                                                runCatching {
                                                    var s = content.getCharIndex(range.start.line, range.start.character)
                                                    var e = content.getCharIndex(range.end.line, range.end.character)
                                                    // LSP 声明符号的 pos 可能落在 fun/struct 等关键字上
                                                    // （`fun main` 的 decl.pos = 'fun' 处），替换区间不是原名时
                                                    // 在同一行按原名重新定位，避免把关键字改掉
                                                    if (content.subSequence(s, e).toString() != req.word) {
                                                        val lineText = content.getLine(range.start.line)
                                                        val hit = lineText.indexOf(req.word, 0)
                                                        if (hit >= 0) {
                                                            s = content.getCharIndex(range.start.line, hit)
                                                            e = content.getCharIndex(range.start.line, hit + req.word.length)
                                                        }
                                                    }
                                                    if (s in 0..e) content.replace(s, e, text)
                                                }
                                            }
                                    } finally {
                                        if (batch) content.endBatchEdit()
                                    }
                                    vm.mdLogUser("**[重命名]** `" + req.word + "` -> `" + target + "`（语言服务定位的全部出现处）")
                                    // 重命名直接落盘，省得再点保存
                                    runCatching { vm.saveCurrent(content.toString()) }
                                }
                            }
                        }
                        renameReq = null
                    },
                    enabled = newName.trim().isNotEmpty()
                ) { Text("替换") }
            },
            dismissButton = { TextButton(onClick = { renameReq = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun LongMenuRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = CandyText,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** 取光标处的标识符（供 转到定义/重命用） */
private fun wordAt(editor: CodeEditor, line: Int, col: Int): String? = runCatching {
    val content = editor.text
    if (line >= content.lineCount) return null
    val text = content.toString()
    val idx = content.getCharIndex(line, col.coerceIn(0, content.getColumnCount(line)))
    if (idx !in text.indices) return null
    var start = idx
    while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
    var end = idx
    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
    text.substring(start, end).ifEmpty { null }
}.getOrNull()

/** 把 LSP 诊断消息翻译成给新手看的解释；命中不了就给通用提示 */
private fun explainDiag(message: String): String {
    val m = message.replace("'", "`")
    return when {
        "期望顶层只能写声明" in m -> "Sublimation 的工程文件里，顶层只能写声明（fun/struct/enum 等），要执行的代码必须放进 fun main() 里。脚本文件（.subs）才允许顶层语句。"
        "声明后从未使用" in m -> "这个变量/参数声明了但没被用到。如果确实不需要，给名字加下划线前缀（如 _s）可以抑制这条提示。"
        "想要" in m && "个实参" in m -> "调用函数时传入的参数个数和声明不一致。检查函数签名，或补上缺少的实参。"
        "与形参" in m && "不符" in m -> "实参的类型和函数声明要求的形式参数类型对不上。"
        "未定义" in m || "未绑定" in m -> "用到的名字在当前作用域里找不到定义：检查拼写，或确认它已经在这份文件/工程里声明。"
        "纯作用域内调用非纯函数" in m -> "这个函数被声明为纯（pure），但里面调用了会做副作用的函数。长按这条诊断可以快速修正：在调用前加 unchecked（临时豁免检查），或把当前函数标记为 @impure。"
        "需 @async 上下文" in m -> "这是挂起操作，只有在标了 @async 的函数里才能用。长按这条诊断可以一键给所在函数加上 @async 注解。"
        "是不可变的" in m -> "变量声明时没加 @mut，所以不能重新赋值。改声明为 `var x : @mut T`，或改用可变容器。长按可快速把声明改成可变。"
        "少了方法" in m -> "类型类（class）里声明的方法，impl 实现里没写全。长按可以一键补上缺失方法的存根，再把体填出来。"
        "类型不匹配" in m -> "两边的类型对不上：看消息里期望的类型和实际的类型，检查变量声明或函数返回类型。"
        "不是函数" in m -> "你在一个不是函数的值后面跟了括号调用。检查这个名字是什么类型。"
        "是异步调用" in m -> "这个调用会挂起，只在标注了 @async 的函数里才能直接使用。"
        "推导为" in m -> "这是类型推导注记：编译器替没有写类型标注的变量补上了推导结果。不是错误。"
        "注入自然命题" in m -> "自然数性质的自动注入提示，不是错误。"
        "无成员" in m && "可省略花括号" in m -> "类型类里没有成员时，可以省略大括号。这是风格建议。"
        "声明返回" in m && "函数体是" in m -> "函数实际返回的类型和声明的返回类型不一致。"
        else -> "这是语言服务给出的诊断信息。如果看不懂，可以在浮窗里长按查看有没有快速修正。"
    }
}

/** 按名字跳定义（补全长按用）：先在本文件按签名查，再退到 goToDefinition 的工程扫描 */
private fun goToDefinitionAt(vm: CandyViewModel, editor: CodeEditor, line: Int, col: Int, word: String) {
    val name = vm.currentFile.value?.name
    if (name != null) {
        runCatching {
            val syms = vm.analysis()?.collectSymbols(name) ?: emptyList()
            val hit = syms.firstOrNull { it.name == word }
            if (hit != null) {
                editor.setSelection((hit.lineStart - 1).coerceAtLeast(0), (hit.colStart - 1).coerceAtLeast(0), true)
                return@runCatching vm.mdLogUser("**[转到定义]** " + word + " -> " + name + ":" + hit.lineStart)
            }
        }
    }
    goToDefinition(vm, editor, line, col, word)
}

/** 关键词模板：与 LSP 的 KEYWORDS snippet 对应（补全长按生成完整语句） */
private val KEYWORD_TEMPLATES = mapOf(
    "fun" to "fun name(): Null {\n    \n}",
    "struct" to "struct Name(field: Nat)",
    "enum" to "enum Name {\n    \n}",
    "class" to "class Name {\n    \n}",
    "impl" to "impl Type for Self {\n    \n}",
    "var" to "var name = ",
    "type" to "type Name = ",
    "if" to "if (condition) {\n    \n} else {\n    \n}",
    "else" to "else {\n    \n}",
    "when" to "when (x) {\n    v => \n    else => \n}",
    "return" to "return ",
    "is" to "is ",
)

/** 长按菜单「查看文档」：LSP hoverFor 的文档（符号文档 / 关键字文档 / 内建函数文档）打到日志页 */
private fun showHoverDoc(vm: CandyViewModel, editor: CodeEditor, line: Int, col: Int, presetWord: String? = null) {
    val word = presetWord ?: wordAt(editor, line, col) ?: return vm.mdLogUser("**[文档]** 光标处没有标识符")
    val name = vm.currentFile.value?.name ?: return vm.mdLogUser("**[文档]** 未打开工程")
    val posLine = if (presetWord != null) editor.cursor.leftLine else line
    val posCol = if (presetWord != null) editor.cursor.leftColumn else col
    val hover = runCatching {
        vm.analysis()?.hoverFor(name, sugared.functor.lsp.Position(posLine, posCol))
    }.getOrNull()
    val text = when (val c = hover?.contents) {
        is String -> c
        is sugared.functor.lsp.MarkupContent -> c.value
        else -> ""
    }
    if (text.isBlank()) return vm.mdLogUser("**[文档]** `" + word + "` 没有文档记录")
    vm.mdLogUser("**[文档]** `" + word + "`\n" + text.trim().take(600))
}

/** 长按菜单「查找引用」：LSP referencesFor 的引用位置打到日志页（goto 链接可点跳） */
private fun findReferences(vm: CandyViewModel, editor: CodeEditor, line: Int, col: Int, presetWord: String? = null) {
    val word = presetWord ?: wordAt(editor, line, col) ?: return vm.mdLogUser("**[查找引用]** 光标处没有标识符")
    val name = vm.currentFile.value?.name ?: return vm.mdLogUser("**[查找引用]** 未打开工程")
    val posLine = if (presetWord != null) editor.cursor.leftLine else line
    val posCol = if (presetWord != null) editor.cursor.leftColumn else col
    val locs = runCatching {
        vm.analysis()?.referencesFor(name, sugared.functor.lsp.Position(posLine, posCol)) ?: emptyList()
    }.getOrDefault(emptyList())
    if (locs.isEmpty()) return vm.mdLogUser("**[查找引用]** `" + word + "` 在本文件没有引用")
    val lines = locs.mapIndexed { i, l ->
        val ln = l.range.start.line + 1
        val c = l.range.start.character + 1
        "${i + 1}. `goto://" + name + ":" + ln + ":" + c + "`"
    }.joinToString("\n")
    vm.mdLogUser("**[查找引用]** `" + word + "` — " + locs.size + " 处：\n" + lines)
}

/**
 * 在工程内找定义（fun/val/var/class/struct/enum 后面同名者），跳过去。
 * 优先走 LSP 的 definitionFor（本文件符号 + stdlib 兜底），失败再退到正则扫描工程。
 */
private fun goToDefinition(vm: CandyViewModel, editor: CodeEditor, line: Int, col: Int, presetWord: String? = null) {
    val word = presetWord ?: wordAt(editor, line, col) ?: return vm.mdLogUser("**[转到定义]** 光标处没有标识符")
    val project = vm.currentProject.value ?: return vm.mdLogUser("**[转到定义]** 未打开工程")
    // 长按菜单带选区词进来时，用编辑器光标位置（选词后光标就在词上，比长按事件的行列可靠）
    val posLine = if (presetWord != null) editor.cursor.leftLine else line
    val posCol = if (presetWord != null) editor.cursor.leftColumn else col
    // 1) LSP definitionFor：本文件符号 / stdlib 符号都能定位
    val name = vm.currentFile.value?.name
    if (name != null) {
        val loc = runCatching {
            if (presetWord != null) vm.analysis()?.definitionOfWord(name, presetWord)
            else vm.analysis()?.definitionFor(name, sugared.functor.lsp.Position(posLine, posCol))
        }.getOrNull() ?: runCatching {
            // 定义没命中时试类型定义：光标在变量/表达式上时，跳到它的类型声明
            vm.analysis()?.typeDefinitionFor(name, sugared.functor.lsp.Position(posLine, posCol))
        }.getOrNull()
        if (loc != null) {
            val uri = loc.uri
            val ln = loc.range.start.line
            when {
                // stdlib：file:/data/.../stdlib/xxx.subl（Windows 上是 file:/F:/…，统一按 file: 前缀识别）→ 打开只读页
                uri.startsWith("file:") -> {
                    val base = uri.substringAfterLast('/')
                    vm.openStdlib(base, ln + 1)
                    return
                }
                // 本文件符号
                uri == name -> {
                    val ed2 = editor
                    runCatching { ed2.setSelection(ln.coerceAtLeast(0), loc.range.start.character, true) }
                    return vm.mdLogUser("**[转到定义]** `" + word + "` -> " + name + ":" + (ln + 1))
                }
                else -> {
                    // 工程内其它文件（LSP 的 uri 与文件名一致）
                    val target = project.walk().firstOrNull { it.isFile() && it.name == uri }
                    if (target != null) {
                        vm.openFile(target)
                        vm.requestGoto(ln)
                        return vm.mdLogUser("**[转到定义]** `" + word + "` -> " + uri)
                    }
                }
            }
        }
        // 内建符号（print 等）没有源码定义：LSP 定位不到，但有 prelude 文档 → 说明它是编译器内建
        if (name != null) {
            val hover = runCatching {
                vm.analysis()?.hoverFor(name, sugared.functor.lsp.Position(posLine, posCol))
            }.getOrNull()
            val text = when (val c = hover?.contents) {
                is String -> c
                is sugared.functor.lsp.MarkupContent -> c.value
                else -> ""
            }
            if (text.isNotBlank()) {
                return vm.mdLogUser("**[转到定义]** `" + word + "` 是编译器内建函数（无源码）： " + text.replace("\n", "  ").take(160))
            }
        }
    }
    // 2) 正则扫描工程兜底（LSP 没覆盖到的声明形式）
    val pattern = Regex("\\b(fun|val|var|class|struct|enum)\\s+" + Regex.escape(word) + "\\b")
    val files = project.walk().filter { it.isFile() && it.extension in setOf("subl", "subs") }
    for (f in files) {
        val src = runCatching { f.readText() }.getOrNull() ?: continue
        val m = pattern.find(src) ?: continue
        val targetLine = src.substring(0, m.range.first).count { it == '\n' }
        vm.openFile(f)
        vm.requestGoto(targetLine)
        return vm.mdLogUser("**[转到定义]** `" + word + "` -> " + f.name)
    }
    vm.mdLogUser("**[转到定义]** 工程与标准库内未找到 `" + word + "` 的定义")
}

/**
 * 使用指名调用法：把光标所在函数调用 `f(a, b)` 改写为 `f(param1 = a, param2 = b)`。
 * 参数名从 LSP 符号签名取（detail 形如 fun add(a: Nat, b: Nat): Nat）。
 */
private fun namedCallOf(editor: CodeEditor, vm: CandyViewModel, line: Int, col: Int, word: String) {
    val content = editor.text
    val text = content.toString()
    val idx = runCatching { content.getCharIndex(line, col.coerceIn(0, content.getColumnCount(line))) }.getOrNull() ?: return
    // 调用括号范围：从标识符后向后找第一个 '('，配平到呼应的 ')'
    var p = idx
    while (p < text.length && text[p] != '(' && text[p] != '\n') p++
    if (p >= text.length || text[p] != '(') return vm.mdLogUser("**[指名调用]** 光标处不是函数调用")
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
    if (close < 0) return vm.mdLogUser("**[指名调用]** 找不到调用的右括号")
    val argsRaw = text.substring(open + 1, close).trim()
    if (argsRaw.isEmpty()) return vm.mdLogUser("**[指名调用]** 无实参，无需指名")
    // 拆分顶层实参（逗号配平）
    val args = mutableListOf<String>()
    var depth2 = 0
    var inStr = false
    val sb = StringBuilder()
    for (c in argsRaw) {
        when {
            c == '"' -> { inStr = !inStr; sb.append(c) }
            inStr -> sb.append(c)
            c == '(' || c == '[' -> { depth2++; sb.append(c) }
            c == ')' || c == ']' -> { depth2--; sb.append(c) }
            c == ',' && depth2 == 0 -> { args.add(sb.toString().trim()); sb.clear() }
            else -> sb.append(c)
        }
    }
    if (sb.isNotEmpty()) args.add(sb.toString().trim())
    // 从 LSP 取函数签名里的形参名序列
    val name = vm.currentFile.value?.name
    val sig = runCatching { vm.analysis()?.collectSymbols(name ?: return@runCatching null)
        ?.firstOrNull { it.name == word && it.variety == "fun" }?.detail }.getOrNull()
    val params = sig?.substringAfter('(', missingDelimiterValue = "")
        ?.substringBefore(')', missingDelimiterValue = "")
        ?.split(',')?.mapNotNull { seg ->
            val body = seg.trim().substringBefore(':').trim()
            // 去掉可变/注解前缀：只留最后一个标识符
            body.split(Regex("\\s+")).lastOrNull { it.isNotEmpty() && it.first().isLetter() }
        } ?: emptyList()
    // 内建函数（prelude 里，工程符号表没有）：print/println/printLn 一元
    val builtins = mapOf("print" to listOf("value"), "println" to listOf("value"), "printLn" to listOf("value"))
    if (params.isEmpty() && builtins.containsKey(word) && args.size == 1) {
        val rewritten2 = builtins[word]!![0] + " = " + args[0]
        runCatching {
            val batch2 = try { content.beginBatchEdit(); true } catch (_: Throwable) { false }
            try { content.replace(open + 1, close, rewritten2) }
            finally { if (batch2) content.endBatchEdit() }
            vm.onEditorTextChanged(content.toString())
            vm.saveCurrent(content.toString())
            vm.mdLogUser("**[指名调用]** " + word + "(" + rewritten2 + ")")
        }
        return
    }
    if (params.size != args.size) {
        return vm.mdLogUser("**[指名调用]** 签名与实参数不符（" + params.size + " 形参 vs " + args.size + " 实参），未改写")
    }
    val rewritten = params.zip(args).joinToString(", ") { (pn, av) -> pn + " = " + av }
    runCatching {
        val batch = try { content.beginBatchEdit(); true } catch (_: Throwable) { false }
        try {
            content.replace(open + 1, close, rewritten)
        } finally { if (batch) content.endBatchEdit() }
        vm.onEditorTextChanged(content.toString())
        vm.saveCurrent(content.toString())
        vm.mdLogUser("**[指名调用]** " + word + "(" + rewritten + ")")
    }.onFailure { vm.mdLogUser("**[指名调用]** 改写失败：" + (it.message ?: it.javaClass.simpleName)) }
}


/** 取编辑器当前光标处的词（灯泡的"重命名"快捷操作用） */
private fun wordAtCursorOf(editor: io.github.rosemoe.sora.widget.CodeEditor, line: Int, col: Int): String {
    val cur = editor.cursor
    if (cur.left != cur.right) {
        return runCatching { editor.text.substring(cur.left, cur.right) }.getOrDefault("")
    }
    return runCatching {
        if (line < 0 || line >= editor.lineCount) return@runCatching ""
        val row = editor.text.getLine(line)
        var l = (col - 1).coerceAtLeast(0)
        var r = col
        while (l > 0 && (row[l - 1].isLetterOrDigit() || row[l - 1] == '_')) l--
        while (r < row.length && (row[r].isLetterOrDigit() || row[r] == '_')) r++
        row.substring(l, r)
    }.getOrDefault("")
}
