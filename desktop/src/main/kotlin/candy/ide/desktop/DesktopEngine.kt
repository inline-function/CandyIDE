package candy.ide.desktop

import candy.ide.data.DiagItem
import candy.ide.desktop.plugin.PluginRegistry
import sugared.functor.checker.DiagBag
import sugared.functor.checker.MultiModule
import sugared.functor.interpreter.Interpreter
import sugared.functor.lsp.CompletionItem
import sugared.functor.lsp.Diagnostic
import sugared.functor.lsp.DiagnosticSeverity
import sugared.functor.lsp.LspAnalysis
import sugared.functor.lsp.LspExtension
import sugared.functor.lsp.Position
import java.io.File
import java.nio.file.Files

private val ENGINE_MARKERS = listOf("frontend", "compiler", "interpreter", "core")

/**
 * 引擎门面（桌面版）：与 Android 端 Engine 同构，但零 Android 依赖。
 * - 实时分析/补全：language-service 的 LspAnalysis（进程内直连，loadPrelude 挂 core）
 * - 工程编译：MultiModule（工程目录 = 根模块，自动挂 core，与 CLI compile <目录> 同构）
 * - 直连执行：Interpreter（runScript / runDir），print 重定向到输出面板
 */
object DesktopEngine {

    /** LSP 访问互斥锁：多窗口并发分析/补全时序列化，避免内部状态竞争 */
    private val lspLock = Any()

    /** sublimation 仓库根：设置覆盖 > 自动探测 */
    val sublimationRoot: File? by lazy {
        val override = Settings.sublimationRoot.trim()
        if (override.isNotEmpty() && File(override).isDirectory &&
            ENGINE_MARKERS.all { File(override, it).exists() }) File(override)
        else AutoDetect.sublimationRoot
    }

    val coreDir: File get() = File(sublimationRoot ?: File("."), "core")

    val nodeCommand: String? by lazy {
        val override = Settings.nodePath.trim()
        when {
            override.isNotEmpty() && File(override).exists() -> override
            else -> {
                var found: String? = null
                runCatching {
                    val p = ProcessBuilder("node", "-v").redirectErrorStream(true).start()
                    if (p.waitFor() == 0 && p.inputStream.bufferedReader().readText().startsWith("v")) found = "node"
                }
                found
            }
        }
    }

    /** 单例 LSP 分析器（prelude 挂 core 标准库符号；惰性初始化时一并装载插件注册的语言服务扩展） */
    @Volatile
    private var lspInstance: LspAnalysis? = null
    val lsp: LspAnalysis
        get() = lspInstance ?: synchronized(lspLock) {
            lspInstance ?: LspAnalysis().also { a ->
                runCatching { a.loadPrelude(coreDir) }
                // 插件的语言服务扩展（android-stdlib：@android 内建 + 处理器脚本）一并注册；
                // 各扩展的 isEnabled() 每次查询实时生效（t14 扩展点契约）
                PluginRegistry.lspExtensions().forEach { ext -> runCatching { a.registerExtension(ext) } }
            }.also { lspInstance = it }
        }

    /** 插件装载完成后补注册扩展：lsp 已初始化时注册新增扩展；未初始化时惰性初始化自取注册表 */
    fun syncLspExtensions() = synchronized(lspLock) {
        val a = lspInstance ?: return
        PluginRegistry.lspExtensions().forEach { ext -> runCatching { a.registerExtension(ext) } }
    }

    /** 插件注册语言服务扩展时直达已创建的 LspAnalysis（注册晚于首轮分析时补位；t50 对齐） */
    internal fun registerLiveLspExtension(extension: LspExtension) = synchronized(lspLock) {
        runCatching { lspInstance?.registerExtension(extension) }
    }

    /** 编译指定文件所属工程为 JS（插件经 PluginHost.compileToJs 调用；失败抛异常） */
    fun compileToJs(script: File, onCompiled: ((Long) -> Unit)? = null): String {
        val project = script.parentFile?.takeIf { it.isDirectory } ?: script
        val t0 = System.currentTimeMillis()
        val (bag, js) = compile(project)
        onCompiled?.invoke(System.currentTimeMillis() - t0)
        if (js == null) throw RuntimeException(bag.report())
        return js
    }

    fun uriOf(f: File): String = "file:///" + f.absolutePath.replace('\\', '/').removeSuffix("/")

    // ───────── 实时诊断 / 补全 ─────────

    fun analyze(uri: String, source: String): List<DiagItem> {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.diagnosticsFor(uri).map { it.toDiagItem() }
            }.getOrElse { emptyList() }
        }
    }

    fun completion(uri: String, line: Int, char: Int): List<CompletionItem> {
        return synchronized(lspLock) {
            runCatching { lsp.completionFor(uri, Position(line, char)).items }.getOrElse { emptyList() }
        }
    }

    /** 快速修复（灯泡）：诊断驱动的 codeAction，对齐安卓端 quickFixes */
    fun codeActions(uri: String, source: String, line: Int, char: Int): List<sugared.functor.lsp.CodeAction> {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                val range = if (line < 0) null else sugared.functor.lsp.Range(
                    sugared.functor.lsp.Position(line, char),
                    sugared.functor.lsp.Position(line, char),
                )
                lsp.codeActionsFor(uri, range)
            }.getOrElse { emptyList() }
        }
    }

    /** 转到定义（LSP definitionFor） */
    fun definition(uri: String, source: String, line: Int, char: Int): sugared.functor.lsp.Location? {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.definitionFor(uri, sugared.functor.lsp.Position(line, char))
            }.getOrNull()
        }
    }

    /** 重命名符号（LSP renameFor）：返回受影响文件 → 编辑列表 */
    fun rename(uri: String, source: String, line: Int, char: Int, newName: String): Map<String, List<sugared.functor.lsp.TextEdit>>? {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.renameFor(uri, sugared.functor.lsp.Position(line, char), newName)
            }.getOrNull()
        }
    }

    /** 格式化文档（LSP formatDocument） */
    fun format(uri: String, source: String): List<sugared.functor.lsp.TextEdit>? {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.formatDocument(uri)
            }.getOrNull()
        }
    }

    /** 查找引用（LSP referencesFor）：本文件内的全部出现位置 */
    fun references(uri: String, source: String, line: Int, char: Int): List<sugared.functor.lsp.Location>? {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.referencesFor(uri, sugared.functor.lsp.Position(line, char))
            }.getOrNull()
        }
    }

    /** 悬停文档（LSP hoverFor）：符号文档 / 关键字文档 / 内建函数文档 */
    fun hover(uri: String, source: String, line: Int, char: Int): sugared.functor.lsp.Hover? {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.hoverFor(uri, sugared.functor.lsp.Position(line, char))
            }.getOrNull()
        }
    }

    /** @composable 视图树（LspAnalysis.composableTreeJson，布局预览用） */
    fun composableTree(uri: String, source: String, line: Int, char: Int): String? {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.composableTreeJson(uri, sugared.functor.lsp.Position(line, char))
            }.getOrNull()
        }
    }

    /** 签名提示（LSP signatureHelpFor）：实参区里显示被调函数签名 */
    fun signatureHelp(uri: String, source: String, line: Int, char: Int): sugared.functor.lsp.SignatureHelp? {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.signatureHelpFor(uri, sugared.functor.lsp.Position(line, char))
            }.getOrNull()
        }
    }

    /** 函数签名明细（collectSymbols）：指名调用用 */
    fun symbolDetail(uri: String, source: String, word: String): String? {
        return synchronized(lspLock) {
            runCatching {
                lsp.updateDocument(uri, source)
                lsp.collectSymbols(uri).firstOrNull { it.name == word && it.variety == "fun" }?.detail
            }.getOrNull()
        }
    }

    // ───────── 编译 / 执行 ─────────

    /** 组装临时编译根：core 标准库 + 工程源码（保持相对路径）；与 Android Engine.prepareCompileRoot 同构 */
    private fun prepareCompileRoot(project: File): File {
        val root = Files.createTempDirectory("candy-compile-").toFile()
        val coreSrc = coreDir
        if (coreSrc.isDirectory) coreSrc.copyRecursively(File(root, "core"), overwrite = true)
        for ((f, rel) in Workspace.walkSources(project)) {
            val target = File(root, rel)
            target.parentFile?.mkdirs()
            f.copyTo(target, overwrite = true)
        }
        return root
    }

    /** 编译工程：返回诊断袋 + 生成的 JS（编译失败时为 null） */
    fun compile(project: File): Pair<DiagBag, String?> {
        val root = prepareCompileRoot(project)
        val mm = MultiModule(root)
        val bag = mm.checkAll()
        return if (bag.hasError) bag to null else bag to mm.generateJs()
    }

    /** 直连解释器执行（.subs 脚本 / .subl 工程目录），print 输出重定向 */
    fun runDirect(target: File, onLine: (String) -> Unit, onDone: (Throwable?) -> Unit) {
        Thread({
            try {
                val interp = Interpreter(coreDir)
                interp.register("print") { args ->
                    onLine(args.joinToString(" ") { it?.toString() ?: "null" }); null
                }
                when {
                    target.isDirectory -> {
                        val subs = interp.subsIn(target)
                        if (subs.isNotEmpty()) subs.forEach { interp.runScript(it) } else interp.runDir(target)
                    }
                    target.name.endsWith(".subs") -> interp.runScript(target)
                    else -> interp.runDir(target.parentFile ?: target)
                }
                interp.inferNotes.forEach { n -> onLine(noteLine(n.code, n.message)) }
                onDone(null)
            } catch (t: Throwable) { onDone(t) }
        }, "candy-engine").apply { isDaemon = true }.start()
    }

    /** 编译产物交给 Node.js 执行（Android 端用 WebView，桌面端用真 Node：require('fs')/Buffer 原生可用） */
    fun runJs(js: String, project: File, onLine: (String) -> Unit, onDone: (Throwable?) -> Unit) {
        val cmd = nodeCommand
        if (cmd == null) { onDone(RuntimeException("未找到 Node.js：请在设置里指定 node 路径，或改用解释器模式")); return }
        Thread({
            var proc: Process? = null
            try {
                val tmp = Files.createTempFile("candy-run-", ".js").toFile()
                tmp.writeText(js)
                proc = ProcessBuilder(cmd, tmp.absolutePath)
                    .directory(project).redirectErrorStream(true).start()
                proc.inputStream.bufferedReader().useLines { it.forEach { line -> onLine(line) } }
                val code = proc.waitFor()
                if (code != 0) onLine("[进程退出码 $code]")
                onDone(null)
            } catch (t: Throwable) { onDone(t) } finally { proc?.destroy() }
        }, "candy-node").apply { isDaemon = true }.start()
    }

    private fun Diagnostic.toDiagItem(): DiagItem = DiagItem(
        severity = when (severity) {
            DiagnosticSeverity.Error -> 0; DiagnosticSeverity.Warning -> 1
            DiagnosticSeverity.Information -> 2; DiagnosticSeverity.Hint -> 3; null -> 0
        },
        line = range.start.line, char = range.start.character,
        endLine = range.end.line, endChar = range.end.character,
        message = (if (code != null) "[$code] " else "") + message,
    )

}

private fun noteLine(code: String, message: String) = "[提示] [$code] $message"

object AutoDetect {
    val sublimationRoot: File? = detect()

    private fun detect(): File? {
        val markers = listOf("frontend", "compiler", "interpreter", "core")
        var cur = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (markers.all { m -> File(cur, m).exists() }) return cur
            for (c in listOf(File(cur, "../sublimation"), File(cur, "sublimation"), File(cur, "../KotlinProject/sublimation"))) {
                val a = File(c.path.replace('\\', '/')).absoluteFile.normalize()
                if (a.isDirectory && markers.all { m -> File(a, m).exists() }) return a
            }
            cur = cur.parentFile ?: return null
        }
        return null
    }
}
