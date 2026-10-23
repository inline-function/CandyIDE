pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CandyIDE"

include(":app")
include(":jsapk-template")
include(":desktop")

// 复合构建：直接从源码依赖 sublimation 语言实现（改 ../sublimation 立即生效）
includeBuild("../sublimation")
