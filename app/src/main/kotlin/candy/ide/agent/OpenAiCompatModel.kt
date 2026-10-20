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
) : Model {

    override val name: String = modelName

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val payload = buildPayload(request)
        val resp = postJson(baseUrl.trimEnd('/') + "/chat/completions", payload)
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
                val argsJson = fn.optJSONObject("arguments")?.toString() ?: "{}"
                val argsMap = mutableMapOf<String, Any?>()
                runCatching {
                    val parsed = JSONObject(argsJson)
                    for (k in parsed.keys()) argsMap[k] = parsed.get(k)
                }
                parts.add(Part(functionCall = FunctionCall(name = name, args = argsMap)))
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
        json.put("stream", false)
        json.put("max_tokens", 4096)
        val msgs = JSONArray()
        val contents = request.contents
        var systemText: String? = null
        for (c in contents) {
            val role = c.role
            val text = c.parts.mapNotNull { it.text }.joinToString("")
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
        json.put("messages", msgs)
        return json
    }

    private fun mapFinish(raw: String): FinishReason? = when (raw) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.MAX_TOKENS
        "tool_calls", "function_call" -> FinishReason.OTHER
        else -> null
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
