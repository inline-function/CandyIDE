package candy.ide.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.DesktopEngine
import candy.ide.desktop.IdeState
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import java.io.File

/** 标准库查看（sublimation/core）：双栏，左文件列表右内容，JBM 等宽显示 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StdlibViewerScreen(state: IdeState) {
    var files by remember { mutableStateOf<List<File>?>(null) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var content by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        files = runCatching {
            val root = DesktopEngine.coreDir
            root.walkTopDown().filter { it.isFile }
                .sortedBy { it.relativeTo(root).path.replace('\\', '/') }
                .toList()
        }.getOrNull()
        selectedFile = files?.firstOrNull()
    }
    LaunchedEffect(selectedFile) {
        content = selectedFile?.let { runCatching { it.readText() }.getOrElse { "读取失败" } } ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标准库 · core") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = candy.ide.desktop.DesktopScreen.Settings }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
            )
        },
        containerColor = CandyBg,
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 左：文件列表
            Column(
                modifier = Modifier.weight(0.35f).verticalScroll(rememberScrollState()).padding(end = 4.dp),
            ) {
                val list = files
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CandyPrimary)
                    }
                } else {
                    list.forEach { f ->
                        Surface(
                            color = if (selectedFile == f) CandyPrimary.copy(alpha = 0.15f) else CandySurface,
                            onClick = { selectedFile = f },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = CandyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stdlibName(f), color = CandyText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            // 右：源码内容
            Column(
                modifier = Modifier.weight(0.65f).verticalScroll(rememberScrollState()).padding(start = 8.dp, top = 4.dp),
            ) {
                selectedFile?.let { f ->
                    Text(stdlibName(f), color = CandyTextDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Text(
                        content,
                        color = CandyText,
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 相对于 core 根的显示名（core 已按模块分目录：math/number/io …） */
private fun stdlibName(f: java.io.File): String =
    f.relativeTo(candy.ide.desktop.DesktopEngine.coreDir).path.replace('\\', '/')
