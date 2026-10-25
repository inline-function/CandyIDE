package candy.ide.desktop

import candy.ide.desktop.agent.AgentState
import candy.ide.desktop.agent.ChatMessage
import candy.ide.desktop.agent.DesktopAgent
import candy.ide.desktop.agent.AgentTools
import candy.ide.desktop.ui.AgentScreen
import candy.ide.desktop.ui.CandyDesktopTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** AI 助手移植：工具注册 / 会话状态 / UI 渲染（不真正联网） */
class AgentTest {
    @Rule fun isolation() = SettingsIsolationRule()
    @get:Rule val compose = createComposeRule()

    @Test fun tools_declared_for_openai_and_executable() {
        val tools = AgentTools()
        val names = mutableListOf<String>()
        for (i in 0 until tools.openAiTools().length()) {
            names.add(tools.openAiTools().getJSONObject(i).getJSONObject("function").getString("name"))
        }
        assertTrue(names.contains("read_current_file"))
        assertTrue(names.contains("run_shell"))
        assertTrue(names.contains("project_info"))
        // 已注册的工具都能执行（未注入状态时返回友好提示而不是崩）
        val r = tools.execute("project_info", emptyMap())
        assertTrue(r.contains("工程") || r.contains("没有"))
        // 未知工具不崩
        assertTrue(tools.execute("nope", emptyMap()).startsWith("未知工具"))
    }

    @Test fun readonly_scope_blocks_write() {
        candy.ide.desktop.Settings.agentScope = "readonly"
        val tools = AgentTools()
        assertEquals(false, tools.mayWrite())
        val r = tools.execute("write_current_file", mapOf("content" to "x"))
        assertTrue(r.contains("只读"))
    }

    @Test fun shell_tool_runs_cmd() {
        candy.ide.desktop.Settings.agentScope = "full"
        val tools = AgentTools()
        val r = tools.execute("run_shell", mapOf("command" to "echo candy"))
        assertTrue(r.contains("candy"))
    }

    @Test fun conversations_roundtrip() {
        val st = AgentState()
        st.chat.add(ChatMessage(ChatMessage.Role.USER, "你好"))
        st.chat.add(ChatMessage(ChatMessage.Role.MODEL, "在的"))
        st.newChat()
        assertEquals(1, st.conversations.size)
        val conv = st.conversations.first()
        assertEquals("你好", conv.title)
        // 重新加载
        val st2 = AgentState()
        st2.loadConversations()
        assertEquals(1, st2.conversations.size)
        st.restore(st2.conversations.first())
        assertEquals(2, st.chat.size)
        st2.dispose()
        st.dispose()
    }

    @Test fun agent_screen_renders_input_and_title() {
        val state = IdeState()
        val agent = AgentState()
        compose.setContent { CandyDesktopTheme { AgentScreen(state, agent, onBack = {}) } }
        compose.onNodeWithText("助手").assertIsDisplayed()
        compose.onAllNodesWithText("问助手（当前工程/文件可直接读）", useUnmergedTree = true).assertCountEquals(1)
        agent.dispose()
    }
}
