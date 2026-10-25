package candy.ide.desktop

import java.util.prefs.Preferences

/** 桌面端运行模式：Auto=优先 Node 跑编译产物，回落解释器 */
enum class RunMode { Auto, Node, Interpreter }

/** 桌面端设置（Windows 注册表式存储：java.util.prefs → HKCU\Software\JavaSoft\Prefs） */
object Settings {
    private const val DEFAULT = ""
    private val prefs = Preferences.userRoot().node("candy/ide/desktop")

    /** 工程根目录：默认 %APPDATA%\CandyIDE\projects */
    var workspaceRoot: String
        get() = prefs.get("workspaceRoot", DEFAULT).ifBlank { defaultWorkspace().absolutePath }
        set(v) = prefs.put("workspaceRoot", v)

    /** sublimation 引擎根覆盖（空=自动探测） */
    var sublimationRoot: String
        get() = prefs.get("sublimationRoot", DEFAULT)
        set(v) = prefs.put("sublimationRoot", v)

    /** node 可执行路径（空=PATH 探测） */
    var nodePath: String
        get() = prefs.get("nodePath", DEFAULT)
        set(v) = prefs.put("nodePath", v)

    var fontSize: Int
        get() = prefs.getInt("fontSize", 14)
        set(v) = prefs.putInt("fontSize", v.coerceIn(10, 28))

    var runMode: RunMode
        get() = RunMode.valueOf(prefs.get("runMode", RunMode.Auto.name))
        set(v) = prefs.put("runMode", v.name)

    var recentProject: String
        get() = prefs.get("recentProject", DEFAULT)
        set(v) = prefs.put("recentProject", v)

    /** 当前配色方案 id（空=内置浮幻） */
    var schemeId: String
        get() = prefs.get("schemeId", "fuhuan")
        set(v) = prefs.put("schemeId", v)

    // —— AI agent 配置（OpenAI 兼容端点；与安卓端同默认值）——
    var agentEnabled: Boolean
        get() = prefs.getBoolean("agentEnabled", true)
        set(v) = prefs.putBoolean("agentEnabled", v)

    var agentScope: String
        get() = prefs.get("agentScope", "project")
        set(v) = prefs.put("agentScope", v)

    var agentBaseUrl: String
        get() = prefs.get("agentBaseUrl", "https://discovery-api.intern-ai.org.cn/v1")
        set(v) = prefs.put("agentBaseUrl", v)

    var agentModel: String
        get() = prefs.get("agentModel", "Atria-Dawn-Preview")
        set(v) = prefs.put("agentModel", v)

    var agentKey: String
        get() = prefs.get("agentKey", "sk-fc709e80739f2ed067823f527cdedc560550948f9f7d70b7743beef5ad4e4f64")
        set(v) = prefs.put("agentKey", v)

    var agentPrompt: String
        get() = prefs.get("agentPrompt", DEFAULT)
        set(v) = prefs.put("agentPrompt", v)

    var agentTemperature: Float
        get() = prefs.getFloat("agentTemperature", 1.0f)
        set(v) = prefs.putFloat("agentTemperature", v.coerceIn(0f, 1f))

    var agentStream: Boolean
        get() = prefs.getBoolean("agentStream", true)
        set(v) = prefs.putBoolean("agentStream", v)

    /** git 提交身份（对应安卓端 Settings.gitUserName/Email） */
    var gitUserName: String
        get() = prefs.get("gitUserName", "CandyIDE Desktop")
        set(v) = prefs.put("gitUserName", v)

    var gitUserEmail: String
        get() = prefs.get("gitUserEmail", "candy@desktop.local")
        set(v) = prefs.put("gitUserEmail", v)

    /** 自动换行（对齐安卓端 Settings.wordWrap） */
    var wordWrap: Boolean
        get() = prefs.getBoolean("wordWrap", false)
        set(v) = prefs.putBoolean("wordWrap", v)

    /** git：修改文件自动提交（对齐安卓端 Settings.gitAutoCommit） */
    var gitAutoCommit: Boolean
        get() = prefs.getBoolean("gitAutoCommit", false)
        set(v) = prefs.putBoolean("gitAutoCommit", v)

    /** git：进入工程自动拉取；开启自动提交时一并推送（对齐安卓端 Settings.gitAutoSync） */
    var gitAutoSync: Boolean
        get() = prefs.getBoolean("gitAutoSync", false)
        set(v) = prefs.putBoolean("gitAutoSync", v)

    fun snapshot(): List<Pair<String, String>> = subMap()

    private fun subMap(): List<Pair<String, String>> = listOf(
        "workspaceRoot" to workspaceRoot,
        "sublimationRoot" to sublimationRoot,
        "nodePath" to nodePath,
        "fontSize" to fontSize.toString(),
        "runMode" to runMode.name,
    )
}

private fun defaultWorkspace(): java.io.File {
    val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
    return java.io.File(appData, "CandyIDE/projects")
}
