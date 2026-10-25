package candy.ide.desktop.plugin.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.plugin.preview.ComposePreviewState
import candy.ide.desktop.plugin.preview.addChild
import candy.ide.desktop.plugin.preview.deepCopy
import candy.ide.desktop.plugin.preview.mkBox
import candy.ide.desktop.plugin.preview.mkText
import candy.ide.desktop.plugin.preview.nodeAt
import candy.ide.desktop.plugin.preview.removeChild
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import org.json.JSONObject

/**
 * @composable 布局预览侧栏（对齐安卓端 ComposePreviewPanel；自桌面宿主 ui 包迁入 android-project 插件，
 * 与安卓端「预览面板由插件提供」的结构一致）：
 *  - 点渲染的视图 = 选中（边框高亮）；视图不响应任何「运行期」点击（仅渲染，不运行代码）；
 *  - 左栏：视图树大纲，可 删除/添加/复制/剪切/粘贴 视图；
 *  - 右栏：设置选中视图的属性（text/placeholder/size/bold/orientation/kind/color）；
 *  - 顶栏「应用」把视图树再生成的函数体写回源码（编辑器撤销可回退）。
 *
 * 数据来自 LspAnalysis.composableTreeJson（宿主经 PluginHost.composableTreeJson 提供）：
 * "name","start","end","children":["name","text","props","children"]。
 * 本地树是源码的一个可编辑快照：外层 treeJson 变化（源码被编辑后刷新）时整体重置。
 */
@Composable
fun ComposePreviewPanel(
    state: ComposePreviewState,
    onClose: () -> Unit,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tree by remember(state.treeJson) { mutableStateOf(JSONObject(state.treeJson)) }
    var selPath by remember(state.treeJson) { mutableStateOf<List<Int>?>(null) }
    var showTree by remember { mutableStateOf(true) }
    var showProps by remember { mutableStateOf(false) }
    var clipboard by remember { mutableStateOf<JSONObject?>(null) }
    var dirty by remember(state.treeJson) { mutableStateOf(false) }

    fun mutate() { tree = JSONObject(tree.toString()); dirty = true }

    Column(
        modifier = modifier.background(CandySurface),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TinyTopBtn(if (showTree) "树▸" else "树", active = showTree) { showTree = !showTree }
            TinyTopBtn(if (showProps) "属性▸" else "属性", active = showProps && selPath != null) {
                if (selPath == null) {
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null && kids.length() > 0) selPath = listOf(0)
                }
                showProps = !showProps
            }
            Text(
                text = "@" + state.fnName + " 布局预览",
                color = CandyTextDim,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (dirty) {
                Text("未应用", color = CandyError, fontSize = 9.sp, modifier = Modifier.padding(end = 2.dp))
            }
            TinyTopBtn("应用", active = dirty) {
                onApply(tree.toString())
                dirty = false
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭预览", tint = CandyTextDim, modifier = Modifier.size(14.dp))
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            // 左栏：视图树大纲 + 节点操作
            if (showTree) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .background(CandyBg.copy(alpha = 0.6f))
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val sel = selPath?.let { nodeAt(tree, it) }
                    if (sel != null) TreeOpsBar(
                        canPaste = clipboard != null,
                        onDelete = { selPath?.let { p -> if (p.isNotEmpty()) { val par = nodeAt(tree, p.dropLast(1))!!; removeChild(par, p.last()); mutate() } } },
                        onCopy = { clipboard = sel.deepCopy() },
                        onCut = { clipboard = sel.deepCopy(); selPath?.let { p -> if (p.isNotEmpty()) { val par = nodeAt(tree, p.dropLast(1))!!; removeChild(par, p.last()); mutate() } } },
                        onPasteChild = { clipboard?.let { cb -> addChild(sel, cb.deepCopy()); mutate() } },
                        onAddBox = { addChild(sel, mkBox()); mutate() },
                        onAddText = { addChild(sel, mkText("文本")); mutate() },
                    )
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null) {
                        for (i in 0 until kids.length()) {
                            TreeRow(kids.getJSONObject(i), emptyList(), selPath, 0) { p -> selPath = p; showProps = true }
                        }
                    }
                }
            }
            // 中间：渲染区（点击视图 = 选中）
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).verticalScroll(rememberScrollState())) {
                Column {
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null) {
                        for (i in 0 until kids.length()) {
                            ViewTreeNode(kids.getJSONObject(i), emptyList(), selPath) { p -> selPath = p }
                        }
                    } else {
                        Text("树解析失败", color = CandyTextDim, fontSize = 10.sp)
                    }
                }
            }
            // 右栏：选中视图的属性
            if (showProps && selPath != null) {
                val node = nodeAt(tree, selPath!!)
                if (node != null) PropPanel(
                    node = node,
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

/** 顶栏小按钮：active 控制高亮色 */
@Composable
private fun TinyTopBtn(label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(24.dp)) {
        Text(
            label,
            color = when {
                active -> CandySecondary
                !enabled -> CandyTextDim
                else -> CandyPrimary
            },
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else null,
            maxLines = 1,
        )
    }
}

/** 树大纲一行：名字 + 摘要，点击选中，按路径高亮 */
@Composable
internal fun TreeRow(node: JSONObject, path: List<Int>, selPath: List<Int>?, depth: Int, onSelect: (List<Int>) -> Unit) {
    val name = node.optString("name")
    val txt = node.optString("text").trim('"')
    val selected = selPath != null && selPath == path
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(path) }
                .background(if (selected) CandyPrimary.copy(alpha = 0.15f) else Color.Transparent)
                .padding(start = (depth * 8).dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name.removePrefix("@"),
                color = if (selected) CandyPrimary else CandyText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (txt.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(txt, color = CandyTextDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            TinyOpBtn("删除", true, onDelete)
            TinyOpBtn("复制", true, onCopy)
            TinyOpBtn("剪切", true, onCut)
            TinyOpBtn("粘贴", canPaste, onPasteChild)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TinyOpBtn("+Box", true, onAddBox)
            TinyOpBtn("+Text", true, onAddText)
        }
    }
}

@Composable
private fun TinyOpBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.height(22.dp)) {
        Text(label, color = if (enabled) CandySecondary else CandyTextDim, fontSize = 10.sp)
    }
}

/** 右侧属性面板：对已知属性做行内编辑 */
@Composable
internal fun PropPanel(
    node: JSONObject,
    onEdit: () -> Unit,
    onAddProp: (String, String) -> Unit,
    onRemoveProp: (String) -> Unit,
) {
    val name = node.optString("name")
    val props = remember(node) { runCatching { node.getJSONObject("props") }.getOrNull() ?: JSONObject() }
    Column(
        modifier = Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(CandyBg.copy(alpha = 0.6f))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            name.removePrefix("@") + " 属性",
            color = CandyText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        // text：既是顶层 text 字段也是 props.text（取显示用值，写回统一写 props）
        val textDisp = node.optString("text").trim('"').ifEmpty { props.optString("text").trim('"') }
        PropTextRow("text", textDisp) { v ->
            node.put("text", "\"" + v + "\""); onEdit()
        }
        PropTextRow("placeholder", props.optString("placeholder").trim('"')) { v ->
            onAddProp("placeholder", "\"" + v + "\"")
        }
        PropTextRow("size", props.optString("size")) { v -> onAddProp("size", v) }
        PropTextRow("color", props.optString("color").trim('"')) { v ->
            onAddProp("color", "\"" + v + "\"")
        }
        PropBoolRow("bold", props.optString("bold") == "true") { v -> onAddProp("bold", if (v) "true" else "false") }
        PropTextRow("orientation", props.optString("orientation").trim('"')) { v ->
            onAddProp("orientation", "\"" + v + "\"")
        }
        PropTextRow("kind", props.optString("kind").trim('"')) { v ->
            onAddProp("kind", "\"" + v + "\"")
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
        Text(label, color = CandyTextDim, fontSize = 10.sp, modifier = Modifier.width(64.dp))
        BasicTextField(
            value = v,
            onValueChange = { v = it; onValue(it) },
            textStyle = TextStyle(color = CandyText, fontSize = 10.sp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CandySurfaceVariant, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PropBoolRow(label: String, value: Boolean, onValue: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CandyTextDim, fontSize = 10.sp, modifier = Modifier.width(64.dp))
        Text(
            if (value) "● true" else "○ false",
            color = if (value) CandyPrimary else CandyTextDim,
            fontSize = 10.sp,
            modifier = Modifier.clickable { onValue(!value) },
        )
    }
}

/**
 * 单个视图节点 -> 缩略 Compose 渲染；children 递归。
 * 点击 = 选中（构建器交互，不运行任何应用逻辑）；选中节点加高亮边框
 */
@Composable
internal fun ViewTreeNode(node: JSONObject, path: List<Int>, selPath: List<Int>?, onSelect: (List<Int>) -> Unit) {
    val name = node.optString("name")
    val props = runCatching { node.getJSONObject("props") }.getOrNull()
    val kids = runCatching { node.getJSONArray("children") }.getOrNull()
    val childCount = kids?.length() ?: 0
    val horizontal = props?.optString("orientation")?.trim('"') == "horizontal"
    val selected = selPath != null && selPath == path
    val selBorder: Modifier = if (selected) Modifier.border(1.5.dp, CandyPrimary, RoundedCornerShape(3.dp)) else Modifier
    val click: Modifier = Modifier.clickable { onSelect(path) }
    when (name) {
        "Box", "Column", "Row" -> {
            Box(
                modifier = Modifier
                    .widthIn(min = 24.dp)
                    .padding(2.dp)
                    .then(selBorder)
                    .then(click)
                    .border(1.dp, CandySurfaceVariant, RoundedCornerShape(2.dp)),
            ) {
                if (childCount > 0) {
                    if (horizontal || name == "Row") {
                        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), path + i, selPath, onSelect) }
                        }
                    } else {
                        Column(modifier = Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), path + i, selPath, onSelect) }
                        }
                    }
                } else {
                    Text("Box", color = CandyTextDim, fontSize = 9.sp, modifier = Modifier.padding(2.dp))
                }
            }
        }
        "Text", "label" -> {
            val t = node.optString("text").trim('"').ifEmpty { props?.optString("text")?.trim('"') ?: "" }
            val sz = props?.optString("size")?.toIntOrNull()
            val bold = props?.optString("bold") == "true"
            Text(
                text = t.ifEmpty { "文本" },
                color = CandyText,
                fontSize = ((sz ?: 12).coerceIn(7, 18)).sp,
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
                    .border(1.dp, CandyPrimary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                if (childCount > 0) Column { repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), path + i, selPath, onSelect) } }
                else Text("按钮", color = CandyPrimary, fontSize = 10.sp)
            }
        }
        "TextField" -> {
            val ph = node.optString("text").trim('"').ifEmpty { props?.optString("placeholder")?.trim('"') ?: "" }
            Box(
                modifier = Modifier
                    .widthIn(min = 60.dp)
                    .padding(2.dp)
                    .then(selBorder)
                    .then(click)
                    .border(1.dp, CandySurfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(ph.ifEmpty { "输入框" }, color = CandyTextDim, fontSize = 10.sp, maxLines = 1)
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
                    .background(Color(0xFFE0AF68).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(4.dp),
            ) {
                Text("📊 柱状图", color = Color(0xFFE0AF68), fontSize = 10.sp)
            }
        }
        "Image" -> Box(
            modifier = Modifier
                .widthIn(min = 40.dp)
                .heightIn(min = 30.dp)
                .padding(2.dp)
                .then(selBorder)
                .then(click)
                .background(CandySurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                .padding(4.dp),
        ) { Text("🖼", fontSize = 14.sp) }
        else -> {
            // @composable 调用节点（name 形如 @rowsView）或未知视图：按带框节点渲染
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
                        if (isComposeCall) CandyPrimary.copy(alpha = 0.6f) else CandySurfaceVariant,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(3.dp),
            ) {
                Column {
                    Text(
                        label,
                        color = if (isComposeCall) CandyPrimary else CandyTextDim,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (childCount > 0) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), path + i, selPath, onSelect) }
                    }
                }
            }
        }
    }
}
