package candy.ide.desktop.agent

import candy.ide.desktop.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** 一次 agent 事件：author 约定与安卓端一致（user/chunk/tool_start/tool/model） */
data class AgentEvent(val author: String, val text: String)

/**
 * 桌面端 AI agent：OpenAI 兼容端点 + 工具调用循环（对应安卓端 CandyAgent，无 ADK 依赖）。
 * 对话历史直接用 org.json 的 messages 数组累积（role: system/user/assistant/tool）。
 */
class DesktopAgent {
    val tools = AgentTools()

    private val history = mutableListOf<JSONObject>()
    private var firstAskOfSession = true
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    fun resetSession() {
        history.clear()
        firstAskOfSession = true
    }

    /** 上下文占用估算（≈ 字符数/4），UI 进度条用 */
    fun estimateTokens(): Int = history.sumOf { it.toString().length } / 4

    private fun systemPrompt(): String =
        workspaceContext() + (Settings.agentPrompt.trim().ifBlank { DEFAULT_PROMPT }) + "\n\n" + tools.toolDescriptions()

    private fun workspaceContext(): String = buildString {
        append("\n【工作区信息】\n")
        append("工作区: ").append(Settings.workspaceRoot).append("\n")
        tools.currentProject?.let { append("当前工程: ").append(it.name).append("（").append(it.absolutePath).append("）\n") }
        append("需要工程信息可调用 workspace_info / project_info 工具。\n\n")
    }

    /** 发一句用户消息，返回事件流 */
    fun ask(userText: String): Flow<AgentEvent> = flow {
        if (Settings.agentBaseUrl.isBlank()) {
            emit(AgentEvent("model", "尚未配置 AI 端点：到设置 → AI 助手里填写端点地址。"))
            return@flow
        }
        if (firstAskOfSession) {
            firstAskOfSession = false
            val docs = docsContext()
            if (docs.isNotBlank()) history.add(JSONObject().put("role", "user").put("content", docs))
        }
        val userMsg = JSONObject().put("role", "user").put("content", userText)
        history.add(userMsg)
        emit(AgentEvent("user", userText))

        var pending = ArrayList(history)
        for (round in 0 until 6) {
            val payload = JSONObject()
                .put("model", Settings.agentModel.ifBlank { "gpt-4o-mini" })
                .put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt()))
                    pending.forEach { put(it) }
                })
                .put("temperature", Settings.agentTemperature.toDouble())
                .put("stream", Settings.agentStream)
            if (tools.openAiTools().length() > 0) payload.put("tools", tools.openAiTools())

            val resp = runCatching { request(payload) { piece -> emit(AgentEvent("chunk", piece)) } }.getOrElse { ex ->
                emit(AgentEvent("model", "调用失败：" + (ex.message ?: ex.toString())))
                return@flow
            }
            // 工具调用或有正文
            val toolCalls = resp.optJSONArray("tool_calls")
            val text = resp.opt("content")?.toString()?.trim() ?: ""
            if (toolCalls == null || toolCalls.length() == 0) {
                val answer = if (text.isBlank()) "（模型未返回正文）" else text
                val modelMsg = JSONObject().put("role", "assistant").put("content", answer)
                history.add(modelMsg)
                emit(AgentEvent("model", answer))
                break
            }
            // 工具调用轮
            val asst = JSONObject().put("role", "assistant").put("content", text)
            val callsArr = JSONArray()
            for (i in 0 until toolCalls.length()) {
                val call = toolCalls.getJSONObject(i)
                callsArr.put(call)
                val fn = call.optJSONObject("function")
                val name = fn?.optString("name") ?: call.optString("name")
                val argsRaw = fn?.optString("arguments") ?: "{}"
                val args = runCatching { jsonObjectToMap(JSONObject(argsRaw)) }.getOrDefault(emptyMap())
                emit(AgentEvent("tool_start", name))
                val result = tools.execute(name, args)
                emit(toolDone(name, argsRaw, result))
                asst.put("tool_calls", callsArr)
                history.add(JSONObject().put("role", "tool").put("tool_call_id", call.optString("id")).put("content", result))
            }
            history.add(asst)
            pending = ArrayList(history)
        }
    }.flowOn(Dispatchers.IO)

    /** 首轮上下文：把全部 Sublimation 文档与 IDE 文档一次喂给模型（classpath 资源，构建期 sync） */
    private fun docsContext(): String = runCatching {
        val sb = StringBuilder()
        val names = candy.ide.desktop.ui.Resources.index("builtin-index.txt")
            .filter { it.startsWith("lang-doc/") || it.startsWith("ide-doc/") }
        for (n in names) {
            val text = candy.ide.desktop.ui.Resources.read("builtin/" + n) ?: continue
            if (text.isBlank()) continue
            sb.append("\n=== 文档 ").append(n).append(" ===\n").append(text).append("\n")
        }
        if (sb.isEmpty()) "" else "\n\n【Sublimation 语言文档与 IDE 文档（全量，工具调用时请参考）】\n" + sb
    }.getOrDefault("")

    private suspend fun request(payload: JSONObject, onChunk: suspend (String) -> Unit): JSONObject {
        val url = Settings.agentBaseUrl.trimEnd('/') + "/chat/completions"
        val streaming = payload.optBoolean("stream", false)
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(180))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Authorization", "Bearer " + Settings.agentKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()
        if (!streaming) {
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) throw RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body().take(300))
            return parseChoice(resp.body())
        }
        // SSE 流式：逐行解析 data: {...}
        val resp = client.send(req, HttpResponse.BodyHandlers.ofLines())
        if (resp.statusCode() !in 200..299) {
            val err = resp.body().reduce("") { a, b -> a + b }.take(300)
            throw RuntimeException("HTTP " + resp.statusCode() + ": " + err)
        }
        val acc = StringBuilder()
        var toolCalls: JSONArray? = null
        var finishReason: String? = null
        resp.body().use { lines ->
            val it = lines.iterator()
            while (it.hasNext()) {
                val line = it.next().trim()
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") continue
                val j = runCatching { JSONObject(data) }.getOrNull() ?: continue
                val choice = j.optJSONArray("choices")?.optJSONObject(0) ?: continue
                val delta = choice.optJSONObject("delta") ?: continue
                val piece = delta.optString("content", "")
                if (piece.isNotEmpty()) {
                    acc.append(piece)
                    onChunk(piece)
                }
                val calls = delta.optJSONArray("tool_calls")
                if (calls != null && calls.length() > 0) toolCalls = calls
                val fr = choice.optString("finish_reason", "")
                if (fr.isNotEmpty()) finishReason = fr
            }
        }
        val out = JSONObject()
        out.put("content", acc.toString())
        out.put("tool_calls", toolCalls ?: JSONObject.NULL)
        if (finishReason != null) out.put("finish_reason", finishReason)
        return out
    }

    private fun parseChoice(body: String): JSONObject {
        val root = JSONObject(body)
        val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: throw RuntimeException("响应缺少 choices: " + body.take(200))
        val msg = choice.optJSONObject("message") ?: JSONObject()
        return msg.put("content", msg.optString("content", ""))
            .put("tool_calls", msg.opt("tool_calls") ?: JSONObject.NULL)
    }

    private fun toolDone(name: String, args: String, result: String): AgentEvent =
        AgentEvent("tool", "§tool§" + name + "§" + args + "§" + result)

    private fun jsonObjectToMap(o: JSONObject): Map<String, Any?> = buildMap {
        for (k in o.keys()) put(k, o.get(k))
    }

    companion object {
        private const val DEFAULT_PROMPT = """你是 CandyIDE 的内置编程助手，服务于 Sublimation 语言的开发者。
Sublimation 是自研函数式语言，编译目标为 JS，IDE 运行在 Windows 桌面（Compose Multiplatform）。
回答用中文，简洁、给代码示例时用 Sublimation 语法：
  fun main() { unchecked print("hi") }   // 函数体、lambda { x => x + 1 }、元组 (a, b)、模式匹配 match
你可用工具读当前文件、执行命令、获取编译诊断。不确定就明说，别编造 API。
需要用户在几个方案间选择时，在回复末尾加一行【选项：方案A|方案B|方案C】（2~6 个，竖线分隔，简短）。
"""
    }
}
