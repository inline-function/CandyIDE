package candy.ide.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel

/**
 * 布局预览全屏页（像 AI 助手页那样单开一页）：
 * 从编辑器悬浮窗的「全屏」进入；左滑栏视图树、中间渲染区、右滑栏属性，
 * 「应用」把编辑后的视图树写回源码（保留函数签名），返回键回编辑器。
 * 仅渲染不运行：视图点击只选中，不响应运行期行为。
 */
@Composable
fun PreviewScreen(vm: CandyViewModel) {
    val pv = vm.composePreviewFull.value
    if (pv == null) {
        // 状态丢失（被清空）直接回编辑器
        Box(modifier = Modifier.fillMaxSize().background(CandyBg), contentAlignment = Alignment.Center) {
            Text("没有可预览的布局", color = CandyTextDim)
        }
        return
    }
    // 本地可编辑的视图树副本：确定或取消才影响源码
    var tree by remember(pv) { mutableStateOf(JSONObject(pv.treeJson)) }
    var dirty by remember(pv) { mutableStateOf(false) }
    var selPath by remember(pv) { mutableStateOf<List<Int>?>(null) }
    var showTree by remember(pv) { mutableStateOf(true) }
    var showProps by remember(pv) { mutableStateOf(false) }
    fun mutate() { tree = JSONObject(tree.toString()); dirty = true }

    Scaffold(
        containerColor = CandyBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.screen.value = Screen.Editor }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回编辑器", tint = CandyText)
                }
                Text(
                    "@" + pv.fnName + " 布局预览",
                    color = CandyText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                TopBarToggle(if (showTree) "树▸" else "树", active = showTree) { showTree = !showTree }
                TopBarToggle(
                    if (showProps) "属性▸" else "属性",
                    active = showProps && selPath != null,
                ) {
                    // 没选中视图时默认选第一个顶层视图，属性面板立即可用
                    if (selPath == null) {
                        val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                        if (kids != null && kids.length() > 0) selPath = listOf(0)
                    }
                    showProps = !showProps
                }
                if (dirty) {
                    Text("未应用", color = CandyError, fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                }
                TextButton2(
                    label = "应用",
                    emphasized = dirty,
                ) {
                    // 写回源码会让整段函数体重生成（丢失 @mut var 声明与按钮逻辑），
                    // 暂不落盘：树编辑只在预览内生效，等带源码区间的精确补丁再开启
                    dirty = false
                }
            }
        },
    ) { pad ->
        Row(modifier = Modifier.fillMaxSize().padding(pad)) {
            // 左滑栏：视图树大纲 + 节点操作
            if (showTree) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .background(CandySurface)
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val sel = selPath?.let { nodeAt(tree, it) }
                    if (sel != null) TreeOpsBar(
                        canPaste = false,
                        onDelete = {
                            selPath?.let { p ->
                                if (p.isNotEmpty()) {
                                    val par = nodeAt(tree, p.dropLast(1))!!
                                    removeChild(par, p.last())
                                    mutate()
                                }
                            }
                        },
                        onCopy = {},
                        onCut = {},
                        onPasteChild = {},
                        onAddBox = { addChild(sel, mkBox()); mutate() },
                        onAddText = { addChild(sel, mkText("文本")); mutate() },
                    )
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null) {
                        for (i in 0 until kids.length()) {
                            TreeRow(kids.getJSONObject(i), emptyList(), selPath, 0) { p ->
                                selPath = p
                                showProps = true
                            }
                        }
                    }
                }
            }
            // 中间：渲染区（点击视图 = 选中；不响应运行期点击）
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
                Column {
                    val kids = runCatching { tree.getJSONArray("children") }.getOrNull()
                    if (kids != null) {
                        for (i in 0 until kids.length()) {
                            ViewTreeNode(kids.getJSONObject(i), emptyList(), selPath, true) { p -> selPath = p }
                        }
                    } else {
                        Text("树解析失败", color = CandyTextDim, fontSize = 12.sp)
                    }
                }
            }
            // 右滑栏：选中视图的属性
            if (showProps && selPath != null) {
                val node = nodeAt(tree, selPath!!)
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

@Composable
private fun TopBarToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) CandySecondary else CandyTextDim,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.Bold else null,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun TextButton2(label: String, emphasized: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (emphasized) CandySecondary else CandyPrimary,
        fontSize = 14.sp,
        fontWeight = if (emphasized) FontWeight.Bold else null,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
