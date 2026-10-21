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

/**
 * CandyIDE 的 AI agent：对话循环 + 工具调用，UI 事件用自定义 author 约定区分：
 *  - "user"     用户消息（UI 直接渲染）
 *  - "chunk"    流式增量块（同一气泡逐字增长，author=chunk 绕过持久化）
 *  - "tool"     工具调用开始/结束（带 toolName，UI 渲染折叠气泡）
 *  - "model"    一轮的最终回答（落进 session 历史）
 *
 * 对话上下文（session events）每次请求都塞进 messages 参数，跨轮保留。
 */
class CandyAgent(private val ctx: Context) {

    private val sessionService = InMemorySessionService()
    private lateinit var session: Session
    val tools = CandyToolRegistry(ctx)

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
        temperature = Settings.agentTemperature,
        streamMode = Settings.agentStream,
    )

    /** 新会话首轮：注入全部语言文档与 IDE 文档（作为 user message 持久化进 session） */
    private var firstAskOfSession = true

    private fun systemPrompt(): String =
        // 实际工作路径：agent 不知道工作区在哪会抓瞎，系统提示里直接给绝对路径
        workspaceContext() + (Settings.agentPrompt.trim().ifBlank { DEFAULT_PROMPT }) + "\n\n" + tools.toolDescriptions()

    /** 工作区 / 当前工程的绝对路径（agent 在终端里跑命令、读写文件要用） */
    private fun workspaceContext(): String {
        val root = runCatching { candy.ide.fs.CandyFs.projectRoot(ctx) }.getOrNull()
        val workspaceReal = root?.let { runCatching { candy.ide.git.GitManager.realPath(it) }.getOrNull() }
        val proj = tools.currentProjectDir
        val projReal = proj?.let { runCatching { candy.ide.git.GitManager.realPath(it) }.getOrNull() }
        return buildString {
            append("\n【工作区信息】")
            append("\nIDE 目录: ").append(root?.displayPath ?: "未知")
            workspaceReal?.let { append("\nIDE 目录真实路径(终端/shell 可用): ").append(it) }
            if (proj != null) {
                append("\n当前工程: ").append(proj.name)
                projReal?.let { append("\n当前工程真实路径(终端/shell 可用): ").append(it) }
            }
            append("\n需要工程信息可调用 workspace_info / project_info 工具。\n\n")
        }
    }

    /** 首轮上下文：把全部 Sublimation 文档与 IDE 文档一次喂给模型，之后随历史携带 */
    private fun docsContext(): String = runCatching {
        val sb = StringBuilder()
        // 语言文档：assets/builtin/lang-doc（释放到私有目录后优先读释放版）
        val released = java.io.File(candy.ide.engine.Engine.root(ctx), "builtin/lang-doc")
        val langNames = (if (released.isDirectory) released.listFiles()?.map { it.name }?.sorted()
            else ctx.assets.list("builtin/lang-doc")?.sorted()) ?: emptyList()
        for (name in langNames) {
            val text = if (released.isDirectory) java.io.File(released, name).readText()
                else ctx.assets.open("builtin/lang-doc/" + name).bufferedReader().use { it.readText() }
            if (text.isBlank()) continue
            sb.append("\n=== 文档 ").append(name).append(" ===\n").append(text).append("\n")
        }
        // IDE 文档：assets/builtin/ide-doc
        val ideNames = ctx.assets.list("builtin/ide-doc")?.sorted() ?: emptyList()
        for (name in ideNames) {
            val text = runCatching {
                ctx.assets.open("builtin/ide-doc/" + name).bufferedReader().use { it.readText() }
            }.getOrDefault("")
            if (text.isBlank()) continue
            sb.append("\n=== IDE 文档 ").append(name).append(" ===\n").append(text).append("\n")
        }
        if (sb.isEmpty()) "" else "\n\n【Sublimation 语言文档与 IDE 文档（全量，工具调用时请参考）】\n" + sb
    }.getOrDefault("")

    /** 上下文占用估算（≈ 字符数/4），UI 进度条用 */
    fun estimateTokens(): Int {
        var chars = 0
        for (e in session.events) {
            e.content?.parts?.forEach { p -> p.text?.let { chars += it.length + 8 } }
        }
        return chars / 4
    }

    init { session = newSession("main") }

    private fun newSession(id: String): Session = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        sessionService.createSession(SessionKey("candy.ide", "local", id), emptyMap())
    }

    fun resetSession() {
        session = newSession("conv-" + java.util.UUID.randomUUID().toString().take(6))
        firstAskOfSession = true
    }

    /** 发一句用户消息，返回事件流（chunk/tool/model 约定见类注释） */
    fun ask(userText: String): Flow<Event> = flow {
        dbg("ask start: " + userText.take(40))
        model.temperature = Settings.agentTemperature
        model.streamMode = Settings.agentStream
        if (firstAskOfSession) {
            firstAskOfSession = false
            val docs = docsContext()
            if (docs.isNotBlank()) {
                val docsContent = Content(parts = listOf(Part(text = docs)), role = "user")
                persist(docsContent, "user")
            }
        }
        val userContent = Content(parts = listOf(Part(text = userText)), role = "user")
        persist(userContent, "user")
        emit(eventOf(userContent, author = "user"))
        // 组织对话历史：system + session 已累积的全部事件 + 本句
        val history = mutableListOf<Content>()
        history.add(Content(parts = listOf(Part(text = systemPrompt())), role = "system"))
        for (e in session.events) {
            val c = e.content ?: continue
            if (c.role == "user" || c.role == "model") history.add(c)
        }
        history.add(userContent)
        var pending = history
        for (round in 0 until 6) {
            val req = LlmRequest(model, pending, com.google.adk.kt.types.GenerateContentConfig(), tools.adkTools())
            var resp: LlmResponse? = null
            var failure: Throwable? = null
            dbg("round " + round + " history=" + pending.size + " stream=" + model.streamMode + " url=" + Settings.agentBaseUrl)
            runCatching {
                kotlinx.coroutines.withTimeout(180_000L) {
                    model.generateContent(req, false).collect { r ->
                        // 流式中间块：增量文本立刻推给 UI（finishReason 为空表示尚未结束）
                        val piece = r.content?.parts?.mapNotNull { it.text }?.joinToString("") ?: ""
                        val hasCalls = r.content?.parts?.any { it.functionCall != null } == true
                        if (piece.isNotEmpty() && r.finishReason == null && !hasCalls) {
                            emit(eventOf(Content(parts = listOf(Part(text = piece)), role = "model"), author = "chunk"))
                        }
                        // 含工具调用或已完成的块覆盖累积结果
                        if (hasCalls || r.finishReason != null) resp = r
                    }
                }
            }.onFailure { failure = it }
            if (failure != null) dbg("model failure: " + failure!!.javaClass.simpleName + ": " + (failure?.message ?: ""))
            if (resp == null) {
                dbg("model returned null (network/parse failure)")
                emit(errorEvent("模型调用失败：检查网络与设置里的端点配置（端点对超大 temperature 或不支持的请求会静默挂死）"))
                return@flow
            }
            val content = resp!!.content ?: Content(parts = emptyList(), role = "model")
            val calls = content.parts.mapNotNull { it.functionCall }
            val text = content.parts.mapNotNull { it.text }.joinToString("").trim()
            if (calls.isEmpty()) {
                // 流式模式下正文已通过 chunk 逐块送达，这里只在历史里落最终版
                val answer = if (text.isBlank()) "（模型未返回正文，可能被思考占满；提高 max_tokens 或换个问法）" else text
                dbg("answer: " + answer.take(60))
                val modelContent = Content(parts = listOf(Part(text = answer)), role = "model")
                persist(modelContent, "model")
                emit(eventOf(modelContent, author = "model"))
                break
            }
            // 工具调用轮
            persist(content, "model")
            val next = pending.toMutableList()
            next.add(content)
            for (fc in calls) {
                val name = fc.name
                val args = fc.args ?: emptyMap()
                dbg("tool call: " + name + " args=" + args)
                // 工具开始：UI 显示「正在读取文件…」式的进行中气泡
                emit(eventOf(Content(parts = listOf(Part(text = name)), role = "user"), author = "tool_start"))
                val tried = runCatching { tools.execute(name, args) }
                val result = tried.getOrElse { ex: Throwable -> "工具执行出错: " + ex.message }
                // 工具完成：带结果与摘要的折叠气泡
                emit(toolDoneEvent(name, args, result))
                val part = Part(functionResponse = com.google.adk.kt.types.FunctionResponse(id = fc.id, name = name, response = mapOf("result" to result)))
                val toolContent = Content(parts = listOf(part), role = "user")
                persist(toolContent, "user")
                next.add(toolContent)
            }
            pending = next
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun persist(content: Content, author: String) {
        runCatching {
            val ev = eventOf(content, author)
            sessionService.appendEvent(session, ev)
        }
    }

    companion object {
        private const val DEFAULT_PROMPT = """你是 CandyIDE 的内置编程助手，服务于 Sublimation 语言的开发者。
Sublimation 是自研函数式语言，编译目标为 JS（WebView 运行），IDE 运行在 Android 上。
回答用中文，简洁、给代码示例时用 Sublimation 语法：
  fun main() { unchecked print("hi") }   // 函数体、lambda { x -> x + 1 }、元组 (a, b)、模式匹配 match
你可用工具读当前文件、执行 shell 命令、获取编译诊断。不确定就明说，别编造 API。
需要用户在几个方案间选择时，在回复末尾加一行【选项：方案A|方案B|方案C】（2~6 个，竖线分隔，简短）。
"""

        private fun eventOf(content: Content, author: String): Event = Event(
            id = java.util.UUID.randomUUID().toString(),
            invocationId = "local",
            author = author,
            content = content,
        )

        private fun errorEvent(msg: String): Event = eventOf(
            Content(parts = listOf(Part(text = msg)), role = "model"), "model"
        )

        /** 工具完成事件：§tool§<工具名>§<参数串>§<结果> 四段分隔，VM 侧解析成折叠气泡 */
        private fun toolDoneEvent(name: String, args: Map<String, Any?>, result: String): Event {
            val argStr = args.entries.joinToString(", ") { e -> e.key.toString() + "=" + e.value }
            val body = "§tool§" + name + "§" + argStr + "§" + result
            val content = Content(parts = listOf(Part(text = body)), role = "user")
            return eventOf(content, "tool")
        }
    }
}
