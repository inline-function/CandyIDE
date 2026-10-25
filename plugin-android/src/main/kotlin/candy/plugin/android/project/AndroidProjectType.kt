package candy.plugin.android.project

import candy.ide.fs.CandyPath
import candy.ide.plugin.PluginHost
import candy.ide.plugin.contrib.ProjectTypeContribution

/**
 * 工程类型贡献：jsapk（安卓工程）。
 * 宿主把无类型标记工程的推断委托给本贡献（claimsProject：源码用了 @composable/@android 即认领）。
 */
class AndroidProjectType : ProjectTypeContribution {

    override val typeId: String = TYPE_ID

    override fun displayName(): String = "安卓工程"

    override fun description(): String =
        "@composable 视图 + @android 内建，编译为原生 APK；含 jsapk 打包与布局预览"

    override fun scaffold(dir: CandyPath, name: String) {
        dir.child("main.subl").writeText(JSAPK_TEMPLATE)
    }

    /** 无类型标记时按源码内容认领：.subl 里出现 @composable/@android 即安卓工程 */
    override fun claimsProject(dir: CandyPath): Boolean = runCatching {
        dir.walk().any { f ->
            f.isFile() && f.name.endsWith(".subl") && runCatching { f.readText() }
                .getOrNull().orEmpty().let { it.contains("@composable") || it.contains("@android") }
        }
    }.getOrDefault(false)

    /** 短按运行：有 jsapk.json（控制台模式）走 JS 控制台，否则进虚拟桥接环境 */
    override fun runShort(host: PluginHost) {
        val p = host.currentProjectDir()
        if (p?.find("jsapk.json")?.isFile() == true) host.runCurrentAsJsConsole()
        else host.openVirtEnvironment()
    }

    override fun packageProject(host: PluginHost, dir: CandyPath) {
        AndroidPackaging.packageProject(host, dir)
    }

    companion object {
        const val TYPE_ID = "jsapk"

        // 新建安卓工程的最小骨架：一个 @composable 函数 + layout 挂载
        const val JSAPK_TEMPLATE = """@composable
fun content(): Box {
    return Box(modifier = Plain()) {
        Text(text = "hello", size = 16)
    }
}

fun main(): Null {
    onCreate { unchecked log("started", 1) }
    onStart { layout(content) }
}
"""
    }
}
