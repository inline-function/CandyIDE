package candy.ide.desktop.agent

import candy.ide.desktop.IdeState
import candy.ide.desktop.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 一条聊天气泡（对应安卓端 ChatMessage） */
class ChatMessage(
    val role: ChatMessage.Role,
    text: String,
    val toolName: String? = null,
) {
    enum class Role { USER, MODEL, TOOL }
    var text: String = text
    var thinking: Boolean = false
    var done: Boolean = false
    fun append(s: String) { text += s }
}

/** 往期对话存档 */
data class AgentConversation(
    val time: Long,
    val title: String,
    val messages: List<ChatMessage>,
    val pinned: Boolean = false,
)

/**
 * agent 的 UI 状态机：气泡列表、忙碌状态、阶段文案、会话存档读写。
 * 与 IdeState 解耦：宿主（主页 / 编辑器窗口）在 ask 时把当前文件、工程、诊断与按键回调注入。
 */
class AgentState {
    val chat = androidx.compose.runtime.mutableStateListOf<ChatMessage>()
    var busy by androidx.compose.runtime.mutableStateOf(false)
    var stage by androidx.compose.runtime.mutableStateOf("")
    val conversations = androidx.compose.runtime.mutableStateListOf<AgentConversation>()
    var tokens by androidx.compose.runtime.mutableIntStateOf(0)

    private val agent = DesktopAgent()
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun dir(): File = File(File(Settings.workspaceRoot).parentFile, "agent").apply { mkdirs() }

    fun ask(
        text: String,
        state: IdeState,
        onTapRun: (() -> Unit)? = null,
        onGotoLine: ((Int) -> Unit)? = null,
    ) {
        if (busy) return
        agent.tools.apply {
            currentFile = state.activeFile
            currentProject = state.currentProject
            diagnostics = state.diagnostics.values.flatten().take(200)
            this.onTapRun = onTapRun
            this.onTapSave = { state.activeFile?.let { f -> state.save(f) } }
            this.onGotoLine = onGotoLine
            this.onTapEdit = { c ->
                state.activeFile?.let { f ->
                    state.editText(f, c)
                    state.save(f)
                }
            }
        }
        chat.add(ChatMessage(ChatMessage.Role.USER, text))
        busy = true
        stage = "思考中…"
        val placeholder = ChatMessage(ChatMessage.Role.MODEL, "").apply { thinking = true }
        chat.add(placeholder)
        job = scope.launch {
            try {
                agent.ask(text).collect { ev ->
                    val body = ev.text
                    when (ev.author) {
                        "user" -> {}
                        "chunk" -> {
                            val last = chat.lastOrNull()
                            if (last != null && last.role == ChatMessage.Role.MODEL) {
                                if (last.thinking) { last.thinking = false; last.text = "" }
                                if (last.toolName != null) return@collect
                                last.append(body)
                            }
                        }
                        "tool_start" -> {
                            stage = "调用工具 " + body + "…"
                            val last = chat.lastOrNull()
                            if (last != null && last.thinking) chat.removeAt(chat.lastIndex)
                            chat.add(ChatMessage(ChatMessage.Role.TOOL, "", body).apply { thinking = true })
                        }
                        "tool" -> {
                            val segs = body.split("§")
                            if (segs.size >= 4 && segs[1] == "tool") {
                                val name = segs[2]
                                val args = segs[3]
                                val result = segs.subList(4, segs.size).joinToString("§")
                                val last = chat.lastOrNull()
                                if (last != null && last.role == ChatMessage.Role.TOOL && last.toolName == name && !last.done) {
                                    last.thinking = false
                                    last.done = true
                                    last.text = "[工具 " + name + "] 参数: " + args + "\n结果: " + result
                                } else {
                                    chat.add(ChatMessage(ChatMessage.Role.TOOL, "[工具 " + name + "] 参数: " + args + "\n结果: " + result, name).apply { done = true })
                                }
                                chat.add(ChatMessage(ChatMessage.Role.MODEL, "").apply { thinking = true })
                            }
                        }
                        "model" -> {
                            if (body.isBlank()) return@collect
                            val last = chat.lastOrNull()
                            if (last != null && last.role == ChatMessage.Role.MODEL && last.toolName == null) {
                                last.thinking = false
                                last.text = body
                            } else {
                                chat.add(ChatMessage(ChatMessage.Role.MODEL, body))
                            }
                        }
                    }
                    tokens = agent.estimateTokens()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                chat.add(ChatMessage(ChatMessage.Role.MODEL, "调用失败：" + (e.message ?: e.toString())))
            } finally {
                busy = false
                stage = ""
                val last = chat.lastOrNull()
                if (last != null && last.thinking) chat.removeAt(chat.lastIndex)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (busy) {
            busy = false
            stage = ""
            chat.add(ChatMessage(ChatMessage.Role.MODEL, "（已停止）"))
        }
    }

    fun newChat() {
        stop()
        if (chat.isNotEmpty()) {
            val first = chat.firstOrNull { it.role == ChatMessage.Role.USER }?.text ?: "对话"
            conversations.add(0, AgentConversation(System.currentTimeMillis(), first, chat.toList()))
            saveConversations()
        }
        chat.clear()
        agent.resetSession()
    }

    fun restore(conv: AgentConversation) {
        stop()
        if (chat.isNotEmpty()) {
            val first = chat.firstOrNull { it.role == ChatMessage.Role.USER }?.text ?: "对话"
            conversations.add(0, AgentConversation(System.currentTimeMillis(), first, chat.toList()))
        }
        conversations.remove(conv)
        chat.clear()
        chat.addAll(conv.messages)
        agent.resetSession()
        saveConversations()
    }

    fun togglePin(conv: AgentConversation) {
        val i = conversations.indexOf(conv)
        if (i >= 0) {
            conversations[i] = conv.copy(pinned = !conv.pinned)
            saveConversations()
        }
    }

    fun deleteConversation(conv: AgentConversation) {
        conversations.remove(conv)
        saveConversations()
    }

    fun renameConversation(conv: AgentConversation, title: String) {
        val i = conversations.indexOf(conv)
        if (i >= 0 && title.isNotBlank()) {
            conversations[i] = conv.copy(title = title)
            saveConversations()
        }
    }

    /** 往期对话落盘（JSON；每段最多 80 条，只留最近 30 段） */
    fun saveConversations() {
        runCatching {
            val arr = JSONArray()
            for (c in conversations.take(30)) {
                val j = JSONObject()
                    .put("time", c.time)
                    .put("title", c.title)
                    .put("pinned", c.pinned)
                val msgs = JSONArray()
                for (m in c.messages.takeLast(80)) msgs.put(JSONObject().put("role", m.role.name).put("text", m.text))
                j.put("messages", msgs)
                arr.put(j)
            }
            File(dir(), "conversations.json").writeText(arr.toString())
        }
    }

    fun loadConversations() {
        if (conversations.isNotEmpty()) return
        runCatching {
            val f = File(dir(), "conversations.json")
            if (!f.isFile) return@runCatching
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val msgs = j.getJSONArray("messages")
                val list = mutableListOf<ChatMessage>()
                for (k in 0 until msgs.length()) {
                    val mo = msgs.getJSONObject(k)
                    val role = runCatching { ChatMessage.Role.valueOf(mo.getString("role")) }.getOrDefault(ChatMessage.Role.MODEL)
                    list.add(ChatMessage(role, mo.getString("text")))
                }
                if (list.isNotEmpty()) conversations.add(AgentConversation(j.getLong("time"), j.optString("title", ""), list, j.optBoolean("pinned", false)))
            }
        }
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}
