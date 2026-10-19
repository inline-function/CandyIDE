package candy.ide.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import sugared.functor.checker.DiagBag
import sugared.functor.checker.MultiModule
import sugared.functor.interpreter.Interpreter
import java.io.File

/**
 * 引擎门面：stdlib 引导 + 编译执行。
 *
 * 编译走多模块驱动：工程目录 = 根模块，自动挂 core 标准库（与 CLI 的 compile <目录> 同构），
 * 这样 Ok/Err/Some/None/List 等容器构造子全部可解析，生成的 JS 也内联 core，运行时无需再挂运行时库。
 */
object Engine {

    private const val TAG = "CandyEngine"
    /** stdlib 资产版本：与 sublimation 的 core 同步后递增，旧版本强制重抽 */
    private const val STDLIB_VERSION = 2

    @Volatile private var ready = false
    private val main = Handler(Looper.getMainLooper())

    /** 把 assets/stdlib（从 sublimation 的 core 同步而来）物化到私有目录。
     *  - files/candy/stdlib：供 LSP 的 loadPrelude / 脚本解释器用
     *  - files/candy/core：编译根临时复制的 core 副本源
     *  版本标记不匹配时强制重抽，避免旧 core 残留。 */
    fun bootstrap(ctx: Context) {
        if (ready) return
        val root = File(ctx.filesDir, "candy")
        val stdlib = File(root, "stdlib")
        val core = File(root, "core")
        val marker = File(root, "stdlib.version")
        val needRefresh = !marker.exists() || marker.readText().trim() != STDLIB_VERSION.toString()
        if (needRefresh) {
            stdlib.deleteRecursively()
            core.deleteRecursively()
            ctx.assets.list("stdlib")?.forEach { entry ->
                copyAsset(ctx, "stdlib/$entry", File(stdlib, entry))
                copyAsset(ctx, "stdlib/$entry", File(core, entry))
            }
            marker.writeText(STDLIB_VERSION.toString())
        }
        // 项目根目录
        val projects = File(root, "projects")
        if (!projects.exists()) projects.mkdirs()
        ready = true
    }

    private fun copyAsset(ctx: Context, assetPath: String, target: File) {
        val item = ctx.assets.list(assetPath)
        if (item == null || item.isEmpty()) {
            target.parentFile?.mkdirs()
            if (!target.exists()) {
                ctx.assets.open(assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } else {
            target.mkdirs()
            if (target.listFiles()?.isNotEmpty() == true) return
            for (child in item) copyAsset(ctx, "$assetPath/$child", File(target, child))
        }
    }

    fun root(ctx: Context): File = File(ctx.filesDir, "candy")

    /** 组装临时编译根：core 标准库（files/candy/core）+ 工程源文件（保持工程内相对路径） */
    private fun prepareCompileRoot(ctx: Context, project: File): File {
        val compileRoot = File(ctx.cacheDir, "candy/compileRoot")
        compileRoot.deleteRecursively()
        compileRoot.mkdirs()
        val coreSrc = File(root(ctx), "core")
        if (coreSrc.isDirectory) coreSrc.copyRecursively(File(compileRoot, "core"), overwrite = true)
        for (f in project.walkTopDown()) {
            if (!f.isFile) continue
            val rel = f.relativeTo(project).path.replace('\\', '/')
            if (rel.startsWith("build/") || rel.startsWith(".")) continue
            if (!f.name.endsWith(".subl") && !f.name.endsWith(".subs") && !f.name.endsWith(".settings")) continue
            val target = File(compileRoot, rel)
            target.parentFile?.mkdirs()
            f.copyTo(target, overwrite = true)
        }
        return compileRoot
    }

    /**
    * 编译并运行。
    * - script：要跑的源码文件（点击运行的当前文件，仅用于定位工程与报错行号）
    * - onLine：程序输出（console.log / JS 报错），回调均在主线程
    * - onDone：本次执行收尾（主线程）
    */
    fun run(
        ctx: Context, script: File,
        onLine: (String) -> Unit, onDone: () -> Unit,
        onInput: (() -> String?)? = null,
        onCompiled: ((Long) -> Unit)? = null,
    ) {
        val project = script.parentFile ?: script
        val src = script.readText()
        val compileRoot = prepareCompileRoot(ctx, project)
        val started = System.currentTimeMillis()
        val mm = MultiModule(compileRoot)
        val bag = mm.checkAll()
        if (bag.hasError) {
            throw RuntimeException("编译未通过，请看「诊断」页签\n" + bag.report(source = src))
        }
        val js = mm.generateJs()
        onCompiled?.invoke(System.currentTimeMillis() - started)
        main.post {
            JsRuntime.run(ctx, js, onLine = onLine, onDone = onDone, onInput = onInput)
        }
    }

    /**
    * 只编译不执行：JS-apk 打包用（与 run() 共用同一套多模块编译）。
    * 抛出即视为编译失败，message 已带报告。
    */
    fun compileToJs(ctx: Context, script: File, onCompiled: ((Long) -> Unit)? = null): String {
        val project = script.parentFile ?: script
        val src = script.readText()
        val compileRoot = prepareCompileRoot(ctx, project)
        val started = System.currentTimeMillis()
        val mm = MultiModule(compileRoot)
        val bag = mm.checkAll()
        if (bag.hasError) {
            throw RuntimeException("编译未通过，请看「诊断」页签\n" + bag.report(source = src))
        }
        val js = mm.generateJs()
        onCompiled?.invoke(System.currentTimeMillis() - started)
        return js
    }

    /**
    * 脚本工程直连解释器（.subs 顶层语句直接执行，无 main）。
    */
    fun runScript(ctx: Context, script: File, onLine: (String) -> Unit, onDone: () -> Unit) {
        val stdlibDir = File(root(ctx), "core")
        val interp = if (stdlibDir.isDirectory) Interpreter(stdlibDir) else Interpreter()
        interp.register("print") { args ->
            val line = args.joinToString(" ") { it?.toString() ?: "null" }
            Log.i(TAG, line)
            onLine(line)
            null
        }
        try {
            if (script.name.endsWith(".subl")) {
                interp.runDir(script.parentFile ?: script)
            } else {
                interp.runScript(script)
            }
        } catch (re: RuntimeException) {
            throw RuntimeException("脚本执行出错：\n" + (re.message ?: re.javaClass.simpleName))
        }
        onDone()
    }
}
