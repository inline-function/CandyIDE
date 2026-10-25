package candy.ide.desktop

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import candy.ide.desktop.ui.CandyDesktopTheme
import candy.ide.desktop.ui.SchemeScreen
import candy.ide.desktop.ui.SettingsScreen
import candy.ide.desktop.ui.ProjectSettingsScreen
import candy.ide.desktop.ui.GitPanel
import candy.ide.desktop.ui.IdeLogScreen
import candy.ide.desktop.data.ProjectStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 新页面：设置入口、配色方案页、工程设置页 */
class ScreensUiTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val compose = createComposeRule()
    @Rule fun isolation() = SettingsIsolationRule()

    private fun setState() {
        Settings.workspaceRoot = tmp.newFolder("projects").absolutePath
    }

    @Test fun settings_shows_scheme_and_about_entries() {
        setState()
        val state = IdeState()
        Schemes.loadCurrent()
        compose.setContent { CandyDesktopTheme { SettingsScreen(state) } }
        compose.onAllNodesWithText("配色方案", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("标准库", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("关于 CandyIDE", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun scheme_screen_lists_builtin_tokens_and_scheme_rows() {
        setState()
        val state = IdeState()
        Schemes.select("fuhuan")
        compose.setContent { CandyDesktopTheme { SchemeScreen(state) } }
        compose.onNodeWithText("配色：浮幻").assertIsDisplayed()
        // 浮幻 + 默认 两个方案行
        compose.onAllNodesWithText("浮幻", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("默认", useUnmergedTree = true).assertCountEquals(1)
        // token 标签存在
        compose.onNodeWithText("关键字", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("字符串", useUnmergedTree = true).assertIsDisplayed()
    }


    @Test fun ide_log_screen_lists_lines_and_clears() {
        setState()
        val state = IdeState()
        state.log("引擎就绪")
        state.log("加载失败", candy.ide.desktop.OutputLine.Kind.Error)
        compose.setContent { CandyDesktopTheme { IdeLogScreen(state) } }
        compose.onNodeWithText("IDE 日志").assertIsDisplayed()
        compose.onNodeWithText("引擎就绪", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("清空").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("引擎就绪", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun git_panel_renders_in_non_repo_project() {
        setState()
        val state = IdeState()
        val dir = ProjectStore.createProject("uiTestGit", ProjectStore.TYPE_JS)!!
        state.openProject(dir)
        compose.setContent { CandyDesktopTheme { GitPanel(state, dir, onClose = {}) } }
        compose.onNodeWithText("Git").assertIsDisplayed()
        compose.waitForIdle()
    }

    @Test fun project_settings_shows_project_info() {
        setState()
        val state = IdeState()
        val dir = ProjectStore.createProject("uiTestPs", ProjectStore.TYPE_JS)!!
        state.projectSettingsTarget = dir
        state.screen = DesktopScreen.ProjectSettings
        compose.setContent { CandyDesktopTheme { ProjectSettingsScreen(state) } }
        compose.onNodeWithText("工程设置").assertIsDisplayed()
        // 名称信息行 + 重命名行的副标题各出现一次
        compose.onAllNodesWithText("uiTestPs", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("重命名工程", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("在文件管理器中打开", useUnmergedTree = true).assertIsDisplayed()
        // 返回主页
        ProjectStore.deleteProject(dir)
    }
}
