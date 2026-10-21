package candy.ide.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.UsageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI 兼容端点的 ADK Model 实现（/chat/completions）。
 *
 * 端点特性适配（discovery-api 实测）：
 *  - 思考过程在 message.reasoning_content，正文在 message.content（可能为 null）
 *  - max_tokens 太小会被 reasoning 吃光 → 默认 4096
 *  - 工具调用在 message.tool_calls（OpenAI 格式），转成 ADK 的 functionCall Part
 *
 * 不引 OkHttp：HttpURLConnection + Android 自带 org.json 足够，依赖最少。
 */
class OpenAiCompatModel(
    private val baseUrl: String,
    private val apiKey: String,
    private val modelName: String,
    /** 可调用工具的 OpenAI 声明；非空时随每次请求发送（模型才会发起 tool_calls） */
    private val toolsJson: org.json.JSONArray? = null,
    /** 采样温度 0~2（运行期可改：设置页拖动条同步过来） */
    var temperature: Float = 1.0f,
    /** 流式输出（SSE）：逐块到达并聚合成完整响应 */
    var streamMode: Boolean = false,
) : Model {

    override val name: String = modelName

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        android.util.Log.w("CandyAgent", "flow start streamMode=" + streamMode)
        val payload = buildPayload(request)
        // 端点规整：常见兼容网关的路径是 /v1/chat/completions，用户只填了域名时自动补 /v1
        val base = baseUrl.trim().trimEnd('/')
        val withV1 = if (base.isEmpty() || android.net.Uri.parse(base).path?.trim('/')?.isEmpty() != false) base + "/v1" else base
        val endpoint = withV1 + "/chat/completions"
        val resp = if (streamMode) collectSse(endpoint, payload) else postJson(endpoint, payload)
        android.util.Log.w("CandyAgent", "model resp head: " + resp.take(400))
        val json = JSONObject(resp)
        val optChoices = json.optJSONArray("choices")
        val choice = optChoices?.optJSONObject(0)
        if (choice == null) {
            android.util.Log.w("CandyAgent", "no choices in response")
            return@flow
        }
        val msg = choice.optJSONObject("message") ?: JSONObject()
        val content = msg.optString("content")
        val parts = mutableListOf<Part>()
        if (!content.isNullOrEmpty()) parts.add(Part(text = content))
        // OpenAI tool_calls → ADK functionCall
        val toolCalls = msg.optJSONArray("tool_calls")
        if (toolCalls != null) {
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.optJSONObject(i) ?: continue
                val fn = tc.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                val callId = tc.optString("id").ifBlank { "call_" + i }
                val argsJson = fn.optJSONObject("arguments")?.toString() ?: "{}"
                val argsMap = mutableMapOf<String, Any?>()
                runCatching {
                    val parsed = JSONObject(argsJson)
                    for (k in parsed.keys()) argsMap[k] = parsed.get(k)
                }
                parts.add(Part(functionCall = FunctionCall(id = callId, name = name, args = argsMap)))
            }
        }
        val usage = json.optJSONObject("usage")
        emit(
            LlmResponse(
                content = Content(parts = parts, role = "model"),
                usageMetadata = UsageMetadata(
                    promptTokenCount = usage?.optInt("prompt_tokens", 0)?.takeIf { it > 0 },
                    candidatesTokenCount = usage?.optInt("completion_tokens", 0)?.takeIf { it > 0 },
                    totalTokenCount = usage?.optInt("total_tokens", 0)?.takeIf { it > 0 },
                ),
                finishReason = mapFinish(choice.optString("finish_reason")),
            )
        )
    }.flowOn(Dispatchers.IO)

    /** ADK contents → OpenAI messages（system 指令合并放最前） */
    private fun buildPayload(request: LlmRequest): JSONObject {
        val json = JSONObject()
        json.put("model", modelName)
        json.put("stream", streamMode)
        json.put("max_tokens", 4096)
        val msgs = JSONArray()
        val contents = request.contents
        var systemText: String? = null
        var toolIdx = 0
        for (c in contents) {
            val role = c.role
            val text = c.parts.mapNotNull { it.text }.joinToString("")
            // 模型发起的工具调用：assistant 消息 + tool_calls（OpenAI 要求带上 id 配对）
            val calls = c.parts.mapNotNull { it.functionCall }
            if (calls.isNotEmpty()) {
                val m = JSONObject().put("role", "assistant")
                if (text.isNotBlank()) m.put("content", text) else m.put("content", "")
                val arr = JSONArray()
                for (fc in calls) {
                    val cid = fc.id ?: "call_" + (toolIdx++)
                    arr.put(JSONObject().put("id", cid)
                        .put("type", "function")
                        .put("function", JSONObject().put("name", fc.name).put("arguments", JSONObject(fc.args ?: emptyMap<String, Any?>()).toString())))
                }
                m.put("tool_calls", arr)
                msgs.put(m)
                continue
            }
            // 工具执行结果：role=tool + tool_call_id + 内容（发给模型的真正回执）
            val responses = c.parts.mapNotNull { it.functionResponse }
            if (responses.isNotEmpty()) {
                for (fr in responses) {
                    val rid = fr.name + "_" + (toolIdx++)
                    val resultText = (fr.response?.get("result") as? String) ?: JSONObject(fr.response ?: emptyMap<String, Any?>()).toString()
                    msgs.put(JSONObject().put("role", "tool").put("tool_call_id", rid).put("content", resultText))
                }
                continue
            }
            if (text.isBlank()) continue
            when (role) {
                "model" -> {
                    val m = JSONObject()
                    m.put("role", "assistant")
                    m.put("content", text)
                    msgs.put(m)
                }
                "system", "system_instruction" -> systemText = text
                else -> {
                    val m = JSONObject()
                    m.put("role", "user")
                    m.put("content", text)
                    msgs.put(m)
                }
            }
        }
        if (systemText != null) {
            val sys = JSONObject()
            sys.put("role", "system")
            sys.put("content", systemText)
            msgs.put(0, sys)
        }
        if (toolsJson != null && toolsJson.length() > 0) json.put("tools", toolsJson)
        json.put("temperature", temperature)
        json.put("stream", streamMode)
        json.put("messages", msgs)
        return json
    }

    private fun mapFinish(raw: String): FinishReason? = when (raw) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.MAX_TOKENS
        "tool_calls", "function_call" -> FinishReason.OTHER
        else -> null
    }

    /** SSE 流式：逐块读取 `data: {...}`，累积 content 与 tool_calls 片段，聚合成完整 JSON */
    private fun collectSse(url: String, payload: JSONObject): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 300_000
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val src = if (code in 200..299) conn.inputStream else conn.errorStream
            val content = StringBuilder()
            val toolArgs = mutableMapOf<Int, StringBuilder>()
            val toolNames = mutableMapOf<Int, String>()
            var finish = ""
            var usageJson: JSONObject? = null
            BufferedReader(InputStreamReader(src, Charsets.UTF_8)).use { r ->
                var line = r.readLine()
                while (line != null) {
                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        if (data.isNotEmpty() && data != "[DONE]") {
                            runCatching {
                                val chunk = JSONObject(data)
                                val delta = chunk.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                                if (delta != null) {
                                    val piece = delta.optString("content")
                                    if (piece.isNotEmpty()) content.append(piece)
                                    val tcs = delta.optJSONArray("tool_calls")
                                    if (tcs != null) for (i in 0 until tcs.length()) {
                                        val tc = tcs.optJSONObject(i) ?: continue
                                        val idx = tc.optInt("index", 0)
                                        val fn = tc.optJSONObject("function") ?: continue
                                        if (fn.has("name")) toolNames[idx] = fn.optString("name")
                                        val argPiece = fn.optString("arguments")
                                        if (argPiece.isNotEmpty()) toolArgs.getOrPut(idx) { StringBuilder() }.append(argPiece)
                                    }
                                }
                                val fr = chunk.optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason")
                                if (!fr.isNullOrEmpty()) finish = fr
                                chunk.optJSONObject("usage")?.let { usageJson = it }
                            }
                        }
                    }
                    line = r.readLine()
                }
            }
            if (code !in 200..299) throw RuntimeException("HTTP $code（SSE）")
            // 诊断：一个 data 块都没收到（非标准 SSE 或网关忽略 stream 参数）→ 回退整包再请求一次
            if (content.isEmpty() && toolNames.isEmpty()) {
                android.util.Log.w("CandyAgent", "sse empty, falling back to non-stream")
                return postJson(url, payload.put("stream", false))
            }
            // 聚合成与非流式相同的结构
            val message = JSONObject()
            if (content.isNotEmpty()) message.put("content", content.toString()) else message.put("content", "")
            if (toolNames.isNotEmpty()) {
                val arr = JSONArray()
                for ((idx, name) in toolNames.toSortedMap()) {
                    val args = toolArgs[idx]?.toString() ?: "{}"
                    arr.put(JSONObject().put("id", "call_$idx").put("type", "function").put("function", JSONObject().put("name", name).put("arguments", args)))
                }
                message.put("tool_calls", arr)
            }
            val choice = JSONObject().put("message", message).put("finish_reason", finish)
            val out = JSONObject().put("choices", JSONArray().put(choice))
            if (usageJson != null) out.put("usage", usageJson)
            android.util.Log.w("CandyAgent", "sse aggregated: content=" + content.length + " chars, tools=" + toolNames.size)
            return out.toString()
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(url: String, payload: JSONObject): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 180_000
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                var line = r.readLine()
                while (line != null) { sb.append(line); line = r.readLine() }
            }
            if (code !in 200..299) throw RuntimeException("HTTP $code: " + sb.toString().take(300))
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }
}
