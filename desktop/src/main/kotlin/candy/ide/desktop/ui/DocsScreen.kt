package candy.ide.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.md.MarkdownText
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim

/**
 * 文档查看器：
 * - 语言文档 / IDE 文档：与 Android 端共享的 assets/builtin（构建期 sync）
 * - 标准库源码：sublimation/core（构建期 sync 为 classpath 资源）
 */
@Composable
fun DocsScreen(state: candy.ide.desktop.IdeState) {
    var selected by remember { mutableStateOf<String?>(null) }
    val docs = remember { Resources.index("builtin-index.txt") }
    val stdlib = remember { Resources.index("stdlib-index.txt") }
    if (selected == null && docs.isNotEmpty()) selected = docs.first()
    Surface(modifier = Modifier.fillMaxSize(), color = CandyBg) {
        Row(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.width(320.dp).fillMaxHeight(), color = CandySurface) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    DocSection("语言文档", docs.filter { it.startsWith("lang-doc/") }, selected) { selected = it }
                    HorizontalDivider()
                    DocSection("IDE 文档", docs.filter { it.startsWith("ide-doc/") }, selected) { selected = it }
                    HorizontalDivider()
                    DocSection("标准库", stdlib.map { "stdlib/" + it }, selected) { selected = it }
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
                val sel = selected
                if (sel != null) {
                    val text = remember(sel) { Resources.read(sel) } ?: "(读取失败：$sel)"
                    if (sel.startsWith("stdlib/")) {
                        Text(
                            text,
                            color = CandyText,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        MarkdownText(
                            source = text,
                            modifier = Modifier.fillMaxWidth(),
                            onLink = { Platform.openInBrowser(it) },
                        )
                    }
                } else {
                    Text("选择左侧文档", color = CandyTextDim)
                }
            }
        }
    }
}

@Composable
private fun DocSection(title: String, items: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Text(title, color = CandyPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
    for (item in items) {
        val name = item.substringAfter("/").substringAfterLast("/").removeSuffix(".md")
        Text(
            name,
            color = if (selected == item) CandyPrimary else CandyText,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(start = 22.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
        )
    }
    if (items.isEmpty()) Text("（无）", color = CandyTextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 22.dp, bottom = 6.dp))
}
