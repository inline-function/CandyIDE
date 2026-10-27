import java.io.File

// CandyIDE 桌面端（Windows）：Compose Multiplatform 单 JVM 目标。
// 与 Android 端（:app）共用：
//   1. sublimation 语言引擎（includeBuild("../sublimation") 的 frontend/compiler/interpreter/language-service）
//   2. app 模块中平台无关的 Kotlin 源码（构建期 sync 进本模块，不修改 app 任何文件）
//   3. app 的内置文档资产（assets/builtin）与 sublimation/core 标准库（构建期 sync 进资源）
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("org.jetbrains.compose") version "1.7.3"
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}
kotlin { jvmToolchain(17) }

// ───────── 与 Android 端共享（只读引用，绝不改动 app 模块） ─────────

/** app 中平台无关的源码：Compose 包名在 JB Compose 下通用，commonmark 引擎亦多平台 */
val syncSharedSources by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("app/src/main/kotlin")) {
        include(
            "candy/ide/ui/theme/Color.kt",
            "candy/ide/ui/theme/Theme.kt",
            "candy/ide/md/Markdown.kt",
            "candy/ide/data/DiagItem.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/sharedSrc/kotlin"))
}

/** app 内置文档（语言 wiki / IDE 使用文档）：Markdown 资产共享 */
val syncBuiltinDocs by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("app/src/main/assets/builtin")) {
        include("**/*.md")
    }
    into(layout.buildDirectory.dir("generated/resources/builtin"))
}

/** 标准库：与 :app 的 copyStdlib 同源（../sublimation/core），供解释器/LSP 装载 */
val syncStdlib by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("../sublimation/core")) {
        include("**/*.subl", "**/*.settings")
    }
    into(layout.buildDirectory.dir("generated/resources/stdlib"))
}

/** 生成文档/标准库索引（classpath 无法列目录；打包后也能查看） */
val genIndexes by tasks.registering {
    val builtinSrc = rootProject.layout.projectDirectory.dir("app/src/main/assets/builtin")
    val coreSrc = rootProject.layout.projectDirectory.dir("../sublimation/core")
    val out = layout.buildDirectory.dir("generated/resources")
    outputs.dir(out)
    doLast {
        val d = out.get().asFile
        d.mkdirs()
        fun walk(base: File, exts: Set<String>): List<String> = base.walkTopDown()
            .filter { it.isFile && it.extension in exts }
            .map { it.relativeTo(base).path.replace('\\', '/') }
            .sorted().toList()
        File(d, "builtin-index.txt").writeText(
            walk(builtinSrc.asFile, setOf("md")).joinToString("\n"), Charsets.UTF_8)
        File(d, "stdlib-index.txt").writeText(
            walk(coreSrc.asFile, setOf("subl", "settings")).joinToString("\n"), Charsets.UTF_8)
    }
}

/** 插件资产根（构建期生成：android-project 插件的 stdlib/procs 资产 + 插件索引） */
val candyPluginsGen = layout.buildDirectory.dir("generated/resources/candy-plugins")

/**
 * android-project 插件资产（android/stdlib + android/procs）：自 :plugin-android 模块资产同步——
 * 与 :app 的 copyStdlib/copyProcs 同源；只取桌面端编辑期所需的安卓标准库与注解处理器，
 * sdk/ 打包模板（jsapk/md/nativeapk）不带（桌面端不做 APK 打包）。
 */
val syncAndroidPluginAssets by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("plugin-android/src/main/assets/candy-plugins/android-project")) {
        include("android/stdlib/**", "android/procs/**")
    }
    into(candyPluginsGen.map { it.dir("android-project") })
    // 该资产目录是 :plugin-android:copyProcs 的输出（stdlib/procs 写入插件资产），显式声明依赖避免隐式依赖校验失败
    dependsOn(":plugin-android:copyProcs")
}

/**
 * 插件索引（与 genIndexes 同理：classpath 无法列目录）：plugin-index.txt（插件 id 清单）+
 * 各插件的 assets-index.txt（插件资产相对路径清单，供运行期 materializeAssets 按索引释放）。
 * 扫描两个来源：src/main/resources/candy-plugins（手写清单）与上面同步生成的资产目录。
 */
val genPluginIndex by tasks.registering {
    val srcPlugins = layout.projectDirectory.dir("src/main/resources/candy-plugins")
    val genPlugins = candyPluginsGen
    dependsOn(syncAndroidPluginAssets)
    doLast {
        val s = srcPlugins.asFile
        val g = genPlugins.get().asFile
        val ids = sortedSetOf<String>()
        fun scanIds(root: File) {
            (root.listFiles { f -> f.isDirectory } ?: emptyArray()).forEach { pd ->
                if (File(pd, "candy-plugin.json").isFile) ids.add(pd.name)
            }
        }
        scanIds(s); scanIds(g)
        for (id in ids) {
            val lines = mutableListOf<String>()
            fun collect(root: File) {
                val pd = File(root, id)
                if (!pd.isDirectory) return
                pd.walkTopDown()
                    .filter { it.isFile && it.name != "candy-plugin.json" && it.name != "assets-index.txt" }
                    .forEach { lines += it.relativeTo(pd).path.replace('\\', '/') }
            }
            collect(s); collect(g)
            val idx = File(g, "$id/assets-index.txt")
            idx.parentFile?.mkdirs()
            idx.writeText(lines.sorted().joinToString("\n"), Charsets.UTF_8)
        }
        File(g, "plugin-index.txt").writeText(ids.joinToString("\n"), Charsets.UTF_8)
    }
}

sourceSets {
    val main by getting {
        kotlin.srcDir(syncSharedSources.map { it.destinationDir })
        resources.srcDir(syncBuiltinDocs.map { it.destinationDir })
        resources.srcDir(syncStdlib.map { it.destinationDir })
        resources.srcDir(genIndexes.map { it.outputs.files.files.first() })
        resources.srcDir(candyPluginsGen)
    }
}

tasks.configureEach {
    if (name == "processResources") dependsOn(genPluginIndex)
}

dependencies {
    // Compose Desktop（Skia 渲染，无需 JVM Swing/AWT 外观）
    // JB Compose 桌面平台胞包（runtime/ui/foundation/material）
    implementation("org.jetbrains.compose.desktop:desktop:1.7.3")
    // Skiko 原生渲染（Windows x64；skiko-awt 主 jar 不含 .dll，需显式平台 runtime artifact）
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.18")
    implementation("org.jetbrains.compose.material3:material3:1.7.3")
    // Material 图标（与 Android 端同套 Icons.Default.*）
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // 语言引擎：复合构建来自 ../sublimation（与 :app 同源，改 sublimation 立即生效）
    implementation("sugared.functor:frontend:1.1-SNAPSHOT")
    implementation("sugared.functor:compiler:1.1-SNAPSHOT")
    implementation("sugared.functor:interpreter:1.1-SNAPSHOT")
    implementation("sugared.functor:language-service:1.1-SNAPSHOT")

    // 共享 Markdown.kt 的解析引擎（与 :app 同版本）
    implementation("org.commonmark:commonmark:0.30.1")
    // org.json：agent 请求负载与会话存档（与安卓端同 API）
    implementation("org.json:json:20240303")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
    // Compose Desktop UI 测试（runComposeUiTest / onNodeWith* / performClick）
    testImplementation("org.jetbrains.compose.ui:ui-test-desktop:1.7.3")
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4:1.7.3")
}

compose.desktop {
    application {
        mainClass = "candy.ide.desktop.MainKt"
        nativeDistributions {
            // AppImage = 免安装目录（含 CandyIDE.exe），不依赖 WiX；MSI/EXE 安装包需本机装 WiX
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
            )
            packageName = "CandyIDE"
            packageVersion = "1.0.0"
            description = "Sublimation（凝华）语言桌面 IDE"
            copyright = "© CandyIDE"
            windows {
                packageName = "CandyIDE"
                menuGroup = "CandyIDE"
                // 与 Android 端 ic_launcher 同源的 exe 图标
                iconFile.set(file("icon.ico"))
                upgradeUuid = "B1E3F2A0-5C7E-4D9A-B6E4-2F8D1C0A7B93"
            }
        }
    }
}
