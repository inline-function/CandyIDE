package candy.ide.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshot.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.terminal.TerminalBridge
import candy.ide.vm.CandyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TermBg = Color(0xFF14181C)
private val TermFg = Color(0xFFB8E6B0)
private val TermDim = Color(0xFF6E8B73)
private val TermAccent = Color(0xFF7FD1FF)
private val TermErr = Color(0xFFFF8A80)

/** 终端行：命令、输出、系统提示 */
data class TermLine(val text: String, val kind: Kind = Kind.OUT) {
    enum class Kind { CMD, OUT, SYS, ERR }
}

/**
 * 仿终端界面：黑底绿字 + 提示符输入行。
 * 实际通道由 TerminalBridge 决定（Shizuku 真执行 / Termux 意图降级）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: CandyViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<TermLine>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val proj = vm.currentProject.value?.name
        lines.add(TermLine("CandyIDE 终端 — shell 通道：Shizuku 优先，Termux 意图降级", TermLine.Kind.SYS))
        lines.add(TermLine(
            when {
                TerminalBridge.isTermuxInstalled(ctx) -> "Termux：已安装；Shizuku：" + shizukuHint()
                else -> "Termux：未安装（设置 → 伴生软件 下载）；Shizuku：" + shizukuHint()
            }, TermLine.Kind.SYS))
        if (proj != null) lines.add(TermLine("当前工程：" + proj, TermLine.Kind.SYS))
        lines.add(TermLine("敲命令试试：git status / pkg install git / ls", TermLine.Kind.SYS))
    }
    LaunchedEffect(lines.size) { listState.animateScrollToItem((lines.size - 1).coerceAtLeast(0)) }

    Scaffold(
        containerColor = TermBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(TermBg).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.screen.value = Screen.Editor }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TermFg)
                }
                Icon(Icons.Default.Terminal, contentDescription = null, tint = TermAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("终端", color = TermFg, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TermAccent, strokeWidth = 2.dp)
                TextButtonTiny("Termux", TerminalBridge.isTermuxInstalled(ctx)) { TerminalBridge.openTermux(ctx) }
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).background(TermBg),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                items(lines) { line ->
                    Text(
                        line.text,
                        color = when (line.kind) {
                            TermLine.Kind.CMD -> TermAccent
                            TermLine.Kind.OUT -> TermFg
                            TermLine.Kind.SYS -> TermDim
                            TermLine.Kind.ERR -> TermErr
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    )
                }
            }
            // 输入行
            Row(
                modifier = Modifier.fillMaxWidth().background(TermBg).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("candy$ ", color = TermAccent, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !busy,
                    textStyle = TextStyle(color = TermFg, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(TermAccent),
                    modifier = Modifier.weight(1f).onKeyEvent { e ->
                        if (e.key == Key.Enter && input.isNotBlank() && !busy) {
                            val cmd = input; input = ""
                            submit(ctx, scope, lines, cmd) { busy = it }
                            true
                        } else false
                    },
                )
                Spacer(Modifier.size(6.dp))
                Text("↵", color = TermDim, fontSize = 14.sp)
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun TextButtonTiny(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) TermAccent else TermDim,
        fontSize = 12.sp,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    )
}

private fun shizukuHint(): String =
    if (candy.ide.data.ShizukuManager.state() == candy.ide.data.ShizukuManager.State.RUNNING_GRANTED) "已授权，命令直连执行"
    else "未连接（设置里连接后命令直接在本终端执行）"

private fun submit(
    ctx: Context,
    scope: CoroutineScope,
    lines: SnapshotStateList<TermLine>,
    cmd: String,
    setBusy: (Boolean) -> Unit,
) {
    lines.add(TermLine("candy$ " + cmd, TermLine.Kind.CMD))
    setBusy(true)
    scope.launch {
        val res = withContext(Dispatchers.IO) { TerminalBridge.exec(ctx, cmd) }
        when (res.channel) {
            TerminalBridge.Channel.SHIZUKU -> {
                if (res.output.isNotBlank()) lines.add(TermLine(res.output, TermLine.Kind.OUT))
            }
            TerminalBridge.Channel.TERMUX -> {
                lines.add(TermLine(res.output, TermLine.Kind.SYS))
                // 轮询中转输出
                var waited = 0
                while (waited < 15000) {
                    delay(500); waited += 500
                    val out = TerminalBridge.readRelay()
                    if (out != null) {
                        lines.add(TermLine(out.ifBlank { "（执行完毕，无输出）" }, TermLine.Kind.OUT))
                        break
                    }
                }
            }
            else -> lines.add(TermLine(res.output, TermLine.Kind.ERR))
        }
        setBusy(false)
    }
}