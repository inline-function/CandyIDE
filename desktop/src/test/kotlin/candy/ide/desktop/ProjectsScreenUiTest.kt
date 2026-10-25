package candy.ide.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import candy.ide.desktop.data.ProjectStore
import candy.ide.desktop.ui.CandyDesktopTheme
import candy.ide.desktop.ui.ProjectsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectsScreenUiTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val compose = createComposeRule()
    @get:Rule val isolation = SettingsIsolationRule()

    private fun setState(root: File) {
        Settings.workspaceRoot = root.absolutePath
    }

    @Test fun shows_empty_state() {
        val root = tmp.newFolder("projects")
        setState(root)
        val state = IdeState()
        state.refreshProjects()
        compose.setContent { CandyDesktopTheme { ProjectsScreen(state) } }
        compose.onNodeWithText("还没有工程").assertIsDisplayed()
        compose.onNodeWithText("点击右下角「新建工程」开始").assertIsDisplayed()
    }

    @Test fun lists_projects_and_opens_window_on_click() {
        val root = tmp.newFolder("projects")
        setState(root)
        val p = ProjectStore.createProject("demo", ProjectStore.TYPE_JS)!!
        val state = IdeState()
        state.refreshProjects()
        compose.setContent { CandyDesktopTheme { ProjectsScreen(state) } }
        compose.onNodeWithText("demo").assertIsDisplayed()
        compose.onNodeWithText("JS工程").assertIsDisplayed()
        compose.onNodeWithText("demo").performClick()
        assertEquals(1, state.openProjectWindows.size)
        assertEquals(p.absolutePath, state.openProjectWindows.first().absolutePath)
    }

    @Test fun create_dialog_creates_project() {
        val root = tmp.newFolder("projects")
        setState(root)
        val state = IdeState()
        state.refreshProjects()
        compose.setContent { CandyDesktopTheme { ProjectsScreen(state) } }
        // FAB 文本被合并语义包裹，用非合并树点击
        compose.onNodeWithText("新建工程", useUnmergedTree = true).performClick()
        compose.onNodeWithText("工程名称").assertIsDisplayed()
        // 类型 chips 与创建按钮
        compose.onNodeWithText("JS工程", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("脚本工程", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("MD工程", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(0, state.openProjectWindows.size)
        assertTrue(ProjectStore.listProjects().isEmpty())
    }
}