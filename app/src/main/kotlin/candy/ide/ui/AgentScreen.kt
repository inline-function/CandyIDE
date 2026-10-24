package candy.ide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.data.ShizukuManager
import candy.ide.terminal.TerminalBridge
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel
import candy.ide.vm.ChatMessage
/** AI agent 聊天页：气泡列表 + 输入框，底层是 ADK + OpenAI 兼容端点 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(vm: CandyViewModel) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val sending = vm.agentBusy.value
    val stage = vm.agentStage.value

    LaunchedEffect(Unit) { vm.loadConversations(ctx) }
    LaunchedEffect(vm.chat.size) {
        if (vm.chat.isNotEmpty()) listState.animateScrollToItem(vm.chat.size - 1)
    }

    var showInfo by remember { mutableStateOf(false) }
    // 输入框附件：文件（SAF）/ 图片（相册）选择
    var attachPicker by remember { mutableStateOf(false) }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = runCatching { ctx.contentResolver.query(uri, null, null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null } }
                .getOrNull() ?: uri.lastPathSegment ?: "文件"
            val cur = input.trim()
            input = if (cur.isEmpty()) "看一下 $name" else cur + "\n看一下 $name"
        }
        attachPicker = false
    }
    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val cur = input.trim()
            val tag = "[图片]"
            input = if (cur.isEmpty()) tag else cur + "\n" + tag
        }
        attachPicker = false
    }
    // 往期对话右侧抽屉
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 抽屉内行内操作：重命名 / 删除 / 置顶（确认弹窗）
    var renameConv by remember { mutableStateOf<candy.ide.vm.AgentConversation?>(null) }
    var deleteConv by remember { mutableStateOf<candy.ide.vm.AgentConversation?>(null) }

    BackHandler {
        // 抽屉开着先关抽屉，否则退出助手页
        if (drawerState.isOpen) scope.launch { drawerState.close() } else vm.screen.value = Screen.Editor
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("往期对话", color = CandyText, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = {
                        scope.launch { drawerState.close() }
                        vm.newAgentChat(ctx)
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "新建会话", tint = CandySecondary)
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = CandySurfaceVariant)
                if (vm.conversations.isEmpty()) {
                    Text("还没有存档的对话", color = CandyTextDim, modifier = Modifier.padding(16.dp))
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(vm.conversations.sortedByDescending { it.pinned }) { conv ->
                            ConversationRow(
                                vm = vm,
                                ctx = ctx,
                                conv = conv,
                                onOpen = { scope.launch { drawerState.close() }; vm.restoreConversation(ctx, conv) },
                                onRename = { renameConv = conv },
                                onDelete = { deleteConv = conv },
                            )
                        }
                    }
                }
            }
        },
    ) {
    androidx.compose.material3.Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = CandyBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp).statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.screen.value = Screen.Editor }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                }
                Text("助手", color = CandyText, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.History, contentDescription = "往期对话", tint = CandySecondary)
                }
                IconButton(onClick = { vm.newAgentChat(ctx) }) {
                    Icon(Icons.Default.Add, contentDescription = "新建对话", tint = CandySecondary)
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Default.Info, contentDescription = "工作区与权限", tint = CandyTextDim)
                }
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(vm.chat) { msg -> ChatBubble(msg) { option -> input = option } }
            }
            // token 用量进度条（编辑框上方）
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val maxTokens = 64000
                val used = vm.agentTokens.intValue.coerceAtLeast(0)
                val ratio = (used.toFloat() / maxTokens).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.weight(1f).height(3.dp),
                    color = if (ratio > 0.8f) candy.ide.ui.theme.CandyPrimary else candy.ide.ui.theme.CandyTextDim,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "可用 " + (maxTokens - used) + " token",
                    color = candy.ide.ui.theme.CandyTextDim,
                    fontSize = 10.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 访问权：只读 / 当前工程 / 当前IDE目录 / 完全访问
                var scopeMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { scopeMenu = true }) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "访问权",
                            tint = candy.ide.ui.theme.CandyPrimary,
                        )
                    }
                    DropdownMenu(expanded = scopeMenu, onDismissRequest = { scopeMenu = false }) {
                        candy.ide.agent.CandyToolRegistry.AgentScope.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = {
                                    candy.ide.data.Settings.agentScope = s.key
                                    candy.ide.data.Settings.persist(ctx)
                                    scopeMenu = false
                                },
                            )
                        }
                    }
                }
                // 附件（文件 / 图片）：点开选择方式
                IconButton(onClick = { attachPicker = true }, enabled = !sending) {
                    Icon(Icons.Default.AttachFile, contentDescription = "添加附件", tint = CandyTextDim)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !sending,
                    // 单行：IME 回车才走 IME_ACTION_SEND（否则部分输入法插换行）
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                        autoCorrect = false,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                        val text = input.trim()
                        if (text.isNotEmpty() && !sending) { input = ""; vm.askAgent(ctx, text) }
                    }),
                    placeholder = { Text("问点关于 Sublimation 的…", color = CandyTextDim, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = CandyText, fontSize = 14.sp),
                )
                Spacer(Modifier.size(8.dp))
                if (sending) {
                    IconButton(onClick = { vm.stopAgent() }) {
                        Icon(Icons.Default.Stop, contentDescription = "停止", tint = CandyPrimary)
                    }
                } else {
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) { input = ""; vm.askAgent(ctx, text) }
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = CandyPrimary)
                    }
                }
            }
            Spacer(Modifier.size(2.dp))
        }
    }
    }
    // 附件选择：文件（SAF）/ 图片（相册）
    if (attachPicker) {
        AlertDialog(
            onDismissRequest = { attachPicker = false },
            title = { Text("添加附件") },
            text = { Text("选择要发给助手的文件或图片（文件路径会填入输入框）") },
            confirmButton = {
                TextButton(onClick = { pickFile.launch(arrayOf("*/*")) }) { Text("选择文件") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pickImage.launch("image/*") }) { Text("相册图片") }
                    TextButton(onClick = { attachPicker = false }) { Text("取消") }
                }
            },
        )
    }

    // 重命名会话
    renameConv?.let { conv ->
        var name by remember(conv) { androidx.compose.runtime.mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renameConv = null },
            title = { Text("重命名会话") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("会话标题") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        val i = vm.conversations.indexOf(conv)
                        if (i >= 0) vm.conversations[i] = conv.copy(title = n)
                        vm.saveConversations(ctx)
                    }
                    renameConv = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameConv = null }) { Text("取消") } },
        )
    }

    // 删除会话确认
    deleteConv?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleteConv = null },
            title = { Text("删除会话") },
            text = { Text("删除「" + conv.title.ifBlank { "对话" } + "」？" + conv.messages.size + " 条消息将无法恢复") },
            confirmButton = {
                TextButton(onClick = {
                    vm.conversations.remove(conv)
                    vm.saveConversations(ctx)
                    deleteConv = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteConv = null }) { Text("取消") } },
        )
    }

    // 工作区 / 访问权限 / 可用命令
    if (showInfo) {
        val proj = vm.currentProject.value
        val shizuku = when (ShizukuManager.state()) {
            ShizukuManager.State.RUNNING_GRANTED -> "已连接并授权（命令直连执行）"
            ShizukuManager.State.RUNNING_NOT_GRANTED -> "已连接，未授权"
            ShizukuManager.State.PRE_V11 -> "版本过低（需 v11+）"
            ShizukuManager.State.NOT_RUNNING -> "未运行"
            null -> "检测中…"
        }
        val termux = if (TerminalBridge.isTermuxInstalled(ctx)) {
            if (TerminalBridge.hasRunCommandGrant(ctx)) "已安装，RUN_COMMAND 已授权" else "已安装，RUN_COMMAND 未授权"
        } else "未安装"
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("工作区 / 权限 / 命令") },
            text = {
                Column {
                    InfoLine("工作区", proj?.name ?: "未打开工程")
                    InfoLine("路径", proj?.key ?: "-")
                    InfoLine("当前文件", vm.currentFile.value?.name ?: "无")
                    Spacer(Modifier.size(6.dp))
                    InfoLine("Shizuku", shizuku)
                    InfoLine("Termux", termux)
                    Spacer(Modifier.size(6.dp))
                    Text("可用命令", color = CandyText, fontSize = 12.sp)
                    Text("read_current_file · read_project_file · write_current_file · run_shell · list_project_files · list_diagnostics · get_project_tree", color = CandyTextDim, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun InfoLine(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(name, color = CandyTextDim, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Text(value, color = CandyText, fontSize = 12.sp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(msg: ChatMessage, onPickOption: (String) -> Unit) {
    val isUser = msg.role == ChatMessage.Role.USER
    val bubbleCtx = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) CandyPrimary.copy(alpha = 0.22f)
                    else if (msg.role == ChatMessage.Role.TOOL) CandySurfaceVariant
                    else CandySurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(
            modifier = Modifier.padding(10.dp).combinedClickable(
                enabled = true,
                onClick = {},
                onDoubleClick = {
                    val cm = bubbleCtx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("candy", msg.text))
                    android.widget.Toast.makeText(
                        bubbleCtx, "已复制", android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            ),
        ) {
                if (msg.role != ChatMessage.Role.TOOL) {
                    Text(
                        when (msg.role) {
                            ChatMessage.Role.USER -> "我"
                            ChatMessage.Role.MODEL -> "助手"
                            ChatMessage.Role.TOOL -> "工具调用"
                        },
                        color = if (isUser) CandyPrimary else CandySecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.size(4.dp))
                }
                // 思考链气泡：reasoning_content 实时增长（加载图标 + 末尾若干行），完成后折叠为「思考过程」
                if (msg.reasoning) {
                    var expanded by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (msg.thinking) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CandyPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.size(6.dp))
                            Text("思考中…", color = CandyTextDim, fontSize = 12.sp)
                        } else {
                            Text(if (expanded) "▼ 思考过程" else "▶ 思考过程", color = CandyTextDim, fontSize = 11.sp,
                                modifier = Modifier.ballClick { expanded = !expanded })
                        }
                    }
                    // 进行中：直接显示链条（限高，随增长刷新，证明没卡死）；完成后折叠、点开看全文
                    if (msg.thinking || expanded) {
                        Spacer(Modifier.size(4.dp))
                        Text(msg.text, color = CandyTextDim, fontSize = 11.sp,
                            maxLines = if (msg.thinking) 16 else 60,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                // 思考中： ConversationFlow 里的占位气泡（加载图标 + 阶段文案）
                else if (msg.thinking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CandyPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp))
                        Text(msg.summary().ifBlank { "思考中…" }, color = CandyTextDim, fontSize = 12.sp)
                    }
                } else if (msg.toolName != null) {
                    // 工具气泡：默认折叠（摘要 + 左侧展开按钮），展开显示参数与结果
                    var expanded by remember { mutableStateOf(!msg.collapsed) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (expanded) "▼" else "▶",
                            color = CandyTextDim,
                            fontSize = 10.sp,
                            modifier = Modifier.ballClick { expanded = !expanded; msg.collapsed = !expanded },
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(msg.summary(), color = if (msg.done) CandyTextDim else CandyPrimary, fontSize = 12.sp,
                            modifier = Modifier.ballClick { expanded = !expanded; msg.collapsed = !expanded })
                    }
                    if (expanded) {
                        Spacer(Modifier.size(4.dp))
                        Text(msg.text, color = CandyTextDim, fontSize = 11.sp)
                    }
                } else if (msg.role == ChatMessage.Role.MODEL) {
                    // 助手正文按 markdown 渲染（粗体/列表/代码块）
                    MarkdownText(msg.text)
                } else {
                    Text(msg.text, color = CandyText, fontSize = 13.sp)
                }
                // 模型用 JSON 提问（真问题）→ 对话框形式呈现问题与选项
                val question = parseQuestion(msg.text)
                var answered by remember { mutableStateOf(false) }
                if (question != null) {
                    Spacer(Modifier.size(6.dp))
                    TextButton(onClick = { answered = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("模型提了个问题，点击查看", color = CandyPrimary, fontSize = 12.sp)
                    }
                    if (question != null && answered) {
                        AlertDialog(
                            onDismissRequest = { answered = false },
                            title = { Text(question.question) },
                            text = {
                                Column {
                                    question.options.forEach { opt ->
                                        TextButton(
                                            onClick = { answered = false; onPickOption(opt) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text(opt, color = CandyPrimary, fontSize = 13.sp) }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { answered = false }) { Text("稍后再答") }
                            },
                        )
                    }
                }
                // 模型给出选项（【选项：A|B|C】）：渲染成可点按钮，点了填入输入框
                val options = if (question == null) parseOptions(msg.text) else null
                if (options != null) {
                    Spacer(Modifier.size(6.dp))
                    options.forEach { opt ->
                        TextButton(onClick = { onPickOption(opt) }, modifier = Modifier.fillMaxWidth()) {
                            Text(opt, color = CandyPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private val OPTION_TAG = Regex("【选项[：:](.+?)】")

/** 结构化提问：JSON 形式的「问题 + 选项」 */
private data class ModelQuestion(val question: String, val options: List<String>)

/** 模型若用 【选项：A|B|C】 标记，解析成选项列表 */
private fun parseOptions(text: String): List<String>? {
    val m = OPTION_TAG.find(text) ?: return null
    val list = m.groupValues[1].split("|").map { it.trim() }.filter { it.isNotEmpty() }
    return if (list.size in 2..6) list else null
}

/**
 * 模型用 JSON 提问时解析成「问题 + 选项」（必须是真问题：含问号或疑问词且给了选项）。
 * 话题引导语句（「我们来看看……」「接下来聊聊……」一类）不匹配，不弹窗。
 */
private fun parseQuestion(text: String): ModelQuestion? = runCatching {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return@runCatching null
    val json = org.json.JSONObject(trimmed)
    val q = json.optString("question").ifBlank { json.optString("q") }.trim()
    val rawOptions = json.optJSONArray("options") ?: json.optJSONArray("choices")
    if (q.isBlank() || rawOptions == null || rawOptions.length() !in 2..6) return@runCatching null
    // 只要真问题：以问号结尾或含疑问词；排除「我们继续/来聊聊」式引导
    val isQuestion = q.endsWith("？") || q.endsWith("?") ||
        listOf("吗", "哪个", "哪种", "要不要", "是否", "选择", "想要").any { q.contains(it) }
    if (!isQuestion) return@runCatching null
    val opts = (0 until rawOptions.length()).mapNotNull { i ->
        (rawOptions.opt(i) as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
    if (opts.size !in 2..6) return@runCatching null
    ModelQuestion(q, opts)
}.getOrNull()

private fun agentStatusLabel(stage: String): String {
    if (stage.isBlank()) return "思考中"
    val tool = stage.removePrefix("调用工具 ").removeSuffix("…").trim()
    return when (tool) {
        "read_current_file", "read_project_file", "list_project_files", "list_diagnostics", "get_project_tree" -> "阅读中"
        "write_current_file" -> "编辑中"
        "run_shell" -> "操作中"
        else -> "思考中"
    }
}

/** 往期对话抽屉行：标题/时间+消息数，行内 置顶/重命名/删除 */
@Composable
private fun ConversationRow(
    vm: CandyViewModel,
    ctx: android.content.Context,
    conv: candy.ide.vm.AgentConversation,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(conv.time))
    androidx.compose.material3.Surface(
        color = if (conv.pinned) candy.ide.ui.theme.CandySurfaceVariant else candy.ide.ui.theme.CandySurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    (if (conv.pinned) "★ " else "") + conv.title.ifBlank { "对话" },
                    color = candy.ide.ui.theme.CandyText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(time + " · " + conv.messages.size + " 条", color = candy.ide.ui.theme.CandyTextDim, fontSize = 11.sp)
            }
            androidx.compose.material3.IconButton(onClick = {
                conv.pinned = !conv.pinned
                vm.saveConversations(ctx)
                // 元素内字段改动不触发列表重组：挪动一下让 Snapshot 刷新顺序
                val i = vm.conversations.indexOf(conv)
                if (i >= 0) { vm.conversations.removeAt(i); vm.conversations.add(i, conv) }
            }) {
                Icon(Icons.Default.Star, contentDescription = "置顶", tint = if (conv.pinned) candy.ide.ui.theme.CandySecondary else candy.ide.ui.theme.CandyTextDim)
            }
            androidx.compose.material3.IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "重命名", tint = candy.ide.ui.theme.CandyTextDim)
            }
            androidx.compose.material3.IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = candy.ide.ui.theme.CandyTextDim)
            }
        }
    }
}