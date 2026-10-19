plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.1.20"
}

android {
    namespace = "candy.jsapk.runtime"
    compileSdk = 35

    defaultConfig {
        applicationId = "candy.jsapk.runtime"
        minSdk = 26
        targetSdk = 29       // v1（JAR）签名在高版本 targetSdk 上会被拒，模板固定 29
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // 模板是「壳」：v1 签名兼容性与 Play 无关，关掉会 abort 的 lint 检查
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
