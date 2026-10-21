package candy.ide.agent

import android.content.Context
import candy.ide.data.Settings
import com.google.adk.kt.tools.BaseTool

/**
 * agent 可用的工具集合。每个工具有名字、参数说明、执行器。
 * 描述以 JSON Schema 片段写进系统提示（模型按 OpenAI function calling 约定调用），
 * adkTools() 目前返回空（ADK 的 FunctionTool 需要 suspend 契约，会话循环已直接解析 functionCall）。
 */
class CandyToolRegistry(private val ctx: Context) {

    /** 当前打开的文件（由编辑页注入，agent 读它） */
    @Volatile var currentFile: candy.ide.fs.CandyPath? = null

    /** 当前诊断快照（由 VM 注入；DiagItem 来自 candy.ide.data） */
    @Volatile var diagnostics: List<candy.ide.data.DiagItem> = emptyList()

    /** 当前打开的工程根（由 VM 注入） */
    @Volatile var currentProjectDir: candy.ide.fs.CandyPath? = null
    private val runners: Map<String, (Map<String, Any?>) -> String> = mapOf(
        "read_current_file" to { _ ->
            val f = currentFile
            if (f == null) "没有打开的文件" else runCatching {
                if (!f.isFile()) "文件不存在: ${f.name}" else f.readText().take(8000)
            }.getOrElse { "读取失败: ${it.message}" }
        },
        "run_shell" to { args ->
            val cmd = args["command"] as? String
            if (cmd.isNullOrBlank()) return@to "参数 command 必填"
            // 走 Shizuku（若已授权）；否则提示
            val out = candy.ide.jsapk.ShizukuShell.exec(arrayOf("sh", "-c", cmd))
            out ?: "Shizuku 未连接/未授权，无法执行 shell。请在设置里连接 Shizuku。"
        },
        "list_diagnostics" to { _ ->
            val diags = diagnostics
            if (diags.isEmpty()) "当前没有诊断信息（编译通过）"
            else diags.take(30).joinToString("\n") { d ->
                val sev = when (d.severity.toInt()) {
                    2 -> "[错误]"
                    1 -> "[警告]"
                    else -> "[提示]"
                }
                "${d.line + 1}:${d.char + 1} $sev ${d.message}"
            }
        },
        "get_project_tree" to { _ ->
            val root = currentProjectDir
            if (root == null || !root.isDirectory()) "没有打开工程"
            else buildString {
                fun walk(dir: candy.ide.fs.CandyPath, depth: Int) {
                    dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                        if (f.name.startsWith(".") || f.name == "build") return@forEach
                        append("  ".repeat(depth)).append(if (f.isDirectory()) "[" + f.name + "]/" else f.name)
                        append("  ".repeat(depth)).append(if (f.isDirectory()) "[" + f.name + "]/" else f.name)
                    }
                walk(root, 0)
            }
            }
        },
        "read_project_file" to { args ->
            val rel = (args["path"] as? String)?.trim()?.trim('/', '\\')
            if (rel.isNullOrBlank()) return@to "参数 path 必填（相对工程目录的路径）"
            val root = currentProjectDir
            if (root == null) "没有打开工程" else runCatching {
                val hit = root.walkRelative().firstOrNull { it.second == rel }?.first
                if (hit == null || !hit.isFile()) "文件不存在: $rel" else hit.readText().take(8000)
            }.getOrElse { "读取失败: ${it.message}" }
        },
        "write_current_file" to { args ->
            val content = args["content"] as? String
            if (content == null) return@to "参数 content 必填"
            val f = currentFile
            if (f == null) "没有打开的文件" else runCatching {
                f.writeText(content)
                "已写入 ${f.name}（" + content.length + " 字符）"
            }.getOrElse { "写入失败: ${it.message}" }
        },
        "list_project_files" to { _ ->
            val root = currentProjectDir
            if (root == null || !root.isDirectory()) "没有打开工程"
            else root.listFiles()?.sortedBy { it.name }?.joinToString("\n") { f ->
                (if (f.isDirectory()) "[目录] " else "") + f.name
            } ?: "工程目录为空"
        },
    )



    fun adkTools(): List<BaseTool> = emptyList()

    /** OpenAI 兼容端点的 tools 声明（buildPayload 里随请求发送，模型才会发起 tool_calls） */
    fun openAiTools(): org.json.JSONArray {
        fun fn(name: String, desc: String, props: org.json.JSONObject, required: List<String> = emptyList()): org.json.JSONObject {
            val p = org.json.JSONObject().put("type", "object").put("properties", props)
            if (required.isNotEmpty()) p.put("required", org.json.JSONArray(required))
            return org.json.JSONObject()
                .put("type", "function")
                .put("function", org.json.JSONObject().put("name", name).put("description", desc).put("parameters", p))
        }
        val noProps = org.json.JSONObject()
        val cmdProps = org.json.JSONObject().put("command", org.json.JSONObject().put("type", "string").put("description", "要在 shell 里执行的命令"))
        val pathProps = org.json.JSONObject().put("path", org.json.JSONObject().put("type", "string").put("description", "相对工程目录的文件路径"))
        val contentProps = org.json.JSONObject().put("content", org.json.JSONObject().put("type", "string").put("description", "要写入的完整文件内容"))
        val arr = org.json.JSONArray()
        arr.put(fn("read_current_file", "读取编辑器当前打开的文件全文", noProps))
        arr.put(fn("run_shell", "经 Shizuku 执行 shell 命令并返回输出", cmdProps, listOf("command")))
        arr.put(fn("list_project_files", "列出当前工程的文件清单", noProps))
        arr.put(fn("list_diagnostics", "当前编辑器的编译诊断", noProps))
        arr.put(fn("get_project_tree", "当前工程的目录树（忽略 build/隐藏目录）", noProps))
        arr.put(fn("read_project_file", "读工程内任意文件", pathProps, listOf("path")))
        arr.put(fn("write_current_file", "覆写编辑器当前打开的文件", contentProps, listOf("content")))
        return arr
    }

    /** 模型给了 functionCall → 在这里执行 */
    fun execute(name: String, args: Map<String, Any?>): String {
        val fn = runners[name] ?: return "未知工具: $name（可调: ${runners.keys.joinToString(", ")}）"
        // 模型发来的 arguments 可能含 org.json 对象（嵌套），统一展平成可消费的 Map
        val flat = args.mapValues { (_, v) ->
            when (v) {
                is org.json.JSONObject -> v.toString()
                is org.json.JSONArray -> v.toString()
                else -> v
            }
        }
        return runCatching { fn(flat) }.getOrElse { "工具执行出错: " + (it.message ?: it.javaClass.simpleName) }
    }

    /** 给系统提示的工具说明（让模型知道有哪些工具可用） */
    fun toolDescriptions(): String = """
【工具调用规定】
- 已注册为可调用函数（function calling）：需要信息时必须发起调用，不要凭空猜测文件内容或命令结果。
- 一次只调一个工具，拿到结果后再决定下一步；禁止在同一轮里并发多个工具。
- 参数严格按 schema 给（JSON 字符串）；工具结果会以 tool 角色返回给你，无需重复询问用户。
- 改文件前先用 read_current_file / read_project_file 看现有内容；写文件用 write_current_file 给出完整新内容。
- run_shell 经 Shizuku 执行，仅在 Shizuku 已连接授权时可用，失败就如实说，不要谎称执行成功。

可用工具（按 OpenAI 约定 functionCall：name + arguments）：
1. read_current_file —— 读取编辑器当前打开的文件全文（无参数）
2. run_shell {"command": "命令"} —— 经 Shizuku 执行 shell 命令（需 Shizuku 已授权）
3. list_project_files —— 列出当前工程的文件清单（无参数）
4. list_diagnostics —— 当前编辑器的编译诊断（无参数）
5. get_project_tree —— 当前工程的目录树（无参数，忽略 build/隐藏目录）
6. read_project_file {"path": "相对路径"} —— 读工程内任意文件
7. write_current_file {"content": "全文"} —— 覆写编辑器当前打开的文件
""".trimIndent()
}
