import java.io.File
plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    namespace = "candy.plugin.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    // 插件框架 SDK（CandyPlugin/PluginHost/贡献点 + fs + 预览模型）
    api(project(":plugin-api"))
    // 编译链：安卓原生工程的多模块检查 + .subs 脚本注解处理器
    implementation("sugared.functor:compiler:1.1-SNAPSHOT")
    implementation("sugared.functor:annotationproc:1.1-SNAPSHOT")
    // 静默安装通道（Shizuku）
    implementation("dev.rikka.shizuku:api:13.1.5")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
}

// copProcs（自 :app 迁来）：主仓 .subs 安卓注解处理器真源同步进本插件资产目录。
// 真源 = ../sublimation/annotation processer/（t9/t10 已确立），消费方 = 本插件的 assets/android/procs。
val copyProcs by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("../sublimation/annotation processer"))
    include("**/*.subs")
    into(layout.projectDirectory.dir("src/main/assets/candy-plugins/android-project/android/procs"))
}


// 资产路径守卫（t50 修复后的回归网）：插件代码引用的资产路径必须真实存在于 assets 树，
// 防止「资产搬家但常量没跟上」式断链（AndroidAssets.ASSET_PATH 曾因此失效导致整个 setup 抛异常）。
// 资产树根（配置期解析一次）
val pluginAssetsRoot: File = file("src/main/assets/candy-plugins")

// 资产路径守卫（t50 修复后的回归网）：插件代码引用的资产路径必须真实存在于 assets 树，
// 防止「资产搬家但常量没跟上」式断链（AndroidAssets 路径前缀缺失曾导致整个 setup 抛异常、全部贡献未注册）。
val verifyPluginAssets by tasks.registering {
    doLast {
        val required = listOf(
            "android-project/candy-plugin.json",
            "android-project/android/stdlib",
            "android-project/android/procs",
            "sublimation-android/candy-plugin.json",
            "sublimation-game/candy-plugin.json",
            "sublimation-wasm/candy-plugin.json",
        )
        val missing = required.filter { rel -> !File(pluginAssetsRoot, rel).exists() }
        require(missing.isEmpty()) { "插件资产缺失/路径错位：\n" + missing.joinToString("\n") }
    }
}

tasks.configureEach {
    if (name == "preBuild") {
        dependsOn(copyProcs)
        dependsOn(verifyPluginAssets)
    }
}

