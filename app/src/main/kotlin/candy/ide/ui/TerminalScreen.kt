package candy.ide.ui

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

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
    val focusReq = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusReq.requestFocus() } }
    var busy by remember { mutableStateOf(false) }
    // 当前工作目录：cd 切换、提示符显示、命令执行前缀
    var cwd by remember { mutableStateOf(vm.currentProject.value?.displayPath ?: "/sdcard") }
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
        lines.add(TermLine("目录：" + cwd + "（cd 切换）", TermLine.Kind.SYS))
        lines.add(TermLine("敲命令试试：git status / pkg install git / ls", TermLine.Kind.SYS))
        // Termux 已装但 RUN_COMMAND 通道未通：引导开启（Termux 默认不允许外部应用调用）
        if (TerminalBridge.isTermuxInstalled(ctx) &&
            !candy.ide.git.GitManager.runCommandReady(ctx)
        ) {
            vm.termuxSetupNeeded.value = true
        }
    }

    // 从主页终端按钮进来时 currentProject 为空 → 回主页；否则回编辑页
    BackHandler {
        vm.screen.value = if (vm.currentProject.value != null) Screen.Editor else Screen.Projects
    }

    Scaffold(
        containerColor = TermBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(TermBg).padding(8.dp).statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
        vm.screen.value = if (vm.currentProject.value != null) Screen.Editor else Screen.Projects
    }) {
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
                verticalArrangement = Arrangement.spacedBy(1.dp),
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
                // 输入行：跟在内容流后面（不是固定在最底下）
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            promptText(cwd, homeDir(ctx)),
                            color = TermAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.35f, fill = false),
                        )
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                val cmd = input.trim()
                                if (cmd.isNotEmpty() && !busy) { input = ""; submitCmd(ctx, scope, lines, cmd, cwd, busy, { busy = it }, { cwd = it }) { vm.syncAfterFsChange() } }
                            }),
                            singleLine = true,
                            textStyle = TextStyle(color = TermFg, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(TermAccent),
                            modifier = Modifier.weight(1f).focusRequester(focusReq).onKeyEvent { e ->
                                if (e.key == Key.Enter && input.isNotBlank() && !busy) {
                                    val cmd = input; input = ""
                                    submitCmd(ctx, scope, lines, cmd, cwd, busy, { busy = it }, { cwd = it })
                                    true
                                } else false
                            },
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("↵", color = TermDim, fontSize = 14.sp)
                    }
                }
            }
            // 底部输入框聚焦句柄：保持 IME 弹起时输入行可见
            LaunchedEffect(lines.size, busy) {
                listState.animateScrollToItem((lines.size).coerceAtLeast(0))
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

private fun homeDir(ctx: android.content.Context): String {
    // 默认目录 = 用户选择的 IDE 目录（SAF 存储目录）的真实路径；拿不到才回退 Termux 家目录
    val root = runCatching { candy.ide.fs.CandyFs.projectRoot(ctx) }.getOrNull()
    val real = root?.let { runCatching { candy.ide.git.GitManager.realPath(it) }.getOrNull() }
    return if (!real.isNullOrBlank()) real else "/data/data/com.termux/files/home"
}

private fun shizukuHint(): String =
    if (candy.ide.data.ShizukuManager.state() == candy.ide.data.ShizukuManager.State.RUNNING_GRANTED) "已授权，命令直连执行"
    else "未连接（设置里连接后命令直接在本终端执行）"

/** 把相对路径解析成绝对路径（支持 .. / . / ~ / 家目录） */
private fun resolvePath(base: String, rel: String, home: String): String {
    val target = when {
        rel.isEmpty() || rel == "~" -> home
        rel.startsWith("~") -> home + rel.substring(1)
        rel.startsWith("/") -> rel
        else -> base.trimEnd('/') + "/" + rel
    }
    // 规范化 . / ..
    val parts = java.util.ArrayDeque<String>()
    for (seg in target.split("/")) {
        when {
            seg.isEmpty() || seg == "." -> { }
            seg == ".." -> { if (parts.isNotEmpty()) parts.removeLast() }
            else -> parts.addLast(seg)
        }
    }
    return "/" + parts.joinToString("/")
}

/** 提示符：家目录显示 ~，其余取最后两段更短一点 */
private fun promptText(cwd: String, home: String): String {
    val disp = if (cwd == home) "~" else if (cwd.startsWith(home + "/")) "~" + cwd.substring(home.length) else cwd
    return "candy " + disp + " $ "
}

/** 命令入口：提示符先落行，cd/pwd 本地处理，其余走 submit 执行 */
private fun submitCmd(
    ctx: Context,
    scope: CoroutineScope,
    lines: SnapshotStateList<TermLine>,
    raw: String,
    cwd: String,
    busy: Boolean,
    setBusy: (Boolean) -> Unit,
    setCwd: (String) -> Unit,
    onDone: () -> Unit = {},
) {
    if (busy) return
    val home = System.getProperty("user.home", "/data/data/com.termux/files/home")
    val trimmed = raw.trim()
    // cd：本地切换工作目录
    if (trimmed == "cd" || trimmed.startsWith("cd ")) {
        val rel = trimmed.removePrefix("cd").trim()
        val target = resolvePath(cwd, rel, home)
        setCwd(target)
        lines.add(TermLine(promptText(cwd, home) + trimmed, TermLine.Kind.CMD))
        lines.add(TermLine(target, TermLine.Kind.SYS))
        return
    }
    // pwd：显示当前工作目录
    if (trimmed == "pwd") {
        lines.add(TermLine(promptText(cwd, home) + trimmed, TermLine.Kind.CMD))
        lines.add(TermLine(cwd, TermLine.Kind.OUT))
        return
    }
    lines.add(TermLine(promptText(cwd, home) + trimmed, TermLine.Kind.CMD))
    submit(ctx, scope, lines, trimmed, cwd, { setBusy(it) }, onDone)
}

private fun submit(
    ctx: Context,
    scope: CoroutineScope,
    lines: SnapshotStateList<TermLine>,
    cmd: String,
    cwd: String? = null,
    setBusy: (Boolean) -> Unit = {},
    onDone: () -> Unit = {},
) {
    lines.add(TermLine("candy$ " + cmd, TermLine.Kind.CMD))
    setBusy(true)
    scope.launch {
        val res = withContext(Dispatchers.IO) { TerminalBridge.exec(ctx, cmd, cwd) }
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
            TerminalBridge.Channel.TERMUX_UNSUITABLE -> lines.add(TermLine(res.output, TermLine.Kind.ERR))
            TerminalBridge.Channel.NONE -> lines.add(TermLine(res.output, TermLine.Kind.ERR))
            else -> lines.add(TermLine(res.output, TermLine.Kind.ERR))
        }
        // 终端命令可能改了工程文件：同步文件树与编辑框（不询问，直接以磁盘为准）
        onDone()
        setBusy(false)
    }
}