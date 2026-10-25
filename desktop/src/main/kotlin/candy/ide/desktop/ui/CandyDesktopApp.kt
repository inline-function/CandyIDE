package candy.ide.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import candy.ide.desktop.DesktopScreen
import candy.ide.desktop.DesktopEngine
import candy.ide.desktop.IdeState
import candy.ide.ui.theme.CandyBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 与 Android 端 CandyApp 同构：全屏页面切换（无导航栏），各页面自带 TopAppBar/FAB */
@Composable
fun CandyDesktopApp(state: IdeState) {
    val agent = androidx.compose.runtime.remember { candy.ide.desktop.agent.AgentState() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { runCatching { DesktopEngine.lsp } }
        runCatching { candy.ide.desktop.Schemes.loadCurrent() }
    }
    // 启动即加载工程列表；之后持续刷新（与 Android VM 的 ON_RESUME 刷新对齐，
    // 同时保证工程窗口的「最近打开」排序、外部新建/删除即时反映）
    LaunchedEffect(Unit) {
        state.refreshProjects()
        while (true) {
            delay(2000)
            state.refreshProjects()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(CandyBg)) {
        when (state.screen) {
            DesktopScreen.Projects -> ProjectsScreen(state)
            DesktopScreen.Settings -> SettingsScreen(state)
            DesktopScreen.Docs -> DocsScreen(state)
            DesktopScreen.Scheme -> SchemeScreen(state)
            DesktopScreen.ProjectSettings -> ProjectSettingsScreen(state)
            DesktopScreen.About -> AboutScreen(state)
            DesktopScreen.Stdlib -> StdlibViewerScreen(state)
            DesktopScreen.IdeLog -> IdeLogScreen(state)
            DesktopScreen.Agent -> AgentScreen(
                state = state,
                agent = agent,
                onBack = { state.screen = DesktopScreen.Projects },
                onOpenSettings = { state.screen = DesktopScreen.Settings },
            )
        }
        ToastOverlay(state)
    }
}
