package candy.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.data.DiagItem
import candy.ide.md.GOTO_LINK
import candy.ide.md.MarkdownText
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion

private val ErrorColor = Color(0xFFFF5C8A)
private val WarnColor = Color(0xFFFFC857)
private val InfoColor = Color(0xFF6FB7FF)
private val HintColor = Color(0xFF9A97A6)
// AI 助手风卡片底色：成功略偏绿、失败略偏红（极浅）
private val OkTint = Color(0xFF1E2420)
private val ErrTint = Color(0xFF271D22)

/**
 * 右抽屉：输出 / 日志 / 诊断 三页签。
 * - 输出：程序 stdout，纯文本
 * - 日志：操作卡片（进行中转圈、完成打勾、失败打叉，可展开错误明细）
 * - 诊断：等级色竖条 + 标题 + 内容的圆角卡片；短按跳位置，长按出快速修正
 */
@Composable
fun OutputDrawer(modifier: Modifier, vm: CandyViewModel, onGoto: (DiagItem) -> Unit) {
    val tab = vm.drawerTab
    val diags = vm.diagnostics

    Surface(modifier = modifier, color = CandyBg, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = CandyBg,
                divider = {},
            ) {
                Tab(selected = tab == 0, onClick = { vm.openDrawerTab(0) }, modifier = Modifier.padding(vertical = 3.dp)) {
                    TabLabel("输出", vm.outputLines.size)
                }
                Tab(selected = tab == 1, onClick = { vm.openDrawerTab(1) }, modifier = Modifier.padding(vertical = 3.dp)) {
                    TabLabel("日志", vm.logLines.size)
                }
                // 诊断不带数量角标
                Tab(selected = tab == 2, onClick = { vm.openDrawerTab(2) }, modifier = Modifier.padding(vertical = 3.dp)) {
                    TabLabel("诊断", 0)
                }
            }
            HorizontalDivider(color = CandySurfaceVariant, thickness = 1.dp)

            when (tab) {
                0 -> OutputList(vm.outputLines)
                1 -> {
                    val filter = vm.logFilter.value
                    val visible = remember(vm.logLines.toList(), filter) {
                        vm.logLines.filter { line -> filter.acceptsLevel(logLevelOf(line)) }
                    }
                    Column(Modifier.fillMaxSize()) {
                        LogFilterBar(vm = vm, active = !filter.all(), count = visible.size)
                        LogList(vm, visible)
                    }
                }
                else -> {
                    val dfilter = vm.diagFilter.value
                    val visible = remember(diags.toList(), dfilter) {
                        diags.filter { d -> dfilter.accepts(d) }
                    }
                    Column(Modifier.fillMaxSize()) {
                        LogFilterBar(vm = vm, diag = true, active = !dfilter.all(), count = visible.size)
                        DiagList(vm, visible, onGoto)
                    }
                }
            }
        }
    }
}

/** 「等级过滤」按钮：logcat 图标 */
@Composable
private fun LogFilterBar(vm: CandyViewModel, diag: Boolean = false, active: Boolean, count: Int) {
    var showFilter by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { showFilter = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Article, contentDescription = "等级过滤", tint = if (active) CandyPrimary else CandyTextDim, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(count.toString() + " 条", color = CandyTextDim, fontSize = 11.sp)
    }
    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text(if (diag) "诊断等级" else "日志等级") },
            text = {
                Column {
                    if (diag) {
                        val f = vm.diagFilter.value
                        FilterSwitchRow("错误", f.error) { vm.diagFilter.value = f.copy(error = it) }
                        FilterSwitchRow("警告", f.warn) { vm.diagFilter.value = f.copy(warn = it) }
                        FilterSwitchRow("提示", f.info) { vm.diagFilter.value = f.copy(info = it) }
                        FilterSwitchRow("补充", f.hint) { vm.diagFilter.value = f.copy(hint = it) }
                    } else {
                        val f = vm.logFilter.value
                        FilterSwitchRow("错误", f.error) { vm.logFilter.value = f.copy(error = it) }
                        FilterSwitchRow("警告", f.warn) { vm.logFilter.value = f.copy(warn = it) }
                        FilterSwitchRow("提示", f.info) { vm.logFilter.value = f.copy(info = it) }
                        FilterSwitchRow("补充", f.hint) { vm.logFilter.value = f.copy(hint = it) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilter = false }) { Text("完成") } },
            dismissButton = {
                TextButton(onClick = {
                    if (diag) vm.diagFilter.value = DiagFilter() else vm.logFilter.value = LogFilter()
                    showFilter = false
                }) { Text("全显") }
            },
        )
    }
}

@Composable
private fun TabLabel(name: String, count: Int, bad: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = if (count > 0) CandyText else CandyTextDim, fontSize = 13.sp)
        if (count > 0) {
            Spacer(Modifier.size(3.dp))
            Text(count.toString(), color = if (bad) CandyPrimary else CandyTextDim, fontSize = 11.sp)
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(lines) { line ->
            Text(line, color = CandyText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

/** 「文件名：行：列」 → md 链接，点击跳过去 */
private val FILE_POS = Regex("""([A-Za-z0-9_.\u4e00-\u9fa5-]+\.\w+):(\d+)(?::(\d+))?""")

private enum class OpState { RUNNING, DONE, FAILED }

/** 一条构建日志卡片：阶段名/正文/等级/状态/可展开明细 */
private data class LogCard(
    val tag: String,
    val body: String,
    val level: Int,
    val state: OpState,
    val detail: String?,
)

private fun parseCard(line: String): LogCard {
    val m = Regex("""\*\*\[([^]]+)]\*\*(.*)""").find(line)
        ?: return LogCard("事件", line, 3, OpState.DONE, null)
    val tag = m.groupValues[1]
    val body = m.groupValues[2].trim()
    val level = when {
        tag.contains("错误") || tag.contains("失败") -> 1
        tag.contains("警告") -> 2
        tag.contains("补充") -> 4
        else -> 3
    }
    return LogCard(tag, body, level, OpState.DONE, null)
}

/** 卡片最终状态：失败行 ✗；带「完成/成功」✓；其余只有「正忙且是末条」才转圈，
 *  否则旧日志里的信息行（如「包名 …」）会永远转圈 */
private fun stateOf(card: LogCard, isLast: Boolean, busy: Boolean): OpState = when {
    card.level == 1 -> OpState.FAILED
    card.tag.contains("完成") || card.tag.contains("成功") ||
        card.body.contains("完成") || card.body.contains("成功") -> OpState.DONE
    busy && isLast -> OpState.RUNNING
    else -> OpState.DONE
}

@Composable
private fun LogList(vm: CandyViewModel, lines: List<String>) {
    if (lines.isEmpty()) {
        Empty("暂无日志")
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.animateScrollToItem(lines.size - 1)
    }
    // vm 是否有进行中的操作（打包/安装）：只有「正在跑且在末条」的卡片才显示加载
    val busy = vm.running.value || vm.installing.value
    // 堆栈行并入上一条失败卡片的明细
    val cards = remember(lines) {
        val out = mutableListOf<LogCard>()
        for (line in lines) {
            val card = parseCard(line)
            if (card.tag == "堆栈" && out.isNotEmpty()) {
                val last = out.removeAt(out.size - 1)
                out.add(last.copy(detail = card.body))
            } else out.add(card)
        }
        out
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(cards) { i, card ->
            LogCardItem(vm, card.copy(state = stateOf(card, i == cards.size - 1, busy)))
        }
    }
}

@Composable
private fun LogCardItem(vm: CandyViewModel, card: LogCard) {
    // 失败条目背景略偏红、成功略偏绿（极浅）
    val bg = when (card.state) {
        OpState.FAILED -> ErrTint
        OpState.DONE -> OkTint
        else -> CandySurface
    }
    var expanded by remember(card) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 状态图标：进行中转圈、完成打勾、失败打叉
            when (card.state) {
                OpState.RUNNING -> CircularProgressIndicator(
                    strokeWidth = 2.dp, modifier = Modifier.size(14.dp),
                    color = when (card.level) { 1 -> ErrorColor; 2 -> WarnColor; else -> CandyPrimary },
                )
                OpState.DONE -> Icon(Icons.Default.Check, contentDescription = null, tint = CandySecondary, modifier = Modifier.size(16.dp))
                OpState.FAILED -> Box(
                    modifier = Modifier.size(18.dp).clip(CircleShape).background(ErrorColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Close, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(12.dp)) }
            }
            Spacer(Modifier.size(8.dp))
            Text(card.tag, color = if (card.level == 1) ErrorColor else CandyText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (card.state == OpState.FAILED && card.detail != null) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = "展开错误明细",
                    tint = CandyTextDim,
                    modifier = Modifier.size(16.dp).clickable { expanded = !expanded },
                )
            }
        }
        if (card.body.isNotEmpty()) {
            val linked = remember(card.body) {
                card.body.replace(FILE_POS) { m ->
                    val file = m.groupValues[1]
                    val ln = m.groupValues[2]
                    val col = m.groupValues[3]
                    val tail = if (col.isEmpty()) "" else ":$col"
                    "[$file:$ln$tail](${"candy.ide.md.GOTO_LINK"}$file:$ln:$col)"
                }
            }
            MarkdownText(linked, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), fontSize = 12.sp,
                color = if (card.level == 1) ErrorColor else CandyTextDim,
                onLink = { url -> handleGotoLink(vm, url) })
        }
        // 展开的错误明细：条目圆角背景里的圆角背景
        if (expanded && card.detail != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = CandySurface,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    card.detail.replace(" | ", "\n"),
                    color = CandyTextDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

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

/** 诊断标题：消息去掉「[类别][文件:行:列]」前缀后的首段 */
/** 诊断标题：LSP 按 code 给出的诊断名（如「变量未使用」）；未收录时按等级兜底 */
private fun diagTitle(vm: CandyViewModel, d: DiagItem): String =
    vm.diagTitleOf(d.code) ?: when (d.level()) {
        1 -> "错误"; 2 -> "警告"; else -> "提示"
    }

/** 诊断内容：原因原文（编译器消息）；带「[类别][file:行:列]」前缀的去掉前缀 */
private fun diagContent(d: DiagItem): String {
    val raw = d.message
    // 形如「[未使用][hello.subl:6:4] 变量 x 声明后从未使用」
    val m = Regex("""^\[[^\]]*]\[[^\]]*:\d+:\d+]\s*""").find(raw)
    if (m != null) return raw.substring(m.range.last + 1).trim()
    // 形如「[hello.subl:6:4] …」
    val m2 = Regex("""^\[[^\]]*:\d+:\d+]\s*""").find(raw)
    if (m2 != null) return raw.substring(m2.range.last + 1).trim()
    return raw.trim()
}

@Composable
private fun DiagList(vm: CandyViewModel, diags: List<DiagItem>, onGoto: (DiagItem) -> Unit) {
    if (diags.isEmpty()) {
        Empty("✨ 没有诊断，代码很干净")
        return
    }
    // 排序：错误最上 → 等级从重到轻 → 标题首字母
    val sorted = remember(diags) {
        diags.sortedWith(compareBy<DiagItem> { it.level() }.thenBy { diagTitle(vm, it).lowercase() })
    }
    var longPressItem by remember { mutableStateOf<DiagItem?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(sorted) { d ->
            DiagCardItem(vm, d, onClick = { onGoto(d) }, onLongClick = { longPressItem = d })
        }
    }
    // 长按：快速修正（与编辑器灯泡同一数据源）
    longPressItem?.let { d ->
        val fixes = remember(d) { runCatching { vm.quickFixes(d.line, d.char) }.getOrDefault(emptyList()) }
        AlertDialog(
            onDismissRequest = { longPressItem = null },
            title = { Text("快速修正") },
            text = {
                Column {
                    if (fixes.isEmpty()) Text("当前没有可用的快速修正", color = CandyTextDim, fontSize = 13.sp)
                    fixes.forEach { fix ->
                        TextButton(onClick = { longPressItem = null; vm.applyQuickFix(fix) }, modifier = Modifier.fillMaxWidth()) {
                            Text(fix.title, color = CandyText)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { longPressItem = null }) { Text("关闭") } },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DiagCardItem(vm: CandyViewModel, d: DiagItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bar = when (d.level()) { 1 -> ErrorColor; 2 -> WarnColor; 3 -> InfoColor; else -> HintColor }
    val title = diagTitle(vm, d)
    val content = diagContent(d)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CandySurface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 色竖条（替换原来的小圆点）+ 标题 + 内容；不标行列
        Box(modifier = Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(bar))
        Spacer(Modifier.size(10.dp))
        Column {
            Text(title, color = CandyText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (content.isNotEmpty()) {
                Text(content, color = CandyTextDim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
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

/** 诊断等级过滤状态（全显 = 四个都 true） */
data class DiagFilter(
    val error: Boolean = true,
    val warn: Boolean = true,
    val info: Boolean = true,
    val hint: Boolean = true,
) {
    fun all() = error && warn && info && hint
    fun accepts(d: DiagItem): Boolean = when (d.level()) {
        1 -> error; 2 -> warn; 3 -> info; else -> hint
    }
}

/** DiagItem 的四等级（1 错误 / 2 警告 / 3 提示 / 4 补充），由 sora severity 常量反映射 */
fun DiagItem.level(): Int = when (severity) {
    DiagnosticRegion.SEVERITY_ERROR -> 1
    DiagnosticRegion.SEVERITY_WARNING -> 2
    DiagnosticRegion.SEVERITY_TYPO -> 3
    else -> 4
}

/** 日志等级过滤状态（全显 = 四个都 true） */
data class LogFilter(
    val error: Boolean = true,
    val warn: Boolean = true,
    val info: Boolean = true,
    val hint: Boolean = true,
) {
    fun all() = error && warn && info && hint
    fun acceptsLevel(level: Int): Boolean = when (level) {
        1 -> error; 2 -> warn; 3 -> info; else -> hint
    }
}

private fun logLevelOf(line: String): Int = when {
    line.contains("**[错误]") || line.contains("**[失败]") || line.contains("失败]") || line.contains("失败**") -> 1
    line.contains("**[警告]") || line.contains("警告") -> 2
    line.contains("**[补充]") -> 4
    line.contains("**[提示]") -> 3
    else -> 4
}

@Composable
private fun FilterSwitchRow(name: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = if (on) CandyText else CandyTextDim, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onToggle)
    }
}
