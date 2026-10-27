package candy.ide.plugin

/**
 * 系统级插件保护（SfA / SfG / SfW 三个平台插件不可停用、不可卸载、不可修改）。
 *
 * 判定双轨：清单可显式声明 "system": true；宿主侧另有硬性名单兜底
 * （名单内的 id 即使清单漏标也按系统插件处理）。UI 依此隐藏卡片三点菜单；
 * [PluginStateStore] 的停用/卸载写入对系统插件直接拒绝。
 */
object SystemPlugins {

    /** 硬性系统插件名单：平台级插件（随平台演进而非用户可换） */
    val IDS: Set<String> = setOf(
        "sublimation-android",   // SfA：APK 编译目标与 Android 标准库
        "sublimation-game",      // SfG：游戏库目标
        "sublimation-wasm",      // SfW：WASM 目标
    )

    /** 是否系统插件：清单声明或名单命中皆算 */
    fun isSystem(manifest: PluginManifest): Boolean = manifest.system || manifest.id in IDS

    fun isSystemId(id: String): Boolean = id in IDS
}
