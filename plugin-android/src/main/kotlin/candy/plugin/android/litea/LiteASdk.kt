package candy.plugin.android.litea

import candy.ide.plugin.PluginHost
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * LiteASDK 工具链：aarch64 精简安卓工具链（aapt2/d8/apksigner/zipalign + android.jar），
 * 解压到插件私有目录 files/candy/plugins/android-project/litea-sdk（自 :app 迁入）。
 */
object LiteASdk {

    const val PLUGIN_ID = "android-project"

    fun dir(host: PluginHost): File = File(host.pluginDataDir(PLUGIN_ID), "litea-sdk")

    fun ready(host: PluginHost): Boolean = runCatching {
        val d = dir(host)
        File(d, "bin/aapt2").isFile && File(d, "lib/android.jar").isFile && File(d, "build").isDirectory
    }.getOrDefault(false)

    /** 从 SAF Uri 导入（长按「导入」行走系统文件选择器） */
    fun importFromUri(host: PluginHost, uri: android.net.Uri, onResult: (String) -> Unit) {
        host.runOnIo {
            runCatching {
                unzip(host, dir(host)) { host.context.contentResolver.openInputStream(uri) }
            }.onSuccess { msg ->
                host.logUser("**[工具链]** " + msg)
                host.postMain { onResult(msg) }
            }.onFailure { e ->
                val msg = "导入失败：" + (e.message ?: e.javaClass.simpleName)
                host.log("**[工具链导入失败]** " + msg)
                host.postMain { onResult(msg) }
            }
        }
    }

    /** 从本地路径导入（SAF 被厂商文件管理器劫持时的备选入口） */
    fun importFromPath(host: PluginHost, path: String, onResult: (String) -> Unit) {
        val src = java.io.File(path)
        if (!src.isFile) {
            val msg = "文件不存在：" + path
            host.log("**[工具链导入失败]** " + msg)
            onResult(msg)
            return
        }
        host.runOnIo {
            runCatching {
                unzip(host, dir(host)) { src.inputStream() }
            }.onSuccess { msg ->
                host.logUser("**[工具链]** " + msg)
                host.postMain { onResult(msg) }
            }.onFailure { e ->
                val msg = "导入失败：" + (e.message ?: e.javaClass.simpleName)
                host.log("**[工具链导入失败]** " + msg)
                host.postMain { onResult(msg) }
            }
        }
    }

    /** 解压到插件私有目录；返回结果消息（成功/缺关键文件），IO 错误抛出 */
    private fun unzip(host: PluginHost, target: java.io.File, open: () -> java.io.InputStream?): String {
        target.deleteRecursively()
        target.mkdirs()
        val base = target.canonicalPath
        var entries = 0
        open()?.use { ins ->
            java.util.zip.ZipInputStream(java.io.BufferedInputStream(ins)).use { zis ->
                var ze = zis.nextEntry
                while (ze != null) {
                    if (!ze.isDirectory) {
                        val out = java.io.File(target, ze.name)
                        if (!out.canonicalPath.startsWith(base)) {
                            zis.closeEntry(); ze = zis.nextEntry; continue
                        }
                        out.parentFile?.mkdirs()
                        out.outputStream().use { o -> zis.copyTo(o) }
                        if (ze.name.startsWith("bin/") ||
                            (ze.name.startsWith("build/") && ze.name.endsWith(".sh"))) {
                            out.setExecutable(true, false)
                        }
                        entries++
                    }
                    zis.closeEntry()
                    ze = zis.nextEntry
                }
            }
        }
        return if (ready(host)) "LiteASDK 已就位（" + entries + " 个文件）：" + target.absolutePath
        else "解压完成但缺少关键文件（bin/aapt2、lib/android.jar、build/）。请确认 zip 是 LiteASDK 官方分发包"
    }
}
