package candy.ide.jsapk

import android.content.Context
import candy.ide.engine.Engine
import rikka.shizuku.Shizuku
import java.io.File
import java.util.zip.ZipFile


/** jsapk.json 工程配置：入口、包名、应用名、版本号、图标 */
data class JsApkConfig(
    val entry: String = "hello.subl",
    val pkg: String = DEFAULT_PACKAGE,
    val label: String = "Sublimation 应用",
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val icon: String? = null,
    /** 自定义签名证书：工程内 .jks 相对路径（相对工程根）；null=内置证书 */
    val certPath: String? = null,
    val certPass: String? = null,
) {
    companion object {
        const val DEFAULT_PACKAGE = "candy.jsapk.runtime"

        /** 读工程目录里的 jsapk.json；缺字段走默认 */
        fun load(project: File): JsApkConfig {
            val f = java.io.File(project, "jsapk.json")
            if (!f.isFile) return JsApkConfig()
            return runCatching {
                val j = org.json.JSONObject(f.readText())
                JsApkConfig(
                    entry = j.optString("entry", "hello.subl").ifBlank { "hello.subl" },
                    pkg = j.optString("package", DEFAULT_PACKAGE).ifBlank { DEFAULT_PACKAGE },
                    label = j.optString("label", "Sublimation 应用").ifBlank { "Sublimation 应用" },
                    versionCode = j.optInt("versionCode", 1).coerceAtLeast(1),
                    versionName = j.optString("versionName", "1.0").ifBlank { "1.0" },
                    icon = j.optString("icon").ifBlank { null },
                    certPath = j.optString("certPath").ifBlank { null },
                    certPass = j.optString("certPass").ifBlank { null },
                )
            }.getOrDefault(JsApkConfig())
        }

        /** 落盘（非法包名返回 false） */
        fun save(project: File, cfg: JsApkConfig): Boolean {
            if (!AxmlEditor.validPackage(cfg.pkg)) return false
            val j = org.json.JSONObject()
            j.put("entry", cfg.entry)
            j.put("package", cfg.pkg)
            j.put("label", cfg.label)
            j.put("versionCode", cfg.versionCode)
            j.put("versionName", cfg.versionName)
            if (cfg.icon != null) j.put("icon", cfg.icon)
            if (cfg.certPath != null) j.put("certPath", cfg.certPath)
            if (cfg.certPass != null) j.put("certPass", cfg.certPass)
            java.io.File(project, "jsapk.json").writeText(j.toString(2))
            return true
        }
    }
}

/**
 * JS-apk 打包链路：
 *   模板 APK（assets/sdk/jsapk/template.apk）→ 注入 assets/app.js（用户 .subl 编译出的 JS）
 *   → v1 签名 → 输出到 <工程>/build/app.apk。
 *
 * 每个工程按各自 jsapk.json 的包名/应用名/版本改写模板（AxmlEditor 改写
 * 二进制 manifest 与 resources.arsc 的包名字段），再签名。
 */
object JsApkBuilder {

    private const val ASSET_TEMPLATE = "sdk/jsapk/template.apk"
    private const val TEMPLATE_VERSION = 3

    /** 编译入口文件并打包：返回产物路径 */
    fun build(ctx: Context, entryFile: File, cfg: JsApkConfig, onCompiled: ((Long) -> Unit)? = null): File {
        ApkSigner.attach(ctx.applicationContext)

        // 1) 编译 .subl → JS
        val js = Engine.compileToJs(ctx, entryFile, onCompiled)
        if (js.isBlank()) throw RuntimeException("编译结果为空")
        val jsAsset = js.toByteArray(Charsets.UTF_8)

        // 2) 读模板
        // 每次都重新解模板：app 更新带来新模板时旧缓存才不会残留
        val tmp = File(Engine.root(ctx), "sdk/jsapk/template-$TEMPLATE_VERSION.apk")
        tmp.parentFile?.mkdirs()
        ctx.assets.open(ASSET_TEMPLATE).use { input -> tmp.outputStream().use { input.copyTo(it) } }

        // 3) 收集条目：跳过模板自带的 META-INF 与 placeholder 的 assets/app.js
        val entries = ArrayList<Pair<String, ByteArray>>()
        ZipFile(tmp).use { zf ->
            val seen = HashSet<String>()
            val it = zf.entries()
            while (it.hasMoreElements()) {
                val ze = it.nextElement()
                val name = ze.name
                if (name.startsWith("META-INF/")) continue
                if (name == "assets/app.js") continue
                if (!seen.add(name)) continue
                entries.add(name to zf.getInputStream(ze).use { s -> s.readBytes() })
            }
        }
        entries.add("assets/app.js" to jsAsset)

        // 4) 按工程配置改写二进制段（包名 / 应用名 / 版本号 / 版本名）
        val samePkg = cfg.pkg == JsApkConfig.DEFAULT_PACKAGE &&
            cfg.label == "Sublimation 应用" && cfg.versionCode == 1 && cfg.versionName == "1.0"
        if (!samePkg) {
            val edits = HashMap<String, String>()
            edits[JsApkConfig.DEFAULT_PACKAGE] = cfg.pkg
            edits["1.0"] = cfg.versionName
            edits["Sublimation 应用"] = cfg.label
            for (i in entries.indices) {
                val en = entries[i]
                if (en.first == "AndroidManifest.xml") {
                    entries[i] = en.first to AxmlEditor.patchManifest(en.second, edits, mapOf("versionCode" to cfg.versionCode))
                } else if (en.first == "resources.arsc") {
                    AxmlEditor.patchArscPackage(en.second, cfg.pkg)
                }
            }
        }

        // 4b) 自定义图标：替换模板里的图像资源。aapt2 把 res 路径压缩成 res/xx.png 的短名，
        // 当前模板的 res png 全部是启动图标（4 密度 × launcher/round），整体替换即可；
        // 模板若新增别的 drawable，需要按 arsc 精确匹配再改这里。
        val iconFile = cfg.icon?.let { File(entryFile.parentFile, it) }
        if (iconFile != null && iconFile.isFile) {
            val iconBytes = iconFile.readBytes()
            for (i in entries.indices) {
                val n = entries[i].first
                if (n.startsWith("res/") && n.endsWith(".png")) entries[i] = n to iconBytes
            }
        }

        // 5) 输出目录 + 重写并签名
        val outDir = File(entryFile.parentFile, "build")
        if (!outDir.exists()) outDir.mkdirs()
        val apk = File(outDir, "app.apk")
        if (apk.exists()) apk.delete()
        tmp.copyTo(apk, overwrite = true)
        ApkSigner.signV1(apk, entries)
        return apk
    }

    /** 用 Shizuku 以 shell 身份流式静默安装；返回(是否走 Shizuku, 输出) */
    fun installViaShizuku(apk: File): Pair<Boolean, String> {
        if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return false to "未连接 Shizuku"
        val out = ShizukuShell.installStream(apk) ?: return false to "Shizuku 接口不可用"
        return true to (if (out.isEmpty()) "已安装" else out)
    }
}
