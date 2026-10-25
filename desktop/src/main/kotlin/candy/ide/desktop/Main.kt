package candy.ide.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.rememberWindowState
import candy.ide.desktop.ui.CandyDesktopApp
import candy.ide.desktop.ui.CandyDesktopTheme
import candy.ide.desktop.ui.EditorScreen
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import java.io.File

/**
 * 桌面端入口：多窗口架构——主页（工程列表）常驻，
 * 点击工程在独立窗口打开编辑器（与 Android 端单窗口导航的唯一结构性差异）。
 * 关闭有未保存修改的编辑器窗口前会弹确认。
 */
fun main() = application {
    val home = remember { IdeState() }
    // 插件框架：后台装载内置插件（对齐安卓端 VM.bootstrap 的 PluginLoader.loadAll；装载失败只记日志，
    // 不阻断启动；LSP 分析器惰性初始化时会自取已注册的语言服务扩展）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { candy.ide.desktop.plugin.PluginBootstrap.load(home) }
    }

    // 主窗口：工程页 / 设置 / 文档（不随工程打开而关闭）
    Window(
        onCloseRequest = ::exitApplication,
        title = "CandyIDE",
        icon = painterResource("icon.png"),
        state = rememberWindowState(width = 1200.dp, height = 900.dp),
    ) {
        CandyDesktopTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = CandyBg) {
                CandyDesktopApp(home)
            }
        }
    }

    // 每个工程一个独立编辑器窗口
    home.openProjectWindows.forEach { dir ->
        key(dir.absolutePath) {
            EditorWindow(dir, onClose = { home.closeProjectWindow(dir) })
        }
    }
}

@Composable
private fun ApplicationScope.EditorWindow(dir: File, onClose: () -> Unit) {
    val state = remember(dir) { IdeState().apply { openProject(dir) } }
    var pendingClose by remember { mutableStateOf(false) }
    // 关窗前：有脏文件 → 确认；没有 → 直接关
    val dirtyCount = state.openFiles.count { state.dirty[it.absolutePath] == true }
    Window(
        onCloseRequest = { pendingClose = true },
        title = "CandyIDE — " + dir.name,
        icon = painterResource("icon.png"),
        state = rememberWindowState(width = 1320.dp, height = 920.dp),
    ) {
        CandyDesktopTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = CandyBg) {
                EditorScreen(state)
                if (pendingClose) {
                    if (dirtyCount == 0) {
                        LaunchedEffect(Unit) { onClose() }
                    } else {
                        AlertDialog(
                            onDismissRequest = { pendingClose = false },
                            title = { Text("关闭前保存修改？", color = MaterialTheme.colorScheme.onSurface) },
                            text = {
                                Text("该工程有 " + dirtyCount + " 个文件未保存。不保存将丢失修改。", color = CandyTextDim)
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val n = state.saveAllDirty()
                                    state.toast = if (n > 0) "已保存 " + n + " 个文件" else "无需保存"
                                    pendingClose = false
                                    onClose()
                                }) { Text("保存并关闭", color = CandyPrimary) }
                            },
                            dismissButton = {
                                androidx.compose.foundation.layout.Row {
                                    TextButton(onClick = { pendingClose = false; onClose() }) {
                                        Text("不保存关闭", color = CandyPrimary)
                                    }
                                    TextButton(onClick = { pendingClose = false }) { Text("取消") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
