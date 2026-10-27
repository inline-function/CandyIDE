package candy.ide.desktop.plugin

import java.util.prefs.Preferences

/**
 * 插件启停状态持久化（对齐安卓端 candy.ide.plugin.PluginStateStore，Preferences 落盘）：
 * - disabled_plugins：已停用的插件 id 集（逗号分隔；下次装载跳过 setup）
 * - uninstalled_plugins：已「卸载」的插件 id 集（v1 = 停用并隐藏卡片）
 * 系统插件（[SystemPlugins]）写入一律拒绝。
 */
object PluginStateStore {

    private val prefs = Preferences.userRoot().node("candy/ide/desktop/plugins")

    fun disabledIds(): Set<String> =
        runCatching { prefs.get("disabled_plugins", "") }.getOrDefault("")
            .split(",").filter { it.isNotEmpty() }.toSet()

    fun uninstalledIds(): Set<String> =
        runCatching { prefs.get("uninstalled_plugins", "") }.getOrDefault("")
            .split(",").filter { it.isNotEmpty() }.toSet()

    /** 停用 / 重新启用；系统插件拒绝并返回 false */
    fun setDisabled(id: String, on: Boolean): Boolean {
        if (SystemPlugins.isSystemId(id)) return false
        val cur = disabledIds().toMutableSet()
        if (on) cur.add(id) else cur.remove(id)
        prefs.put("disabled_plugins", cur.joinToString(","))
        return true
    }

    /** 卸载（内置插件=停用并隐藏）；系统插件拒绝并返回 false */
    fun setUninstalled(id: String): Boolean {
        if (SystemPlugins.isSystemId(id)) return false
        val cur = uninstalledIds().toMutableSet()
        cur.add(id)
        prefs.put("uninstalled_plugins", cur.joinToString(","))
        return true
    }

    data class Snapshot(val disabled: Set<String>, val uninstalled: Set<String>) {
        fun diff(other: Snapshot): Boolean = disabled != other.disabled || uninstalled != other.uninstalled
    }

    fun snapshot(): Snapshot = Snapshot(disabledIds(), uninstalledIds())
}
