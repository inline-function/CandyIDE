plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    namespace = "candy.ide"

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
            )
        }
    }
    compileSdk = 35

    defaultConfig {
        applicationId = "candy.ide"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 语言引擎：复合构建来自 ../sublimation（includeBuild 直接消费源码产物）
    implementation("sugared.functor:annotationproc:1.1-SNAPSHOT")
    implementation("sugared.functor:frontend:1.1-SNAPSHOT")
    implementation("sugared.functor:compiler:1.1-SNAPSHOT")
    implementation("sugared.functor:interpreter:1.1-SNAPSHOT")
    implementation("sugared.functor:language-service:1.1-SNAPSHOT")

    // Markdown 解析引擎（Apache-2.0，见 OPEN_SOURCE_NOTICES.md）
    implementation("org.commonmark:commonmark:0.30.1")
    // 取色盘组件（Apache-2.0，见 OPEN_SOURCE_NOTICES.md）
    implementation("com.github.skydoves:colorpicker-compose:1.0.8")
    // Shizuku API（Apache-2.0）：adb/root 级能力的统一入口（见 OPEN_SOURCE_NOTICES.md）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 代码编辑器（LGPL-2.1，见 OPEN_SOURCE_NOTICES.md）
    // 0.24.7 要求 compileSdk 37（AGP 8.7.3 不支持），故用兼容 compileSdk 34/35 的最新 0.23.x
    implementation("io.github.rosemoe:editor:0.23.7")

    // Google ADK for Kotlin（Android AAR）：Agent / Runner / Session / Model 抽象
    implementation("com.google.adk:google-adk-kotlin-core-android:0.2.0")

    // AndroidX / Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// 把 sublimation/core 标准库同步进 assets/stdlib（首启拷到应用私有目录供解释器加载）
val copyStdlib by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("../sublimation/core"))
    include("**/*.subl", "**/*.settings")
    into(layout.projectDirectory.dir("src/main/assets/stdlib"))
}

tasks.configureEach {
    if (name == "preBuild") dependsOn(copyStdlib)
}
