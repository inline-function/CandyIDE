package candy.ide.desktop.plugin

/**
 * 系统级插件保护（SfA / SfG / SfW 三个平台插件不可停用、不可卸载、不可修改）。
 * 与安卓端 candy.ide.plugin.SystemPlugins 同构：双轨判定（清单声明 "system": true + 硬名单）。
 */
object SystemPlugins {

    val IDS: Set<String> = setOf(
        "sublimation-android",   // SfA
        "sublimation-game",      // SfG
        "sublimation-wasm",      // SfW
    )

    fun isSystem(manifest: PluginManifest): Boolean = manifest.system || manifest.id in IDS

    fun isSystemId(id: String): Boolean = id in IDS
}
