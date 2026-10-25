package candy.ide.desktop.data

import candy.ide.desktop.Settings
import java.io.File

/**
 * 工程仓库（桌面版）：与 Android 端 ProjectStore 同构——类型标记 candy.project、
 * 最近打开标记 .candy_open、主页按最近活跃倒序，工程类型四档判定逻辑一致。
 * 工程根目录 = Settings.workspaceRoot（默认 %APPDATA%\CandyIDE\projects）
 */
object ProjectStore {
    const val TYPE_JS = "js"          // JS 平台工程：编译 → JS → Node 执行
    const val TYPE_SCRIPT = "script"  // 脚本工程：解释器直接执行
    const val TYPE_JSAPK = "jsapk"    // JS-apk 工程（安卓侧打包链路；桌面端按 JS 工程对待）
    const val TYPE_MD = "md"          // md 文档工程

    private const val META = "candy.project"
    private const val OPEN_MARKER = ".candy_open"

    fun projectsRoot(): File = File(Settings.workspaceRoot).apply { if (!exists()) mkdirs() }

    /** 列出全部工程（= 目录）：按上次打开时间倒序（没打开过的退到目录修改时间） */
    fun listProjects(): List<File> =
        projectsRoot().listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.sortedByDescending { if (lastOpened(it) > 0) lastOpened(it) else it.lastModified() } ?: emptyList()

    /** 打开工程时调用：记下时间，主页排序用它 */
    fun touchOpened(dir: File) {
        runCatching { File(dir, OPEN_MARKER).writeText(System.currentTimeMillis().toString()) }
    }

    private fun lastOpened(dir: File): Long =
        runCatching { File(dir, OPEN_MARKER).takeIf { it.isFile }?.readText()?.trim()?.toLong() }.getOrNull() ?: 0L

    /** 最近活跃时间（上次打开标记 > 最近改动 > 0）：主页「上次编辑于 xx 前」用它 */
    fun lastEdited(dir: File): Long = maxOf(lastOpened(dir), dir.lastModified())

    /** 写工程类型标记（保留已有的 remote 行） */
    fun writeType(dir: File, type: String) {
        runCatching { File(dir, META).writeText("type=" + type + remoteMetaLine(dir)) }
    }

    /** 读工程的远程仓库地址（无则空串） */
    fun remoteOf(dir: File?): String {
        if (dir == null) return ""
        val meta = File(dir, META)
        if (!meta.isFile) return ""
        val raw = runCatching { meta.readText() }.getOrNull() ?: ""
        return raw.substringAfter("remote=", "").trim()
    }

    private fun remoteMetaLine(dir: File): String {
        val r = remoteOf(dir)
        return if (r.isBlank()) "" else "\nremote=" + r
    }

    /** 写远程仓库地址（保留已有类型行） */
    fun setRemote(dir: File, url: String) {
        runCatching { File(dir, META).writeText("type=" + typeOf(dir) + if (url.isBlank()) "" else "\nremote=" + url) }
    }

    /** 读工程类型：有标记读标记（含插件注册的类型），否则问插件认领，再按内容推断，兜底 JS 平台 */
    fun typeOf(dir: File): String {
        val meta = File(dir, META)
        if (meta.isFile) {
            val raw = runCatching { meta.readText() }.getOrNull() ?: ""
            val t = raw.substringAfter("type=", "").substringBefore("\n").trim()
            if (t == TYPE_JS || t == TYPE_SCRIPT || t == TYPE_MD) return t
            // 插件注册的工程类型（如 android-project 的 jsapk）
            if (candy.ide.desktop.plugin.PluginRegistry.projectType(t) != null) return t
        }
        // 无类型标记：先按注册顺序问插件认领（与安卓端 ProjectStore 同构）
        candy.ide.desktop.plugin.PluginRegistry.projectTypes().firstOrNull {
            runCatching { it.claimsProject(dir) }.getOrDefault(false)
        }?.let { return it.typeId }
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        val hasSubl = files.any { it.name.endsWith(".subl") }
        if (hasSubl) {
            val androidish = files.any { f ->
                f.name.endsWith(".subl") && runCatching { f.readText() }.getOrNull().orEmpty()
                    .let { it.contains("@composable") || it.contains("@android") }
            }
            return if (androidish) TYPE_JSAPK else TYPE_JS
        }
        val hasMd = files.any { it.name.endsWith(".md") }
        return if (hasMd) TYPE_MD else TYPE_SCRIPT
    }

    /** 合法名称才创建；写类型标记 + 模板入口文件 */
    fun createProject(name: String, type: String): File? {
        if (!isValidName(name)) return null
        val dir = File(projectsRoot(), name.trim())
        if (dir.exists()) return null
        dir.mkdirs()
        writeType(dir, type)
        // 插件注册的工程类型（如 jsapk）由插件提供样板；插件未装载时仍落内置模板兜底
        val pluginType = candy.ide.desktop.plugin.PluginRegistry.projectType(type)
        if (pluginType != null) {
            runCatching { pluginType.scaffold(dir, name.trim()) }.onFailure {
                File(dir, "main.subl").writeText(MAIN_SUBL)
            }
        } else when (type) {
            TYPE_JS, TYPE_JSAPK -> File(dir, "main.subl").writeText(MAIN_SUBL)
            TYPE_MD -> File(dir, "README.md").writeText(MD_TEMPLATE)
            else -> File(dir, "hello.subs").writeText(HELLO_SUBS)
        }
        return dir
    }

    /** 删除工程（仅允许删工程根下的直接子目录） */
    fun deleteProject(dir: File): Boolean {
        if (dir.parentFile?.absolutePath != projectsRoot().absolutePath) return false
        return dir.deleteRecursively()
    }

    fun rename(dir: File, newName: String): Boolean {
        if (!isValidName(newName)) return false
        val target = File(dir.parentFile, newName.trim())
        if (target.exists()) return false
        return dir.renameTo(target)
    }

    private fun isValidName(name: String): Boolean =
        name.isNotBlank() && !name.any { it in "\\/:*?\"<>|" }

    // ── 模板 ──

    internal const val HELLO_SUBS = """// Sublimation 脚本：顶层语句直接执行（无需 main），listOf/print/toStr 等为脚本模式内建
print("Hello, Sublimation!")
var xs = listOf(1, 2, 3, 4, 5)
print("翻倍=" + toStr(xs.map { x => x * 2 }))
print("总和=" + toStr(xs.fold(0) { acc, x => acc + x }))
"""

    internal const val MAIN_SUBL = """// Sublimation 工程：fun main 为入口（目录模式自动挂 stdlib）
@impure
fun main() {
    unchecked print("Hello, Sublimation!")
    var n: Nat = 1 + 2 * 3
    unchecked print("1 + 2 * 3 = " + toStr(n))
}
"""

    private const val MD_TEMPLATE = """# 工程文档

在这里写下你的文档。工程内所有 .md 文件都可以在文档页查看。
"""
}

/** 工程类型显示名：插件注册的优先（如 jsapk -> 安卓工程），兜底内置四类（与安卓端 projectTypeLabel 同构） */
fun projectTypeLabel(dir: java.io.File): String {
    val t = ProjectStore.typeOf(dir)
    return candy.ide.desktop.plugin.PluginRegistry.projectType(t)?.displayName() ?: when (t) {
        ProjectStore.TYPE_JS -> "JS工程"
        ProjectStore.TYPE_SCRIPT -> "脚本工程"
        ProjectStore.TYPE_JSAPK -> "安卓工程"
        else -> "MD工程"
    }
}
