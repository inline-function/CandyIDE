package candy.ide.jsapk

import rikka.shizuku.Shizuku
import java.io.File

/**
 * Shizuku 的 newProcess 在编译期工件里是 private（hidden-api 存根），运行时 binder 给的实例可用，
 * 用反射拿到。
 */
object ShizukuShell {

    /** 执行命令，返回 stdout（空则 stderr）。stdin 不可写。 */
    fun exec(cmd: Array<String>): String? = runCatching {
        val proc = obtain(cmd) ?: return null
        val out = proc.inputStream.bufferedReader().use { it.readText().trim() }
        val err = proc.errorStream.bufferedReader().use { it.readText().trim() }
        proc.waitFor()
        if (out.isNotEmpty()) out else err
    }.getOrNull()

    /** 流式安装：pm install -S <size> 从 stdin 读 APK，绕开 shell 用户读不了应用私有目录的限制 */
    fun installStream(apk: candy.ide.fs.CandyPath): String? = runCatching {
        val data = apk.inputStream().use { it.readBytes() }
        val proc = obtain(arrayOf("pm", "install", "-S", data.size.toString())) ?: return null
        proc.outputStream.use { it.write(data); it.flush() }
        val out = proc.inputStream.bufferedReader().use { it.readText().trim() }
        val err = proc.errorStream.bufferedReader().use { it.readText().trim() }
        proc.waitFor()
        if (out.isNotEmpty()) out else err
    }.getOrNull()

    private fun obtain(cmd: Array<String>): Process? = runCatching {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        m.invoke(null, cmd, null, null) as? Process
    }.getOrNull()
}
