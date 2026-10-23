package candy.ide.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import candy.ide.data.Settings
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File

/** 自定义颜色 id（避开预定义池，见 EditorColorScheme 注释） */
const val STRING_ID = 256
const val DECORATED_ID = 257

/** 语法 token 的可配置项：颜色 + 粗体/斜体标记（1=粗体 2=斜体 3=两者）。 */
data class Scheme(
    val id: String,
    val name: String,
    val builtin: Boolean,
    val colors: Map<String, Int>,
    val flags: Map<String, Int> = emptyMap(),
)

object Schemes {

    /** 可编辑的 token 列表（顺序即设置页显示顺序） */
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

    private val TOKEN_COLOR_IDS: Map<String, Int> = mapOf(
        "background" to EditorColorScheme.WHOLE_BACKGROUND,
        "text" to EditorColorScheme.TEXT_NORMAL,
        "lineNumber" to EditorColorScheme.LINE_NUMBER,
        "lineDivider" to EditorColorScheme.LINE_DIVIDER,
        "currentLine" to EditorColorScheme.CURRENT_LINE,
        "selection" to EditorColorScheme.SELECTION_INSERT,
        "keyword" to EditorColorScheme.KEYWORD,
        "number" to EditorColorScheme.LITERAL,
        "string" to STRING_ID,
        "comment" to EditorColorScheme.COMMENT,
        "function" to EditorColorScheme.FUNCTION_NAME,
        "operator" to EditorColorScheme.OPERATOR,
        "punctuation" to EditorColorScheme.IDENTIFIER_NAME,
        "identifier" to EditorColorScheme.IDENTIFIER_VAR,
        "type" to EditorColorScheme.ATTRIBUTE_NAME,
        "parameter" to EditorColorScheme.ATTRIBUTE_VALUE,
        "annotation" to EditorColorScheme.ANNOTATION,
        "decorated" to DECORATED_ID,
        "problemError" to EditorColorScheme.PROBLEM_ERROR,
        "problemWarning" to EditorColorScheme.PROBLEM_WARNING,
        "problemTypo" to EditorColorScheme.PROBLEM_TYPO,
    )

    /** 内置方案：浮幻（用户设计的 Darcula Contrast 映射，默认生效）+ 默认（保守低饱和） */
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

    /** 当前生效方案：State 便于 Compose 追踪；只在主线程改（select/saveCustom 处） */
    var current by mutableStateOf(Scheme("init", "加载中", true, emptyMap()))

    fun colorOf(token: String): Int = current.colors[token] ?: 0xFFBFBFBF.toInt()

    /** 给分析器用：token → sora TextStyle（颜色 id 指向当前方案注入的颜色） */
    fun styleOfToken(token: String): Long {
        val id = TOKEN_COLOR_IDS[token] ?: EditorColorScheme.TEXT_NORMAL
        val flags = current.flags[token] ?: 0
        return TextStyle.makeStyle(id, 0, flags and 1 != 0, flags and 2 != 0, false)
    }

    /** 应用到编辑器：把当前方案的颜色按 id 注册进 EditorColorScheme。纯赋值，不触发 version。 */
    fun applyTo(editor: CodeEditor) {
        editor.colorScheme = toEditorScheme(current)
        editor.invalidate()
    }

    private fun toEditorScheme(s: Scheme): EditorColorScheme = EditorColorScheme().apply {
        for ((token, id) in TOKEN_COLOR_IDS) setColor(id, s.colors[token] ?: 0xFFBFBFBF.toInt())
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, s.colors["background"] ?: 0)
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, s.colors["selection"] ?: 0)
        setColor(EditorColorScheme.SELECTION_HANDLE, s.colors["selection"] ?: 0)
        setColor(EditorColorScheme.TEXT_SELECTED, 0x44FFFFFF)
        setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, 0xFF241F2E.toInt())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, s.colors["text"] ?: 0)
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, 0xFF9A93A8.toInt())
        setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, s.colors["selection"] ?: 0)
    }

    /** 切换方案（持久化 + 立即生效） */
    fun select(ctx: Context, scheme: Scheme) {
        current = scheme
        Settings.schemeId = scheme.id
        Settings.persist(ctx)
    }

    /** 重置内置方案：丢弃运行时改色，恢复打包时的原始配色 */
    fun resetBuiltin(ctx: Context, id: String) {
        val origin = BUILTINS.firstOrNull { it.id == id } ?: return
        if (Schemes.current.id == id) {
            current = origin
            applyToEditors()
        }
    }

    /** 删除自定义方案 */
    fun deleteCustom(ctx: Context, id: String) {
        File(dir(ctx), "$id.properties").takeIf { it.exists() }?.delete()
    }

    /** 方案变了：通知所有打开的编辑器重新应用配色（version 变化触发重组） */
    private fun applyToEditors() { /* 由 VM 的 schemeVersion 驱动重组；这里只切 current */ }

    /** 自定义方案目录 */
    private fun dir(ctx: Context): File = File(candy.ide.engine.Engine.root(ctx), "schemes")

    fun listCustom(ctx: Context): List<Scheme> {
        val d = dir(ctx)
        if (!d.isDirectory) return emptyList()
        return d.listFiles { f -> f.isFile && f.name.endsWith(".properties") }
            ?.mapNotNull { loadScheme(it) }?.sortedBy { it.name } ?: emptyList()
    }

    fun loadScheme(f: File): Scheme? = runCatching {
        val props = java.util.Properties().apply { f.inputStream().use { load(it) } }
        val colors = LinkedHashMap<String, Int>()
        val flags = HashMap<String, Int>()
        for (token in TOKEN_LABELS.map { it.first }) {
            val raw = props.getProperty(token) ?: continue
            val rgb = runCatching { raw.toLong(16).toInt() }.getOrNull() ?: continue
            colors[token] = if (rgb <= 0xFFFFFF) (0xFF000000 or rgb.toLong()).toInt() else rgb
            var flag = 0
            if (props.getProperty("$token.bold") == "true") flag = flag or 1
            if (props.getProperty("$token.italic") == "true") flag = flag or 2
            if (flag != 0) flags[token] = flag
        }
        Scheme(f.nameWithoutExtension, props.getProperty("name", f.nameWithoutExtension), false, colors, flags)
    }.getOrNull()

    /** 保存自定义方案（名字为空时直接覆盖） */
    fun saveCustom(ctx: Context, scheme: Scheme) {
        val d = dir(ctx).apply { mkdirs() }
        val f = File(d, scheme.id + ".properties")
        val props = java.util.Properties().apply {
            setProperty("name", scheme.name)
            for ((token, color) in scheme.colors) {
                setProperty(token, Integer.toHexString(color and 0xFFFFFF).padStart(6, '0'))
                val flag = scheme.flags[token] ?: 0
                if (flag and 1 != 0) setProperty("$token.bold", "true")
                if (flag and 2 != 0) setProperty("$token.italic", "true")
            }
        }
        props.store(f.outputStream(), "CandyIDE custom color scheme")
    }

    fun byId(ctx: Context, id: String): Scheme {
        // v0.4 的 darcula 已更名浮幻：旧设置平滑迁移
        val key = if (id == "darcula") "fuhuan" else if (id == "candy") "default" else id
        return BUILTINS.firstOrNull { it.id == key }
            ?: listCustom(ctx).firstOrNull { it.id == key }
            ?: BUILTINS[0]
    }
}
