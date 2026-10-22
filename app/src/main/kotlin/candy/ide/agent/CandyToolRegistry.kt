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

        enum class AgentScope(val label: String, val key: String) {
        READONLY("只读", "readonly"), PROJECT("当前工程", "project"),
        WORKSPACE("当前IDE目录", "workspace"), FULL("完全访问", "full"),
    }

    fun scope(): AgentScope = AgentScope.entries.firstOrNull { it.key == Settings.agentScope } ?: AgentScope.PROJECT
    /** 写 / 执行类操作的统一守卫：只读模式下拒绝 */
    fun mayWrite(): Boolean = scope() != AgentScope.READONLY
/** 当前打开的文件（由编辑页注入，agent 读它） */
    @Volatile var currentFile: candy.ide.fs.CandyPath? = null

    /** 当前诊断快照（由 VM 注入；DiagItem 来自 candy.ide.data） */
    @Volatile var diagnostics: List<candy.ide.data.DiagItem> = emptyList()

    /** 当前打开的工程根（由 VM 注入） */
    @Volatile var currentProjectDir: candy.ide.fs.CandyPath? = null

    // ---- UI 按键工具：由 VM 注入，调用等价于在编辑页按对应按钮 ----
    /** 长按「运行」按钮：把当前工程打包成 APK */
    @Volatile var onLongPressRun: (() -> Unit)? = null
    /** 点「运行」按钮：保存并编译运行当前工程 */
    @Volatile var onTapRun: (() -> Unit)? = null
    /** 点「保存」按钮：保存当前文件 */
    @Volatile var onTapSave: (() -> Unit)? = null
    /** 跳转到指定行 */
    @Volatile var onGotoLine: ((Int) -> Unit)? = null
    /** 替换当前编辑器正文并保存（write_current_file 的 UI 版） */
    @Volatile var onTapEdit: ((String) -> Unit)? = null
    private val runners: Map<String, (Map<String, Any?>) -> String> = mapOf(
        "read_current_file" to { _ ->
            val f = currentFile
            if (f == null) "没有打开的文件" else runCatching {
                if (!f.isFile()) "文件不存在: ${f.name}" else f.readText().take(8000)
            }.getOrElse { "读取失败: ${it.message}" }
        },
        "run_shell" to { args ->
            if (!mayWrite()) return@to "当前为只读访问权，无法执行命令"
            if (scope() == AgentScope.PROJECT) {
                // 当前工程模式：shell 只能在工程目录里跑
                val cwd = currentProjectDir ?: return@to "没有打开工程，当前访问权只允许在工程内执行"
            }

            val cmd = args["command"] as? String
            if (cmd.isNullOrBlank()) return@to "参数 command 必填"
            // agent 的 shell 一律走 Termux（RUN_COMMAND）：Shizuku 的 shell 环境没有 git 等完整工具链，
            // 且其用户被 SELinux 挡在 /data/data/com.termux 之外
            val proj0 = scope()
            val cwd = if (proj0 == AgentScope.PROJECT) currentProjectDir?.let { candy.ide.git.GitManager.realPath(it) } else null
            val res = candy.ide.terminal.TerminalBridge.exec(ctx, cmd, cwd, forceTermux = true)
            return@to when (res.channel) {
                candy.ide.terminal.TerminalBridge.Channel.TERMUX -> {
                    // 轮询中转输出（最多 20s）
                    var waited = 0
                    var relayOut: String? = null
                    while (waited < 20000) {
                        Thread.sleep(400); waited += 400
                        relayOut = candy.ide.terminal.TerminalBridge.readRelay()
                        if (relayOut != null) break
                    }
                    relayOut?.ifBlank { "（执行完毕，无输出）" } ?: "Termux 命令超时未返回（Termux 没在运行？）"
                }
                else -> "shell 执行失败：" + res.output
            }
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
            val root = currentProjectDir
            if (root == null || !root.isDirectory()) "没有打开工程"
            else root.listFiles()?.sortedBy { it.name }?.joinToString("\n") { f ->
                (if (f.isDirectory()) "[目录] " else "") + f.name
            } ?: "工程目录为空"
        },
        // 当前工程信息：真实路径 / 类型 / 文件数 / 当前文件 —— agent 干活前先看这个
        "project_info" to { _ ->
            val root = currentProjectDir
            if (root == null || !root.isDirectory()) "没有打开工程"
            else buildString {
                append("工程目录(真实路径): ").append(candy.ide.git.GitManager.realPath(root) ?: root.displayPath).append("\n")
                append("工程目录(IDE 路径): ").append(root.displayPath).append("\n")
                append("工程类型: ").append(
                    when (candy.ide.data.ProjectStore.typeOf(root)) {
                        candy.ide.data.ProjectStore.TYPE_JSAPK -> "安卓工程"
                        candy.ide.data.ProjectStore.TYPE_SCRIPT -> "脚本工程"
                        candy.ide.data.ProjectStore.TYPE_MD -> "MD工程"
                        else -> "JS工程"
                    },
                ).append("\n")
                val files = root.walk().filter { it.isFile() }.toList()
                append("文件总数: ").append(files.size).append("\n")
                currentFile?.let { append("当前打开文件: ").append(it.name).append("\n") }
            }
        },
        // 工作区（IDE 目录）真实路径与工程清单
        "workspace_info" to { _ ->
            val root = currentProjectDir?.parent
            buildString {
                append("工作区: ").append(root?.displayPath ?: "未知").append("\n")
                root?.let { r ->
                    append("工作区真实路径: ").append(candy.ide.git.GitManager.realPath(r) ?: "(SAF 目录树)").append("\n")
                    r.listFiles()?.sortedBy { it.name }?.forEach { d ->
                        if (d.isDirectory()) append("[工程] ").append(d.name).append("\n")
                    }
                }
                currentProjectDir?.let { append("当前工程真实路径: ").append(candy.ide.git.GitManager.realPath(it) ?: "(SAF)").append("\n") }
            }
        },
        // ---- UI 按键工具：替 agent 按编辑页的按钮，比 shell 更直接 ----
        "tap_run" to { _ ->
            if (!mayWrite()) return@to "当前为只读访问权，无法执行运行"

            val a = onTapRun
            if (a == null) "IDE 没有打开编辑器，无法运行"
            else { a.invoke(); "已点运行按钮：保存并编译运行当前工程，输出在终端页/控制台" }
        },
        "long_press_run" to { _ ->
            if (!mayWrite()) return@to "当前为只读访问权，无法打包"

            val a = onLongPressRun
            if (a == null) "IDE 没有打开编辑器，无法打包"
            else { a.invoke(); "已长按运行按钮：开始把当前工程打包成 APK，进度在终端日志，完成后会提示安装" }
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

            // 别名直达写入：内容塞进编辑器再保存（走黄色顶条/文件树的标准链路）
            val content = args["content"] as? String
            if (content == null) "参数 content 必填（完整的新文件全文）"
            else { onTapEdit?.invoke(content); "已把新内容写入编辑器并保存" }
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
        arr.put(fn("run_shell", "执行 shell 命令并返回输出（经 Termux 通道：完整 Linux 工具链，含 git/ls/cat 等）", cmdProps, listOf("command")))
        arr.put(fn("list_project_files", "列出当前工程的文件清单", noProps))
        arr.put(fn("project_info", "获取当前工程的信息：真实文件路径、工程类型、文件数量、当前打开的文件。开始任何工程操作前先调用", noProps))
        arr.put(fn("workspace_info", "获取工作区（IDE 目录）的真实绝对路径与工程清单。需要在终端里跑命令（git/打包等）时用它拿到真实路径", noProps))
        arr.put(fn("list_diagnostics", "当前编辑器的编译诊断", noProps))
        arr.put(fn("get_project_tree", "当前工程的目录树（忽略 build/隐藏目录）", noProps))
        arr.put(fn("read_project_file", "读工程内任意文件", pathProps, listOf("path")))
        arr.put(fn("write_current_file", "覆写编辑器当前打开的文件", contentProps, listOf("content")))
        // ---- UI 按键工具：等价于在编辑页按按钮（优先于 shell 模拟点击） ----
        arr.put(fn("tap_run", "点编辑页运行按钮：保存并编译运行当前工程（无参数）", noProps))
        arr.put(fn("long_press_run", "长按编辑页运行按钮：把当前工程打包成 APK（无参数）", noProps))
        arr.put(fn("tap_save", "点编辑页保存按钮：保存当前文件（无参数）", noProps))
        arr.put(fn("goto_line", "编辑器跳转到指定行（行号从 1 开始）", org.json.JSONObject().put("line", org.json.JSONObject().put("type", "integer").put("description", "目标行号，从 1 开始")), listOf("line")))
        arr.put(fn("edit_current_file", "替换编辑器当前文件的全部内容并保存（等价于 write_current_file，但走编辑器标准保存链路）", contentProps, listOf("content")))
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

    /** 给系统提示的工具说明：按 OpenAI Function Calling 格式给出具体调用格式与示例，
     *  端点是 OpenAI 兼容的 /v1/chat/completions，tools 数组就是下面的声明；
     *  响应里以 message.tool_calls 返回调用，arguments 是 JSON 字符串。 */
    fun toolDescriptions(): String = """
【工具调用规定（OpenAI Function Calling）】
工具已注册为可调用函数。需要信息时必须发起调用，不要凭空猜测文件内容或命令结果。
调用格式（端点为 OpenAI 兼容 /v1/chat/completions）：
  你在回复里返回 message.tool_calls，每个调用形如
  {"id": "call_0", "type": "function", "function": {"name": "工具名", "arguments": "<JSON 字符串>"}}
  arguments 必须是合法 JSON 字符串，参数一个都不能少、不能空（无参工具也写 "{}"）。
  工具结果会以 tool 角色返回给你，无需重复询问用户。一次只调一个工具，拿到结果再决定下一步。
  改文件前先用 read_current_file / read_project_file 读现有内容；写文件用 write_current_file 给出完整新内容。

调用示例：
  用户问"当前文件写的是什么" -> 你返回 tool_calls: name="read_current_file", arguments="{}"
  用户问"src/main.subl 里有什么" -> name="read_project_file", arguments="{\"path\": \"src/main.subl\"}"
  用户要你改当前文件 -> 先 name="read_current_file" arguments="{}"，拿到结果后再
     name="write_current_file" arguments="{\"content\": \"完整的新文件全文\"}"（必须是完整内容，不是片段）

可用工具：
1. read_current_file —— 读取编辑器当前打开的文件全文（无参数，arguments="{}"）
2. run_shell {"command": "命令字符串"} —— 执行 shell 命令并返回输出（普通命令经 Shizuku；git 相关命令自动经 Termux 通道执行，因为 Shizuku 的 shell 环境里没有 git）
3. list_project_files —— 列出当前工程的文件清单（无参数）
4. list_diagnostics —— 当前编辑器的编译诊断（无参数）
5. get_project_tree —— 当前工程的目录树（无参数，忽略 build/ 等生成目录）
6. read_project_file {"path": "工程内相对路径，如 src/main.subl"} —— 读工程内任意文件
7. write_current_file {"content": "完整的新文件全文"} —— 覆写编辑器当前打开的文件
8. project_info —— 当前工程信息：真实文件路径、工程类型、文件数、当前文件（无参数，干活前先调）
9. workspace_info —— 工作区（IDE 目录）真实绝对路径与工程清单（无参数，跑终端命令前先调）
10. tap_run —— 点运行按钮：保存并编译运行当前工程（无参数，输出在终端）
11. long_press_run —— 长按运行按钮：把当前工程打包成 APK（无参数；想编译/运行用 tap_run，想打包成应用用它）
12. tap_save —— 点保存按钮：保存当前文件（无参数）
13. goto_line {"line": 行号} —— 编辑器跳转到指定行（行号从 1 开始）
14. edit_current_file {"content": "完整的新文件全文"} —— 替换当前编辑器内容并保存（走编辑器标准链路）

【优先级】要运行工程用 tap_run，要打包成 APK 用 long_press_run，要改文件用 edit_current_file，
不要用 run_shell 模拟按键动作。""".trimIndent()
}
