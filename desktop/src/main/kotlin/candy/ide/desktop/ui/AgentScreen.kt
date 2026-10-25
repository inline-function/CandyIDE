package candy.ide.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.DesktopScreen
import candy.ide.desktop.IdeState
import candy.ide.desktop.agent.AgentConversation
import candy.ide.desktop.agent.AgentState
import candy.ide.desktop.agent.ChatMessage
import candy.ide.desktop.Settings
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import kotlinx.coroutines.launch

/**
 * AI 助手页（对应安卓端 AgentScreen）：气泡列表 + 输入框 + 往期对话抽屉。
 * 宿主自行决定返回行为：主页里回主页，编辑器窗口里回编辑器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    state: IdeState,
    agent: AgentState,
    onBack: () -> Unit,
    onTapRun: (() -> Unit)? = null,
    onGotoLine: ((Int) -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val sending = agent.busy
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var manageConv by remember { mutableStateOf<AgentConversation?>(null) }

    LaunchedEffect(Unit) { agent.loadConversations() }
    LaunchedEffect(agent.chat.size) {
        if (agent.chat.isNotEmpty()) listState.animateScrollToItem(agent.chat.size - 1)
    }

    fun send() {
        val t = input.trim()
        if (t.isBlank() || sending) return
        input = ""
        agent.ask(t, state, onTapRun, onGotoLine)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = CandySurface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("往期对话", color = CandyText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { drawerState.close() }; agent.newChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "新建会话", tint = CandySecondary)
                    }
                }
                HorizontalDivider(color = CandySurfaceVariant)
                if (agent.conversations.isEmpty()) {
                    Text("还没有存档的对话", color = CandyTextDim, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(agent.conversations.sortedByDescending { it.pinned }) { conv ->
                            ConversationRow(
                                conv = conv,
                                onOpen = { scope.launch { drawerState.close() }; agent.restore(conv) },
                                onManage = { manageConv = conv },
                            )
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = CandyBg,
            topBar = {
                TopAppBar(
                    title = { Text("助手", color = CandyText) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                        }
                    },
                    actions = {
                        Text(
                            if (agent.tokens > 0) (agent.tokens / 1000).toString() + "k" else "",
                            color = CandyTextDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.History, contentDescription = "往期对话", tint = CandySecondary)
                        }
                        IconButton(onClick = { agent.newChat() }) {
                            Icon(Icons.Default.Add, contentDescription = "新建对话", tint = CandySecondary)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = CandyTextDim)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("端点信息") }, onClick = { menu = false; showInfo = true })
                                DropdownMenuItem(text = { Text("AI 设置") }, onClick = { menu = false; onOpenSettings() })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (sending && agent.stage.isNotBlank()) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = CandyPrimary,
                        trackColor = CandySurfaceVariant,
                    )
                    Text(
                        agent.stage,
                        color = CandyTextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(agent.chat) { m -> Bubble(m) }
                }
                HorizontalDivider(color = CandySurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).widthIn(max = 720.dp),
                        label = { Text("问助手（当前工程/文件可直接读）", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = CandyText),
                        maxLines = 5,
                    )
                    if (sending) {
                        IconButton(onClick = { agent.stop() }) {
                            Icon(Icons.Default.Stop, contentDescription = "停止", tint = CandyError)
                        }
                    } else {
                        IconButton(onClick = { send() }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = CandyPrimary)
                        }
                    }
                }
            }
        }
    }
    if (showInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("知道了") } },
            title = { Text("端点信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("端点：" + Settings.agentBaseUrl, color = CandyText, fontSize = 12.sp)
                    Text("模型：" + Settings.agentModel, color = CandyText, fontSize = 12.sp)
                    Text("访问权：" + AgentScopeLabel(), color = CandyText, fontSize = 12.sp)
                    Text("流式输出：" + (if (Settings.agentStream) "开" else "关"), color = CandyTextDim, fontSize = 12.sp)
                }
            },
        )
    }
    manageConv?.let { conv ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { manageConv = null },
            title = { Text("对话：" + conv.title) },
            text = { Text("置顶 / 删除 / 重命名（重命名暂未做，可删除后重建）", color = CandyTextDim, fontSize = 12.sp) },
            confirmButton = {
                Row {
                    TextButton(onClick = { agent.togglePin(conv); manageConv = null }) { Text("置顶/取消") }
                    TextButton(onClick = { agent.deleteConversation(conv); manageConv = null }) { Text("删除", color = CandyError) }
                }
            },
            dismissButton = { TextButton(onClick = { manageConv = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AgentScopeLabel(): String {
    val s = candy.ide.desktop.agent.AgentTools.AgentScope.entries.firstOrNull { it.key == Settings.agentScope }
    return s?.label ?: Settings.agentScope
}

@Composable
private fun ConversationRow(
    conv: AgentConversation,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(conv.time))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (if (conv.pinned) "★ " else "") + conv.title,
                color = CandyText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(time + " · " + conv.messages.size + " 条", color = CandyTextDim, fontSize = 10.sp)
        }
        TextButton(onClick = onOpen) { Text("打开", fontSize = 12.sp) }
        TextButton(onClick = onManage) { Text("管理", fontSize = 12.sp) }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val bg = when (m.role) {
        ChatMessage.Role.USER -> CandyPrimary
        ChatMessage.Role.MODEL -> CandySurface
        ChatMessage.Role.TOOL -> CandySurfaceVariant
    }
    val fg = if (m.role == ChatMessage.Role.USER) CandyBg else CandyText
    val align = if (m.role == ChatMessage.Role.USER) Alignment.End else Alignment.Start
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            if (m.thinking) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = CandySecondary)
                    Text(
                        if (m.role == ChatMessage.Role.TOOL) ("正在调用 " + (m.toolName ?: "") + "…") else "思考中…",
                        color = CandyTextDim,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Text(
                    m.text,
                    color = fg,
                    fontSize = 13.sp,
                    fontFamily = if (m.role == ChatMessage.Role.TOOL) FontFamily.Monospace else null,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
