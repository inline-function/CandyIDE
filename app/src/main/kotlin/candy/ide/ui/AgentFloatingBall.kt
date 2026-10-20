package candy.ide.ui

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.data.Settings
import candy.ide.vm.CandyViewModel
import candy.ide.vm.ChatMessage

private val BubbleColor = Color(0xFFE879F9)
private val FloatBg = Color(0xFF1E1F26)
private val FloatDim = Color(0xFF9A97A6)

/**
 * agent 悬浮球：
 *  - 收起态：小圆球（可拖动）
 *  - 展开态：迷你聊天窗（气泡列表 + 输入框 + 全屏按钮）
 *  - 全屏：进 AgentScreen（App 内页面，不是悬浮层）
 */
@Composable
fun AgentFloatingBall(vm: CandyViewModel) {
    // 订阅 Settings 的可观察状态：开关一变（即便 attach 晚于组合）也会重组
    val enabled by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { Settings.agentEnabled } }
    if (!enabled) return
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(240f) }
    var dragging by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!expanded) {
            // 收起态：悬浮小球
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), -offsetY.toInt()) }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BubbleColor.copy(alpha = 0.9f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                when (e.type) {
                                    PointerEventType.Press -> dragging = false
                                    PointerEventType.Move -> {
                                        val change = e.changes.first()
                                        offsetX += change.position.x - change.previousPosition.x
                                        offsetY -= change.position.y - change.previousPosition.y
                                        change.consume()
                                        dragging = true
                                    }
                                    PointerEventType.Release -> {
                                        if (!dragging) expanded = true
                                    }
                                    else -> {}
                                }
                            }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = "AI 助手", tint = Color.White, modifier = Modifier.size(26.dp))
                }
        } else {
            // 展开态：迷你聊天窗（贴右下，避开输入法）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding(),
            ) {
                MiniChatWindow(
                vm = vm,
                onFullScreen = {
                    expanded = false
                    vm.screen.value = Screen.Agent
                },
                onClose = { expanded = false },
                )
            }
        }
    }
}

@Composable
private fun MiniChatWindow(
    vm: CandyViewModel,
    onFullScreen: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val sending = vm.agentBusy.value

    LaunchedEffect(vm.chat.size) {
        if (vm.chat.isNotEmpty()) listState.animateScrollToItem(vm.chat.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(340.dp)
            .imePadding()
            .clip(RoundedCornerShape(14.dp))
            .background(FloatBg)
            .padding(10.dp),
    ) {
        // 标题行：模型名 / 全屏 / 关闭
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = BubbleColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(Settings.agentModel, color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "全屏",
                tint = FloatDim,
                modifier = Modifier.size(20.dp).clickable(onClick = onFullScreen),
            )
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = FloatDim,
                modifier = Modifier.size(20.dp).clickable(onClick = onClose),
            )
        }
        Spacer(Modifier.size(6.dp))
        // 气泡列表
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (vm.chat.isEmpty()) {
                item { Text("问点关于 Sublimation 的…", color = FloatDim, fontSize = 12.sp) }
            }
            items(vm.chat) { msg -> MiniBubble(msg) }
        }
        Spacer(Modifier.size(6.dp))
        // 输入行
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                enabled = !sending,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty() && !sending) {
                        input = ""
                        vm.askAgent(ctx, text)
                    }
                }),
                placeholder = { Text("消息…", color = FloatDim, fontSize = 12.sp) },
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            )
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BubbleColor)
                    .clickable {
                        val text = input.trim()
                        if (text.isNotEmpty() && !sending) {
                            input = ""
                            vm.askAgent(ctx, text)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("➤", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MiniBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            msg.text,
            color = if (isUser) Color.White else Color(0xFFD5D2E0),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isUser) BubbleColor.copy(alpha = 0.35f) else Color(0xFF2A2C36))
                .padding(8.dp),
        )
    }
}