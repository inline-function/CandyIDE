package candy.ide.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Properties

/**
 * 编辑器配色方案（与 Android 端 EditorSchemes 同构：token 列表、内置方案、
 * properties 存储格式完全一致，.properties 文件可双端互换）。
 * 方案目录：%APPDATA%\CandyIDE\schemes，与工程目录同级。
 */
data class Scheme(
    val id: String,
    val name: String,
    val builtin: Boolean,
    val colors: Map<String, Int>,
    val flags: Map<String, Int> = emptyMap(),
)

object Schemes {

    /** 可编辑的 token 列表（顺序即设置页显示顺序；与 Android 端一致） */
    val TOKEN_LABELS: List<Pair<String, String>> = listOf(
        "background" to "背景", "text" to "正文",
        "lineNumber" to "行号", "lineDivider" to "行号分割线", "currentLine" to "当前行", "selection" to "选区",
        "keyword" to "关键字", "number" to "数字", "string" to "字符串", "comment" to "注释",
        "function" to "函数名/箭头", "operator" to "运算符", "punctuation" to "标点", "identifier" to "标识符/变量",
        "type" to "类型/结构体/枚举", "parameter" to "形参/字段",
        "annotation" to "注解",
        "decorated" to "@view/@compose 名（强调）",
        "problemError" to "错误下划线", "problemWarning" to "警告下划线", "problemTypo" to "提示下划线",
    )

    /** 内置方案：浮幻（Darcula Contrast 映射，默认生效）+ 默认（保守低饱和） */
    val BUILTINS: List<Scheme> = listOf(
        Scheme(
            id = "fuhuan", name = "浮幻", builtin = true,
            colors = mapOf(
                "background" to 0xFF1E1F22.toInt(),
                "text" to 0xFFA9B7C6.toInt(),
                "lineNumber" to 0xFF546E7A.toInt(),
                "lineDivider" to 0xFF3C3F44.toInt(),
                "currentLine" to 0x33FFFFFF.toInt(),
                "selection" to 0xFF214283.toInt(),
                "keyword" to 0xFFCC7832.toInt(),
                "number" to 0xFFFAFF6D.toInt(),
                "string" to 0xFF6A8759.toInt(),
                "comment" to 0xFF808080.toInt(),
                "function" to 0xFF7393C4.toInt(),
                "operator" to 0xFFFFFFFF.toInt(),
                "punctuation" to 0xFF7F7F7D.toInt(),
                "identifier" to 0xFF6A9BD2.toInt(),
                "annotation" to 0xFF87B1FC.toInt(),
                "decorated" to 0xFFFFD54F.toInt(),
                "type" to 0xFF869D20.toInt(),
                "parameter" to 0xFF0078FF.toInt(),
                "problemError" to 0xFFFF5C8A.toInt(),
                "problemWarning" to 0xFFFFC857.toInt(),
                "problemTypo" to 0xFF8A8494.toInt(),
            ),
            flags = mapOf(
                "keyword" to 1, "number" to 1, "operator" to 1, "function" to 1,
                "annotation" to 3, "decorated" to 1, "punctuation" to 1, "type" to 1, "parameter" to 1,
            ),
        ),
        Scheme(
            id = "default", name = "默认", builtin = true,
            colors = mapOf(
                "background" to 0xFF1A1B26.toInt(),
                "text" to 0xFFC0CAF5.toInt(),
                "lineNumber" to 0xFF4C526D.toInt(),
                "lineDivider" to 0xFF2A2E42.toInt(),
                "currentLine" to 0x1AFFFFFF.toInt(),
                "selection" to 0xFF33467C.toInt(),
                "keyword" to 0xFF7AA2F7.toInt(),
                "number" to 0xFFFF9E64.toInt(),
                "string" to 0xFF9ECE6A.toInt(),
                "comment" to 0xFF565F89.toInt(),
                "function" to 0xFF7DCFFF.toInt(),
                "operator" to 0xFF89DDFF.toInt(),
                "punctuation" to 0xFF9AA5CE.toInt(),
                "identifier" to 0xFF6A9BD2.toInt(),
                "annotation" to 0xFFE0AF68.toInt(),
                "decorated" to 0xFFE0AF68.toInt(),
                "type" to 0xFF9D7CD8.toInt(),
                "parameter" to 0xFFE5C07B.toInt(),
                "problemError" to 0xFFF7768E.toInt(),
                "problemWarning" to 0xFFE0AF68.toInt(),
                "problemTypo" to 0xFF565F89.toInt(),
            ),
            flags = mapOf("keyword" to 1),
        ),
    )

    /** 当前生效方案：State 便于 Compose 追踪 */
    var current by mutableStateOf(Scheme("init", "加载中", true, emptyMap()))
        private set

    /** 启动时按设置里的方案 id 加载 */
    fun loadCurrent() {
        current = byId(Settings.schemeId)
    }

    fun select(id: String) {
        Settings.schemeId = id
        current = byId(id)
    }

    fun colorOf(token: String): Int = current.colors[token] ?: 0xFFBFBFBF.toInt()

    /** 自定义方案目录（与工程目录同级：CandyIDE/schemes） */
    fun dir(): File = File(File(Settings.workspaceRoot).parentFile, "schemes")

    fun listCustom(): List<Scheme> {
        val d = dir()
        if (!d.isDirectory) return emptyList()
        return d.listFiles { f -> f.isFile && f.name.endsWith(".properties") }
            ?.mapNotNull { loadScheme(it) }?.sortedBy { it.name } ?: emptyList()
    }

    fun loadScheme(f: File): Scheme? = runCatching {
        val props = Properties().apply { f.inputStream().use { load(it) } }
        val colors = LinkedHashMap<String, Int>()
        val flags = HashMap<String, Int>()
        for (token in TOKEN_LABELS.map { it.first }) {
            val raw = props.getProperty(token) ?: continue
            val rgb = runCatching { raw.toLong(16).toInt() }.getOrNull() ?: continue
            colors[token] = if (rgb <= 0xFFFFFF) (0xFF000000 or rgb.toLong()).toInt() else rgb
            var flag = 0
            if (props.getProperty(token + ".bold") == "true") flag = flag or 1
            if (props.getProperty(token + ".italic") == "true") flag = flag or 2
            if (flag != 0) flags[token] = flag
        }
        Scheme(f.nameWithoutExtension, props.getProperty("name", f.nameWithoutExtension), false, colors, flags)
    }.getOrNull()

    /** 保存自定义方案 */
    fun saveCustom(scheme: Scheme) {
        val d = dir().apply { mkdirs() }
        val f = File(d, scheme.id + ".properties")
        val props = Properties().apply {
            setProperty("name", scheme.name)
            for ((token, color) in scheme.colors) {
                setProperty(token, Integer.toHexString(color and 0xFFFFFF).padStart(6, '0'))
                val flag = scheme.flags[token] ?: 0
                if (flag and 1 != 0) setProperty(token + ".bold", "true")
                if (flag and 2 != 0) setProperty(token + ".italic", "true")
            }
        }
        f.outputStream().use { props.store(it, "CandyIDE custom color scheme") }
    }

    fun deleteCustom(id: String) {
        val f = File(dir(), id + ".properties")
        if (f.isFile) f.delete()
    }

    fun byId(id: String): Scheme {
        // v0.4 的 darcula 已更名浮幻：旧设置平滑迁移
        val key = if (id == "darcula") "fuhuan" else if (id == "candy") "default" else id
        return BUILTINS.firstOrNull { it.id == key }
            ?: listCustom().firstOrNull { it.id == key }
            ?: BUILTINS[0]
    }
}
