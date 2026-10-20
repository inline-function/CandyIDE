package candy.ide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    LaunchedEffect(vm.chat.size) {
        if (vm.chat.isNotEmpty()) listState.animateScrollToItem(vm.chat.size - 1)
    }

    BackHandler { vm.screen.value = Screen.Editor }

    androidx.compose.material3.Scaffold(
        containerColor = CandyBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.screen.value = Screen.Editor }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                }
                Text("AI 助手", color = CandyText, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CandyPrimary, strokeWidth = 2.dp)
                }
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(vm.chat) { msg ->
                    ChatBubble(msg)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !sending,
                    placeholder = { Text("问点关于 Sublimation 的…", color = CandyTextDim, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = CandyText, fontSize = 14.sp),
                )
                Spacer(Modifier.size(8.dp))
                IconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isNotEmpty() && !sending) {
                            input = ""
                            vm.askAgent(ctx, text)
                        }
                    },
                    enabled = !sending && input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = CandyPrimary)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) candy.ide.ui.theme.CandyPrimary.copy(alpha = 0.22f)
                    else if (msg.role == ChatMessage.Role.TOOL) candy.ide.ui.theme.CandySurfaceVariant
                    else candy.ide.ui.theme.CandySurface,
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
            }
        }
    }
}