package candy.plugin.android.ui

import candy.ide.plugin.ui.PluginColors
import candy.ide.plugin.preview.ComposePreviewState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * @composable 布局预览悬浮窗（可编辑视图树的可视构建器）：
 *  - 顶栏极简（像 AI 助手顶栏：小、无边框）且可拖动整个悬浮窗；
 *  - 点渲染的视图 = 选中（边框高亮）；视图不响应任何「运行期」点击（仅渲染，不运行代码）；
 *  - 左侧滑栏：视图树大纲，可 删除/添加/复制/剪切/粘贴 视图；
 *  - 右侧滑栏：设置选中视图的属性（text/placeholder/size/bold/orientation/kind/color）；
 *  - 顶栏「应用」把视图树再生成的函数体写回源码（编辑器撤销可回退）。
 *
 *  数据来自 LspAnalysis.composableTreeJson：{"name","start","end","children":[{"name","text","props","children"}]}
 *  本地树是源码的一个可编辑快照：外层 state.treeJson 变化（源码被编辑后 1s 刷新）时整体重置。
 */

internal fun JSONObject.deepCopy(): JSONObject = JSONObject(this.toString())

/** 按路径取节点（root 自身的路径是空表） */
internal fun nodeAt(root: JSONObject, path: List<Int>): JSONObject? {
    var cur: JSONObject = root
    for (i in path) {
        val kids = runCatching { cur.getJSONArray("children") }.getOrNull() ?: return null
        if (i < 0 || i >= kids.length()) return null
        cur = kids.getJSONObject(i)
    }
    return cur
}

/** 父节点里删除指定下标的孩子 */
internal fun removeChild(parent: JSONObject, index: Int) {
    runCatching { parent.getJSONArray("children") }.getOrNull()?.let { arr ->
        if (index in 0 until arr.length()) {
            val kids = (0 until arr.length()).map { arr.getJSONObject(it) }.toMutableList()
            kids.removeAt(index)
            parent.put("children", JSONArray(kids))
        }
    }
}

internal fun addChild(parent: JSONObject, node: JSONObject) {
    val arr = runCatching { parent.getJSONArray("children") }.getOrNull() ?: JSONArray().also { parent.put("children", it) }
    arr.put(node)
}

internal fun insertChild(parent: JSONObject, index: Int, node: JSONObject) {
    val arr = runCatching { parent.getJSONArray("children") }.getOrNull() ?: JSONArray().also { parent.put("children", it) }
    val kids = (0 until arr.length()).map { arr.getJSONObject(it) }.toMutableList()
    kids.add(index.coerceIn(0, kids.size), node)
    parent.put("children", JSONArray(kids))
}

internal fun mkBox(): JSONObject = JSONObject().apply {
    put("name", "Box")
    put("props", JSONObject().apply { put("modifier", "Plain()") })
}

internal fun mkText(t: String): JSONObject = JSONObject().apply {
    put("name", "Text")
    put("text", "\"" + t + "\"")
    put("props", JSONObject().apply { put("size", "14") })
}

@Composable
fun ComposePreviewPanel(
    state: ComposePreviewState,
    fullscreen: Boolean,
    /** 是否允许编辑视图树/属性：悬浮窗只读，独立全屏页可编辑 */
    editEnabled: Boolean = false,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit,
    onApply: (String) -> Unit,
    onDrag: (IntOffset) -> Unit = {},
) {
    // 本地可编辑树：外层 treeJson 变（源码刷新）时重置
    var tree by remember(state.treeJson) { mutableStateOf(JSONObject(state.treeJson)) }
    var selPath by remember(state.treeJson) { mutableStateOf<List<Int>?>(null) }
    var showTree by remember { mutableStateOf(false) }
    var showProps by remember { mutableStateOf(false) }
    var clipboard by remember { mutableStateOf<JSONObject?>(null) }
    var dirty by remember(state.treeJson) { mutableStateOf(false) }

    fun mutate() { tree = JSONObject(tree.toString()); dirty = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(candy.ide.plugin.ui.PluginColors.Surface),
    ) {
        if (fullscreen) {
            // 全屏顶栏：像 AI 助手页那样 —— 返回箭头 + 标题 + 操作，含状态栏 padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "退出全屏",
                        tint = candy.ide.plugin.ui.PluginColors.Text,
                    )
                }
                Text(
                    text = "@" + state.fnName + " 布局预览",
                    color = candy.ide.plugin.ui.PluginColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                TinyTopBtn(if (showTree) "树▸" else "树", active = showTree) { showTree = !showTree }
                TinyTopBtn(if (showProps) "属性▸" else "属性", active = showProps && selPath != null) {
                    // 没选中视图时默认选第一个顶层视图，属性面板立即可用
                    if (selPath == null) {
                        val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                        if (kids != null && kids.length() > 0) selPath = listOf(0)
                    }
                    showProps = !showProps
                }
                if (dirty) {
                    Text(
                        text = "未应用",
                        color = candy.ide.plugin.ui.PluginColors.Error,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                TinyTopBtn("应用", active = dirty) {
                    onApply(tree.toString())
                    dirty = false
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭预览",
                        tint = candy.ide.plugin.ui.PluginColors.TextDim,
                    )
                }
            }
        } else {
            // 悬浮小窗顶栏：极简、无边框、可拖动
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { _, drag ->
                            onDrag(IntOffset(drag.x.toInt(), drag.y.toInt()))
                        }
                    }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "@" + state.fnName,
                    color = candy.ide.plugin.ui.PluginColors.TextDim,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 悬浮窗只读：点全屏图标进独立页才可编辑树/属性
                TinyIconBtn(Icons.Default.Fullscreen, contentDescription = "全屏编辑") { onToggleFullscreen() }
                TinyTopBtn("✕", true) { onClose() }
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧滑栏：视图树大纲 + 节点操作（悬浮窗只读 → 不显示树）
            if (showTree && editEnabled) {
                Column(
                    modifier = Modifier
                        .width(if (fullscreen) 170.dp else 110.dp)
                        .fillMaxHeight()
                        .background(candy.ide.plugin.ui.PluginColors.Bg.copy(alpha = 0.6f))
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val sel = selPath?.let { nodeAt(tree, it) }
                    if (sel != null && editEnabled) TreeOpsBar(
                        canPaste = clipboard != null,
                        onDelete = { selPath?.let { p -> if (p.isNotEmpty()) { val par = nodeAt(tree, p.dropLast(1))!!; removeChild(par, p.last()); mutate() } } },
                        onCopy = { clipboard = sel.deepCopy() },
                        onCut = { clipboard = sel.deepCopy(); selPath?.let { p -> if (p.isNotEmpty()) { val par = nodeAt(tree, p.dropLast(1))!!; removeChild(par, p.last()); mutate() } } },
                        onPasteChild = {
                            clipboard?.let { cb -> addChild(sel, cb.deepCopy()); mutate() }
                        },
                        onAddBox = { addChild(sel, mkBox()); mutate() },
                        onAddText = { addChild(sel, mkText("文本")); mutate() },
                    )
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null) {
                        for (i in 0 until kids.length()) {
                            TreeRow(
                        kids.getJSONObject(i), emptyList(), selPath, 0,
                    ) { p ->
                        selPath = p
                        if (editEnabled) showProps = true
                    }
                        }
                    }
                }
            }
            // 中间：渲染区（点击视图 = 选中；不响应运行期点击）
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)) {
                Column {
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null) {
                        for (i in 0 until kids.length()) {
                            ViewTreeNode(kids.getJSONObject(i), emptyList(), selPath, fullscreen) { p -> selPath = p }
                        }
                    } else {
                        Text("树解析失败", color = candy.ide.plugin.ui.PluginColors.TextDim, fontSize = 10.sp)
                    }
                }
            }
            // 右侧滑栏：选中视图的属性
            if (showProps && selPath != null && editEnabled) {
                val node = nodeAt(tree, selPath!!)
                if (node != null) PropPanel(
                    node = node,
                    fullscreen = fullscreen,
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

/** 顶栏小按钮（无边框、像 AI 助手顶栏）：enabled 控制可点；active 控制高亮色 */
@Composable
private fun TinyTopBtn(label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(24.dp)) {
        Text(
            label,
            color = when {
                active -> candy.ide.plugin.ui.PluginColors.Secondary
                !enabled -> candy.ide.plugin.ui.PluginColors.TextDim
                else -> candy.ide.plugin.ui.PluginColors.Primary
            },
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.Bold else null,
            maxLines = 1,
        )
    }
}

/** 顶栏图标按钮（与 AI 助手悬浮球的全屏图标同款风格） */
@Composable
private fun TinyIconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, active: Boolean = false, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(24.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = when {
                active -> candy.ide.plugin.ui.PluginColors.Secondary
                !enabled -> candy.ide.plugin.ui.PluginColors.TextDim
                else -> candy.ide.plugin.ui.PluginColors.Primary
            },
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 树大纲一行：名字 + 摘要，点击选中，按路径高亮 */
@Composable
internal fun TreeRow(node: JSONObject, path: List<Int>, selPath: List<Int>?, depth: Int, onSelect: (List<Int>) -> Unit) {
    val name = node.optString("name")
    val txt = node.optString("text").trim('\"')
    val selected = selPath != null && selPath == path
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(path) }
                .background(if (selected) candy.ide.plugin.ui.PluginColors.Primary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
                .padding(start = (depth * 8).dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name.removePrefix("@"),
                color = if (selected) candy.ide.plugin.ui.PluginColors.Primary else candy.ide.plugin.ui.PluginColors.Text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (txt.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(txt, color = candy.ide.plugin.ui.PluginColors.TextDim, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val kids = runCatching { node.getJSONArray("children") }.getOrNull()
        if (kids != null) for (i in 0 until kids.length()) {
            TreeRow(kids.getJSONObject(i), path + i, selPath, depth + 1, onSelect)
        }
    }
}

/** 选中节点的操作条：删除/复制/剪切/粘贴/添加 */
@Composable
internal fun TreeOpsBar(
    canPaste: Boolean,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPasteChild: () -> Unit,
    onAddBox: () -> Unit,
    onAddText: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TinyOpBtn("🗑", true, onDelete)
            TinyOpBtn("📋", true, onCopy)
            TinyOpBtn("✂", true, onCut)
            TinyOpBtn("📥", canPaste, onPasteChild)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TinyOpBtn("＋Box", true, onAddBox)
            TinyOpBtn("＋Text", true, onAddText)
        }
    }
}

@Composable
internal fun TinyOpBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(20.dp)) {
        Text(label, color = if (enabled) candy.ide.plugin.ui.PluginColors.Secondary else candy.ide.plugin.ui.PluginColors.TextDim, fontSize = 8.sp)
    }
}

/** 右侧属性面板：对已知属性做行内编辑 */
@Composable
internal fun PropPanel(
    node: JSONObject,
    fullscreen: Boolean,
    onEdit: () -> Unit,
    onAddProp: (String, String) -> Unit,
    onRemoveProp: (String) -> Unit,
) {
    val name = node.optString("name")
    val props = remember(node) { runCatching { node.getJSONObject("props") }.getOrNull() ?: JSONObject() }
    Column(
        modifier = Modifier
            .width(if (fullscreen) 180.dp else 120.dp)
            .fillMaxHeight()
            .background(candy.ide.plugin.ui.PluginColors.Bg.copy(alpha = 0.6f))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            name.removePrefix("@") + " 属性",
            color = candy.ide.plugin.ui.PluginColors.Text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        // text：既是顶层 text 字段也是 props.text（取显示用值，写回统一写 props）
        val textDisp = node.optString("text").trim('\"').ifEmpty { props.optString("text").trim('\"') }
        PropTextRow("text", textDisp) { v ->
            node.put("text", "\"" + v + "\""); onEdit()
        }
        PropTextRow("placeholder", props.optString("placeholder").trim('\"')) { v ->
            onAddProp("placeholder", "\"" + v + "\"")
        }
        PropTextRow("size", props.optString("size")) { v -> onAddProp("size", v) }
        PropTextRow("color", props.optString("color")) { v -> onAddProp("color", "\"" + v + "\"") }
        PropBoolRow("bold", props.optString("bold") == "true") { v -> onAddProp("bold", if (v) "true" else "false") }
        PropTextRow("orientation", props.optString("orientation").trim('\"')) { v ->
            onAddProp("orientation", "\"" + v + "\"")
        }
        PropTextRow("kind", props.optString("kind").trim('\"')) { v -> onAddProp("kind", "\"" + v + "\"") }
        // 节点自带的其他属性：任何视图都应能编辑自身全部属性
        val extras = remember(props) {
            val shown = setOf("text", "placeholder", "size", "color", "bold", "orientation", "kind", "modifier")
            val out = ArrayList<Pair<String, String>>()
            val ks = props.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                if (k !in shown) out.add(k to (runCatching { props[k] }.getOrNull()?.toString() ?: ""))
            }
            out.sortedBy { it.first }
        }
        extras.forEach { (k, raw) ->
            PropTextRow(k, raw.trim('"')) { v ->
                onAddProp(k, if (v.matches(Regex("-?\\d+(\\.\\d+)?"))) v else "\"" + v + "\"")
            }
        }
        Spacer(Modifier.height(6.dp))
        TinyOpBtn("清空属性", true) {
            for (k in PROP_EDIT_KEYS) onRemoveProp(k)
            onEdit()
        }
    }
}

private val PROP_EDIT_KEYS = listOf("text", "placeholder", "size", "color", "bold", "orientation", "kind", "modifier", "contentAlignment")

@Composable
private fun PropTextRow(label: String, value: String, onValue: (String) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = candy.ide.plugin.ui.PluginColors.TextDim, fontSize = 9.sp, modifier = Modifier.width(56.dp))
        BasicTextField(
            value = v,
            onValueChange = { v = it; onValue(it) },
            textStyle = TextStyle(color = candy.ide.plugin.ui.PluginColors.Text, fontSize = 9.sp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, candy.ide.plugin.ui.PluginColors.SurfaceVariant, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PropBoolRow(label: String, value: Boolean, onValue: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = candy.ide.plugin.ui.PluginColors.TextDim, fontSize = 9.sp, modifier = Modifier.width(56.dp))
        Text(
            if (value) "● true" else "○ false",
            color = if (value) candy.ide.plugin.ui.PluginColors.Primary else candy.ide.plugin.ui.PluginColors.TextDim,
            fontSize = 10.sp,
            modifier = Modifier.clickable { onValue(!value) },
        )
    }
}

/** 单个视图节点 → 缩略 Compose 渲染；children 递归。
 *  点击 = 选中（构建器交互，不运行任何应用逻辑）；选中节点加高亮边框 */
@Composable
internal fun ViewTreeNode(node: JSONObject, path: List<Int>, selPath: List<Int>?, fullscreen: Boolean, onSelect: (List<Int>) -> Unit) {
    val name = node.optString("name")
    val props = runCatching { node.getJSONObject("props") }.getOrNull()
    val kids = runCatching { node.getJSONArray("children") }.getOrNull()
    val childCount = kids?.length() ?: 0
    val horizontal = props?.optString("orientation")?.trim('\"') == "horizontal"
    val selected = selPath != null && selPath == path
    val selBorder: Modifier = if (selected) Modifier.border(1.5.dp, candy.ide.plugin.ui.PluginColors.Primary, RoundedCornerShape(3.dp)) else Modifier
    val click: Modifier = Modifier.clickable { onSelect(path) }
    val scale = if (fullscreen) 1f else 0.6f
    when (name) {
        "Box", "Column", "Row" -> {
            Box(
                modifier = Modifier
                    .widthIn(min = 24.dp)
                    .padding(2.dp)
                    .then(selBorder)
                    .then(click)
                    .border(1.dp, candy.ide.plugin.ui.PluginColors.SurfaceVariant, RoundedCornerShape(2.dp)),
            ) {
                if (childCount > 0) {
                    if (horizontal || name == "Row") {
                        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), path + i, selPath, fullscreen, onSelect) }
                        }
                    } else {
                        Column(modifier = Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), path + i, selPath, fullscreen, onSelect) }
                        }
                    }
                } else {
                    Text("Box", color = candy.ide.plugin.ui.PluginColors.TextDim, fontSize = 8.sp, modifier = Modifier.padding(2.dp))
                }
            }
        }
        "Text", "label" -> {
            val t = node.optString("text").trim('\"').ifEmpty { props?.optString("text")?.trim('\"') ?: "" }
            val sz = props?.optString("size")?.toIntOrNull()
            val bold = props?.optString("bold") == "true"
            Text(
                text = t.ifEmpty { "文本" },
                color = candy.ide.plugin.ui.PluginColors.Text,
                fontSize = (((sz ?: 12) * scale).coerceIn(7f, 16f)).sp,
                fontWeight = if (bold) FontWeight.Bold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.then(selBorder).then(click),
            )
        }
        "Button" -> {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .then(selBorder)
                    .then(click)
                    .border(1.dp, candy.ide.plugin.ui.PluginColors.Primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                if (childCount > 0) Column { repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), path + i, selPath, fullscreen, onSelect) } }
                else Text("按钮", color = candy.ide.plugin.ui.PluginColors.Primary, fontSize = 9.sp)
            }
        }
        "TextField" -> {
            val ph = node.optString("text").trim('\"').ifEmpty { props?.optString("placeholder")?.trim('\"') ?: "" }
            Box(
                modifier = Modifier
                    .widthIn(min = 60.dp)
                    .padding(2.dp)
                    .then(selBorder)
                    .then(click)
                    .border(1.dp, candy.ide.plugin.ui.PluginColors.SurfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(ph.ifEmpty { "输入框" }, color = candy.ide.plugin.ui.PluginColors.TextDim, fontSize = 9.sp, maxLines = 1)
            }
        }
        "Chart" -> {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .heightIn(min = 30.dp)
                    .padding(2.dp)
                    .then(selBorder)
                    .then(click)
                    .background(androidx.compose.ui.graphics.Color(0xFFE0AF68).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(4.dp),
            ) {
                Text("📊 柱状图", color = androidx.compose.ui.graphics.Color(0xFFE0AF68), fontSize = 9.sp)
            }
        }
        "Image" -> Box(
            modifier = Modifier
                .widthIn(min = 40.dp)
                .heightIn(min = 30.dp)
                .padding(2.dp)
                .then(selBorder)
                .then(click)
                .background(candy.ide.plugin.ui.PluginColors.SurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                .padding(4.dp),
        ) { Text("🖼", fontSize = 12.sp) }
        else -> {
            // @composable 调用节点（name 形如 "@rowsView"）或未知视图：按带框节点渲染
            val label = name.removePrefix("@")
            val isComposeCall = name.startsWith("@")
            Box(
                modifier = Modifier
                    .widthIn(min = 28.dp)
                    .padding(2.dp)
                    .then(selBorder)
                    .then(click)
                    .border(
                        1.dp,
                        if (isComposeCall) candy.ide.plugin.ui.PluginColors.Primary.copy(alpha = 0.6f) else candy.ide.plugin.ui.PluginColors.SurfaceVariant,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(3.dp),
            ) {
                Column {
                    Text(
                        label,
                        color = if (isComposeCall) candy.ide.plugin.ui.PluginColors.Primary else candy.ide.plugin.ui.PluginColors.TextDim,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (childCount > 0) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), path + i, selPath, fullscreen, onSelect) }
                    }
                }
            }
        }
    }
}
