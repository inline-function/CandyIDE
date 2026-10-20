package candy.ide.vm

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import candy.ide.data.DiagItem
import candy.ide.data.ProjectStore
import candy.ide.data.Settings
import candy.ide.editor.Schemes
import candy.ide.engine.Engine
import candy.ide.jsapk.JsApkBuilder
import candy.ide.jsapk.JsApkConfig
import candy.ide.ui.Screen
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import sugared.functor.lsp.DiagnosticSeverity
import sugared.functor.lsp.LspAnalysis
import sugared.functor.lsp.Position
import sugared.functor.lsp.Range
import sugared.functor.lsp.TextEdit
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow

class CandyViewModel(app: Application) : AndroidViewModel(app) {

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "candy-engine").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    val screen = mutableStateOf(Screen.Projects)
    val projects = mutableStateListOf<File>()
    val currentProject = mutableStateOf<File?>(null)
    val currentFile = mutableStateOf<File?>(null)
    val logOpen = mutableStateOf(false)
    /** 本次运行是否失败（编译/执行报错）：日志页标红、不跳输出页 */
    val runFailed = mutableStateOf(false)
    val running = mutableStateOf(false)

    // 多标签页：openFiles 是已打开文件，currentFile 永远 = 活动标签页
    val openFiles = mutableStateListOf<File>()
    val activeIndex = mutableIntStateOf(0)
    val treeVersion = mutableIntStateOf(0)
    // 文件树剪贴板：复制/剪切 → 粘贴到目录
    val clipFile = mutableStateOf<File?>(null)
    var clipCut = false
        private set
    // md 打开模式：true=纯文本编辑，false=Markdown 渲染
    val mdTextMode = mutableStateMapOf<String, Boolean>()
    // 正文缓存：文件路径 → 上次落盘/载入的正文 + 仍有修改未保存的标记（跨标签页保留正文用）
    private val savedTexts = HashMap<String, String>()
    val dirtyTexts = mutableStateMapOf<String, Boolean>()
    val pendingFormatEdits = mutableStateListOf<Pair<Range, String>>()  // 格式化编辑（LSP Range → 新文本）
    // 退出工程确认 / 打包完成待安装
    val exitGuard = mutableStateOf(false)
    val pendingApk = mutableStateOf<File?>(null)
    private val pendingPkg = mutableStateOf<String?>(null)
    val installing = mutableStateOf(false)
    /** 工程导出等待框 */
    val archiving = mutableStateOf(false)
    /** 轻量提示（解压等操作的即时反馈） */
    val toast = mutableStateOf("")
    /** 日志等级过滤（输出抽屉） */
    val logFilter = mutableStateOf(candy.ide.ui.LogFilter())

    /** 诊断等级过滤（输出抽屉）：错误/警告/提示/补充 四档 */
    val diagFilter = mutableStateOf(candy.ide.ui.DiagFilter())
    /** 接收外部文件的等待框与结果 */
    val ingesting = mutableStateOf(false)
    val ingestResult = mutableStateOf<String?>(null)
    val installedPkg = mutableStateOf<String?>(null)
    val installError = mutableStateOf<String?>(null)
    // 工程设置页的工程
    val settingsProject = mutableStateOf<File?>(null)

    // 设置（配色方案/字号/换行）：Settings 落盘 + 编辑器/分析器读最新值
    val schemeVersion = mutableIntStateOf(0)
    fun setFontSize(v: Int) {
        Settings.fontSize = v
        Settings.persist(getApplication())
    }
    fun setWordWrap(v: Boolean) {
        Settings.wordWrap = v
        Settings.persist(getApplication())
    }
    fun setFont(id: String) {
        Settings.fontId = id
        Settings.persist(getApplication())
    }
    fun setScheme(id: String) {
        Schemes.select(getApplication(), Schemes.byId(getApplication(), id))
        schemeVersion.intValue++
    }
    /** 重置内置方案为打包时的原始配色 */
    fun resetScheme(id: String) {
        Schemes.resetBuiltin(getApplication(), id)
        schemeVersion.intValue++
    }

    /** 删除自定义方案（当前在用时切回默认） */
    fun deleteScheme(id: String) {
        Schemes.deleteCustom(getApplication(), id)
        if (Schemes.current.id == id) setScheme("fuhuan")
        schemeVersion.intValue++
    }

    fun schemeIds(): List<Pair<String, String>> =
        Schemes.BUILTINS.map { it.id to it.name } + Schemes.listCustom(getApplication()).map { it.id to it.name }

    // 右抽屉三个页签的数据源
    val outputLines = mutableStateListOf<String>()        // 程序 stdout（纯文本）
    val buildLog = mutableStateListOf<String>()           // 构建/编译/运行/打包/安装（日志页）
    val ideLog = mutableStateListOf<String>()             // IDE 事件日志（设置页查看 + 落盘）
    /** 旧字段保留以减少改动：logLines 指向构建日志 */
    val logLines: MutableList<String> get() = buildLog
    val diagnostics = mutableStateListOf<DiagItem>()      // 语言诊断（红/黄下划线 + 页签列表）

    /** 右抽屉当前页签：0=输出 1=日志 2=诊断 */
    var drawerTab by mutableIntStateOf(0)
        private set
    fun openDrawerTab(tab: Int) {
        drawerTab = tab.coerceIn(0, 2)
        logOpen.value = true
    }
    private fun setDrawerTabMain(tab: Int) {
        main.post { drawerTab = tab.coerceIn(0, 2) }
    }

    // 语言服务：进程内直连 sublimation 的 LspAnalysis（零子进程、零 JSON-RPC）
    private val lspLock = Any()
    @Volatile private var lsp: LspAnalysis? = null
    /** LSP 已完成至少一轮分析（拿到诊断/tokens）才解锁补全与跳转，避免空文档期空表 */
    val lspReady = MutableStateFlow(false)

    fun analysis(): LspAnalysis? = synchronized(lspLock) {
        lsp ?: runCatching {
            LspAnalysis().apply {
                val stdlib = File(Engine.root(getApplication()), "stdlib")
                loadPrelude(if (stdlib.isDirectory) stdlib else null)
            }
        }.onFailure {
            mdLog("**[语言服务]** 初始化失败 · " + (it.message ?: it.javaClass.simpleName))
        }.getOrNull().also { lsp = it }
    }

    /** 编辑器每跑完一轮 Lexer 就把最新正文送进来：同步文档并刷新诊断。 */
    /** 分析器回调：同步给 LSP，并返回 semanticTokens 供编辑器着色 */
    fun onAnalyzed(text: String): List<Int>? {
        val name = currentFile.value?.name ?: return null
        if (text.length > MAX_LSP_SOURCE) return null
        val analysis = analysis() ?: return null
        var tokens: List<Int>? = null
        try {
            analysis.updateDocument(name, text)
            // updateDocument 本身就是一次完整分析（parse+check+collect）；能拿到诊断即语义就绪
            tokens = runCatching { analysis.semanticTokens(name) }.getOrNull()
            lspReady.value = true
            // 脚本模式下 LSP 把 stdlib 条目拼在用户文件前面检查，诊断会带 stdlib 的行号（越界）→ 过滤掉
            val lineCount = text.count { it == '\n' } + 1
            // 工程模式挂 core 后，stdlib 声明的诊断会一并混入（报 stdlib 的内容、位置压成 0:0）。
            // 本文件诊断消息带文件名前缀「[类别][hello.subl:行:列]」，stdlib 的没有。
            // 双重判定：前缀含本文件名，或位置在文件真实行数内但消息无前缀（推导注记类）。
            // 消息前缀形如「[语法][hello.subl:6:4] …」；stdlib 混入的诊断无此前缀
            val tag = Regex.fromLiteral("[" + name + ":")
            val items = analysis.diagnosticsFor(name)
                .filter { d -> d.message.contains(tag) }
                .map { d ->
                DiagItem(
                    severity = d.severity.severityShort(),
                    line = d.range.start.line,
                    char = d.range.start.character,
                    endLine = d.range.end.line,
                    endChar = d.range.end.character,
                    message = d.message.replace("`", "'"),
                )
            }
            main.post {
                if (diagnostics.toList() != items) {
                    diagnostics.clear()
                    diagnostics.addAll(items)
                }
            }
        } catch (_: Throwable) {
            // 中途态分析失败：忽略，下一轮 analyze 自愈
        }
        return tokens
    }

    private fun DiagnosticSeverity?.severityShort(): Short = when (this) {
        DiagnosticSeverity.Error -> DiagnosticRegion.SEVERITY_ERROR
        DiagnosticSeverity.Warning -> DiagnosticRegion.SEVERITY_WARNING
        DiagnosticSeverity.Information -> DiagnosticRegion.SEVERITY_TYPO
        DiagnosticSeverity.Hint, null -> DiagnosticRegion.SEVERITY_NONE
    }

    fun bootstrap() {
        io.execute {
            try {
                Engine.bootstrap(getApplication())
                Settings.attach(getApplication())
                schemeVersion.intValue++
            } catch (e: Throwable) {
                mdLog("**[引擎]** 初始化失败 · " + (e.message ?: e.javaClass.simpleName))
            }
            main.post {
                refreshProjects()
            }
        }
    }

    fun refreshProjects() {
        projects.clear()
        projects.addAll(ProjectStore.listProjects(getApplication()))
    }

    fun createProject(name: String, type: String) {
        io.execute {
            ProjectStore.create(getApplication(), name)?.let { dir ->
                ProjectStore.writeType(dir, type)
                when (type) {
                    ProjectStore.TYPE_JSAPK -> {
                        File(dir, "hello.subl").writeText(DEMO_SOURCE)
                        File(dir, "jsapk.json").writeText(
                            buildString {
                                append("{\n")
                                append("  \"entry\": \"hello.subl\",\n")
                                append("  \"package\": \"candy.jsapk.runtime\",\n")
                                append("  \"note\": \"v0 SDK 固定包名：装新应用会覆盖旧的\"\n")
                                append("}\n")
                            }
                        )
                    }
                    ProjectStore.TYPE_SCRIPT -> File(dir, "hello.subs").writeText(SCRIPT_DEMO_SOURCE)
                    else -> File(dir, "hello.subl").writeText(DEMO_SOURCE)
                }
                main.post { openProject(dir) }
            }
        }
    }

    fun renameProject(dir: File, newName: String) {
        io.execute {
            if (ProjectStore.rename(dir, newName)) {
                if (currentProject.value == dir) {
                    currentProject.value = File(dir.parentFile, newName)
                    savedTexts.clear()
                    dirtyTexts.clear()
                }
                main.post { refreshProjects() }
            } else {
                mdLog("**[重命名失败]** 名字非法或已存在")
            }
        }
    }

    fun deleteProject(dir: File) {
        io.execute {
            dir.deleteRecursively()
            main.post {
                refreshProjects()
                if (currentProject.value == dir) goProjects()
            }
        }
    }

    fun openProject(dir: File) {
        // 打开时间顶上去：主页工程卡按「上次打开时间」排序
        ProjectStore.touchOpened(dir)
        val first = dir.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".subs") || it.name.endsWith(".subl")) }
            .minByOrNull { it.name }
        Snapshot.withMutableSnapshot {
            currentProject.value = dir
            openFiles.clear()
            activeIndex.intValue = 0
            if (first != null) {
                openFiles.add(first)
                currentFile.value = first
            } else {
                currentFile.value = null
            }
            screen.value = Screen.Editor
        }
        mdLogUser("**[工程]** 已打开 `" + dir.name + "`（" +
            when (ProjectStore.typeOf(dir)) {
                ProjectStore.TYPE_JSAPK -> "JS-apk 工程"
                ProjectStore.TYPE_SCRIPT -> "脚本工程"
                else -> "JS 平台工程"
            } + "）")
    }

    /** 日志页的 `文件名：行` 超链接：工程内找同名文件并跳到指定行（0-based） */
    fun openFileLine(name: String, line: Int, col: Int = 0) {
        val project = currentProject.value
        val file = if (project != null) {
            project.walkTopDown().firstOrNull { it.isFile && it.name == name }
        } else {
            openFiles.firstOrNull { it.name == name }
        }
        if (file != null) {
            openFile(file)
            requestGoto((line - 1).coerceAtLeast(0))
            mdLogUser("**[跳转]** `" + name + ":" + line + "`")
        } else {
            mdLogUser("**[跳转失败]** 工程里找不到 `$name`")
        }
    }

    /** 诊断报告里的 `[3:5]` 位置补上文件名，凑成日志页可点的 `文件：行：列` */
    private fun linkPositions(text: String, file: File?): String {
        if (file == null) return text
        val n = file.name
        return text.replace(Regex("\\[(\\d+):(\\d+)\\]"), "[" + n + ":$1:$2]")
    }

    /** 文件树点击：进标签页（已存在则激活）。apk 直接走安装，不进编辑器 */
    fun openFile(file: File) {
        if (file.name.endsWith(".apk", ignoreCase = true)) {
            installApk(file)
            return
        }
        savedTexts.putIfAbsent(file.absolutePath, runCatching { file.readText() }.getOrDefault(""))
        // 单文件打开也计入「上次打开时间」（主页排序用它）
        ProjectStore.touchOpened(file.parentFile ?: file)
        val idx = openFiles.indexOf(file)
        if (idx >= 0) selectTab(idx)
        else Snapshot.withMutableSnapshot {
            openFiles.add(file)
            selectTab(openFiles.size - 1)
        }
    }

    /** 编辑器正文加载源：有未保存修改的标签页保留修改过的正文，否则读磁盘 */
    fun loadTextFor(file: File): String =
        dirtyTexts[file.absolutePath]?.let { savedTexts[file.absolutePath] } ?: file.readText()

    /** 正文每次变更：与落盘副本比对，决定脏标记 */
    fun onEditorTextChanged(text: String) {
        val f = currentFile.value ?: return
        val dirty = savedTexts[f.absolutePath] != text
        if (dirty) {
            savedTexts[f.absolutePath] = text
            dirtyTexts[f.absolutePath] = true
        } else {
            dirtyTexts.remove(f.absolutePath)
        }
    }

    fun selectTab(i: Int) {
        if (i !in openFiles.indices) return
        activeIndex.intValue = i
        currentFile.value = openFiles[i]
    }

    // 未保存修改时的关页签确认对象
    val closeGuard = mutableStateOf<File?>(null)

    /** 关页签：有未保存修改先弹确认，由 EditorScreen 渲染对话框 */
    fun closeTabChecked(i: Int) {
        val f = openFiles.getOrNull(i) ?: return
        if (dirtyTexts[f.absolutePath] == true) closeGuard.value = f
        else closeTab(i)
    }

    fun cancelCloseTab() { closeGuard.value = null }

    fun saveAndCloseTab(file: File) {
        val text = savedTexts[file.absolutePath]
        if (text != null) {
            savedTexts[file.absolutePath] = text
            io.execute { runCatching { file.writeText(text) } }
        }
        dirtyTexts.remove(file.absolutePath)
        closeGuard.value = null
        val idx = openFiles.indexOf(file)
        if (idx >= 0) closeTab(idx)
    }

    fun discardAndCloseTab(file: File) {
        dirtyTexts.remove(file.absolutePath)
        savedTexts.remove(file.absolutePath)
        closeGuard.value = null
        val idx = openFiles.indexOf(file)
        if (idx >= 0) closeTab(idx)
    }

    fun closeTab(i: Int) {
        if (i !in openFiles.indices) return
        val wasActive = activeIndex.intValue == i
        Snapshot.withMutableSnapshot {
            openFiles.removeAt(i)
            val newIdx = when {
                openFiles.isEmpty() -> 0
                wasActive -> i.coerceAtMost(openFiles.size - 1)
                i < activeIndex.intValue -> activeIndex.intValue - 1
                else -> activeIndex.intValue
            }
            activeIndex.intValue = newIdx
            currentFile.value = openFiles.getOrNull(newIdx)
        }
    }

    /** 编辑器返回：有未保存修改先弹确认 */
    fun backFromEditor() {
        if (dirtyTexts.isNotEmpty()) exitGuard.value = true
        else goProjects()
    }

    fun cancelExit() { exitGuard.value = false }

    fun discardChangesAndExit() {
        exitGuard.value = false
        savedTexts.clear()
        dirtyTexts.clear()
        goProjects()
    }

    fun saveAndExit(text: String) {
        saveCurrent(text)
        exitGuard.value = false
        goProjects()
    }

    fun goProjects() {
        Snapshot.withMutableSnapshot {
            screen.value = Screen.Projects
            currentProject.value = null
            currentFile.value = null
            openFiles.clear()
            activeIndex.intValue = 0
        }
    }

    fun goSettings() { screen.value = Screen.Settings }
    fun goIdeLog() { ensureIdeLogLoaded(); screen.value = Screen.IdeLog }
    fun goProjectSettings(dir: File) {
        settingsProject.value = dir
        screen.value = Screen.ProjectSettings
    }
    fun goScheme() { screen.value = Screen.Scheme }
    fun goDevelopers() { screen.value = Screen.Developers }
    fun goTerminal() { screen.value = Screen.Terminal }
    fun goAgent() { screen.value = Screen.Agent }

    // —— AI agent ——
    val chat = mutableStateListOf<ChatMessage>()
    val agentBusy = mutableStateOf(false)
    val agentStage = mutableStateOf("")   // "思考中…/调用工具 list_project_files…/空"
    /** 往期对话存档 */
    val conversations = mutableStateListOf<AgentConversation>()
    @Volatile private var agent: candy.ide.agent.CandyAgent? = null
    private var agentJob: kotlinx.coroutines.Job? = null

    /** 问 agent 一句；事件流逐条落进 chat */
    fun askAgent(ctx: android.content.Context, text: String) {
        if (agentBusy.value) return
        if (agent == null) agent = candy.ide.agent.CandyAgent(ctx.applicationContext)
        agent!!.apply {
            tools.currentFile = currentFile.value?.absolutePath
            tools.currentProjectDir = currentProject.value
            tools.diagnostics = diagnostics.toList()
        }
        chat.add(ChatMessage(ChatMessage.Role.USER, text))
        agentBusy.value = true
        agentStage.value = "思考中…"
        agentJob = viewModelScope.launch {
            try {
                agent!!.ask(text).collect { ev ->
                    val author = ev.author
                    if (author == "user") return@collect   // 用户气泡已在上方直接添加，跳过回声
                    val body = ev.content?.parts?.mapNotNull { it.text }?.joinToString("")?.trim() ?: ""
                    if (body.isBlank()) return@collect
                    if (author == "tool") {
                        val name = body!!.substringAfter("[工具 ").substringBefore(" ")
                        agentStage.value = "调用工具 " + name + "…"
                    }
                    val role = when (author) {
                        "model" -> ChatMessage.Role.MODEL
                        "tool" -> ChatMessage.Role.TOOL
                        else -> ChatMessage.Role.USER
                    }
                    chat.add(ChatMessage(role, body!!))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                chat.add(ChatMessage(ChatMessage.Role.MODEL, "调用失败：" + (e.message ?: e.toString())))
            } finally {
                agentBusy.value = false
                agentStage.value = ""
            }
        }
    }

    /** 随时停止当前生成 */
    fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
        if (agentBusy.value) {
            agentBusy.value = false
            agentStage.value = ""
            chat.add(ChatMessage(ChatMessage.Role.MODEL, "（已停止）"))
        }
    }

    /** 新建对话：当前未空则存档，再清空并重置 agent 会话 */
    fun newAgentChat(ctx: android.content.Context) {
        stopAgent()
        if (chat.isNotEmpty()) {
            val first = chat.firstOrNull { it.role == ChatMessage.Role.USER }?.text ?: "对话"
            conversations.add(0, AgentConversation(System.currentTimeMillis(), first, chat.toList()))
            saveConversations(ctx)
        }
        chat.clear()
        if (agent == null) agent = candy.ide.agent.CandyAgent(ctx.applicationContext)
        agent!!.resetSession()
    }

    /** 恢复一段往期对话 */
    fun restoreConversation(ctx: android.content.Context, conv: AgentConversation) {
        stopAgent()
        if (chat.isNotEmpty()) {
            val first = chat.firstOrNull { it.role == ChatMessage.Role.USER }?.text ?: "对话"
            conversations.add(0, AgentConversation(System.currentTimeMillis(), first, chat.toList()))
        }
        conversations.remove(conv)
        chat.clear()
        chat.addAll(conv.messages)
        if (agent == null) agent = candy.ide.agent.CandyAgent(ctx.applicationContext)
        agent!!.resetSession()
        saveConversations(ctx)
    }

    /** 往期对话落盘（JSON；每段最多 80 条，只留最近 30 段） */
    fun saveConversations(ctx: android.content.Context) {
        runCatching {
            val arr = org.json.JSONArray()
            for (c in conversations.take(30)) {
                val j = org.json.JSONObject()
                    .put("time", c.time)
                    .put("title", c.title)
                val msgs = org.json.JSONArray()
                for (m in c.messages.takeLast(80)) msgs.put(org.json.JSONObject().put("role", m.role.name).put("text", m.text))
                j.put("messages", msgs)
                arr.put(j)
            }
            val dir = java.io.File(candy.ide.engine.Engine.root(ctx), "agent")
            dir.mkdirs()
            java.io.File(dir, "conversations.json").writeText(arr.toString())
        }
    }

    /** 启动时从磁盘读回往期对话 */
    fun loadConversations(ctx: android.content.Context) {
        if (conversations.isNotEmpty()) return
        runCatching {
            val f = java.io.File(candy.ide.engine.Engine.root(ctx), "agent/conversations.json")
            if (!f.isFile) return@runCatching
            val arr = org.json.JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val msgs = j.getJSONArray("messages")
                val list = mutableListOf<ChatMessage>()
                for (k in 0 until msgs.length()) {
                    val mo = msgs.getJSONObject(k)
                    val role = runCatching { ChatMessage.Role.valueOf(mo.getString("role")) }.getOrDefault(ChatMessage.Role.MODEL)
                    list.add(ChatMessage(role, mo.getString("text")))
                }
                if (list.isNotEmpty()) conversations.add(AgentConversation(j.getLong("time"), j.optString("title"), list))
            }
        }
    }

    /** 在工程内生成自签名 jks 证书；成功返回相对路径，失败返回原因 */
    fun generateCert(project: File, alias: String, pass: String, cn: String, years: Int): String {
        if (alias.isBlank()) return "别名不能为空"
        if (pass.length < 6) return "密码至少 6 位"
        if (cn.isBlank()) return "CN 不能为空"
        val name = alias.trim() + ".jks"
        val d = File(project, "certificates").apply { mkdirs() }
        val target = File(d, name)
        val r = candy.ide.jsapk.JksWriter.generate(target, alias.trim(), pass.toCharArray(), cn.trim(), years.coerceIn(1, 30))
        return r.fold(
            onSuccess = { "certificates/" + name },
            onFailure = { "生成失败：" + (it.message ?: it.javaClass.simpleName) },
        )
    }


    /** 解压归档到同名目录（工程内），完成后刷新文件树 */
    fun extractArchive(file: File) {
        toast.value = "正在解压 " + file.name + "…"
        io.execute {
            val out = File(file.parentFile, file.nameWithoutExtension)
            var n = 0
            val ok = runCatching {
                java.util.zip.ZipFile(file).use { zf ->
                    out.mkdirs()
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.isDirectory) continue
                        val f = File(out, e.name)
                        // 防 Zip Slip
                        if (!f.absolutePath.startsWith(out.absolutePath)) continue
                        f.parentFile?.mkdirs()
                        zf.getInputStream(e).use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                        n++
                    }
                }
            }.isSuccess
            if (ok) {
                mdLog("**[解压]** " + file.name + " → " + out.name + "（" + n + " 个文件）")
                main.post { toast.value = "已解压 " + n + " 个文件到 " + out.name; treeVersion.intValue++ }
            } else {
                mdLogUser("**[解压失败]** " + file.name)
                main.post { toast.value = "解压失败" }
            }
        }
    }


    /** 把外部选中的 .jks 复制进工程 certificates/，返回相对路径 */
    fun importCertInto(project: File, uri: android.net.Uri): String? {
        return runCatching {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
                ?: ("cert-" + System.currentTimeMillis() + ".jks")
            val d = File(project, "certificates").apply { mkdirs() }
            val target = File(d, name)
            getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.use { ins ->
                target.outputStream().use { o -> ins.copyTo(o) }
            } ?: return null
            "certificates/" + name
        }.getOrNull()
    }


    /** 处理外部发来的文件（jks/caco/capr）：落盘到对应目录，返回可显示的结果 */
    fun ingestSharedFile(uri: android.net.Uri) {
        main.post { ingesting.value = true }
        io.execute {
            val name = runCatching { uri.lastPathSegment ?: "" }.getOrDefault("")
            val res = runCatching {
                val app = getApplication<android.app.Application>()
                val root = candy.ide.engine.Engine.root(app)
                when {
                    name.endsWith(".caco") -> {
                        val d = java.io.File(root, "schemes").apply { mkdirs() }
                        val target = java.io.File(d, name)
                        app.contentResolver.openInputStream(uri)?.use { it.copyTo(target.outputStream()) }
                        "配色已导入：" + name
                    }
                    name.endsWith(".jks") -> {
                        val d = java.io.File(root, "keys").apply { mkdirs() }
                        val target = java.io.File(d, name)
                        app.contentResolver.openInputStream(uri)?.use { it.copyTo(target.outputStream()) }
                        "证书已导入：" + name + "（打包时可选用）"
                    }
                    name.endsWith(".capr") -> {
                        val d = java.io.File(root, "projects").apply { mkdirs() }
                        val pname = name.removeSuffix(".capr")
                        val out = java.io.File(d, pname)
                        out.mkdirs()
                        app.contentResolver.openInputStream(uri)?.use { ins ->
                            java.util.zip.ZipInputStream(ins).use { zis ->
                                while (true) {
                                    val e = zis.nextEntry ?: break
                                    if (!e.isDirectory) {
                                        val f = java.io.File(out, e.name)
                                        f.parentFile?.mkdirs()
                                        f.outputStream().use { o -> zis.copyTo(o) }
                                    }
                                    zis.closeEntry()
                                }
                            }
                        }
                        "工程已解压：" + pname
                    }
                    else -> "不支持的文件类型：" + name
                }
            }.getOrElse { "导入失败：" + (it.message ?: it.javaClass.simpleName) }
            mdLogUser("**[接收文件]** " + res)
            main.post { ingesting.value = false; ingestResult.value = res }
        }
    }


    /** 导出配色方案：把方案写成 .caco（SAF 由 UI 传入输出流目标 uri） */
    fun exportScheme(schemeId: String, uri: android.net.Uri) {
        io.execute {
            runCatching {
                val s = candy.ide.editor.Schemes.byId(getApplication(), schemeId)
                val props = java.util.Properties().apply {
                    setProperty("name", s.name)
                    for ((token, color) in s.colors) {
                        setProperty(token, Integer.toHexString(color and 0xFFFFFF).padStart(6, '0'))
                        val flag = s.flags[token] ?: 0
                        if (flag and 1 != 0) setProperty(token + ".bold", "true")
                        if (flag and 2 != 0) setProperty(token + ".italic", "true")
                    }
                }
                getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use { os ->
                    props.store(os, "CandyIDE color scheme (.caco)")
                }
                mdLogUser("**[导出]** 配色 " + s.name + " → " + (uri.lastPathSegment ?: ""))
            }.onFailure { mdLogUser("**[导出失败]** " + (it.message ?: it.javaClass.simpleName)) }
        }
    }

    /** 导出工程：整个工程目录压成 .capr（SAF 输出目标 uri） */
    fun exportProjectArchive(dir: File, uri: android.net.Uri) {
        main.post { archiving.value = true }
        io.execute {
            runCatching {
                val base = dir.absolutePath
                getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use { os ->
                    java.util.zip.ZipOutputStream(os).use { zos ->
                        dir.walkTopDown().filter { it.isFile }.forEach { f ->
                            val rel = f.absolutePath.removePrefix(base).trimStart('/')
                            // build 目录不打包
                            if (!rel.startsWith("build/")) {
                                zos.putNextEntry(java.util.zip.ZipEntry(rel))
                                f.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
                mdLogUser("**[导出]** 工程 " + dir.name + " → " + (uri.lastPathSegment ?: ""))
            }.onFailure { mdLogUser("**[导出失败]** " + (it.message ?: it.javaClass.simpleName)) }
            main.post { archiving.value = false }
        }
    }

    fun goBack() {
        screen.value = when (screen.value) {
            Screen.ProjectSettings -> Screen.Projects
            Screen.IdeLog -> Screen.Settings
            Screen.Scheme -> Screen.Settings
            Screen.Developers -> Screen.Settings
            Screen.Settings -> if (currentProject.value != null) Screen.Editor else Screen.Projects
            else -> Screen.Projects
        }
    }

    fun toggleLog() {
        logOpen.value = !logOpen.value
    }

    /** 工程保存：把所有打开且改动过的文件落盘 */
    fun saveProject() {
        val open = openFiles.toList()
        var n = 0
        for (f in open) {
            val text = savedTexts[f.absolutePath] ?: continue
            if (dirtyTexts.containsKey(f.absolutePath)) {
                runCatching { f.writeText(text) }
                    .onFailure { mdLogUser("**[保存失败]** " + f.name + " " + (it.message ?: it.javaClass.simpleName)) }
                dirtyTexts.remove(f.absolutePath)
                n++
            }
        }
        mdLogUser("**[工程保存]** 共保存 " + n + " 个文件")
    }

    fun saveCurrent(text: String) {
        val file = currentFile.value ?: return
        savedTexts[file.absolutePath] = text
        dirtyTexts.remove(file.absolutePath)
        io.execute {
            runCatching { file.writeText(text) }
                .onSuccess {
                    runFailed.value = false
                    mdLogUser("**[保存]** `" + file.name + "`")
                }
                .onFailure { mdLogUser("**[保存失败]** " + (it.message ?: it.javaClass.simpleName)) }
        }
    }

    fun saveAndRun(text: String?) {
        val file = currentFile.value ?: return
        if (running.value) {
            mdLog("**[运行]** 上一次还没跑完")
            return
        }
        running.value = true
        runFailed.value = false
        outputLines.clear()
        logLines.clear()
        // 编译期间停在日志页，成功跑完才跳输出页；失败留在日志页标红
        openDrawerTab(1)
        io.execute {
            try {
                if (text != null) file.writeText(text)
                val project = currentProject.value
                val type = if (project != null) ProjectStore.typeOf(project) else ProjectStore.TYPE_JS
                if (type == ProjectStore.TYPE_JS || type == ProjectStore.TYPE_JSAPK) {
                    mdLog("**[运行]** `" + file.name + "` 编译 → JS → WebView")
                    Engine.run(getApplication(), file,
                        onLine = { line -> main.post { outputLines.add(line) } },
                    // 编译在 IO 线程同步完成；JS 执行异步，收尾回调在主线程
                        onDone = {
                            mdLog("**[运行结束]**")
                            main.post { runFailed.value = false }
                            running.value = false
                            setDrawerTabMain(0)
                        },
                        onCompiled = { ms -> mdLog("**[编译完成]** 用时 " + fmtElapsed(ms)) },
                        onInput = { readlnDialog() },
                    )
                } else {
                    mdLog("**[运行]** `" + file.name + "` 脚本直连解释器")
                    Engine.runScript(getApplication(), file,
                        onLine = { line -> main.post { outputLines.add(line) } },
                        onDone = {
                            mdLog("**[运行结束]**")
                            main.post { runFailed.value = false }
                            running.value = false
                            setDrawerTabMain(0)
                        },
                    )
                }
            } catch (t: Throwable) {
                mdLog("**[错误]** " + linkPositions(t.message?.replace("`", "'") ?: t.javaClass.simpleName, file))
                main.post {
                    runFailed.value = true
                    running.value = false
                }
            }
        }
    }

    // ---- 控制台输入：JS 的 readLine 走这里弹输入框（WebView JS 线程同步等结果）----

    // 输入闩锁：JS 线程阻塞等主线程的对话框结果（与 jsapk 模板运行时同构）
    @Volatile private var inputLatch: java.util.concurrent.CountDownLatch? = null
    @Volatile private var inputResult: String? = null
    var showInput by mutableStateOf(false)
    var inputText by mutableStateOf("")

    /** JS 线程调用：主线程弹输入框并同步等回结果（null = 超时/取消 → EOF/Err） */
    private fun readlnDialog(): String? {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            mdLogBridge("readlnDialog", "在主线程被调用，无法弹框")
            return null
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        inputText = ""
        main.post { inputLatch = latch; showInput = true }
        val ok = latch.await(120, java.util.concurrent.TimeUnit.SECONDS)
        mdLogBridge(if (!ok) "超时/取消" else if (inputResult.isNullOrEmpty()) "提交" else "提交", "结果=" + (inputResult?.take(20) ?: "null（EOF）"))
        return inputResult
    }

    fun submitInput() {
        inputResult = inputText
        showInput = false
        inputLatch?.countDown()
        inputLatch = null
    }

    fun cancelInput() {
        inputResult = null
        showInput = false
        inputLatch?.countDown()
        inputLatch = null
    }

    // ---- JS-apk 打包 ----

    /** 当前工程的入口 .subl（有 jsapk.json 读 entry，没有就取第一个 .subl） */
    private fun jsapkEntry(project: File): File? {
        // 走 JsApkConfig 的 org.json 解析：手工 substringAfter 截到行尾会把整段 JSON 粘进文件名
        val entry = runCatching { JsApkConfig.load(project).entry }.getOrNull()
        val candidate = entry?.let { File(project, it) }
        return when {
            candidate?.isFile == true -> candidate
            else -> project.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".subl") }
        }
    }

    /** 编译 → 注入模板 APK → v1 签名 → <工程>/build/app.apk；按设置直接静默安装或弹确认 */
    /** 工程名 → Android 合法包名段：只留 ASCII 字母数字下划线（isLetterOrDigit 会放过汉字），
     *  空串或数字开头补 app 前缀 */
    fun asciiPackageSeed(name: String): String {
        val clean = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        return if (clean.isEmpty() || clean.first().isDigit()) "app" + clean else clean
    }

    /** 指定工程打包：有 jsapk.json（JS-apk 或 JS 平台工程在设置页配过）走它，否则 JS 平台工程用自动 ASCII 包名 */
    fun buildApkProject(dir: File) {
        if (File(dir, "jsapk.json").isFile) { buildApkIn(dir); return }
        if (ProjectStore.typeOf(dir) == ProjectStore.TYPE_JSAPK) { buildApkIn(dir); return }
        buildApkIn(dir, JsApkConfig(
            pkg = JsApkConfig.DEFAULT_PACKAGE + "." + asciiPackageSeed(dir.name),
            label = dir.name,
            entry = dir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".subl") }?.name ?: "main.subl",
        ))
    }

    fun buildApk() {
        buildApkProject(currentProject.value ?: return)
    }

    /** 工程设置页也能触发：指定工程打包，入口/包名走该工程的 jsapk.json */
    fun buildApkIn(project: File, forced: JsApkConfig? = null) {
        val pj = project
        if (running.value) return
        running.value = true
        openDrawerTab(1)
        val entry = jsapkEntry(pj)
        if (entry == null) {
            mdLog("**[打包失败]** 工程里没有 .subl 源文件")
            running.value = false
            return
        }
        mdLog("**[打包]** " + entry.name + " 编译 → 注入模板 → 签名")
        io.execute {
            try {
                val cfg = forced ?: JsApkConfig.load(pj)
                // 自定义证书（工程 jsapk.json 指定的 .jks）
                val certErr = if (cfg.certPath != null && cfg.certPass != null) {
                    val jks = java.io.File(pj, cfg.certPath)
                    if (!jks.isFile) "[打包] 证书文件不存在：" + cfg.certPath
                    else {
                        val err = candy.ide.jsapk.ApkSigner.loadJks(jks, cfg.certPass.toCharArray())
                        if (err != null) "[打包] " + err else null
                    }
                } else {
                    candy.ide.jsapk.ApkSigner.useCustomKey(null, null)
                    null
                }
                if (certErr != null) {
                    mdLogUser("**" + certErr + "，改用内置证书**")
                    candy.ide.jsapk.ApkSigner.useCustomKey(null, null)
                }
                val apk = JsApkBuilder.build(getApplication(), entry, cfg, onCompiled = { ms ->
                    mdLog("**[编译完成]** 用时 " + fmtElapsed(ms))
                })
                val sizeKb = apk.length() / 1024
                mdLog("**[打包完成]** " + apk.name + " " + sizeKb + " KB · 在工程 build/ 目录")
                main.post { runFailed.value = false }
                if (cfg.pkg != JsApkConfig.DEFAULT_PACKAGE) mdLog("**[打包]** 包名 " + cfg.pkg + " · 应用名 " + cfg.label)
                if (Settings.silentInstall) installPkg(apk, cfg.pkg)
                else {
                    main.post {
                        pendingPkg.value = cfg.pkg
                        pendingApk.value = apk
                    }
                }
            } catch (t: Throwable) {
                mdLog("**[打包失败]** " + linkPositions(t.message?.replace("`", "'") ?: t.javaClass.simpleName, File(project, JsApkConfig.load(project).entry)))
                main.post { runFailed.value = true }
            } finally {
                main.post { running.value = false }
            }
        }
    }

    /** 打包确认对话框：立即安装（Shizuku 流式安装） */
    fun confirmInstallApk() {
        val apk = pendingApk.value ?: return
        pendingApk.value = null
        installPkg(apk, pendingPkg.value)
    }

    /** 安装完成反馈（成弹「打开」、败弹原因），installing 期间 UI 显示等待框 */
    fun installPkg(apk: File, pkg: String? = null) {
        if (installing.value) return
        installing.value = true
        openDrawerTab(1)
        mdLog("**[安装 APK]** " + apk.name)
        io.execute {
            val (via, msg) = JsApkBuilder.installViaShizuku(apk)
            main.post {
                installing.value = false
                if (via) {
                    installedPkg.value = pkg ?: pendingPkg.value
                    runFailed.value = false
                    mdLog("**[安装]** " + msg)
                } else {
                    installError.value = msg
                    mdLog("**[安装]** Shizuku 不可用：" + msg + " · 请在设置里连接 Shizuku，或用其它应用打开")
                }
            }
        }
    }

    /** 启动已安装的 apk（包名兜底失败给提示） */
    fun launchInstalled(pkg: String) {
        val ctx: android.content.Context = getApplication()
        runCatching {
            val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                ?: throw IllegalStateException("找不到包 " + pkg)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }.onFailure { mdLog("**[打开失败]** " + (it.message ?: it.javaClass.simpleName)) }
    }

    fun cancelInstallApk() {
        pendingApk.value = null
        pendingPkg.value = null
    }

    /** 文件树/任意 apk：包名未知时不传，成功后「打开」走包名兜底失败提示 */
    fun installApk(file: File) = installPkg(file)

    /** 用其它应用打开（FileProvider 授权读） */
    fun openExternal(ctx: android.content.Context, file: File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, ctx.packageName + ".fileprovider", file)
            val mime = when (file.extension.lowercase()) {
                "apk" -> "application/vnd.android.package-archive"
                "md" -> "text/markdown"
                "subl", "subs", "txt", "json" -> "text/plain"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }.onFailure { mdLog("**[打开失败]** " + (it.message ?: it.javaClass.simpleName)) }
    }

    /** 复制路径到系统剪贴板 */
    fun copyPath(ctx: android.content.Context, file: File) {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("path", file.absolutePath))
        mdLogUser("**[已复制路径]** " + file.absolutePath)
    }

    /** 打开方式：md 以纯文本进编辑器（其它类型直接打开） */
    fun openAsText(file: File) {
        if (file.name.endsWith(".md", ignoreCase = true)) mdTextMode[file.absolutePath] = true
        openFile(file)
    }

    /** 打开方式：md 以 Markdown 渲染阅读 */
    fun openAsMd(file: File) {
        if (file.name.endsWith(".md", ignoreCase = true)) mdTextMode[file.absolutePath] = false
        openFile(file)
    }

    fun copyNode(f: File) {
        clipFile.value = f
        clipCut = false
        mdLogUser("**[已复制]** " + f.name)
    }

    fun cutNode(f: File) {
        clipFile.value = f
        clipCut = true
        mdLogUser("**[已剪切]** " + f.name)
    }

    fun pasteInto(dir: File) {
        val src = clipFile.value ?: return
        io.execute {
            val target = File(dir, src.name)
            val ok = if (clipCut) {
                if (src.parentFile == dir) true else src.renameTo(target) ||
                    runCatching { src.copyTo(target, overwrite = false); src.deleteRecursively() }.getOrDefault(false)
            } else {
                runCatching { src.copyTo(target, overwrite = false); true }.getOrDefault(false)
            }
            if (ok) {
                main.post { treeVersion.intValue++ }
                mdLog("**[粘贴成功]** " + src.name + " → " + dir.name)
                if (clipCut) main.post { clipFile.value = null }
            } else {
                mdLog("**[粘贴失败]** 目标已存在或 IO 错误")
            }
        }
    }

    fun clearLog() = logLines.clear()
    fun clearOutput() = outputLines.clear()
    fun clearDiagnostics() = diagnostics.clear()

    // ---- 文件树操作 ----

    fun createFile(parent: File, name: String) {
        io.execute {
            val f = ProjectStore.createFile(parent, name)
            if (f != null) main.post { treeVersion.intValue++ } else mdLog("**[新建失败]** 名字非法或文件已存在")
        }
    }

    fun createDir(parent: File, name: String) {
        io.execute {
            val f = ProjectStore.createDir(parent, name)
            if (f != null) main.post { treeVersion.intValue++ } else mdLog("**[新建失败]** 名字非法或目录已存在")
        }
    }

    fun renameNode(f: File, newName: String) {
        io.execute {
            val ok = ProjectStore.renameFile(f, newName)
            if (ok) {
                val target = File(f.parentFile, newName)
                savedTexts.remove(f.absolutePath)?.let { savedTexts[target.absolutePath] = it }
                if (dirtyTexts.remove(f.absolutePath) != null) dirtyTexts[target.absolutePath] = true
                val idx = openFiles.indexOf(f)
                if (idx >= 0) openFiles[idx] = File(f.parentFile, newName)
                if (currentFile.value == f) currentFile.value = File(f.parentFile, newName)
                main.post {
                    treeVersion.intValue++
                    if (idx >= 0) selectTab(idx)
                }
            } else mdLog("**[重命名失败]** 名字非法或已存在")
        }
    }

    /** 多选批量操作：把一组文件复制/剪切到剪贴板集合（粘贴时还原目录结构） */
    private val _multiClip = mutableStateOf<List<File>>(emptyList())
    val multiClip get() = _multiClip
    private var multiClipCut = false

    fun copyMulti(files: List<File>) { _multiClip.value = files; multiClipCut = false }
    fun cutMulti(files: List<File>) { _multiClip.value = files; multiClipCut = true }

    /** 粘贴多选集合到目标目录 */
    fun pasteMultiInto(dir: File) {
        val src = _multiClip.value
        if (src.isEmpty()) return
        io.execute {
            var n = 0
            for (f in src) {
                val target = File(dir, f.name)
                if (target.absolutePath == f.absolutePath) continue
                runCatching {
                    if (multiClipCut) { if (f.renameTo(target)) n++ }
                    else { f.copyRecursively(target, overwrite = true); n++ }
                }
            }
            if (multiClipCut) _multiClip.value = emptyList()
            main.post {
                treeVersion.intValue++
                toast.value = "已粘贴 " + n + " 项到 " + dir.name
            }
        }
    }

    /** 多选批量删除 */
    fun deleteMulti(files: List<File>) {
        io.execute {
            var n = 0
            for (f in files) {
                savedTexts.remove(f.absolutePath)
                dirtyTexts.remove(f.absolutePath)
                if (ProjectStore.deleteFile(f)) n++
            }
            main.post { treeVersion.intValue++; toast.value = "已删除 " + n + " 项" }
        }
    }

    /** 多选压缩为 zip */
    fun zipMulti(files: List<File>, target: File) {
        toast.value = "正在压缩…"
        io.execute {
            val ok = runCatching {
                java.util.zip.ZipOutputStream(target.outputStream()).use { zos ->
                    for (f in files) {
                        val base = f.parentFile ?: break
                        if (f.isDirectory) {
                            f.walkTopDown().filter { it.isFile }.forEach { sub ->
                                val rel = sub.absolutePath.removePrefix(base.absolutePath).trimStart('/')
                                zos.putNextEntry(java.util.zip.ZipEntry(rel))
                                sub.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        } else {
                            zos.putNextEntry(java.util.zip.ZipEntry(f.name))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }.isSuccess
            main.post {
                if (ok) toast.value = "已压缩到 " + target.name else toast.value = "压缩失败"
                treeVersion.intValue++
            }
        }
    }

    fun deleteNode(f: File) {
        io.execute {
            savedTexts.remove(f.absolutePath)
            dirtyTexts.remove(f.absolutePath)
            mdTextMode.remove(f.absolutePath)
            val ok = ProjectStore.deleteFile(f)
            if (ok) {
                val idx = openFiles.indexOf(f)
                if (idx >= 0) main.post { closeTab(idx) }
                main.post { treeVersion.intValue++ }
            } else mdLog("**[删除失败]**")
        }
    }

    /** 工程根目录（文件树「新建」的默认父目录） */
    fun treeRoot(): File? = currentProject.value

    /** 编译/打包用时：毫秒 → 秒 → mm:ss → hh:mm:ss 自适应 */
    private fun fmtElapsed(ms: Long): String {
        val sec = ms / 1000
        return when {
            ms < 1000 -> ms.toString() + " ms"
            sec < 60 -> (ms / 100.0 / 10.0).toString() + " s"
            sec < 3600 -> "%02d:%02d".format(sec / 60, sec % 60)
            else -> "%02d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60)
        }
    }

    private fun mdLog(md: String) {
        main.post { buildLog.add(md) }
        appendLogFile(md)
    }

    /** IDE 事件日志：编辑器交互反馈（重命名/复制/跳转等），进设置页的 IDE 日志并落盘 */
    fun mdLogUser(md: String) {
        main.post { ideLog.add(md) }
        appendLogFile(md)
    }

    /** 按回车后问 LSP 要智能缩进编辑（onTypeFormatting）；空表说明无需调整 */
    fun indentEditsOnEnter(text: String, targetLine: Int): List<TextEdit>? {
        val name = currentFile.value?.name ?: return null
        if (text.length > MAX_LSP_SOURCE) return null
        val analysis = analysis() ?: return null
        return runCatching {
            analysis.updateDocument(name, text)
            analysis.onTypeFormattingEdits(name, Position(targetLine, 0), "\n")
        }.getOrNull()
    }

    /** 语言服务的重命名结果：isSymbol=false 表示光标处不是可重命名的标识符 */
    data class RenameResult(val isSymbol: Boolean, val edits: List<Pair<Range, String>>)

    /** 用语言服务重命名：先 prepareRename 校验是标识符（声明或引用），再拿全量替换编辑 */
    fun renameAt(text: String, line: Int, column: Int, newName: String): RenameResult? {
        val name = currentFile.value?.name ?: return null
        val analysis = analysis() ?: return null
        return runCatching {
            // LSP 的所有查询都基于 updateDocument 后的正文；先同步再取 edits
            analysis.updateDocument(name, text)
            val pos = Position(line, column)
            if (analysis.prepareRenameFor(name, pos) == null) return@runCatching RenameResult(false, emptyList())
            val changes = analysis.renameFor(name, pos, newName) ?: return@runCatching RenameResult(false, emptyList())
            RenameResult(true, changes[name]?.map { it.range to it.newText } ?: emptyList())
        }.getOrNull()
    }

    /** 灯泡：当前文件可用的快速修正（LSP codeActions） */
    data class QuickFix(
        val title: String,
        val edits: List<Pair<Range, String>>,
        /** command 型快捷操作：重命名 / 转到定义（IDE 侧已有实现，点灯泡直接触发） */
        val command: String? = null,
    )

        /** 全文格式化：向 LSP 要 TextEdit，回填编辑器（批量编辑，可一次撤销） */
    fun formatDocument(): Boolean {
        val f = currentFile.value ?: return false
        val name = f.name
        val text = savedTexts[f.absolutePath] ?: f.readText()
        val analysis = runCatching { analysis() }.getOrNull() ?: return false
        analysis.updateDocument(name, text)
        val edits = analysis.formatDocument(name) ?: return false
        if (edits.isEmpty()) return false   // 无需改动
        pendingFormatEdits.clear()
        pendingFormatEdits.addAll(edits.map { it.range to it.newText })
        return true
        return false
    }

    fun quickFixes(): List<QuickFix> {
        val f = currentFile.value ?: return emptyList()
        val name = f.name
        if (!name.endsWith(".subl") && !name.endsWith(".subs")) return emptyList()
        val analysis = analysis() ?: return emptyList()
        return runCatching {
            analysis.updateDocument(name, loadTextFor(f))
            analysis.codeActions(name).map { a ->
                QuickFix(
                    a.title,
                    a.edit?.changes?.get(name)?.map { it.range to it.newText } ?: emptyList(),
                    a.command?.command,
                )
            }
        }.getOrDefault(emptyList())
    }

    private val logFile: File by lazy { File(Engine.root(getApplication()), "logs/ide.log") }

    // 输入桥失败入日志（定位「点确定无响应」用）
    private fun mdLogBridge(tag: String, extra: String) {
        main.post { buildLog.add("**[输入桥]** " + tag + " " + extra) }
    }

    private fun appendLogFile(md: String) {
        io.execute {
            runCatching {
                logFile.parentFile?.mkdirs()
                if (logFile.length() > 1L shl 20) {  // 超 1MB 保留尾部 256KB
                    val tail = logFile.readText().takeLast(1 shl 18)
                    logFile.writeText(tail)
                }
                logFile.appendText("[" + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + "] " + md + "\n")
            }
        }
    }

    /** 设置页 IDE 日志：初始载入落盘文件尾部的记录 */
    fun ensureIdeLogLoaded() {
        if (ideLog.isNotEmpty() || !logFile.isFile) return
        runCatching { ideLog.addAll(logFile.readText().takeLast(1 shl 16).lines().filter { it.isNotBlank() }) }
    }

    fun clearIdeLog() {
        ideLog.clear()
        io.execute { runCatching { logFile.delete() } }
    }

    /** 跳转请求：>=0 表示有待跳转的行，EditorScreen 消费后置 -1 */
    var pendingGotoLine by mutableIntStateOf(-1)
        private set
    fun requestGoto(line: Int) { pendingGotoLine = line.coerceAtLeast(0) }
    fun consumeGoto() { pendingGotoLine = -1 }

    /** 保证 demo 工程存在且是最新示例；旧脚本版种子自动迁移为编译模式版。 */
    private fun seedDemo() {
        val projects = ProjectStore.projectsRoot(getApplication())
        val demo = File(projects, "demo").apply { mkdirs() }
        val current = File(demo, "hello.subl")
        if (current.exists()) return
        val legacy = File(demo, "hello.subs")
        if (legacy.exists()) {
            // 内容是旧脚本版才替换；被用户改动过的原样保留
            if (legacy.readText() == OLD_SCRIPT_DEMO) legacy.delete()
            else legacy.renameTo(current)
        }
        if (!current.exists()) current.writeText(DEMO_SOURCE)
        refreshProjects()
    }

    companion object {
        private const val MAX_LSP_SOURCE = 40_000

        private const val DEMO_SOURCE = """// Sublimation 编译示例 —— 点右上角「运行」编译成 JS 并执行
fun main() {
  unchecked print("Hello, Sublimation!")
  var add = { x -> x + 1 }
  unchecked print(add(41))
  var p = (3, 4)
  var (a, b) = p
  unchecked print(a + b)
}
"""
        // 脚本模式时期的旧种子内容（检测到就迁移为新的编译模式示例）
        private const val OLD_SCRIPT_DEMO = """// Sublimation 脚本示例 —— 点右上角「运行」试试
var xs = listOf(1, 2, 3)
print("Hello, Sublimation!")
print(xs.map({ x => x + 1 }).size())
print(xs.isEmpty())
"""

        private const val SCRIPT_DEMO_SOURCE = """// Sublimation 脚本示例 —— 顶层语句直接执行，无需 main
print("Hello, Sublimation!")
print(40 + 2)
var name = "CandyIDE"
print("从 " + name + " 运行")
""" 
    }
}

/** agent 聊天消息 */
data class ChatMessage(val role: Role, val text: String) {
    enum class Role { USER, MODEL, TOOL }
}
