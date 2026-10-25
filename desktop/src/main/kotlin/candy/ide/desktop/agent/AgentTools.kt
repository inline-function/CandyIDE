package candy.ide.desktop.agent

import candy.ide.desktop.IdeState
import candy.ide.desktop.Settings
import candy.ide.desktop.data.ProjectStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * agent 可用的工具集合（桌面端形态；对应安卓端 CandyToolRegistry）。
 * 描述按 OpenAI Function Calling 声明，随请求发送；模型返回 tool_calls 时由 DesktopAgent 解析执行。
 */
class AgentTools {
    enum class AgentScope(val label: String, val key: String) {
        READONLY("只读", "readonly"), PROJECT("当前工程", "project"),
        WORKSPACE("当前IDE目录", "workspace"), FULL("完全访问", "full"),
    }

    fun scope(): AgentScope = AgentScope.entries.firstOrNull { it.key == Settings.agentScope } ?: AgentScope.PROJECT
    fun mayWrite(): Boolean = scope() != AgentScope.READONLY

    /** 由 UI 注入的当前状态 */
    @Volatile var currentFile: File? = null
    @Volatile var currentProject: File? = null
    @Volatile var diagnostics: List<candy.ide.data.DiagItem> = emptyList()

    // ---- UI 按键工具：等价于编辑页按钮 ----
    @Volatile var onTapRun: (() -> Unit)? = null
    @Volatile var onTapSave: (() -> Unit)? = null
    @Volatile var onGotoLine: ((Int) -> Unit)? = null
    @Volatile var onTapEdit: ((String) -> Unit)? = null

    private fun workspaceRoot(): File = File(Settings.workspaceRoot)

    private val runners: Map<String, (Map<String, Any?>) -> String> = mapOf(
        "read_current_file" to { _ ->
            val f = currentFile
            if (f == null) "没有打开的文件" else runCatching {
                if (!f.isFile) "文件不存在: ${f.name}" else f.readText().take(8000)
            }.getOrElse { "读取失败: ${it.message}" }
        },
        "run_shell" to { args ->
            if (!mayWrite()) return@to "当前为只读访问权，无法执行命令"
            val cmd = args["command"] as? String
            if (cmd.isNullOrBlank()) return@to "参数 command 必填"
            val cwd = when (scope()) {
                AgentScope.PROJECT -> currentProject ?: return@to "没有打开工程，当前访问权只允许在工程内执行"
                AgentScope.WORKSPACE -> workspaceRoot()
                AgentScope.FULL -> null
                AgentScope.READONLY -> return@to "当前为只读访问权"
            }
            runCatching {
                val parts = if (System.getProperty("os.name").startsWith("Windows"))
                    listOf("cmd", "/c", cmd) else listOf("sh", "-c", cmd)
                val p = ProcessBuilder(parts).apply {
                    if (cwd != null) directory(cwd)
                    redirectErrorStream(true)
                }.start()
                val out = p.inputStream.bufferedReader().use { it.readText() }
                val code = p.waitFor()
                out.ifBlank { "（执行完毕，退出码 $code）" }
            }.getOrElse { "shell 执行出错: ${it.message}" }
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
            val root = currentProject
            if (root == null || !root.isDirectory) "没有打开工程"
            else buildString {
                fun walk(dir: File, depth: Int) {
                    dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                        if (f.name.startsWith(".") || f.name == "build") return@forEach
                        append("  ".repeat(depth)).append(if (f.isDirectory) "[" + f.name + "]/" else f.name).append("\n")
                        if (f.isDirectory) walk(f, depth + 1)
                    }
                }
                walk(root, 0)
            }
        },
        "read_project_file" to { args ->
            val rel = (args["path"] as? String)?.trim()?.trim('/', '\\')
            if (rel.isNullOrBlank()) return@to "参数 path 必填（相对工程目录的路径）"
            val root = currentProject
            if (root == null) "没有打开工程" else runCatching {
                val hit = root.walkTopDown().firstOrNull { it.relativeTo(root).path.replace('\\', '/') == rel }
                if (hit == null || !hit.isFile) "文件不存在: $rel" else hit.readText().take(8000)
            }.getOrElse { "读取失败: ${it.message}" }
        },
        "write_current_file" to { args ->
            if (!mayWrite()) return@to "当前为只读访问权，无法写文件"
            val content = args["content"] as? String
            if (content == null) return@to "参数 content 必填"
            val f = currentFile
            if (f == null) "没有打开的文件" else runCatching {
                f.writeText(content)
                "已写入 ${f.name}（" + content.length + " 字符）"
            }.getOrElse { "写入失败: ${it.message}" }
        },
        "list_project_files" to { _ ->
            val root = currentProject
            if (root == null || !root.isDirectory) "没有打开工程"
            else root.listFiles()?.sortedBy { it.name }?.joinToString("\n") { f ->
                (if (f.isDirectory) "[目录] " else "") + f.name
            } ?: "工程目录为空"
        },
        "project_info" to { _ ->
            val root = currentProject
            if (root == null || !root.isDirectory) "没有打开工程"
            else buildString {
                append("工程目录: ").append(root.absolutePath).append("\n")
                append("工程类型: ").append(
                    when (ProjectStore.typeOf(root)) {
                        ProjectStore.TYPE_JSAPK -> "安卓工程"
                        ProjectStore.TYPE_SCRIPT -> "脚本工程"
                        ProjectStore.TYPE_MD -> "MD工程"
                        else -> "JS工程"
                    },
                ).append("\n")
                append("文件总数: ").append(root.walkTopDown().count { it.isFile() }).append("\n")
                currentFile?.let { append("当前打开文件: ").append(it.name).append("\n") }
            }
        },
        "workspace_info" to { _ ->
            val root = workspaceRoot()
            buildString {
                append("工作区: ").append(root.absolutePath).append("\n")
                root.listFiles()?.sortedBy { it.name }?.forEach { d ->
                    if (d.isDirectory) append("[工程] ").append(d.name).append("\n")
                }
                currentProject?.let { append("当前工程: ").append(it.absolutePath).append("\n") }
            }
        },
        "tap_run" to { _ ->
            if (!mayWrite()) return@to "当前为只读访问权，无法执行运行"
            val a = onTapRun
            if (a == null) "IDE 没有打开编辑器，无法运行"
            else { a.invoke(); "已点运行按钮：保存并编译运行当前工程，输出在控制台抽屉" }
        },
        "tap_save" to { _ ->
            if (!mayWrite()) return@to "当前为只读访问权，无法保存"
            val a = onTapSave
            if (a == null) "IDE 没有打开编辑器，无法保存"
            else { a.invoke(); "已点保存按钮：当前文件已落盘" }
        },
        "goto_line" to { args ->
            val line = (args["line"] as? Number)?.toInt()
            if (line == null) "参数 line 必填（行号，从 1 开始）"
            else { onGotoLine?.invoke(line); "已跳转到第 " + line + " 行" }
        },
        "edit_current_file" to { args ->
            if (!mayWrite()) return@to "当前为只读访问权，无法编辑文件"
            val content = args["content"] as? String
            if (content == null) "参数 content 必填（完整的新文件全文）"
            else {
                val a = onTapEdit
                if (a == null) "当前没有打开的文件可编辑"
                else { a.invoke(content); "已把新内容写入编辑器并保存" }
            }
        },
    )

    /** OpenAI 兼容端点的 tools 声明 */
    fun openAiTools(): JSONArray {
        fun fn(name: String, desc: String, props: JSONObject, required: List<String> = emptyList()): JSONObject {
            val p = JSONObject().put("type", "object").put("properties", props)
            if (required.isNotEmpty()) p.put("required", JSONArray(required))
            return JSONObject()
                .put("type", "function")
                .put("function", JSONObject().put("name", name).put("description", desc).put("parameters", p))
        }
        val noProps = JSONObject()
        val cmdProps = JSONObject().put("command", JSONObject().put("type", "string").put("description", "要在 shell 里执行的命令"))
        val pathProps = JSONObject().put("path", JSONObject().put("type", "string").put("description", "相对工程目录的文件路径"))
        val contentProps = JSONObject().put("content", JSONObject().put("type", "string").put("description", "要写入的完整文件内容"))
        val arr = JSONArray()
        arr.put(fn("read_current_file", "读取编辑器当前打开的文件全文", noProps))
        arr.put(fn("run_shell", "执行 shell 命令并返回输出", cmdProps, listOf("command")))
        arr.put(fn("list_project_files", "列出当前工程的文件清单", noProps))
        arr.put(fn("project_info", "获取当前工程的信息：文件路径、工程类型、文件数量、当前打开的文件。开始任何工程操作前先调用", noProps))
        arr.put(fn("workspace_info", "获取工作区目录的绝对路径与工程清单", noProps))
        arr.put(fn("list_diagnostics", "当前编辑器的编译诊断", noProps))
        arr.put(fn("get_project_tree", "当前工程的目录树（忽略 build/隐藏目录）", noProps))
        arr.put(fn("read_project_file", "读工程内任意文件", pathProps, listOf("path")))
        arr.put(fn("write_current_file", "覆写编辑器当前打开的文件", contentProps, listOf("content")))
        arr.put(fn("tap_run", "点编辑页运行按钮：保存并编译运行当前工程（无参数）", noProps))
        arr.put(fn("tap_save", "点编辑页保存按钮：保存当前文件（无参数）", noProps))
        arr.put(fn("goto_line", "编辑器跳转到指定行（行号从 1 开始）", JSONObject().put("line", JSONObject().put("type", "integer").put("description", "目标行号，从 1 开始")), listOf("line")))
        arr.put(fn("edit_current_file", "替换编辑器当前文件的全部内容并保存", contentProps, listOf("content")))
        return arr
    }

    /** 模型给了 functionCall → 在这里执行 */
    fun execute(name: String, args: Map<String, Any?>): String {
        val fn = runners[name] ?: return "未知工具: $name（可调: ${runners.keys.joinToString(", ")}）"
        val flat = args.mapValues { (_, v) ->
            when (v) {
                is JSONObject -> v.toString()
                is JSONArray -> v.toString()
                else -> v
            }
        }
        return runCatching { fn(flat) }.getOrElse { "工具执行出错: " + (it.message ?: it.javaClass.simpleName) }
    }

    /** 给系统提示的工具说明 */
    fun toolDescriptions(): String = """
【工具调用规定（OpenAI Function Calling）】
工具已注册为可调用函数。需要信息时必须发起调用，不要凭空猜测文件内容或命令结果。
  你在回复里返回 message.tool_calls，每个调用形如
  {"id": "call_0", "type": "function", "function": {"name": "工具名", "arguments": "<JSON 字符串>"}}
  arguments 必须是合法 JSON 字符串，参数一个都不能少、不能空（无参工具也写 "{}"）。
  工具结果会以 tool 角色返回给你，无需重复询问用户。一次只调一个工具，拿到结果再决定下一步。
  改文件前先用 read_current_file / read_project_file 读现有内容；写文件用 write_current_file 给出完整新内容。

可用工具：
1. read_current_file —— 读取编辑器当前打开的文件全文（无参数）
2. run_shell {"command": "命令字符串"} —— 执行 shell 命令并返回输出
3. list_project_files —— 列出当前工程的文件清单（无参数）
4. list_diagnostics —— 当前编辑器的编译诊断（无参数）
5. get_project_tree —— 当前工程的目录树（无参数，忽略 build/ 等生成目录）
6. read_project_file {"path": "工程内相对路径，如 src/main.subl"} —— 读工程内任意文件
7. write_current_file {"content": "完整的新文件全文"} —— 覆写编辑器当前打开的文件
8. project_info —— 当前工程信息（无参数，干活前先调）
9. workspace_info —— 工作区目录绝对路径与工程清单（无参数）
10. tap_run —— 点运行按钮：保存并编译运行当前工程（无参数，输出在控制台）
11. tap_save —— 点保存按钮：保存当前文件（无参数）
12. goto_line {"line": 行号} —— 编辑器跳转到指定行（行号从 1 开始）
13. edit_current_file {"content": "完整的新文件全文"} —— 替换当前编辑器内容并保存
【优先级】要运行工程用 tap_run，要改文件用 edit_current_file，不要用 run_shell 模拟按键动作。""".trimIndent()
}
