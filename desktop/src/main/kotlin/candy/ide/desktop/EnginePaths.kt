package candy.ide.desktop

import java.io.File

/**
 * sublimation 引擎定位器。
 * 查找顺序：显式设置 → cwd 向上逐级探测（含 CandyIDE/desktop 的 ../..）→ classpath 标准库。
 */
object EnginePaths {
    private val MARKERS = listOf("frontend", "compiler", "interpreter", "core")

    val sublimationRoot: File? by lazy {
        // 候选：工作目录向上找含 frontend/compiler/interpreter 的目录
        var cur = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            if (MARKERS.all { m -> File(cur, m).exists() }) return@lazy cur
            // CandyIDE/desktop → CandyIDE → KotlinProject（sublimation 在 KotlinProject 下）
            listOf(File(cur, "../sublimation"), File(cur, "sublimation"), File(cur, "../KotlinProject/sublimation")).forEach { c ->
                val a = c.absoluteFile
                if (a.isDirectory && MARKERS.all { m -> File(a, m).exists() }) return@lazy a
            }
            cur = cur.parentFile ?: return@lazy null
        }
        null
    }

    val coreDir: File get() = File(sublimationRoot ?: File("."), "core")
}
