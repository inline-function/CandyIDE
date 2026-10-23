package candy.ide.jsapk

import android.content.Context
import candy.ide.engine.Engine
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * 原生安卓工程（SfA 链路）打包器：壳模板 APK（SfA 源码内置构建，WebView + DshBridge，
 * Kotlin 源不变）+ 注入 assets/app.js → v1 + v2 签名。
 *
 * 端上不跑 JVM 工具链（普通 Android 进程无 java/d8/apksigner/bash/python3）：
 * 壳 dex 与资源随模板内置，变的只有 app.js；签名默认用内置证书，可在 app.json 里换自己的 jks。
 */
object AndroidApkBuilder {
    private const val ASSET_TEMPLATE = "sdk/nativeapk/template.apk"
    /** 模板版本：壳 dex / 资源 / 依赖更新后 +1，旧缓存自动作废 */
    private const val TEMPLATE_VERSION = 2
    /** 模板 manifest 的原包名：与设备上已装的 SfA demo 同包名，重打要换种子避开 */
    private const val TEMPLATE_PKG = "functor.sublimation.android"
    /** 模板 manifest 的原应用名：替换 label 的字符串池键 */
    private const val TEMPLATE_LABEL = "Sublimation"

    /** 工程的打包配置（app.json，都可省）
     *  - pkgSeed：包名种子 → functor.sublimation.<pkgSeed>；空则用工程目录名
     *  - label：应用名（替换 manifest 的 android:label）
     *  - versionCode / versionName：版本
     *  - icon：启动器图标，工程内相对路径（任意尺寸 png，按密度桶塞进 mipmap）
     *  - keystore / storePass / keyPass：自定义签名（jks）；空则用内置证书 */
    data class ApkConfig(
        val pkgSeed: String = "",
        val label: String = "",
        val versionCode: Int = 1,
        val versionName: String = "1.0",
        val icon: String = "",
        val keystore: String = "",
        val storePass: String = "",
        val keyPass: String = "",
    )

    /** 从工程根读 app.json；缺文件 / 解析失败都走全默认 */
    fun loadConfig(project: candy.ide.fs.CandyPath): ApkConfig = runCatching {
        val f = project.find("app.json") ?: return@runCatching ApkConfig()
        val j = JSONObject(f.readText())
        ApkConfig(
            pkgSeed = j.optString("pkgSeed", ""),
            label = j.optString("label", ""),
            versionCode = j.optInt("versionCode", 1),
            versionName = j.optString("versionName", "1.0"),
            icon = j.optString("icon", ""),
            keystore = j.optString("keystore", ""),
            storePass = j.optString("storePass", ""),
            keyPass = j.optString("keyPass", ""),
        )
    }.getOrDefault(ApkConfig())

    /**
     * 把编译产物 JS 装进壳模板：
     * - boot/tail 包装与 ProcessorApi.buildAndroidApk 对齐（__dshBoot、main() 启动）
     * - 包名替换为 functor.sublimation.<pkgSeed>（空则用工程目录名）
     * - 应用名 / 版本号 / 启动器图标按 app.json 替换
     * - 签名：app.json 指定 jks 就用它，否则内置证书；v1 + v2 一起打
     * - 输出 <工程>/build/app.apk
     */
    fun build(
        ctx: Context,
        js: ByteArray,
        project: candy.ide.fs.CandyPath,
        config: ApkConfig = loadConfig(project),
    ): candy.ide.fs.CandyPath {
        ApkSigner.attach(ctx.applicationContext)
        ApkSigner.attachCache(ctx.cacheDir)

        // 自定义签名（先于打包装载，v1/v2 都走它；失败回内置证书并留痕）
        var signNote = ""
        if (config.keystore.isNotBlank()) {
            val kf = project.find(config.keystore)
            if (kf == null) signNote = "签名文件不存在，用内置证书: " + config.keystore
            else {
                val err = ApkSigner.loadJks(kf, (if (config.keyPass.isNotBlank()) config.keyPass else config.storePass).toCharArray())
                if (err != null) { ApkSigner.useCustomKey(null, null); signNote = err + "，用内置证书" }
            }
        } else ApkSigner.useCustomKey(null, null)

        // 模板每次重新解压：app 更新带来新壳时旧缓存不残留
        val tmpDir = File(Engine.root(ctx), "sdk/nativeapk")
        tmpDir.mkdirs()
        val tmp = File(tmpDir, "template-$TEMPLATE_VERSION.apk")
        ctx.assets.open(ASSET_TEMPLATE).use { ins -> tmp.outputStream().use { out -> ins.copyTo(out) } }

        val boot = "try{__dshBoot();}catch(e){}\n"
        val tail = "\ntry{ if (typeof main === 'function') main(); }catch(e){ if(__dshAndroid) __dshAndroid('log',[String(e),3]); }\n"
        val jsAsset = (boot + js.toString(Charsets.UTF_8) + tail).toByteArray(Charsets.UTF_8)

        // 收集条目：跳过 META-INF 与模板自带的 assets/app.js
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

        // ---- manifest / 资源改写 ----
        // 包名：模板包名字符串池替换 + arsc 包名 patch；标签同理Strings 池里的 "Sublimation"。
        // 版本：versionCode(0x0101021b) / versionName(0x0101021c) 是框架 attr，走 intEdits/stringEdits。
        val pkg = "functor.sublimation." + (config.pkgSeed.ifBlank { asciiSeed(project) })
        val manifestEdits = HashMap<String, String>()
        if (pkg != TEMPLATE_PKG) {
            manifestEdits[TEMPLATE_PKG] = pkg
            // 派生串必须一起改：provider authorities 与自定义权限要求全局唯一，
            // 不改则两个工程装出同名 authority（INSTALL_FAILED_CONFLICTING_PROVIDER）；
            // 组件类名（.MainActivity 等）不能动——dex 里的类仍在模板包下
            manifestEdits[TEMPLATE_PKG + ".androidx-startup"] = pkg + ".androidx-startup"
            manifestEdits[TEMPLATE_PKG + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"] =
                pkg + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        }
        if (config.label.isNotBlank() && config.label != TEMPLATE_LABEL) manifestEdits[TEMPLATE_LABEL] = config.label
        if (config.versionName != "1.0") manifestEdits["1.0"] = config.versionName
        val intEdits = HashMap<String, Int>()
        intEdits["versionCode"] = config.versionCode
        // R+ 的系统更严格，compileSdk/targetSdk 降到 29 让 v1 也能装载兜底
        intEdits["compileSdkVersion"] = 29
        intEdits["targetSdkVersion"] = 29
        // 启动器图标：指定 icon 就按密度桶塞进模板的 mipmap 条目（名字不变，arsc 资源 id 不变）
        var iconNote = ""
        if (config.icon.isNotBlank()) {
            val iconFile = project.find(config.icon)
            if (iconFile == null) iconNote = "图标文件不存在: " + config.icon
            else {
                val png = iconFile.inputStream().use { it.readBytes() }
                val buckets = listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
                for ((density, _) in buckets) {
                    val name = "res/mipmap-$density-v4/ic_launcher.png"
                    val i = entries.indexOfFirst { it.first == name }
                    if (i >= 0) entries[i] = name to png
                }
            }
        }
        for (i in entries.indices) {
            val en = entries[i]
            if (en.first == "AndroidManifest.xml") {
                val patched = runCatching {
                    AxmlEditor.patchManifest(en.second, manifestEdits, intEdits)
                }.getOrElse { runCatching {
                    AxmlEditor.patchManifest(en.second, manifestEdits, mapOf("compileSdkVersion" to 29))
                }.getOrElse {
                    AxmlEditor.patchManifest(en.second, manifestEdits)
                } }
                entries[i] = en.first to patched
            } else if (en.first == "resources.arsc" && pkg != TEMPLATE_PKG) {
                entries[i] = en.first to en.second.also { AxmlEditor.patchArscPackage(it, pkg) }
            }
        }

        // 输出并签名：SAF 下 build/ 不一定可写，兜底落工程根（与 JS-apk 同策略）
        val outDir = project.find("build") ?: project.createDirectory("build")
        val apk = try {
            (outDir ?: project).child("app.apk")
        } catch (e: Exception) {
            project.child("app.apk")
        }
        if (signNote.isNotBlank()) android.util.Log.w("CandyApk", signNote)
        if (iconNote.isNotBlank()) android.util.Log.w("CandyApk", iconNote)
        ApkSigner.signV1(apk, entries)
        // v2 块必装：Android 11+（含 MagicOS）没有 v2 块的 APK 直接判无签名
        ApkSigner.signV2(apk)
        return apk
    }

    /** 工程目录名转 ASCII 种子：非字母数字字符去掉 */
    private fun asciiSeed(project: candy.ide.fs.CandyPath): String =
        project.name.toCharArray().filter { it.isLetterOrDigit() }.joinToString("").lowercase().ifBlank { "app" }
}