package candy.ide.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.data.DiagItem
import candy.ide.md.GOTO_LINK
import candy.ide.md.MarkdownText
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion

private val ErrorColor = Color(0xFFFF5C8A)
private val WarnColor = Color(0xFFFFC857)

/**
 * 右抽屉：输出 / 日志 / 诊断 三页签。
 * - 输出：程序 stdout，纯文本
 * - 日志：IDE 事件流，Markdown 渲染
 * - 诊断：语言诊断，点击跳到对应行列
 */
@Composable
fun OutputDrawer(modifier: Modifier, vm: CandyViewModel, onGoto: (DiagItem) -> Unit) {
    // 页签状态由 VM 持有：运行开始时跳日志页、跑完跳输出页
    val tab = vm.drawerTab
    val diags = vm.diagnostics

    Surface(modifier = modifier, color = CandyBg, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bad = diags.any { it.severity >= DiagnosticRegion.SEVERITY_ERROR }
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = CandyBg,
                    divider = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Tab(selected = tab == 0, onClick = { vm.openDrawerTab(0) }) {
                        TabLabel("输出", vm.outputLines.size)
                    }
                    Tab(selected = tab == 1, onClick = { vm.openDrawerTab(1) }) {
                        TabLabel("日志", vm.logLines.size)
                    }
                    Tab(selected = tab == 2, onClick = { vm.openDrawerTab(2) }) {
                        TabLabel("诊断", diags.size, bad = bad)
                    }
                }

            }

            when (tab) {
                0 -> OutputList(vm.outputLines)
                1 -> {
                    // 日志等级过滤：信息 / 警告 / 错误
                    val showFilterState = remember { mutableStateOf(false) }
                    var showFilter by showFilterState
                    val filter = vm.logFilter.value
                    val visible = remember(vm.logLines.toList(), filter) {
                        vm.logLines.filter { line ->
                            when (logLevelOf(line)) {
                                2 -> filter.error
                                1 -> filter.warn
                                else -> filter.info
                            }
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val active = !(filter.info && filter.warn && filter.error)
                            TextButton(onClick = { showFilter = true }) {
                                Text(if (active) "等级过滤 ·" else "等级过滤", color = if (active) CandyPrimary else CandyTextDim, fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(visible.size.toString() + " 条", color = CandyTextDim, fontSize = 11.sp)
                        }
                        LogList(vm, visible, failed = vm.runFailed.value)
                    }
                    if (showFilter) {
                        AlertDialog(
                            onDismissRequest = { showFilter = false },
                            title = { Text("日志等级") },
                            text = {
                                Column {
                                    FilterRow("信息", filter.info) { vm.logFilter.value = filter.copy(info = it) }
                                    FilterRow("警告", filter.warn) { vm.logFilter.value = filter.copy(warn = it) }
                                    FilterRow("错误", filter.error) { vm.logFilter.value = filter.copy(error = it) }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showFilter = false }) { Text("完成") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    vm.logFilter.value = LogFilter()
                                    showFilter = false
                                }) { Text("全显") }
                            },
                        )
                    }
                }
                else -> DiagList(vm, diags, onGoto)
            }
        }
    }
}

@Composable
private fun TabLabel(name: String, count: Int, bad: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = if (count > 0) CandyText else CandyTextDim, fontSize = 13.sp)
        if (count > 0) {
            Spacer(Modifier.size(3.dp))
            Text(
                count.toString(),
                color = if (bad) CandyPrimary else CandyTextDim,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun OutputList(lines: List<String>) {
    if (lines.isEmpty()) {
        Empty("暂无输出 —— 点右上角「▶」运行试试")
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.animateScrollToItem(lines.size - 1)
    }
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(lines) { line ->
            Text(line, color = CandyText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

/** 「文件名：行：列」 → md 链接，点击跳过去 */
private val FILE_POS = Regex("""([A-Za-z0-9_.\u4e00-\u9fa5-]+\.\w+):(\d+)(?::(\d+))?""")

@Composable
private fun LogList(vm: CandyViewModel, lines: List<String>, failed: Boolean = false) {
    if (lines.isEmpty()) {
        Empty("暂无日志")
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.animateScrollToItem(lines.size - 1)
    }
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(lines) { line ->
            // 失败运行或错误行：整条标红，其余正常 md 渲染；两种都支持文件位置超链接
            val bad = failed || line.contains("**[错误]**") || line.contains("失败")
            val linked = remember(line) {
                line.replace(FILE_POS) { m ->
                    val file = m.groupValues[1]
                    val ln = m.groupValues[2]
                    val col = m.groupValues[3]
                    val tail = if (col.isEmpty()) "" else ":$col"
                    "[$file:$ln$tail](${GOTO_LINK}$file:$ln:$col)"
                }
            }
            if (bad) {
                MarkdownText(linked, modifier = Modifier.fillMaxWidth(), fontSize = 12.sp, color = ErrorColor,
                    onLink = { url -> handleGotoLink(vm, url) })
            } else {
                MarkdownText(linked, modifier = Modifier.fillMaxWidth(), fontSize = 13.sp,
                    onLink = { url -> handleGotoLink(vm, url) })
            }
        }
    }
}

/** goto://文件名:行:列 → 打开文件并跳转 */
private fun handleGotoLink(vm: CandyViewModel, url: String) {
    if (!url.startsWith(GOTO_LINK)) return
    val body = url.removePrefix(GOTO_LINK)
    val parts = body.split(":")
    if (parts.size < 2) return
    val name = parts[0]
    val line = parts[1].toIntOrNull() ?: return
    val col = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0
    vm.openFileLine(name, line, col)
}

@Composable
private fun DiagList(vm: CandyViewModel, diags: List<DiagItem>, onGoto: (DiagItem) -> Unit) {
    if (diags.isEmpty()) {
        Empty("✨ 没有诊断，代码很干净")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(diags) { d ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGoto(d) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val dot = when (d.severity) {
                    DiagnosticRegion.SEVERITY_ERROR -> ErrorColor
                    DiagnosticRegion.SEVERITY_WARNING -> WarnColor
                    else -> CandyTextDim
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(
                        d.message,
                        color = CandyText,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        (vm.currentFile.value?.name ?: "文件") + ":" + (d.line + 1) + ":" + (d.char + 1),
                        color = CandyPrimary,
                        fontSize = 10.sp,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = CandyTextDim, fontSize = 12.sp)
    }
}

/** 日志等级过滤状态（全显 = 三个都 true） */
data class LogFilter(
    val info: Boolean = true,
    val warn: Boolean = true,
    val error: Boolean = true,
)

/** 判定一行日志的等级：错误 > 警告 > 信息 */
private fun logLevelOf(line: String): Int = when {
    line.contains("**[错误]**") || line.contains("**[失败]") || line.contains("失败]**") -> 2
    line.contains("**[警告]**") || line.contains("警告") -> 1
    else -> 0
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