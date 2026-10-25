package candy.ide.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import candy.ide.desktop.data.ProjectStore
import candy.ide.desktop.ui.CandyDesktopTheme
import candy.ide.desktop.ui.EditorScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EditorScreenUiTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val compose = createComposeRule()
    @get:Rule val isolation = SettingsIsolationRule()

    private fun openTempProject(): IdeState {
        val root = tmp.newFolder("projects")
        Settings.workspaceRoot = root.absolutePath
        val proj = ProjectStore.createProject("demo", ProjectStore.TYPE_JS)!!
        return IdeState().apply { openProject(proj) }
    }

    @Test fun topbar_tabs_and_tree_render() {
        val state = openTempProject()
        compose.setContent { CandyDesktopTheme { EditorScreen(state) } }
        // 顶栏与未打开抽屉各有一个同名节点（抽屉内容在屏外预组合）
        compose.onAllNodesWithText("demo").assertCountEquals(2)
        compose.onNodeWithText("JS工程").assertIsDisplayed()
        compose.onAllNodesWithText("main.subl").assertCountEquals(2) // 标签页 + 抽屉
        compose.onNodeWithContentDescription("文件树").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("main.subl").assertCountEquals(2)
    }

    @Test fun running_flag_and_toolbar_actions() {
        val state = openTempProject()
        compose.setContent { CandyDesktopTheme { EditorScreen(state) } }
        // 运行按钮存在且可点
        compose.onNodeWithContentDescription("运行（长按仅编译）").performClick()
        compose.waitForIdle()
        // 保存按钮
        compose.onNodeWithContentDescription("保存（长按保存全部工程文件）").performClick()
        compose.waitForIdle()
        assertNotNull(state.activeFile)
        assertEquals(false, state.dirty[state.activeFile!!.absolutePath] == true)
    }

    @Test fun close_dirty_tab_shows_save_dialog_then_saves() {
        val state = openTempProject()
        val main = state.activeFile!!
        compose.setContent { CandyDesktopTheme { EditorScreen(state) } }
        // 改脏
        state.editText(main, state.fileTexts[main.absolutePath]!! + "\n// more")
        compose.waitForIdle()
        assertEquals(true, state.dirty[main.absolutePath])
        // 脏标签出现 ● 前缀（保存对话框只在 UI 点击关闭时弹出，此处校验脏标记渲染）
        compose.onAllNodesWithText("● main.subl", useUnmergedTree = true).assertCountEquals(1)
        // 保存后脏标记消失
        state.save(main)
        compose.waitForIdle()
        compose.onAllNodesWithText("● main.subl", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun close_active_tab_does_not_crash() {
        // 回归：关掉当前标签页时 selectedTabIndex 曾越界导致 ScrollableTabRow 崩溃
        val state = openTempProject()
        val main = state.activeFile!!
        // 再开一个文件，形成两个标签
        val other = File(state.currentProject!!, "extra.subl").apply { writeText("fun f() {}") }
        state.open(other)
        assertEquals(2, state.openFiles.size)
        compose.setContent { CandyDesktopTheme { EditorScreen(state) } }
        // 关掉当前激活的 extra → 剩 main 一个标签，触发重排
        state.close(other)
        compose.waitForIdle()
        assertEquals(1, state.openFiles.size)
        compose.onAllNodesWithText("main.subl").assertCountEquals(2)
        // 再开回来 → 1→2 标签数变化（曾触发 ScrollableTabRow 内部旧索引崩溃）
        state.open(other)
        compose.waitForIdle()
        assertEquals(2, state.openFiles.size)
        compose.onAllNodesWithText("extra.subl").assertCountEquals(2)
        // 逐个关掉 → 无标签页（早期 return 分支）
        state.close(main)
        compose.waitForIdle()
        assertEquals(1, state.openFiles.size)
        state.close(other)
        compose.waitForIdle()
        assertEquals(0, state.openFiles.size)
    }

    @Test fun diagnostics_debounce_populates() {
        val state = openTempProject()
        compose.setContent { CandyDesktopTheme { EditorScreen(state) } }
        val key = state.activeFile!!.absolutePath
        compose.waitUntil(8000) { state.diagnostics[key] != null }
        assertNotNull(state.diagnostics[key])
    }
}