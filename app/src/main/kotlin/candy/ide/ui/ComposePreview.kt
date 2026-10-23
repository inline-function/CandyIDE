package candy.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * @composable 布局预览悬浮窗：把 LSP 提取的视图树 JSON 渲染成缩略 Compose 布局。
 *  尺寸约为屏幕 1/5（比 1/4 略小）；可关闭、可全屏；编辑函数体 1s 后无错自动刷新（见 EditorScreen）。
 *  JSON 形如 {"name":"card","start":2,"end":9,"children":[{"name":"Box","props":{...},"children":[...]}]}
 */
data class ComposePreviewState(
    val fnName: String,
    val start: Int,
    val end: Int,
    val treeJson: String,
)

@Composable
fun ComposePreviewPanel(
    state: ComposePreviewState,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit,
) {
    val root = runCatching { JSONObject(state.treeJson) }.getOrNull()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(candy.ide.ui.theme.CandySurface),
    ) {
        // 标题栏：函数名 + 全屏切换 + 关闭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "@" + state.fnName + " 布局预览",
                color = candy.ide.ui.theme.CandyText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onToggleFullscreen, modifier = Modifier.padding(0.dp)) {
                Text(
                    if (fullscreen) "退出全屏" else "全屏",
                    color = candy.ide.ui.theme.CandyPrimary,
                    fontSize = 10.sp,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.padding(0.dp)) {
                Text("✕", color = candy.ide.ui.theme.CandyTextDim, fontSize = 12.sp)
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            if (root != null) {
                ViewTreeNode(root, fullscreen)
            } else {
                Text("树解析失败", color = candy.ide.ui.theme.CandyTextDim, fontSize = 10.sp)
            }
        }
    }
}

/** 单个视图节点 → 缩略 Compose 渲染；children 递归 */
@Composable
private fun ViewTreeNode(node: JSONObject, fullscreen: Boolean) {
    val name = node.optString("name")
    val props = runCatching { node.getJSONObject("props") }.getOrNull()
    val kids = runCatching { node.getJSONArray("children") }.getOrNull()
    val childCount = kids?.length() ?: 0
    val horizontal = props?.optString("orientation")?.trim('"') == "horizontal"
    val scale = if (fullscreen) 1f else 0.6f
    when (name) {
        "Box", "Column", "Row" -> {
            val shape = RoundedCornerShape(2.dp)
            Box(
                modifier = Modifier
                    .widthIn(min = 24.dp)
                    .padding(2.dp)
                    .border(1.dp, candy.ide.ui.theme.CandySurfaceVariant, shape),
            ) {
                if (childCount > 0) {
                    if (horizontal || name == "Row") {
                        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), fullscreen) }
                        }
                    } else {
                        Column(modifier = Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), fullscreen) }
                        }
                    }
                } else {
                    Text(
                        "Box",
                        color = candy.ide.ui.theme.CandyTextDim,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
        "Text", "label" -> {
            val t = node.optString("text").trim('"').ifEmpty { props?.optString("text")?.trim('"') ?: "" }
            val sz = props?.optString("size")?.toIntOrNull()
            val bold = props?.optString("bold") == "true"
            Text(
                text = t.ifEmpty { "文本" },
                color = candy.ide.ui.theme.CandyText,
                fontSize = (((sz ?: 12) * scale).coerceIn(7f, 16f)).sp,
                fontWeight = if (bold) FontWeight.Bold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        "Button" -> {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .border(1.dp, candy.ide.ui.theme.CandyPrimary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                if (childCount > 0) Column { repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), fullscreen) } }
                else Text("按钮", color = candy.ide.ui.theme.CandyPrimary, fontSize = 9.sp)
            }
        }
        "TextField" -> {
            val ph = node.optString("text").trim('"').ifEmpty { props?.optString("placeholder")?.trim('"') ?: "" }
            Box(
                modifier = Modifier
                    .widthIn(min = 60.dp)
                    .padding(2.dp)
                    .border(1.dp, candy.ide.ui.theme.CandySurfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(ph.ifEmpty { "输入框" }, color = candy.ide.ui.theme.CandyTextDim, fontSize = 9.sp, maxLines = 1)
            }
        }
        "Chart" -> {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .heightIn(min = 30.dp)
                    .padding(2.dp)
                    .background(Color(0xFFE0AF68).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(4.dp),
            ) {
                Text("📊 柱状图", color = Color(0xFFE0AF68), fontSize = 9.sp)
            }
        }
        "Image" -> Box(
            modifier = Modifier
                .widthIn(min = 40.dp)
                .heightIn(min = 30.dp)
                .padding(2.dp)
                .background(candy.ide.ui.theme.CandySurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
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
                    .border(
                        1.dp,
                        if (isComposeCall) candy.ide.ui.theme.CandyPrimary.copy(alpha = 0.6f) else candy.ide.ui.theme.CandySurfaceVariant,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(3.dp),
            ) {
                Column {
                    Text(
                        label,
                        color = if (isComposeCall) candy.ide.ui.theme.CandyPrimary else candy.ide.ui.theme.CandyTextDim,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (childCount > 0) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(childCount) { i -> ViewTreeNode(kids!!.getJSONObject(i), fullscreen) }
                    }
                }
            }
        }
    }
}
