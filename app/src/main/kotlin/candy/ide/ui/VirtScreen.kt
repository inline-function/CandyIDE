package candy.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * 虚拟桥接环境：模拟「APK 安装后」的效果 —— 把工程的根组合函数（layout(...) 挂的 @composable）
 * 以手机壳形态整屏渲染。仅渲染、不运行：视图的点击事件不生效（按钮也不触发 onClick）。
 */
@Composable
fun VirtScreen(vm: candy.ide.vm.CandyViewModel) {
    val pv = vm.virtState.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(candy.ide.ui.theme.CandyBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 极简顶栏：返回编辑器 + 虚拟环境标识
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.screen.value = candy.ide.ui.Screen.Editor }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回编辑器",
                    tint = candy.ide.ui.theme.CandyText,
                )
            }
            Text(
                "虚拟桥接环境",
                color = candy.ide.ui.theme.CandyText,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "仅渲染不运行",
                color = candy.ide.ui.theme.CandySecondary,
                fontSize = 10.sp,
            )
        }
        Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            if (pv == null) {
                Text(
                    "未提取到视图树：请确认入口 layout(根组合函数) 调用存在",
                    color = candy.ide.ui.theme.CandyTextDim,
                    fontSize = 12.sp,
                )
            } else {
                PhoneFrame(pv)
            }
        }
    }
}

/** 手机壳：状态栏 + 屏幕区（渲染根组合函数的视图树） */
@Composable
private fun PhoneFrame(pv: candy.ide.ui.ComposePreviewState) {
    val root = runCatching { JSONObject(pv.treeJson) }.getOrNull()
    Column(
        modifier = Modifier
            .width(320.dp)
            .height(600.dp)
            .background(Color(0xFF0B090F), RoundedCornerShape(18.dp))
            .padding(6.dp),
    ) {
        // 模拟状态栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(candy.ide.ui.theme.CandySurface)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "21:30",
                color = candy.ide.ui.theme.CandyTextDim,
                fontSize = 8.sp,
            )
            Spacer(Modifier.weight(1f))
            Text("📶 🔋", fontSize = 8.sp)
        }
        // 屏幕区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(candy.ide.ui.theme.CandySurface)
                .padding(6.dp),
        ) {
            if (root != null) {
                val kids = runCatching { root.getJSONArray("children") }.getOrNull()
                if (kids != null) {
                    for (i in 0 until kids.length()) {
                        VirtNode(kids.getJSONObject(i))
                    }
                }
            } else {
                Text("树解析失败", color = candy.ide.ui.theme.CandyTextDim, fontSize = 10.sp)
            }
        }
    }
}

/** 虚拟环境的视图节点：只渲染，不接收任何点击（运行期交互不模拟） */
@Composable
private fun VirtNode(node: JSONObject) {
    val name = node.optString("name")
    val props = runCatching { node.getJSONObject("props") }.getOrNull()
    val kids = runCatching { node.getJSONArray("children") }.getOrNull()
    val childCount = kids?.length() ?: 0
    val horizontal = props?.optString("orientation")?.trim('"') == "horizontal"
    when (name) {
        "Box", "Column", "Row" -> {
            Box(modifier = Modifier.padding(2.dp)) {
                if (childCount > 0) {
                    if (horizontal || name == "Row") {
                        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(childCount) { i -> VirtNode(kids!!.getJSONObject(i)) }
                        }
                    } else {
                        Column(modifier = Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(childCount) { i -> VirtNode(kids!!.getJSONObject(i)) }
                        }
                    }
                }
            }
        }
        "Text", "label" -> {
            val txt = node.optString("text").trim('"').ifEmpty { props?.optString("text")?.trim('"') ?: "" }
            val sz = props?.optString("size")?.toIntOrNull()
            val bold = props?.optString("bold") == "true"
            Text(
                text = txt.ifEmpty { "文本" },
                color = candy.ide.ui.theme.CandyText,
                fontSize = ((sz ?: 14).toFloat().coerceIn(8f, 22f)).sp,
                fontWeight = if (bold) FontWeight.Bold else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        "Button" -> {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .background(candy.ide.ui.theme.CandyPrimary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                if (childCount > 0) Column { repeat(childCount) { i -> VirtNode(kids!!.getJSONObject(i)) } }
                else Text("按钮", color = candy.ide.ui.theme.CandyBg, fontSize = 10.sp)
            }
        }
        "TextField" -> {
            val ph = props?.optString("placeholder")?.trim('"') ?: ""
            Box(
                modifier = Modifier
                    .widthIn(min = 60.dp)
                    .padding(2.dp)
                    .border(1.dp, candy.ide.ui.theme.CandySurfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(ph.ifEmpty { "输入框" }, color = candy.ide.ui.theme.CandyTextDim, fontSize = 10.sp, maxLines = 1)
            }
        }
        "Chart" -> {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .heightIn(min = 36.dp)
                    .padding(2.dp)
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
                .background(candy.ide.ui.theme.CandySurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                .padding(4.dp),
        ) { Text("🖼", fontSize = 12.sp) }
        else -> {
            // @composable 调用节点或未知视图：展开其内联子树
            if (childCount > 0) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(childCount) { i -> VirtNode(kids!!.getJSONObject(i)) }
            } else Text(
                name.removePrefix("@"),
                color = candy.ide.ui.theme.CandyTextDim,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
