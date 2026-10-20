package candy.ide.agent

import android.content.Context
import candy.ide.data.Settings
import com.google.adk.kt.tools.BaseTool
import java.io.File

/**
 * agent 可用的工具集合。每个工具有名字、参数说明、执行器。
 * 描述以 JSON Schema 片段写进系统提示（模型按 OpenAI function calling 约定调用），
 * adkTools() 目前返回空（ADK 的 FunctionTool 需要 suspend 契约，会话循环已直接解析 functionCall）。
 */
class CandyToolRegistry(private val ctx: Context) {

    /** 当前打开的文件路径（由编辑页注入，agent 读它） */
    @Volatile var currentFile: String? = null

    /** 当前诊断快照（由 VM 注入；DiagItem 来自 candy.ide.data） */
    @Volatile var diagnostics: List<candy.ide.data.DiagItem> = emptyList()

    private val runners: Map<String, (Map<String, Any?>) -> String> = mapOf(
        "read_current_file" to { _ ->
            val p = currentFile
            if (p == null) "没有打开的文件" else runCatching {
                val f = File(p)
                if (!f.isFile) "文件不存在: $p" else f.readText().take(8000)
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
            if (root == null || !root.isDirectory) "没有打开工程"
            else buildString {
                fun walk(dir: File, depth: Int) {
                    dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                        if (f.name.startsWith(".") || f.name == "build") return@forEach
                        append("  ".repeat(depth)).append(if (f.isDirectory) "[" + f.name + "]/" else f.name).append("\n")
                        if (f.isDirectory && depth < 3) walk(f, depth + 1)
                    }
                }
                walk(root, 0)
            }
        },
        "list_project_files" to { _ ->
            val root = currentProjectDir
            if (root == null || !root.isDirectory) "没有打开工程"
            else root.listFiles()?.sortedBy { it.name }?.joinToString("\n") { f ->
                (if (f.isDirectory) "[目录] " else "") + f.name
            } ?: "工程目录为空"
        },
    )

    @Volatile var currentProjectDir: File? = null

    fun adkTools(): List<BaseTool> = emptyList()

    /** 模型给了 functionCall → 在这里执行 */
    fun execute(name: String, args: Map<String, Any?>): String {
        val fn = runners[name] ?: return "未知工具: $name（可调: ${runners.keys.joinToString(", ")}）"
        return fn(args)
    }

    /** 给系统提示的工具说明（让模型知道有哪些工具可用） */
    fun toolDescriptions(): String = """
可用工具（返回 functionCall 时按 OpenAI 约定：{"name": 工具名, "arguments": {...}}）：
1. read_current_file —— 读取编辑器当前打开的文件全文（无参数）
2. run_shell {"command": "命令"} —— 经 Shizuku 执行 shell 命令（需 Shizuku 已授权）
3. list_project_files —— 列出当前工程的文件清单（无参数）
4. list_diagnostics —— 当前编辑器的编译诊断（无参数）
5. get_project_tree —— 当前工程的目录树（无参数，忽略 build/隐藏目录）
""".trimIndent()
}
