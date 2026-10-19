// 根工程：各模块在自己的 build.gradle.kts 里声明插件版本
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
