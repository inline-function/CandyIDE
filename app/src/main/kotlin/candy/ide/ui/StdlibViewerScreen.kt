package candy.ide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import candy.ide.editor.SoraEditorView
import candy.ide.vm.CandyViewModel
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 标准库只读查看页：转到定义指向 stdlib 时打开这里。
 * 编辑器复用 SoraEditorView 的只读模式（保留高亮/行号/长按转定义，禁编辑）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StdlibViewerScreen(vm: CandyViewModel) {
    val ctx = LocalContext.current
    val file = vm.stdlibFile.value
    var editor by remember { mutableStateOf<CodeEditor?>(null) }

    BackHandler { vm.closeStdlib() }

    Scaffold(
        containerColor = candy.ide.ui.theme.CandyBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (file != null) "标准库 · 只读 · " + file.name else "标准库 · 只读",
                        color = candy.ide.ui.theme.CandyText,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { vm.closeStdlib() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = candy.ide.ui.theme.CandyTextDim,
                        )
                    }
                },
            )
        },
    ) { pad ->
        if (file == null) return@Scaffold
        SoraEditorView(
            modifier = Modifier.fillMaxSize().padding(pad),
            file = file,
            vm = vm,
            textProvider = { f -> runCatching { f.readText() }.getOrDefault("") },
            readOnly = true,
            onEditorReady = { editor = it },
            onLongPress = { _, _, _, _ -> },
        )
        // 跳转行号消费（与编辑页同一机制）
        LaunchedEffect(file, vm.pendingGotoLine) {
            val target = vm.pendingGotoLine
            if (target >= 0) {
                val ed = editor
                if (ed != null) runCatching {
                    val line = target.coerceIn(0, (ed.text.lineCount - 1).coerceAtLeast(0))
                    ed.setSelection(line, 0, true)
                    // 消费掉，避免回到编辑页时重复跳转一次
                    vm.consumeGoto()
                }
            }
        }
    }
    // 提示一次只读
    LaunchedEffect(file) {
        if (file != null) android.widget.Toast.makeText(
            ctx, "标准库文件，只读查看", android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
}
