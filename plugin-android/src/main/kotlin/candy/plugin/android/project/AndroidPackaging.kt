package candy.plugin.android.project

import candy.ide.fs.CandyPath
import candy.ide.plugin.PluginHost
import candy.plugin.android.AndroidAssets
import candy.plugin.android.jsapk.AndroidApkBuilder
import candy.plugin.android.jsapk.ApkSigner
import candy.plugin.android.jsapk.JsApkBuilder
import candy.plugin.android.jsapk.JsApkConfig
import candy.plugin.android.jsapk.MdApkBuilder
import candy.plugin.android.litea.LiteASdk
import sugared.functor.annotationproc.ProcessorPipeline
import sugared.functor.annotationproc.ScriptProcessor
import sugared.functor.checker.MultiModule
import java.io.File

/**
 * 安卓工程打包链路（自 CandyViewModel 迁入，改为经 PluginHost 使用宿主能力）。
 * host.setBusy/setRunFailed/log/requestInstall 与旧 VM 的 running/runFailed/mdLog/installPkg 一一对应。
 */
object AndroidPackaging {

    // 宿主工程类型常量（candy.project 的 type 值；宿主 ProjectStore 侧同值）
    const val TYPE_JS = "js"
    const val TYPE_SCRIPT = "script"
    const val TYPE_JSAPK = "jsapk"
    const val TYPE_MD = "md"

    /** 打包调度（同旧 VM.buildApkProject 的路由） */
    fun packageProject(host: PluginHost, dir: CandyPath) {
        when {
            host.projectTypeOf(dir) == TYPE_MD -> buildMdApkProject(host, dir)
            host.projectTypeOf(dir) == TYPE_SCRIPT -> buildScriptApkProject(host, dir)
            dir.find("jsapk.json")?.isFile() == true -> buildApkIn(host, dir)
            host.projectTypeOf(dir) == TYPE_JSAPK -> nativePackageApk(host, dir)
            else -> buildApkIn(host, dir, JsApkConfig(
                pkg = JsApkConfig.DEFAULT_PACKAGE + "." + asciiPackageSeed(dir.name),
                label = dir.name,
                entry = dir.walk().firstOrNull { it.isFile() && it.name.endsWith(".subl") }?.name ?: "main.subl",
            ))
        }
    }

    /** md 文档工程打包：收集文档 → 注入阅读器 → 签名 */
    fun buildMdApkProject(host: PluginHost, dir: CandyPath) {
        host.setBusy(true)
        host.openBuildLog()
        host.log("**[打包]** md 文档工程 收集文档 → 注入阅读器 → 签名")
        host.runOnIo {
            try {
                val cfg = JsApkConfig(
                    pkg = JsApkConfig.DEFAULT_PACKAGE + "." + asciiPackageSeed(dir.name),
                    label = dir.name,
                )
                val apk = MdApkBuilder.build(host.context, dir, cfg) { n ->
                    host.log("**[打包]** 收集到 " + n + " 篇文档")
                }
                host.log("**[打包完成]** " + apk.name + " " + apk.length() / 1024 + " KB · 路径 " + apk.displayPath)
                host.log("**[打包]** 包名 " + cfg.pkg + " · 应用名 " + cfg.label)
                host.postMain { host.setRunFailed(false) }
                host.requestInstall(apk, cfg.pkg)
            } catch (t: Throwable) {
                host.log("**[打包失败]** " + (t.message ?: t.javaClass.simpleName).substringBefore("\n").take(400))
                host.log("**[提示]** 完整诊断请看「诊断」页签")
                host.log("**[堆栈]** " + t.stackTraceToString().take(1500).replace("\n", " | "))
                host.postMain { host.setRunFailed(true) }
            } finally {
                host.postMain { host.setBusy(false) }
            }
        }
    }

    /** 脚本工程打包：顶层语句包一层 @impure fun main，落进 __entry__.subl 再走标准链路 */
    fun buildScriptApkProject(host: PluginHost, dir: CandyPath) {
        host.setBusy(true)
        host.openBuildLog()
        host.log("**[打包]** 脚本工程 收集源码 → 包一层 main → 编译注入")
        host.runOnIo {
            try {
                val src = dir.walk().firstOrNull { it.isFile() && it.name.endsWith(".subs") }
                    ?: dir.walk().firstOrNull { it.isFile() && it.name.endsWith(".subl") }
                if (src == null) {
                    host.log("**[打包失败]** 脚本工程里没有 .subs 源文件")
                    host.postMain { host.setBusy(false) }
                    return@runOnIo
                }
                val body = src.readText().lines()
                    .map { if (it.isBlank()) "" else "  " + it }
                    .joinToString("\n")
                val wrapperText = "@impure\nfun main() {\n" + body + "\n}\n"
                // 临时入口放工程根（JsApkBuilder 以入口所在目录为工程根，build/ 由它自建；
                // 放 build/ 下会导致 build/build/ 嵌套，SAF 抛错）
                val wrapper = dir.child("__entry__.subl")
                wrapper.writeText(wrapperText)
                val cfg = JsApkConfig(
                    pkg = JsApkConfig.DEFAULT_PACKAGE + "." + asciiPackageSeed(dir.name),
                    label = dir.name,
                )
                val apk = JsApkBuilder.build(host, wrapper, cfg, onCompiled = { ms ->
                    host.log("**[编译完成]** 用时 " + fmtElapsed(ms))
                })
                runCatching { wrapper.delete() }
                host.log("**[打包完成]** " + apk.name + " " + apk.length() / 1024 + " KB · 路径 " + apk.displayPath)
                host.log("**[打包]** 包名 " + cfg.pkg + " · 应用名 " + cfg.label)
                host.postMain { host.setRunFailed(false) }
                host.requestInstall(apk, cfg.pkg)
            } catch (t: Throwable) {
                runCatching { dir.find("__entry__.subl")?.delete() }
                host.log("**[打包失败]** " + (t.message ?: t.javaClass.simpleName).substringBefore("\n").take(400))
                host.log("**[提示]** 完整诊断请看「诊断」页签")
                host.log("**[堆栈]** " + t.stackTraceToString().take(1500).replace("\n", " | "))
            } finally {
                host.postMain { host.setBusy(false) }
            }
        }
    }

    /** 有 jsapk.json 的工程打包（工程设置页也走这里）：入口/包名走配置 */
    fun buildApkIn(host: PluginHost, project: CandyPath, forced: JsApkConfig? = null) {
        host.setBusy(true)
        host.openBuildLog()
        val entry = jsapkEntry(project)
        if (entry == null) {
            host.log("**[打包失败]** 工程里没有 .subl 源文件")
            host.setBusy(false)
            return
        }
        host.log("**[打包]** " + entry.name + " 编译 → 注入模板 → 签名")
        host.runOnIo {
            try {
                val cfg = forced ?: JsApkConfig.load(project)
                // 自定义证书（工程 jsapk.json 指定的 .jks）
                val certErr = if (cfg.certPath != null && cfg.certPass != null) {
                    val jks = project.find(cfg.certPath!!) ?: project.walk().firstOrNull { it.name == cfg.certPath?.substringAfterLast('/') }
                    if (jks == null || !jks.isFile()) "[打包] 证书文件不存在：" + cfg.certPath
                    else {
                        val err = ApkSigner.loadJks(jks, cfg.certPass!!.toCharArray())
                        if (err != null) "[打包] " + err else null
                    }
                } else {
                    ApkSigner.useCustomKey(null, null)
                    null
                }
                if (certErr != null) {
                    host.logUser("**" + certErr + "，改用内置证书**")
                    ApkSigner.useCustomKey(null, null)
                }
                val apk = JsApkBuilder.build(host, entry, cfg, onCompiled = { ms ->
                    host.log("**[编译完成]** 用时 " + fmtElapsed(ms))
                })
                host.log("**[打包完成]** " + apk.name + " " + apk.length() / 1024 + " KB · 路径 " + apk.displayPath)
                host.postMain { host.setRunFailed(false) }
                if (cfg.pkg != JsApkConfig.DEFAULT_PACKAGE) host.log("**[打包]** 包名 " + cfg.pkg + " · 应用名 " + cfg.label)
                host.requestInstall(apk, cfg.pkg)
            } catch (t: Throwable) {
                host.log("**[打包失败]** " + (t.message?.replace("`", "'") ?: t.javaClass.simpleName).substringBefore("\n").take(400))
                host.log("**[提示]** 完整诊断请看「诊断」页签")
                host.postMain { host.setRunFailed(true) }
            } finally {
                host.postMain { host.setBusy(false) }
            }
        }
    }

    /** 原生安卓工程（SfA 链）：处理器/编译 → 壳模板注 JS → v1 签名 → 安装 */
    private fun nativePackageApk(host: PluginHost, dir: CandyPath) {
        host.setBusy(true)
        host.openBuildLog()
        try {
            val entry = dir.walk().firstOrNull { it.isFile() && it.name.endsWith(".subl") }
                ?: throw RuntimeException("工程里没有 .subl 入口")
            host.log("**[打包]** " + entry.name + " 注解处理器 → 编译 → 注进壳模板 → 签名")
            val js = compileAndroidJs(host, entry).toByteArray(Charsets.UTF_8)
            val cfg = AndroidApkBuilder.loadConfig(dir)
            val seed = cfg.pkgSeed.ifBlank { asciiPackageSeed(dir.name) }
            val pkg = "functor.sublimation." + seed
            val apk = AndroidApkBuilder.build(host.context, js, dir, cfg)
            host.log("**[打包完成]** " + apk.name + " " + apk.length() / 1024 + " KB · 路径 " + apk.displayPath)
            host.log("**[打包]** 包名 " + pkg + (if (cfg.label.isNotBlank()) " · 应用名 " + cfg.label else "") + (if (cfg.icon.isNotBlank()) " · 自定义图标" else "") + (if (cfg.keystore.isNotBlank()) " · 自定义签名" else ""))
            host.postMain { host.setRunFailed(false) }
            host.requestInstall(apk, pkg)
        } catch (e: Throwable) {
            host.log("**[打包失败]** " + ((e as? RuntimeException)?.message ?: e.message ?: e.javaClass.simpleName).substringBefore("\n").take(400))
            host.log("**[堆栈]** " + e.stackTraceToString().take(1200).replace("\n", " | "))
            host.postMain { host.setRunFailed(true) }
        } finally {
            host.postMain { host.setBusy(false) }
        }
    }

    /** 当前工程的入口 .subl（有 jsapk.json 读 entry，没有就取第一个 .subl） */
    fun jsapkEntry(project: CandyPath): CandyPath? {
        // 走 JsApkConfig 的 org.json 解析：手工 substringAfter 截到行尾会把整段 JSON 粘进文件名
        val entry = runCatching { JsApkConfig.load(project).entry }.getOrNull()
        val candidate = entry?.let { project.find(it) }
        return when {
            candidate?.isFile() == true -> candidate
            else -> project.walk().firstOrNull { it.isFile() && it.name.endsWith(".subl") }
        }
    }

    /** 工程名 → Android 合法包名段：只留 ASCII 字母数字下划线，空串或数字开头补 app 前缀 */
    fun asciiPackageSeed(name: String): String {
        val clean = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        return if (clean.isEmpty() || clean.first().isDigit()) "app" + clean else clean
    }

    private fun fmtElapsed(ms: Long): String =
        if (ms < 1000) "${ms} ms" else "%.2f s".format(ms / 1000.0)

    /** 安卓工程编译：处理器变换 + 全量检查 → JS；失败抛带报告的异常（编译产物在 cacheDir） */
    fun compileAndroidJs(host: PluginHost, script: CandyPath): String {
        val ctx = host.context
        val project = script.parent ?: script
        val src = script.readText()
        // 编译根 = core + 安卓标准库（平铺根模块，与 SfA app/ 同构）+ 工程源
        val compileRoot = File(ctx.cacheDir, "candy/androidRoot")
        compileRoot.deleteRecursively(); compileRoot.mkdirs()
        val coreSrc = File(host.candyRoot(), "core")
        if (coreSrc.isDirectory) coreSrc.copyRecursively(File(compileRoot, "core"), overwrite = true)
        val aStd = AndroidAssets.stdlib()
        if (aStd.isDirectory) aStd.listFiles { f -> f.isFile && f.name.endsWith(".subl") }
            ?.sortedBy { it.name }?.forEach { it.copyTo(File(compileRoot, it.name), overwrite = true) }
        for ((f, rel) in project.walkRelative()) {
            if (rel.isBlank() || !f.isFile()) continue
            if (rel.startsWith("build/") || rel.startsWith(".")) continue
            if (!rel.endsWith(".subl") && !rel.endsWith(".subs") && !rel.endsWith(".settings")) continue
            val target = File(compileRoot, rel)
            target.parentFile?.mkdirs()
            f.inputStream().use { ins -> target.outputStream().use { out -> ins.copyTo(out) } }
        }
        // 处理器装载（文件名=处理器名=关注注解；@interested 声明跨注解关注）
        val procsDir = AndroidAssets.procs()
        val processors = procsDir.listFiles { f -> f.isFile && f.name.endsWith(".subs") }
            ?.sortedBy { it.name }?.map { f ->
                val name = f.name.removeSuffix(".subs")
                val txt = f.readText()
                val extra = Regex("@interested\\s+([A-Za-z0-9_, ]+)").find(txt)?.groupValues?.get(1)
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                ScriptProcessor(
                    name = name,
                    interestedAnnotations = setOf(name) + extra,
                    scriptSource = txt,
                    scriptFileName = f.path,
                    stdlibDir = coreSrc,
                )
            } ?: emptyList()
        val options = mapOf(
            "appDir" to "", "jsPath" to "", "outDir" to "", "sdkDir" to LiteASdk.dir(host).path,
        )
        val mm = MultiModule(compileRoot)
        val pbag = mm.processModules(ProcessorPipeline.moduleTransformer(processors, options))
        val bag = mm.checkAll()
        bag.absorb(pbag)
        if (bag.hasError) throw RuntimeException("安卓工程编译未通过，请看「诊断」页签\n" + bag.report(source = src))
        return mm.generateJs()
    }
}
