package candy.ide.agent

import android.content.Context
import candy.ide.data.Settings
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.File

/**
 * CandyIDE 的 AI agent：用 Google ADK for Kotlin 的 Model/Session/Event 抽象搭对话循环，
 * 底层模型是我们的 OpenAiCompatModel（对接用户给的 OpenAI 兼容端点）。
 *
 * 对话历史存在 ADK Session（InMemorySessionService），UI 层把 events 渲染成聊天气泡。
 * 工具（读当前文件 / 运行 shell / 编译诊断）在 [tools] 里，模型返回 functionCall 时
 * 由 [CandyToolRegistry] 执行并把结果作为 tool 响应塞回下一轮。
 */
class CandyAgent(private val ctx: Context) {

    private val sessionService = InMemorySessionService()
    private lateinit var session: Session
    val tools = CandyToolRegistry(ctx)

    /** 诊断日志：写 files/candy/logs/agent_debug.log（排查请求链路） */
    private fun dbg(msg: String) {
        runCatching {
            val dir = java.io.File(candy.ide.engine.Engine.root(ctx), "logs")
            dir.mkdirs()
            val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.CHINA).format(java.util.Date())
            java.io.File(dir, "agent_debug.log").appendText("[" + ts + "] " + msg + "\n")
        }
    }

    private val model: OpenAiCompatModel = OpenAiCompatModel(
        baseUrl = Settings.agentBaseUrl,
        apiKey = Settings.agentKey,
        modelName = Settings.agentModel,
        toolsJson = tools.openAiTools(),
        temperature = Settings.agentTemperature.coerceIn(0f, 1.0f),
        streamMode = Settings.agentStream,
    )

    /** 新会话首轮：把 Sublimation 根文档/wiki 注入系统上下文（重置会话时置回 true） */
    private var firstAskOfSession = true

    /** 系统提示：设置页可配初始提示词，为空则用内置默认 */
    /** 新会话首轮注入的文档上下文：内置工程里的根文档与 wiki（每篇截断，防撑爆上下文） */
    private fun docsContext(): String {
        return runCatching {
            val sb = StringBuilder()
            // 内置工程释放目录（设置里点过「语言文档」后存在），没有则读 assets 原档
            val released = java.io.File(candy.ide.engine.Engine.root(ctx), "builtin/lang-doc")
            val files = if (released.isDirectory) released.listFiles()?.sortedBy { it.name } ?: emptyList()
                else ctx.assets.list("builtin/lang-doc")?.sorted() ?: emptyList()
            var budget = 24000
            for (f in files.take(14)) {
                if (budget <= 0) break
                val name = if (f is java.io.File) f.name else f
                val text = if (f is java.io.File) f.readText()
                    else ctx.assets.open("builtin/lang-doc/" + name).bufferedReader().use { it.readText() }
                if (text.isBlank()) continue
                sb.append("\n=== 文档 ").append(name).append(" ===\n")
                val chunk = if (text.length <= 1600) text else text.substring(0, 1600) + "\n…（截断）"
                sb.append(chunk).append("\n")
                budget -= chunk.length
            }
            if (sb.isEmpty()) "" else "\n\n【Sublimation 语言文档（节选）】\n" + sb
        }.getOrDefault("")
    }

    private fun systemPrompt(): String =
        (Settings.agentPrompt.trim().ifBlank {
            """你是 CandyIDE 的内置编程助手，服务于 Sublimation 语言的开发者。
Sublimation 是自研函数式语言，编译目标为 JS（WebView 运行），IDE 运行在 Android 上。
回答用中文，简洁、给代码示例时用 Sublimation 语法：
  fun main() { unchecked print("hi") }   // 函数体、lambda { x -> x + 1 }、元组 (a, b)、模式匹配 match
你可用工具读当前文件、执行 shell 命令、获取编译诊断。不确定就明说，别编造 API。
需要用户在几个方案间选择时，在回复末尾加一行【选项：方案A|方案B|方案C】（2~6 个，竖线分隔，简短）。
"""
        }) + tools.toolDescriptions()


    init {
        // ADK session：appName=candy.ide，userId=local，sessionId 固定（单用户 IDE）
        session = newSession("main")
    }

    /** 新建会话（新建/恢复对话时调用）*/
    private fun newSession(id: String): Session = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        sessionService.createSession(SessionKey("candy.ide", "local", id), emptyMap())
    }

    /** 重置会话：丢弃旧上下文（聊天历史仍保留在 VM 的 chat 列表里） */
    fun resetSession() {
        session = newSession("conv-" + java.util.UUID.randomUUID().toString().take(6))
        firstAskOfSession = true
    }

    /** 发一句用户消息，返回逐条事件流（先用户后模型；工具调用会自动接力） */
    fun ask(userText: String): Flow<Event> = flow {
        dbg("ask start: " + userText.take(40))
        // 每次发起前刷新一次工具看到的上下文与模型参数（设置页可能刚改过）
        model.temperature = Settings.agentTemperature.coerceIn(0f, 1.0f)
        model.streamMode = Settings.agentStream
        // 首轮：以 user message 形式把语言文档喂给模型（只插一次，之后随历史携带）
        if (firstAskOfSession) {
            firstAskOfSession = false
            val docs = docsContext()
            if (docs.isNotBlank()) {
                val docsContent = Content(parts = listOf(Part(text = docs)), role = "user")
                persist(docsContent, "user")
            }
        }
        // 1) 追加用户消息
        val userContent = Content(parts = listOf(Part(text = userText)), role = "user")
        persist(userContent, "user")
        emit(eventOf(userContent, author = "user"))
        // 2) 组织对话历史（system + session 里已累积的 events）
        val history = mutableListOf<Content>()
        history.add(Content(parts = listOf(Part(text = systemPrompt())), role = "system"))
        for (e in session.events) {
            val c = e.content ?: continue
            if (c.role == "user" || c.role == "model") history.add(c)
        }
        history.add(userContent)
        // 3) 多轮工具循环（最多 6 轮，防失控）
        var pending = history
        for (round in 0 until 6) {
            val req = LlmRequest(model, pending, com.google.adk.kt.types.GenerateContentConfig(), tools.adkTools())
            // generateContent 返回 Flow<LlmResponse>：无 stream 模式只发一个，collect 收下
            var resp: LlmResponse? = null
            var failure: Throwable? = null
            dbg("round " + round + " history=" + pending.size + " stream=" + model.streamMode + " url=" + Settings.agentBaseUrl)
            runCatching {
                kotlinx.coroutines.withTimeout(180_000L) {
                    model.generateContent(req, false).collect { resp = it }
                }
            }.onFailure { failure = it }
            if (failure != null) dbg("model failure: " + failure!!.javaClass.simpleName + ": " + failure?.message)
            if (resp == null) {
                dbg("model returned null (network/parse failure)")
                emit(errorEvent("模型调用失败：检查网络与设置里的端点配置"))
                return@flow
            }
            val content = resp.content ?: Content(parts = emptyList(), role = "model")
            // 没有工具调用 → 收下正文，结束
            val calls = content.parts.mapNotNull { it.functionCall }
            val text = content.parts.mapNotNull { it.text }.joinToString("").trim()
            if (calls.isEmpty()) {
                val answer = if (text.isBlank()) "（模型未返回正文，可能被思考占满；提高 max_tokens 或换个问法）" else text
                dbg("answer: " + answer.take(60))
                val modelContent = Content(parts = listOf(Part(text = answer)), role = "model")
                persist(modelContent, "model")
                emit(eventOf(modelContent, author = "model"))
                break
            }
            // 有工具调用：先把「模型想调工具」这件事可视化，再执行
            if (text.isNotBlank()) emit(eventOf(Content.fromText(text, "model"), author = "model"))
            // 工具调用轮的内容与回执都落进 session：下一句对话仍带完整历史
            persist(content, "model")
            val next = pending.toMutableList()
            next.add(content)
            for (fc in calls) {
                val name = fc.name
                val args = fc.args ?: emptyMap()
                dbg("tool call: " + name + " args=" + args)
                val tried = runCatching { tools.execute(name, args) }
                val result = tried.getOrElse { ex: Throwable -> "工具执行出错: " + ex.message }
                // 带 id 的回执：模型端按 tool_call_id 认领这次工具结果
                val part = Part(functionResponse = com.google.adk.kt.types.FunctionResponse(id = fc.id, name = name, response = mapOf("result" to result)))
                val toolContent = Content(parts = listOf(part), role = "user")
                persist(toolContent, "user")
                next.add(toolContent)
                emit(toolEvent(name, args, result))
            }
            pending = next
        }
    }.flowOn(Dispatchers.IO)

    /** 把消息追加进 ADK 会话历史（多轮上下文的来源） */
    private suspend fun persist(content: Content, author: String) {
        runCatching {
            val ev = eventOf(content, author)
            sessionService.appendEvent(session, ev)
        }
    }


    companion object {
    private fun toolText(name: String, args: Map<String, Any?>, result: String): String {
        val argStr = args.entries.joinToString(", ") { entry -> entry.key.toString() + "=" + entry.value }
        return "[工具 " + name + "] " + argStr + "\n" + result
    }
        fun eventOf(content: Content, author: String): Event = Event(
            id = java.util.UUID.randomUUID().toString(),
            invocationId = "local",
            author = author,
            content = content,
        )

        private fun errorEvent(msg: String): Event = eventOf(Content(parts = listOf(Part(text = msg)), role = "model"), author = "model")

        private fun toolEvent(name: String, args: Map<String, Any?>, result: String): Event = Event(
            id = java.util.UUID.randomUUID().toString(),
            invocationId = "local",
            author = "tool",
            content = Content(parts = listOf(Part(text = toolText(name, args, result))), role = "user"),
        )
    }
}
