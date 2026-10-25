package candy.ide.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.data.DiagItem
import candy.ide.desktop.IdeState
import candy.ide.desktop.OutputLine
import candy.ide.desktop.ui.JetBrainsMono
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.ui.theme.CandyTertiary

private val ErrorColor = Color(0xFFFF5C8A)
private val WarnColor = Color(0xFFFFC857)
private val InfoColor = Color(0xFF6FB7FF)
private val HintColor = Color(0xFF9A97A6)

private val FILE_POS = Regex("""([A-Za-z0-9_.\u4e00-\u9fa5-]+\.\w+):(\d+)(?::(\d+))?""")

/** 四档过滤状态（全显 = 四个都 true） */
data class LevelFilter(
    val error: Boolean = true,
    val warn: Boolean = true,
    val info: Boolean = true,
    val hint: Boolean = true,
) {
    val all get() = error && warn && info && hint
}

/** 日志行等级：1 错误 / 2 警告 / 3 提示 / 4 补充（与诊断四档一致） */
private fun logLevelOf(line: OutputLine): Int = when {
    line.kind == OutputLine.Kind.Error || line.text.contains("失败") -> 1
    line.text.contains("[警告]") || line.text.contains("警告") -> 2
    line.kind == OutputLine.Kind.Info || line.kind == OutputLine.Kind.Success -> 3
    else -> 4
}

/** 诊断等级：severity 0=错误 1=警告 2=提示 其它=补充 */
private fun DiagItem.level(): Int = when (severity.toInt()) { 0 -> 1; 1 -> 2; 2 -> 3; else -> 4 }

/**
 * 右抽屉：输出 / 日志 / 诊断 三页签（对齐 Android 端 OutputDrawer）。
 * - 输出：程序 stdout（等宽字体）
 * - 日志：IDE 事件流，「文件名:行」可点击跳转；支持等级过滤
 * - 诊断：当前文件诊断，点击跳到对应行；支持等级过滤
 */
@Composable
fun OutputDrawer(
    modifier: Modifier,
    state: IdeState,
    onGoto: (DiagItem) -> Unit,
    onOpenFileLine: ((String, Int) -> Unit)? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    val diags = state.activeFile?.let { state.diagnostics[it.absolutePath] } ?: emptyList()
    val bad = diags.any { it.level() == 1 }

    Surface(modifier = modifier, color = CandyBg, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = CandyBg, divider = {}) {
                Tab(selected = tab == 0, onClick = { tab = 0 }) { DrawerTabLabel("输出", state.outputLines.size) }
                Tab(selected = tab == 1, onClick = { tab = 1 }) { DrawerTabLabel("日志", state.logLines.size) }
                Tab(selected = tab == 2, onClick = { tab = 2 }) { DrawerTabLabel("诊断", diags.size, bad = bad) }
            }
            when (tab) {
                0 -> OutputList(state)
                1 -> LogList(state, onOpenFileLine)
                else -> DiagList(diags, onGoto)
            }
        }
    }
}

@Composable
private fun DrawerTabLabel(name: String, count: Int, bad: Boolean = false) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = CandyText, fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            count.toString(),
            color = if (bad) ErrorColor else CandyTextDim,
            fontSize = 11.sp,
            fontWeight = if (bad) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun OutputList(state: IdeState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.outputLines.size) {
        if (state.outputLines.isNotEmpty()) listState.animateScrollToItem(state.outputLines.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("程序输出", color = CandyTextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            TextButton({ state.clearOutput() }) { Text("清除", fontSize = 11.sp) }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            items(state.outputLines) { line ->
                Text(
                    line.text,
                    color = when (line.kind) {
                        OutputLine.Kind.Error -> CandyError
                        OutputLine.Kind.Info -> CandyTertiary
                        OutputLine.Kind.Success -> CandySecondary
                        else -> CandyText
                    },
                    fontSize = 12.sp,
                    fontFamily = JetBrainsMono,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun LogList(state: IdeState, onOpenFileLine: ((String, Int) -> Unit)?) {
    var filter by remember { mutableStateOf(LevelFilter()) }
    var showFilter by remember { mutableStateOf(false) }
    val visible = remember(state.logLines.toList(), filter) {
        state.logLines.filter { line ->
            when (logLevelOf(line)) {
                1 -> filter.error
                2 -> filter.warn
                3 -> filter.info
                else -> filter.hint
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showFilter = true }) {
                Text(if (filter.all) "等级过滤" else "等级过滤 ·", color = if (filter.all) CandyTextDim else CandyPrimary, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(visible.size.toString() + " 条", color = CandyTextDim, fontSize = 11.sp)
            TextButton({ state.clearLogs() }) { Text("清除", fontSize = 11.sp) }
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无日志", color = CandyTextDim)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                items(visible) { line ->
                    LogLineText(line, onOpenFileLine)
                }
            }
        }
    }
    if (showFilter) {
        LevelFilterDialog(
            title = "日志等级",
            filter = filter,
            onToggle = { filter = it },
            onReset = { filter = LevelFilter() },
            onDismiss = { showFilter = false },
        )
    }
}

/** 一条日志：普通文本，文件位置片段渲染成可点链接 */
@Composable
private fun LogLineText(line: OutputLine, onOpenFileLine: ((String, Int) -> Unit)?) {
    val color = when (line.kind) {
        OutputLine.Kind.Error -> ErrorColor
        OutputLine.Kind.Success -> CandySecondary
        OutputLine.Kind.Info -> InfoColor
        else -> CandyText
    }
    if (onOpenFileLine == null) {
        Text(line.text, color = color, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        return
    }
    val annotated = remember(line) {
        buildAnnotatedString {
            var cursor = 0
            for (m in FILE_POS.findAll(line.text)) {
                append(line.text.substring(cursor, m.range.first))
                val linkText = line.text.substring(m.range)
                pushStringAnnotation(tag = "file", m.groupValues[1] + ":" + m.groupValues[2])
                withStyle(SpanStyle(color = CandyPrimary, textDecoration = TextDecoration.Underline)) { append(linkText) }
                pop()
                cursor = m.range.last + 1
            }
            if (cursor < line.text.length) append(line.text.substring(cursor))
        }
    }
    ClickableText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(color = color, fontSize = 12.sp, fontFamily = JetBrainsMono),
        onClick = { offset ->
            annotated.getStringAnnotations("file", offset, offset).firstOrNull()?.let { tag ->
                val parts = tag.item.split(":")
                onOpenFileLine(parts[0], parts[1].toIntOrNull() ?: 1)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun DiagList(diags: List<DiagItem>, onGoto: (DiagItem) -> Unit) {
    var filter by remember { mutableStateOf(LevelFilter()) }
    var showFilter by remember { mutableStateOf(false) }
    val visible = remember(diags, filter) { diags.filter { filter.acceptsLevel(it.level()) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showFilter = true }) {
                Text(if (filter.all) "等级过滤" else "等级过滤 ·", color = if (filter.all) CandyTextDim else CandyPrimary, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(visible.size.toString() + " 条", color = CandyTextDim, fontSize = 11.sp)
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (filter.all) "✨ 没有诊断，代码很干净" else "当前过滤条件下没有诊断", color = CandyTextDim, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                items(visible) { d ->
                    val dot = when (d.level()) {
                        1 -> ErrorColor
                        2 -> WarnColor
                        3 -> InfoColor
                        else -> HintColor
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onGoto(d) }.padding(vertical = 5.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(d.message, color = CandyText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "行 " + (d.line + 1) + " · 列 " + (d.char + 1),
                                color = CandyPrimary,
                                fontSize = 10.sp,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showFilter) {
        LevelFilterDialog(
            title = "诊断等级",
            filter = filter,
            onToggle = { filter = it },
            onReset = { filter = LevelFilter() },
            onDismiss = { showFilter = false },
        )
    }
}


private fun LevelFilter.acceptsLevel(level: Int): Boolean = when (level) {
    1 -> error
    2 -> warn
    3 -> info
    else -> hint
}

@Composable
private fun LevelFilterDialog(
    title: String,
    filter: LevelFilter,
    onToggle: (LevelFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FilterRow("错误", filter.error) { onToggle(filter.copy(error = it)) }
                FilterRow("警告", filter.warn) { onToggle(filter.copy(warn = it)) }
                FilterRow("提示", filter.info) { onToggle(filter.copy(info = it)) }
                FilterRow("补充", filter.hint) { onToggle(filter.copy(hint = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = { TextButton(onClick = { onReset(); onDismiss() }) { Text("全显") } },
    )
}

@Composable
private fun FilterRow(name: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onToggle(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = if (on) CandyText else CandyTextDim, modifier = Modifier.weight(1f))
        Text(if (on) "显示" else "隐藏", color = CandyPrimary, fontSize = 12.sp)
    }
}
