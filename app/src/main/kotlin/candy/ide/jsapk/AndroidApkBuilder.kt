package candy.ide.jsapk

import android.content.Context
import candy.ide.engine.Engine
import java.io.File
import java.util.zip.ZipFile

/**
 * 原生安卓工程（SfA 链路）打包器：壳模板 APK（Windows 上 aapt2/d8/apksigner
 * 预构建，WebView + DshBridge，Kotlin 源不变）+ 注入 assets/app.js → v1 签名。
 *
 * 端上不跑 JVM 工具链（普通 Android 进程无 java/d8/apksigner/bash/python3）：
 * 壳 dex 与资源随模板内置，变的只有 app.js；重签名复用 JS-apk 的内置证书。
 */
object AndroidApkBuilder {
    private const val ASSET_TEMPLATE = "sdk/nativeapk/template.apk"
    private const val TEMPLATE_VERSION = 1
    /** 模板 manifest 的原包名：与设备上已装的 SfA demo 同包名，重打要换种子避开 */
    private const val TEMPLATE_PKG = "functor.sublimation.android"

    /**
     * 把编译产物 JS 装进壳模板：
     * - boot/tail 包装与 ProcessorApi.buildAndroidApk 对齐（__dshBoot、main() 启动）
     * - 包名替换为 functor.sublimation.<pkgSeed>（seed 为空则保留模板包名）
     * - v1 签名 → <工程>/build/app.apk
     */
    fun build(
        ctx: Context,
        js: ByteArray,
        pkgSeed: String,
        project: candy.ide.fs.CandyPath,
    ): candy.ide.fs.CandyPath {
        ApkSigner.attach(ctx.applicationContext)
        ApkSigner.attachCache(ctx.cacheDir)

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

        // manifest 改写：包名（字符串池）+ targetSdk 降到 29。
        // R+（targetSdk≥30）的 APK 必须带 v2 签名块，端上只写 v1；降到 29 后 v1 照常安装。
        // 属性找不到（某些模板可能用别名）时降级：至少把包名替换做了。
        val pkg = if (pkgSeed.isBlank()) TEMPLATE_PKG else "functor.sublimation." + pkgSeed
        val edits = HashMap<String, String>()
        if (pkg != TEMPLATE_PKG) edits[TEMPLATE_PKG] = pkg
        for (i in entries.indices) {
            val en = entries[i]
            if (en.first == "AndroidManifest.xml") {
                // 兜底层级：两个降级键都打不动就只换包名
                val patched = runCatching {
                    AxmlEditor.patchManifest(en.second, edits, mapOf("compileSdkVersion" to 29, "targetSdkVersion" to 29))
                }.getOrElse { runCatching {
                    AxmlEditor.patchManifest(en.second, edits, mapOf("compileSdkVersion" to 29))
                }.getOrElse {
                    AxmlEditor.patchManifest(en.second, edits)
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
        ApkSigner.signV1(apk, entries)
        // v2 块必装：Android 11+（含 MagicOS）没有 v2 块的 APK 直接判无签名
        ApkSigner.signV2(apk)
        return apk
    }
}
