package candy.ide.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import candy.ide.data.DiagItem
import candy.ide.desktop.data.ProjectStore
import java.io.File

enum class DesktopScreen { Projects, Settings, Docs, Scheme, ProjectSettings, About, Stdlib, IdeLog, Agent }

/** 输出一行：终端式输出面板（stdout/stderr/信息/诊断四类着色） */
data class OutputLine(val text: String, val kind: Kind = Kind.Out) {
    enum class Kind { Out, Error, Info, Success }
}

/** 全局 IDE 状态（纯 Compose 快照状态，无 Android ViewModel 依赖） */
class IdeState {
    var screen by mutableStateOf(DesktopScreen.Projects)
    /** 工程设置页的対象工程 */
    var projectSettingsTarget by mutableStateOf<File?>(null)

    // ── 工程区 ──
    val projects: SnapshotStateList<File> = mutableStateListOf()
    var currentProject by mutableStateOf<File?>(null)
    /** 已在独立窗口打开的工程（主页不关闭，每工程一个编辑器窗口） */
    val openProjectWindows: SnapshotStateList<File> = mutableStateListOf()

    // ── 编辑器 ──
    val openFiles: SnapshotStateList<File> = mutableStateListOf()
    var activeFile by mutableStateOf<File?>(null)
    /** 文件 → 正文缓冲（键 = 文件绝对路径，避免文件被移动/改名后错位） */
    val fileTexts: SnapshotStateMap<String, String> = mutableStateMapOf()
    val dirty: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    /** 文件 → 实时诊断（LSP 后台分析） */
    val diagnostics: SnapshotStateMap<String, List<DiagItem>> = mutableStateMapOf()
    /** 强制编辑器丢弃缓冲重读磁盘的自增版本（外部修改同步用） */
    val reloadVersion: SnapshotStateMap<String, Int> = mutableStateMapOf()
    /** 打开树目录 */
    val expandedDirs: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    // ── 输出 ──
    val outputLines: SnapshotStateList<OutputLine> = mutableStateListOf()
    var running by mutableStateOf(false)
    var runInfo by mutableStateOf("")

    // ── 提示 ──
    var toast by mutableStateOf<String?>(null)

    // ── 日志（输出页签放程序 stdout，日志页签放引擎/编译事件） ──
    val logLines: SnapshotStateList<OutputLine> = mutableStateListOf()
    /** md 文件：true = 纯文本编辑，null/false = 渲染预览 */
    val mdTextMode: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    /** 上次落盘/载入的正文（脏标记比对用，避免每次按键读磁盘） */
    private val savedTexts = HashMap<String, String>()

    fun activeText(): String? = activeFile?.let { fileTexts[it.absolutePath] }
    fun isOpen(f: File): Boolean = openFiles.any { it.absolutePath == f.absolutePath }

    fun open(f: File) {
        if (!isOpen(f)) openFiles.add(f)
        val key = f.absolutePath
        if (!fileTexts.containsKey(key)) {
            val t = runCatching { f.readText() }.getOrElse { "" }
            fileTexts[key] = t; savedTexts[key] = t
        }
        activeFile = f
    }

    fun close(f: File) {
        openFiles.removeAll { it.absolutePath == f.absolutePath }
        val key = f.absolutePath
        if (openFiles.none { it.absolutePath == key }) {
            fileTexts.remove(key); dirty.remove(key); diagnostics.remove(key)
        }
        activeFile = if (activeFile?.absolutePath == key) openFiles.lastOrNull() else activeFile
    }

    fun editText(f: File, text: String) {
        val key = f.absolutePath
        fileTexts[key] = text
        dirty[key] = text != savedTexts[key]
    }

    fun save(f: File): Boolean {
        val text = fileTexts[f.absolutePath] ?: return false
        return runCatching {
            f.parentFile?.mkdirs(); f.writeText(text)
            savedTexts[f.absolutePath] = text; dirty[f.absolutePath] = false
            gitAutoCommitIfEnabled("修改 " + f.name + " 文件")
            true
        }.getOrElse { false }
    }

    /** 保存全部脏文件，返回保存数量（运行/编译/关窗口前调用，避免运行旧磁盘内容） */
    fun saveAllDirty(): Int {
        var n = 0
        openFiles.forEach { f -> if (dirty[f.absolutePath] == true && save(f)) n++ }
        return n
    }

    /** git 自动提交：设置页开启后，保存/新建/删除文件时以「修改 xx 文件」名义提交；
     *  开自动同步时一并推送（对齐安卓端 gitAutoCommit/gitAutoSync；后台线程，失败静默） */
    fun gitAutoCommitIfEnabled(subject: String) {
        if (!Settings.gitAutoCommit) return
        val project = currentProject ?: return
        kotlin.concurrent.thread(name = "candy-git-auto", isDaemon = true) {
            runCatching {
                if (!candy.ide.desktop.git.DesktopGit.hasRepo(project)) return@runCatching
                candy.ide.desktop.git.DesktopGit.configIdentity(project, Settings.gitUserName, Settings.gitUserEmail)
                val r = candy.ide.desktop.git.DesktopGit.commitAll(project, subject)
                if (r.ok && Settings.gitAutoSync) candy.ide.desktop.git.DesktopGit.push(project)
            }
        }
    }

    /** 自动同步：进入工程时若云端领先则拉取，并把未修改的打开缓冲从磁盘重载 */
    fun pullIfBehind(project: File) {
        runCatching {
            val info = candy.ide.desktop.git.DesktopGit.fetchAndCheckAhead(project)
            if (info.error == null && info.behind > 0) {
                candy.ide.desktop.git.DesktopGit.pull(project)
                reloadUnchangedFromDisk()
                log("**[自动同步]** 拉取云端更新：" + info.behind + " 个提交", OutputLine.Kind.Info)
            }
        }
    }

    /** 未修改（无脏缓冲）的打开文件按磁盘内容重载（自动同步后） */
    fun reloadUnchangedFromDisk() {
        openFiles.forEach { f ->
            val key = f.absolutePath
            if (dirty[key] != true) {
                val t = runCatching { f.readText() }.getOrNull() ?: return@forEach
                fileTexts[key] = t; savedTexts[key] = t
            }
        }
    }


    /** 刷新工程列表（按最近活跃倒序）；内容/顺序没变就不动，避免打断列表滚动 */
    fun refreshProjects() {
        val fresh = ProjectStore.listProjects()
        val cur = projects.toList()
        val same = fresh.size == cur.size &&
            fresh.zip(cur).all { (a, b) -> a.absolutePath == b.absolutePath }
        if (!same) {
            projects.clear(); projects.addAll(fresh)
        }
    }

    fun createProject(name: String, type: String): File? {
        val dir = candy.ide.desktop.data.ProjectStore.createProject(name, type) ?: return null
        refreshProjects(); openProject(dir); return dir
    }

    fun deleteProject(dir: File): Boolean {
        val ok = candy.ide.desktop.data.ProjectStore.deleteProject(dir)
        if (ok) refreshProjects(); return ok
    }

    /** 打开工程：切到编辑器并打开入口文件 */
    fun openProject(dir: File) {
        currentProject = dir
        Settings.recentProject = dir.absolutePath
        ProjectStore.touchOpened(dir)
        // 工程类型变化通知插件（对齐安卓端 VM：android-stdlib 扩展按工程类型启停；
        // 桌面端多窗口共享单例 LSP 分析器，启停跟随最近打开的工程）
        val openType = candy.ide.desktop.data.ProjectStore.typeOf(dir)
        runCatching {
            candy.ide.desktop.plugin.PluginRegistry.plugins.forEach { it.instance?.onProjectTypeChanged(openType, dir) }
        }
        openFiles.clear(); fileTexts.clear(); dirty.clear(); diagnostics.clear()
        expandedDirs.clear()
        activeFile = null
        val entry = listOf(File(dir, "main.subl"), File(dir, "hello.subs"))
            .plus(dir.listFiles { f -> f.isFile && (f.name.endsWith(".subl") || f.name.endsWith(".subs")) }
                ?.sortedBy { it.name } ?: emptyList())
            .firstOrNull { it.isFile }
        if (entry != null) open(entry)
    }

    /** 主页点击工程：新开编辑器窗口（主页保持打开） */
    fun openProjectWindow(dir: File) {
        if (openProjectWindows.none { it.absolutePath == dir.absolutePath }) openProjectWindows.add(dir)
        ProjectStore.touchOpened(dir)
        refreshProjects()
    }

    fun closeProjectWindow(dir: File) {
        openProjectWindows.removeAll { it.absolutePath == dir.absolutePath }
    }

    fun log(text: String, kind: OutputLine.Kind = OutputLine.Kind.Info) {
        if (text.isEmpty()) return
        for (line in text.split("\n", "\r\n", "\r")) logLines.add(OutputLine(line, kind))
        if (logLines.size > 3000) repeat(logLines.size - 3000) { logLines.removeAt(0) }
    }

    fun clearLogs() = logLines.clear()

    fun output(text: String, kind: OutputLine.Kind = OutputLine.Kind.Out) {
        // 跨线程写快照状态：逐行入库，重组在主线程消费
        if (text.isEmpty()) return
        for (line in text.split("\n", "\r\n", "\r")) outputLines.add(OutputLine(line, kind))
        if (outputLines.size > 5000) repeat(outputLines.size - 5000) { outputLines.removeAt(0) }
    }

    fun clearOutput() = outputLines.clear()

    // ───────── 运行 / 编译（自编辑器侧迁入对象方法；对齐安卓端 VM 的运行路由） ─────────

    /** 短按「运行」：.subs 脚本或解释器模式走解释器；否则插件工程类型（jsapk）路由给插件 runShort；兜底编译 + Node */
    fun runTarget() {
        val project = currentProject ?: return
        val f = activeFile
        // 运行前自动保存脏文件（否则执行的是磁盘旧内容）
        if (f != null && dirty[f.absolutePath] == true && save(f)) {
            toast = "已保存 " + f.name
            log("自动保存 " + f.name, OutputLine.Kind.Success)
        }
        val script = f?.takeIf { it.name.endsWith(".subs") }
        if (script != null || Settings.runMode == RunMode.Interpreter) {
            val target: File = script ?: project
            running = true
            log("[解释器] 运行 " + target.name, OutputLine.Kind.Info)
            DesktopEngine.runDirect(target,
                onLine = { output(it) },
                onDone = { err ->
                    running = false
                    if (err != null) {
                        output("出错：" + (err.message ?: err.javaClass.simpleName), OutputLine.Kind.Error)
                        log("运行出错：" + (err.message ?: err.javaClass.simpleName), OutputLine.Kind.Error)
                    } else {
                        output("[完成]", OutputLine.Kind.Success)
                        log("运行完成", OutputLine.Kind.Success)
                    }
                },
            )
        } else {
            // 插件注册的工程类型（android-project 的 jsapk）：由插件决定如何运行
            val type = candy.ide.desktop.data.ProjectStore.typeOf(project)
            val contrib = candy.ide.desktop.plugin.PluginRegistry.projectType(type)
            if (contrib != null) contrib.runShort(candy.ide.desktop.plugin.host.DesktopPluginHost(this))
            else compileAndRun(project)
        }
    }

    /** 编译当前工程（不运行）：报告 + 生成 JS 大小 */
    fun compileOnly() {
        val project = currentProject ?: return
        // 编译从磁盘读源码：先落盘全部脏文件
        val saved = saveAllDirty()
        if (saved > 0) log("自动保存 $saved 个文件", OutputLine.Kind.Success)
        running = true
        log("[编译] 检查工程 " + project.name, OutputLine.Kind.Info)
        Thread({
            val (bag, js) = DesktopEngine.compile(project)
            log(bag.report(), if (bag.hasError) OutputLine.Kind.Error else OutputLine.Kind.Success)
            if (js != null) log("[编译] 通过，生成 " + (js.length / 1024) + " KB JS", OutputLine.Kind.Success)
            running = false
        }, "candy-compile").apply { isDaemon = true }.start()
    }

    /** 以 JS 控制台模式运行当前工程：编译 → Node（插件 PluginHost.runCurrentAsJsConsole 转发） */
    fun runJsConsole() {
        val project = currentProject ?: return
        compileAndRun(project)
    }

    private fun compileAndRun(project: File) {
        // 编译前落盘
        val saved = saveAllDirty()
        if (saved > 0) log("自动保存 $saved 个文件", OutputLine.Kind.Success)
        running = true
        log("[编译] 检查工程 " + project.name, OutputLine.Kind.Info)
        Thread({
            val (bag, js) = DesktopEngine.compile(project)
            if (js == null) {
                log(bag.report(), OutputLine.Kind.Error)
                running = false
            } else {
                log("[Node] 运行编译产物", OutputLine.Kind.Info)
                DesktopEngine.runJs(js, project,
                    onLine = { output(it) },
                    onDone = { err ->
                        running = false
                        if (err != null) {
                            output("出错：" + (err.message ?: err.javaClass.simpleName), OutputLine.Kind.Error)
                            log("运行出错：" + (err.message ?: err.javaClass.simpleName), OutputLine.Kind.Error)
                        } else {
                            output("[完成]", OutputLine.Kind.Success)
                            log("运行完成", OutputLine.Kind.Success)
                        }
                    },
                )
            }
        }, "candy-compile").apply { isDaemon = true }.start()
    }
}

/** 自动换行（对齐安卓端 Settings.wordWrap）：全局可观察状态，设置页开关后全部窗口（主页 + 各编辑器窗口）实时生效 */
private val wordWrapGlobal = mutableStateOf(Settings.wordWrap)

var IdeState.wordWrap: Boolean
    get() = wordWrapGlobal.value
    set(v) {
        Settings.wordWrap = v
        wordWrapGlobal.value = v
    }