package candy.ide.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import candy.ide.editor.Schemes
import java.io.File

/**
 * IDE 级设置（工程无关）：配色方案、编辑器字体、字号、自动换行。
 * 存 files/candy/settings.properties；改完立即落盘，开关即时生效。
 */
object Settings {

    @Volatile private var ctx: Context? = null
    private val file: File? get() = ctx?.let { File(candy.ide.engine.Engine.root(it), "settings.properties") }

    var schemeId by mutableStateOf("fuhuan")
    var fontSize by mutableIntStateOf(14)       // sp，sora 内部按设备缩放
    var wordWrap by mutableStateOf(false)
    var fontId by mutableStateOf("jetbrains")   // 编辑器字体：jetbrains=内置 / system=系统 / 其它=导入的字体名
    var silentInstall by mutableStateOf(false)   // JS-apk 打包完直接静默安装（跳过「是否安装」对话框）

    // —— 辅助功能（编辑器符号栏；操作窗由顶栏羽毛笔按钮开关）——
    var assistSymbolBar by mutableStateOf(true)   // 符号栏：ASCII + Unicode 两列
    var symbolBarFull by mutableStateOf(false)   // 完全显示符号栏：换行展开 + A/U 切换

    // —— AI agent 配置（OpenAI 兼容端点；ADK 里由 OpenAiModel 消费）——
    var agentEnabled by mutableStateOf(false)   // 悬浮球总开关；关掉不显
    var agentScope by mutableStateOf("project")   // agent 访问权：readonly/project/workspace/full
    var agentBaseUrl by mutableStateOf("https://discovery-api.intern-ai.org.cn/v1")
    var agentModel by mutableStateOf("Atria-Dawn-Preview")
    var agentKey by mutableStateOf("sk-fc709e80739f2ed067823f527cdedc560550948f9f7d70b7743beef5ad4e4f64")
    var agentPrompt by mutableStateOf("")   // 初始提示词；空则用内置默认
var agentTemperature by mutableStateOf(1.0f)   // 采样温度 0~2
var agentStream by mutableStateOf(true)   // 流式输出（SSE）

    // —— git 协同配置 ——
    var gitUserName by mutableStateOf("")
    var gitUserEmail by mutableStateOf("")
    var gitRemote by mutableStateOf("")      // 远程仓库地址（https 或 ssh）
    var gitAutoCommit by mutableStateOf(true)   // 修改文件自动提交（“修改 xx 文件”）
    var gitAutoSync by mutableStateOf(false)    // 自动同步云端：进工程拉取 + 自动提交时推送
var gitInitDeferred by mutableStateOf(false)  // git 初始化引导：点过一次就不再弹（持久化）
    var companionDialogShown by mutableStateOf(false)   // 协同应用对话框已经显示过（点过稍后）

    /** 首次拿到 Context 后加载；同时把当前配色方案从磁盘恢复 */
    fun attach(ctx: Context) {
        if (this.ctx != null) return
        this.ctx = ctx
        val f = file
        // 首次安装没有配置文件：schemeId 走默认值，也必须把 Schemes.current 置成实际方案，
        // 否则编辑器停在「加载中」的空配色上（重装后必须手动切方案就是这个 bug）
        if (f != null && f.isFile) {
            runCatching {
                val props = java.util.Properties().apply { f.inputStream().use { load(it) } }
                props.getProperty("scheme")?.let { schemeId = it }
                props.getProperty("fontSize")?.toIntOrNull()?.let { fontSize = it.coerceIn(10, 30) }
                wordWrap = props.getProperty("wordWrap") == "true"
                props.getProperty("font")?.let { if (it.isNotBlank()) fontId = it }
                silentInstall = props.getProperty("silentInstall") == "true"
                assistSymbolBar = props.getProperty("assistSymbolBar") != "false"
                symbolBarFull = props.getProperty("symbolBarFull") == "true"
                props.getProperty("agentBaseUrl")?.let { if (it.isNotBlank()) agentBaseUrl = it }
                props.getProperty("agentModel")?.let { if (it.isNotBlank()) agentModel = it }
                props.getProperty("agentKey")?.let { if (it.isNotBlank()) agentKey = it }
                agentEnabled = props.getProperty("agentEnabled") == "true"
    props.getProperty("agentScope")?.let { if (it.isNotBlank()) agentScope = it }
    props.getProperty("agentPrompt")?.let { agentPrompt = it }
    props.getProperty("agentTemperature")?.toFloatOrNull()?.let { agentTemperature = it.coerceIn(0f, 1f) }
    props.getProperty("agentStream")?.toBooleanStrictOrNull()?.let { agentStream = it }
    props.getProperty("gitUserName")?.let { gitUserName = it }
    props.getProperty("gitUserEmail")?.let { gitUserEmail = it }
    props.getProperty("gitRemote")?.let { gitRemote = it }
    companionDialogShown = props.getProperty("companionDialogShown") == "true"
    gitAutoCommit = props.getProperty("gitAutoCommit") != "false"
    gitAutoSync = props.getProperty("gitAutoSync") == "true"
    gitInitDeferred = props.getProperty("gitInitDeferred") == "true"
            }
        }
        Schemes.current = Schemes.byId(ctx, schemeId)
    }

    fun persist(ctx: Context) {
        val f = File(candy.ide.engine.Engine.root(ctx), "settings.properties") ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val props = java.util.Properties().apply {
                setProperty("scheme", schemeId)
                setProperty("fontSize", fontSize.toString())
                setProperty("wordWrap", wordWrap.toString())
                setProperty("font", fontId)
                setProperty("silentInstall", silentInstall.toString())
                setProperty("assistSymbolBar", assistSymbolBar.toString())
                setProperty("symbolBarFull", symbolBarFull.toString())
                setProperty("agentBaseUrl", agentBaseUrl)
                setProperty("agentModel", agentModel)
                setProperty("agentKey", agentKey)
                setProperty("agentEnabled", agentEnabled.toString())
        setProperty("agentScope", agentScope)
    setProperty("agentPrompt", agentPrompt)
        setProperty("agentTemperature", agentTemperature.toString())
        setProperty("agentStream", agentStream.toString())
        setProperty("gitUserName", gitUserName)
        setProperty("gitUserEmail", gitUserEmail)
        setProperty("gitRemote", gitRemote)
        setProperty("gitAutoCommit", gitAutoCommit.toString())
        setProperty("gitAutoSync", gitAutoSync.toString())
    setProperty("gitInitDeferred", gitInitDeferred.toString())
        setProperty("companionDialogShown", companionDialogShown.toString())
            }
            props.store(f.outputStream(), "CandyIDE settings")
        }
    }
}
