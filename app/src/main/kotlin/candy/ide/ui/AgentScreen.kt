package candy.ide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    var showHistory by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    BackHandler { vm.screen.value = Screen.Editor }

    androidx.compose.material3.Scaffold(
        containerColor = CandyBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.screen.value = Screen.Editor }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                }
                Text("AI 助手", color = CandyText, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showHistory = true }) {
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
            // 阶段提示条：思考中 / 调用工具中 + 停止按钮
            if (sending || stage.isNotBlank()) {
                Surface(color = CandySurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CandyPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stage.ifBlank { "思考中…" }, color = CandyTextDim, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.stopAgent() }) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = CandyPrimary, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("停止", color = CandyPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(vm.chat) { msg -> ChatBubble(msg) { option -> input = option } }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !sending,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = {
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

    // 往期对话
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("往期对话") },
            text = {
                if (vm.conversations.isEmpty()) {
                    Text("还没有存档的对话", color = CandyTextDim)
                } else {
                    Column {
                        vm.conversations.forEach { conv ->
                            val title = conv.title.ifBlank { "对话" }
                            val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(conv.time))
                            Surface(
                                color = CandySurface,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                onClick = { showHistory = false; vm.restoreConversation(ctx, conv) },
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(title, color = CandyText, fontSize = 13.sp, maxLines = 1)
                                    Text(time + " · " + conv.messages.size + " 条", color = CandyTextDim, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("关闭") } },
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
                    InfoLine("路径", proj?.absolutePath ?: "-")
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

@Composable
private fun ChatBubble(msg: ChatMessage, onPickOption: (String) -> Unit) {
    val isUser = msg.role == ChatMessage.Role.USER
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
            Column(Modifier.padding(10.dp)) {
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
                Text(msg.text, color = CandyText, fontSize = 13.sp)
                // 模型给出选项（【选项：A|B|C】）：渲染成可点按钮，点了填入输入框
                val options = parseOptions(msg.text)
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

/** 模型若用 【选项：A|B|C】 标记，解析成选项列表 */
private fun parseOptions(text: String): List<String>? {
    val m = OPTION_TAG.find(text) ?: return null
    val list = m.groupValues[1].split("|").map { it.trim() }.filter { it.isNotEmpty() }
    return if (list.size in 2..6) list else null
}