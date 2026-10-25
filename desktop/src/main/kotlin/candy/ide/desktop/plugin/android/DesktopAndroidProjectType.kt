package candy.ide.desktop.plugin.android

import candy.ide.desktop.plugin.PluginHost
import candy.ide.desktop.plugin.contrib.ProjectTypeContribution
import java.io.File

/**
 * 工程类型贡献：jsapk（安卓工程）——桌面端裁剪版（对齐安卓端 candy.plugin.android.project.AndroidProjectType）。
 *
 * 桌面端平台差异（相对安卓端）：
 *  - runShort：一律按 JS 控制台运行（编译 → Node）；安卓端的 jsapk.json 控制台判定与
 *    虚拟桥接环境分支不适用（桌面端无 Virt 全屏，布局预览并入编辑器右侧栏）；
 *  - packageProject：Windows 端不做 APK 打包（无 jsapk 模板 / 签名 / Shizuku 安装链），
 *    给出说明并指引用安卓端 CandyIDE。
 * 通用部分与安卓端一致：scaffold 骨架（@composable 函数 + layout 挂载）、
 * claimsProject 内容认领（源码出现 @composable/@android 即认领）。
 */
class DesktopAndroidProjectType : ProjectTypeContribution {

    override val typeId: String = TYPE_ID

    override fun displayName(): String = "安卓工程"

    override fun description(): String =
        "@composable 视图 + @android 内建；桌面端可编辑、实时诊断与布局预览，按 JS 控制台运行（APK 打包请用安卓端 CandyIDE）"

    override fun scaffold(dir: File, name: String) {
        File(dir, "main.subl").writeText(JSAPK_TEMPLATE)
    }

    /** 无类型标记时按源码内容认领：.subl 里出现 @composable/@android 即安卓工程（与安卓端同判定） */
    override fun claimsProject(dir: File): Boolean = runCatching {
        dir.walkTopDown().any { f ->
            f.isFile && f.name.endsWith(".subl") && runCatching { f.readText() }
                .getOrNull().orEmpty().let { it.contains("@composable") || it.contains("@android") }
        }
    }.getOrDefault(false)

    /** 短按运行：桌面端一律 JS 控制台（编译 → Node 执行） */
    override fun runShort(host: PluginHost) {
        host.runCurrentAsJsConsole()
    }

    /** 打包 APK：Windows 端不可用——说明并指引用安卓端 */
    override fun packageProject(host: PluginHost, dir: File) {
        host.log("**[打包]** Windows 端不支持 APK 打包：jsapk 打包链（模板注入 + v2 签名 + Shizuku 安装）只在安卓端 CandyIDE 提供。")
        host.log("**[打包]** 桌面端可继续编辑与调试该工程：编译为 JS 后由 Node 运行，布局预览见 Ctrl+Shift+P。")
        host.toast("Windows 端不支持 APK 打包（请用安卓端 CandyIDE）")
    }

    companion object {
        const val TYPE_ID = "jsapk"

        // 新建安卓工程的最小骨架：一个 @composable 函数 + layout 挂载（与安卓端 JSAPK_TEMPLATE 同内容）
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
