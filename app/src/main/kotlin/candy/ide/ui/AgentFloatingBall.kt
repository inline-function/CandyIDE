package candy.ide.ui

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
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
    var winOffsetX by remember { mutableStateOf(0f) }
    var winOffsetY by remember { mutableStateOf(0f) }
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
                                        if (!dragging) {
                                            // 就在球的位置展开窗（右边/底边与球对齐）
                                            winOffsetX = offsetX
                                            winOffsetY = -offsetY
                                            expanded = true
                                        }
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
            // 展开态：迷你聊天窗（可拖动；尺寸约为全宽的一半）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(winOffsetX.toInt(), winOffsetY.toInt()) },
            ) {
                MiniChatWindow(
                vm = vm,
                onDrag = { dx, dy ->
                    winOffsetX += dx
                    winOffsetY += dy
                },
                onFullScreen = {
                    expanded = false
                    offsetX = winOffsetX
                    offsetY = -winOffsetY
                    vm.screen.value = Screen.Agent
                },
                onClose = {
                    expanded = false
                    // 窗在哪收起，球就出现在哪
                    offsetX = winOffsetX
                    offsetY = -winOffsetY
                },
                )
            }
        }
    }
}

@Composable
private fun MiniChatWindow(
    vm: CandyViewModel,
    onDrag: (Float, Float) -> Unit,
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
            .fillMaxWidth(0.46f)
            .height(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FloatBg)
            .padding(10.dp),
    ) {
        // 标题行：模型名 / 全屏 / 关闭（按住「助手」标识拖动整窗；右侧按钮不被拖动拦截）
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { }, 
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        },
                    )
                },
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = BubbleColor, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(5.dp))
                Text("助手", color = Color.White, fontSize = 11.sp)
                // 发送中：助手旁显示状态 + 转圈（思考中/阅读中/编辑中/操作中）
                if (sending) {
                    Spacer(Modifier.size(5.dp))
                    CircularProgressIndicator(modifier = Modifier.size(10.dp), color = BubbleColor, strokeWidth = 1.5.dp)
                    Spacer(Modifier.size(3.dp))
                    Text(statusLabel(vm), color = BubbleColor, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "全屏",
                tint = FloatDim,
                modifier = Modifier.size(20.dp).ballClick(onClick = onFullScreen)
            )
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = FloatDim,
                modifier = Modifier.size(20.dp).ballClick(onClick = onClose)
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
                item {
                    Column {
                        Text("问点什么，或试试点这些：", color = FloatDim, fontSize = 12.sp)
                        Spacer(Modifier.size(4.dp))
                        QuickChip("当前工作区有什么文件？") { input = it }
                        QuickChip("检查一下编译诊断") { input = it }
                        QuickChip("解释这段代码") { input = it }
                    }
                }
            }
            items(vm.chat) { msg -> MiniBubble(msg) }
            if (sending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = BubbleColor, strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp))
                        Text("正在思考…", color = FloatDim, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        // 输入行
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 自绘输入框：Material TextField 的默认内边距在悬浮窗里太大，
            // 直接用 BasicTextField 控制内边距与高度（26dp 贴合悬浮窗比例）
            // 原生 EditText：Compose TextField 的最小高度（48dp）在悬浮窗里太大，
            // 直接用 AndroidView 精确控制 28dp 高度与内边距，避免文字被裁
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(28.dp),
                factory = { c ->
                    object : android.widget.EditText(c) {
                        override fun onTextContextMenuItem(id: Int): Boolean = false
                    }.apply {
                        setBackground(null)
                        setPadding(0, 0, 0, 0)
                        textSize = 11f
                        setTextColor(android.graphics.Color.WHITE)
                        setHintTextColor(android.graphics.Color.parseColor("#7A6CA8"))
                        hint = "消息…"
                        maxLines = 1
                        inputType = android.text.InputType.TYPE_CLASS_TEXT
                        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                                val text = text.toString().trim()
                                val st = vm.agentBusy.value
                                if (text.isNotEmpty() && !st) {
                                    setText("")
                                    vm.askAgent(c, text)
                                }
                            }
                            true
                        }
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                val v = s?.toString() ?: ""
                                if (v != input) input = v
                            }
                        })
                    }
                },
                update = { et ->
                    if (et.text.toString() != input) {
                        et.keepScreenOn = true
                        et.setText(input)
                        et.setSelection(input.length)
                    }
                },
            )
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (sending) FloatDim else BubbleColor)
                    .ballClick {
                        if (sending) {
                            // 生成中再点 = 终止这一轮
                            vm.stopAgent()
                        } else {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                input = ""
                                vm.askAgent(ctx, text)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (sending) "■" else "➤", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

/** 把 vm.agentStage（思考中…/调用工具 xxx…）映射成短状态：思考中/阅读中/编辑中/操作中 */
private fun statusLabel(vm: CandyViewModel): String {
    val stage = vm.agentStage.value
    if (stage.isBlank()) return "思考中"
    val tool = stage.removePrefix("调用工具 ").removeSuffix("…").trim()
    return when (tool) {
        "read_current_file", "read_project_file", "list_project_files", "list_diagnostics", "get_project_tree" -> "阅读中"
        "write_current_file" -> "编辑中"
        "run_shell" -> "操作中"
        else -> "思考中"
    }
}

/** 快捷指令小条：点一下把文字填进输入框 */
@Composable
private fun QuickChip(label: String, onPick: (String) -> Unit) {
    Text(
        label,
        color = BubbleColor,
        fontSize = 11.sp,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .ballClick { onPick(label) }
    )
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
/** 在悬浮窗里替代 Modifier.clickable：手动检测 Press+Release（clickable 的事件链在悬浮窗里会被打断） */
@Composable
fun Modifier.ballClick(onClick: () -> Unit): Modifier = this.then(
    // 按下即触发：悬浮窗里 click 的 down→up 序列会被 IME 开关引发的重组打断
    Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                if (e.type == PointerEventType.Press) {
                    e.changes.firstOrNull()?.consume()
                    onClick()
                }
            }
        }
    },
)

