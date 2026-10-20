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

    // —— AI agent 配置（OpenAI 兼容端点；ADK 里由 OpenAiModel 消费）——
    var agentEnabled by mutableStateOf(false)   // 悬浮球总开关；关掉不显
    var agentBaseUrl by mutableStateOf("https://discovery-api.intern-ai.org.cn/v1")
    var agentModel by mutableStateOf("Atria-Dawn-Preview")
    var agentKey by mutableStateOf("sk-fc709e80739f2ed067823f527cdedc560550948f9f7d70b7743beef5ad4e4f64")
    var agentPrompt by mutableStateOf("")   // 初始提示词；空则用内置默认

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
                props.getProperty("agentBaseUrl")?.let { if (it.isNotBlank()) agentBaseUrl = it }
                props.getProperty("agentModel")?.let { if (it.isNotBlank()) agentModel = it }
                props.getProperty("agentKey")?.let { if (it.isNotBlank()) agentKey = it }
                agentEnabled = props.getProperty("agentEnabled") == "true"
    props.getProperty("agentPrompt")?.let { agentPrompt = it }
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
                setProperty("agentBaseUrl", agentBaseUrl)
                setProperty("agentModel", agentModel)
                setProperty("agentKey", agentKey)
                setProperty("agentEnabled", agentEnabled.toString())
    setProperty("agentPrompt", agentPrompt)
            }
            props.store(f.outputStream(), "CandyIDE settings")
        }
    }
}
