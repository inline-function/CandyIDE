package candy.plugin.android.ui

import candy.ide.plugin.ui.PluginColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import candy.ide.plugin.ui.PluginColors.Bg
import candy.ide.plugin.ui.PluginColors.Error
import candy.ide.plugin.ui.PluginColors.Primary
import candy.ide.plugin.ui.PluginColors.Secondary
import candy.ide.plugin.ui.PluginColors.Surface
import candy.ide.plugin.ui.PluginColors.Text
import candy.ide.plugin.ui.PluginColors.TextDim

/**
 * 布局预览全屏页（像 AI 助手页那样单开一页）：
 * - 顶栏：退出（最左）、函数名、视图树（右二）、属性（最右），下接 1dp 分隔线
 * - 左滑栏：视图树大纲，长按节点弹出符号栏（添加子组件/删除/复制/粘贴/剪切）
 * - 右滑栏：选中视图的属性
 * - 树/属性改动即时重渲染；返回（退出）时把树写回源码（精确补丁，仅改动的节点）
 * - 仅渲染不运行：视图点击只选中，不响应运行期行为
 */
@Composable
fun PreviewScreen(pv: candy.ide.plugin.preview.ComposePreviewState?, onBack: () -> Unit, onApply: (String, candy.ide.plugin.preview.ComposePreviewState) -> Unit) {
    var leaving by remember { mutableStateOf(false) }
    if (pv == null) {
        Box(modifier = Modifier.fillMaxSize().background(PluginColors.Bg), contentAlignment = Alignment.Center) {
            Text("没有可预览的布局", color = PluginColors.TextDim)
        }
        return
    }
    var tree by remember(pv) { mutableStateOf(JSONObject(pv.treeJson)) }
    var dirty by remember(pv) { mutableStateOf(false) }
    var selPath by remember(pv) { mutableStateOf<List<Int>?>(null) }
    var showTree by remember(pv) { mutableStateOf(false) }
    var showProps by remember(pv) { mutableStateOf(false) }

    /** 退出：把编辑后的树写回源码，再回编辑器。 */
    fun exitWithApply() {
        if (leaving) return
        leaving = true
        if (dirty) onApply(tree.toString(), pv)
        onBack()
    }
    /** 树变了：刷新本地状态 + 即时重渲染（tree 本身就是渲染源） */
    fun mutate() { tree = JSONObject(tree.toString()); dirty = true }

    Column(modifier = Modifier.fillMaxSize().background(PluginColors.Bg)) {
        // 顶栏：退出 | 函数名 ... 树 | 属性
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { exitWithApply() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出预览", tint = PluginColors.Text)
            }
            Text(
                pv.fnName,
                color = PluginColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 2.dp),
            )
            if (dirty) {
                Text("未写回", color = PluginColors.Error, fontSize = 10.sp, modifier = Modifier.padding(end = 4.dp))
            }
            IconButton(onClick = { showTree = !showTree }) {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = "视图树",
                    tint = if (showTree) PluginColors.Secondary else PluginColors.TextDim,
                )
            }
        }
        // 顶栏下方的 1dp 分隔线
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PluginColors.TextDim.copy(alpha = 0.3f)))
        // 主体：渲染区全宽；左/右抽屉是侧滑浮层（默认收起，左缘右滑出树、右缘左滑出属性）
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 渲染区（点击视图 = 选中）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) {
                        // 侧滑手势：左缘右滑 → 视图树；右缘左滑 → 属性
                        var startX = 0f
                        var total = 0f
                        val edge = 56.dp.toPx()
                        val w = size.width.toFloat()
                        detectHorizontalDragGestures(
                            onDragStart = { startX = it.x; total = 0f },
                            onHorizontalDrag = { _, d -> total += d },
                            onDragEnd = {
                                when {
                                    startX < edge && total > 40f -> showTree = true
                                    startX > w - edge && total < -40f -> {
                                        if (selPath == null) {
                                            val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                                            if (kids != null && kids.length() > 0) selPath = listOf(0)
                                        }
                                        showProps = true
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Column {
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null) {
                        for (i in 0 until kids.length()) {
                            ViewTreeNode(kids.getJSONObject(i), emptyList(), selPath, true) { p -> selPath = p }
                        }
                    } else {
                        Text("树解析失败", color = PluginColors.TextDim, fontSize = 12.sp)
                    }
                }
            }
            // 遮罩：抽屉开着时点外部关闭
            if (showTree || showProps) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f))
                        .clickable { showTree = false; showProps = false },
                )
            }
            // 左抽屉：视图树（长按节点弹符号栏）
            androidx.compose.animation.AnimatedVisibility(
                visible = showTree,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = androidx.compose.animation.slideInHorizontally { -it },
                exit = androidx.compose.animation.slideOutHorizontally { -it },
            ) {
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight()
                        .background(PluginColors.Surface)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                ) {
                    Text("视图树", color = PluginColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null) {
                        for (i in 0 until kids.length()) {
                            TreeRowLongPress(
                                kids.getJSONObject(i), emptyList(), selPath, 0,
                                onSelect = { p -> selPath = p; showProps = true },
                                tree = tree,
                                onChange = ::mutate,
                            )
                        }
                    }
                }
            }
            // 右抽屉：选中视图的属性
            androidx.compose.animation.AnimatedVisibility(
                visible = showProps && selPath != null,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = androidx.compose.animation.slideInHorizontally { it },
                exit = androidx.compose.animation.slideOutHorizontally { it },
            ) {
                selPath?.let { sp ->
                    val node = nodeAt(tree, sp)
                    if (node != null) PropPanel(
                        node = node,
                        fullscreen = true,
                        onEdit = { mutate() },
                        onAddProp = { k, v ->
                            val props = runCatching { node.getJSONObject("props") }.getOrNull()
                                ?: JSONObject().also { node.put("props", it) }
                            props.put(k, v)
                            mutate()
                        },
                        onRemoveProp = { k ->
                            runCatching { node.getJSONObject("props") }.getOrNull()?.remove(k)
                            mutate()
                        },
                    )
                }
            }
        }
    }
    // 返回键同样走「写回再退出」
    androidx.activity.compose.BackHandler { exitWithApply() }
}

/**
 * 树行：短按选中，长按弹出符号栏（添加子组件 / 删除 / 复制 / 剪切 / 粘贴）。
 */
@Composable
private fun TreeRowLongPress(
    node: JSONObject,
    path: List<Int>,
    selPath: List<Int>?,
    depth: Int,
    onSelect: (List<Int>) -> Unit,
    tree: JSONObject,
    onChange: () -> Unit,
) {
    val name = node.optString("name")
    val txt = node.optString("text").trim('\"')
    val selected = selPath != null && selPath == path
    var opsFor by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(path) {
                    detectTapGestures(
                        onTap = { onSelect(path) },
                        onLongPress = { opsFor = true },
                    )
                }
                .background(
                    if (selected) PluginColors.Secondary.copy(alpha = 0.15f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(6.dp),
                )
                .padding(start = (depth * 12 + 4).dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, color = if (selected) PluginColors.Secondary else PluginColors.Text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else null)
            if (txt.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Text(txt, color = PluginColors.TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // 符号栏（长按弹出）：在节点正下方就地展开
        if (opsFor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (depth * 12 + 4).dp, top = 2.dp, bottom = 2.dp)
                    .background(PluginColors.Bg.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 树可能在编辑中变化：父节点取不到时回退到根，避免 !! 空指针崩溃
                val par = if (path.isEmpty()) tree else (nodeAt(tree, path.dropLast(1)) ?: tree)
                TinyOpBtn("＋子", true) { addChild(node, mkBox()); onChange(); opsFor = false }
                TinyOpBtn("🗑", true) {
                    if (path.isNotEmpty()) { removeChild(par, path.last()); onChange() }
                    opsFor = false
                }
                TinyOpBtn("📋", true) { vmClipboardCopy(node); opsFor = false }
                TinyOpBtn("✂", true) {
                    vmClipboardCut(node)
                    if (path.isNotEmpty()) { removeChild(par, path.last()); onChange() }
                    opsFor = false
                }
                TinyOpBtn("📥", clipboardGet() != null) {
                    clipboardGet()?.let { addChild(node, it); onChange() }
                    opsFor = false
                }
            }
        }
        val kids = runCatching { node.getJSONArray("children") }.getOrNull()
        if (kids != null) {
            for (i in 0 until kids.length()) {
                TreeRowLongPress(kids.getJSONObject(i), path + i, selPath, depth + 1, onSelect, tree, onChange)
            }
        }
    }
}

// 树节点剪贴板（进程内单例：跨节点复制/剪切/粘贴）
private val nodeClipboard = arrayOfNulls<JSONObject>(1)
private fun vmClipboardCopy(n: JSONObject) { nodeClipboard[0] = n.deepCopy() }
private fun vmClipboardCut(n: JSONObject) { nodeClipboard[0] = n.deepCopy() }
private fun clipboardGet(): JSONObject? = nodeClipboard[0]?.let { JSONObject(it.toString()) }
