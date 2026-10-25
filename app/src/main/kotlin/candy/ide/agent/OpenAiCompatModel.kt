package candy.ide.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.FunctionCall
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
 * OpenAI 兼容端点的 ADK Model 实现（POST /chat/completions）。
 *
 * 端点约定（API 文档）：
 *  - messages：历史对话数组（历史上下文全部塞进来）
 *  - stream=true：SSE 逐块返回；stream=false：整包 JSON
 *  - tools：OpenAI Function Calling 格式；响应里 message.tool_calls 携带调用
 *
 * 流式行为：streamMode=true 时 flow **逐块 emit** LlmResponse（finishReason=null 的增量块），
 * 最后再 emit 一个聚合结果（含 tool_calls 与 finishReason），上游据此转发增量给 UI。
 * 非流式：collect 到一个完整 LlmResponse。
 */
class OpenAiCompatModel(
    private val baseUrl: String,
    private val apiKey: String,
    private val modelName: String,
    private val toolsJson: JSONArray? = null,
    var temperature: Float = 1.0f,
    var streamMode: Boolean = false,
) : Model {

    override val name: String = modelName

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val payload = buildPayload(request)
        val base = baseUrl.trim().trimEnd('/')
        val withV1 = if (base.isEmpty() || android.net.Uri.parse(base).path?.trim('/')?.isEmpty() != false) base + "/v1" else base
        val endpoint = withV1 + "/chat/completions"
        if (!streamMode) {
            // 非流式：整包解析；若带 reasoning_content 先推一条 think 增量（finishReason=null）
            val raw = postJson(endpoint, payload)
            val reason = runCatching {
                JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("reasoning_content")
            }.getOrDefault("")
            if (!reason.isNullOrEmpty()) {
                emit(LlmResponse(
                    content = Content(parts = listOf(Part(text = "§think§" + reason)), role = "model"),
                    finishReason = null,
                ))
            }
            val parts = parseResponse(raw)
            emit(LlmResponse(content = Content(parts = parts, role = "model"), finishReason = FinishReason.STOP))
            return@flow
        }
        // 流式：逐块 emit 增量文本（finishReason=null），最后 emit 聚合结果
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
            val reasonAccum = StringBuilder()
            val toolArgs = mutableMapOf<Int, StringBuilder>()
            val toolNames = mutableMapOf<Int, String>()
            var finish = ""
            BufferedReader(InputStreamReader(src, Charsets.UTF_8)).use { r ->
                var line = r.readLine()
                while (line != null) {
                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        if (data.isNotEmpty() && data != "[DONE]") {
                            runCatching {
                                val chunk = JSONObject(data)
                                val choice = chunk.optJSONArray("choices")?.optJSONObject(0)
                                val delta = choice?.optJSONObject("delta")
                                if (delta != null) {
                                    val piece = delta.optString("content")
                                    if (piece.isNotEmpty()) {
                                        content.append(piece)
                                        emit(LlmResponse(
                                            content = Content(parts = listOf(Part(text = piece)), role = "model"),
                                            finishReason = null,
                                        ))
                                    }
                                    // 思考链（DeepSeek 系/兼容端点用 reasoning_content，部分用 reasoning）：
                                    // 只走旁路（§think§ 前缀），绝不进 content累加器——否则会落进会话历史回传给模型
                                    val reason = delta.optString("reasoning_content")
                                        .ifEmpty { delta.optString("reasoning") }
                                    if (reason.isNotEmpty()) {
                                        android.util.Log.w("CandyModel", "sse reason len=" + reason.length + " piece=" + reason.take(30))
                                        reasonAccum.append(reason)
                                        emit(LlmResponse(
                                            content = Content(parts = listOf(Part(text = "§think§" + reason)), role = "model"),
                                            finishReason = null,
                                        ))
                                    }
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
                                val fr = choice?.optString("finish_reason")
                                if (!fr.isNullOrEmpty()) finish = fr
                            }
                        }
                    }
                    line = r.readLine()
                }
            }
            if (code !in 200..299) throw RuntimeException("HTTP $code（SSE）")
            // 一个块都没收到（非标准 SSE 或网关忽略 stream）-> 回退整包再请求一次
            if (content.isEmpty() && toolNames.isEmpty() && reasonAccum.isEmpty()) {
                android.util.Log.w("CandyAgent", "sse empty, falling back to non-stream")
                val parts = parseResponse(postJson(endpoint, payload.put("stream", false)))
                emit(LlmResponse(content = Content(parts = parts, role = "model"), finishReason = FinishReason.STOP))
                return@flow
            }
            // 聚合最终块：正文 + 工具调用 + finishReason（上游据此进入工具循环或收下回答）
            val parts = mutableListOf<Part>()
            if (content.isNotEmpty()) parts.add(Part(text = content.toString()))
            for ((idx, name) in toolNames.toSortedMap()) {
                val args = toolArgs[idx]?.toString() ?: "{}"
                val argsMap = mutableMapOf<String, Any?>()
                runCatching {
                    val parsed = JSONObject(args)
                    for (k in parsed.keys()) argsMap[k] = parsed.get(k)
                }
                parts.add(Part(functionCall = FunctionCall(id = "call_$idx", name = name, args = argsMap)))
            }
            android.util.Log.w("CandyAgent", "sse aggregated: content=" + content.length + " tools=" + toolNames.size)
            emit(LlmResponse(content = Content(parts = parts, role = "model"), finishReason = FinishReason.STOP))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /** 整包 JSON 响应 -> ADK parts */
    private fun parseResponse(resp: String): List<Part> {
        val json = JSONObject(resp)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val msg = choice?.optJSONObject("message") ?: JSONObject()
        val parts = mutableListOf<Part>()
        val content = msg.optString("content")
        if (!content.isNullOrEmpty()) parts.add(Part(text = content))
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
        return parts
    }

    /** ADK contents -> OpenAI messages（system 指令合并放最前）；历史上下文全部塞进 messages */
    private fun buildPayload(request: LlmRequest): JSONObject {
        val json = JSONObject()
        json.put("model", modelName)
        json.put("stream", streamMode)
        val msgs = JSONArray()
        val contents = request.contents
        val callsAccum = mutableMapOf<String, JSONArray>()
        for (c in contents) {
            val role = c.role
            val text = c.parts.mapNotNull { it.text }.joinToString("")
            val calls = c.parts.mapNotNull { it.functionCall }
            if (calls.isNotEmpty()) {
                val m = JSONObject().put("role", "assistant")
                m.put("content", if (text.isNotBlank()) text else "")
                val arr = JSONArray()
                for (fc in calls) {
                    val cid = fc.id ?: "call_" + callsAccum.size
                    callsAccum[cid] = JSONArray()
                    arr.put(JSONObject().put("id", cid)
                        .put("type", "function")
                        .put("function", JSONObject().put("name", fc.name)
                            .put("arguments", JSONObject(fc.args ?: emptyMap<String, Any?>()).toString())))
                }
                m.put("tool_calls", arr)
                msgs.put(m)
                continue
            }
            val responses = c.parts.mapNotNull { it.functionResponse }
            if (responses.isNotEmpty()) {
                for (fr in responses) {
                    val resText = (fr.response?.get("result") ?: "").toString()
                    msgs.put(JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", fr.id)
                        .put("content", resText))
                }
                continue
            }
            if (text.isBlank()) continue
            val openRole = when (role) {
                "system" -> "system"
                "model" -> "assistant"
                else -> "user"
            }
            msgs.put(JSONObject().put("role", openRole).put("content", text))
        }
        json.put("messages", msgs)
        if (toolsJson != null && toolsJson.length() > 0) json.put("tools", toolsJson)
        json.put("temperature", temperature)
        return json
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
                while (line != null) { sb.append(line).append('\n'); line = r.readLine() }
            }
            if (code !in 200..299) throw RuntimeException("HTTP $code: " + sb.toString().take(200))
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }
}
