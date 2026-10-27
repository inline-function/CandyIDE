package candy.ide.plugin

import android.content.Context
import android.content.SharedPreferences

/**
 * 插件启停状态持久化（files 无关，独立 SharedPreferences「candy_plugins」）：
 * - disabled_plugins：已停用的插件 id 集（下次装载跳过 setup）
 * - uninstalled_plugins：已「卸载」的插件 id 集（内置插件资产无法真正删除，
 *   v1 降级为停用并隐藏卡片，下次构建去掉资产后才是真卸载）
 *
 * 系统插件（[SystemPlugins]）任何写入都被拒绝。
 * [PluginLoader] 装载时读取本库；插件中心 UI 读写本库。
 */
object PluginStateStore {

    private const val PREFS = "candy_plugins"
    private const val KEY_DISABLED = "disabled_plugins"
    private const val KEY_UNINSTALLED = "uninstalled_plugins"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 拿到 Context 后初始化（VM.bootstrap 与 PluginLoader.loadAll 各调一次，幂等） */
    fun ensure(ctx: Context) {
        if (prefs == null) prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun p(): SharedPreferences = prefs ?: throw IllegalStateException("PluginStateStore.ensure 未调用")

    /** 已停用插件 id 集 */
    fun disabledIds(): Set<String> = runCatching { p().getStringSet(KEY_DISABLED, emptySet()) }.getOrNull() ?: emptySet()

    /** 已卸载（隐藏）插件 id 集 */
    fun uninstalledIds(): Set<String> = runCatching { p().getStringSet(KEY_UNINSTALLED, emptySet()) }.getOrNull() ?: emptySet()

    /** 停用 / 重新启用；系统插件拒绝并返回 false */
    fun setDisabled(id: String, on: Boolean): Boolean {
        if (SystemPlugins.isSystemId(id)) return false
        val cur = disabledIds().toMutableSet()
        if (on) cur.add(id) else cur.remove(id)
        p().edit().putStringSet(KEY_DISABLED, cur).apply()
        return true
    }

    /** 卸载（内置插件=停用并隐藏）；系统插件拒绝并返回 false */
    fun setUninstalled(id: String): Boolean {
        if (SystemPlugins.isSystemId(id)) return false
        val cur = uninstalledIds().toMutableSet()
        cur.add(id)
        p().edit().putStringSet(KEY_UNINSTALLED, cur).apply()
        return true
    }

    /** 状态快照（插件中心进入时留底，退出时比对驱动重启提示） */
    data class Snapshot(val disabled: Set<String>, val uninstalled: Set<String>) {
        fun diff(other: Snapshot): Boolean = disabled != other.disabled || uninstalled != other.uninstalled
    }

    fun snapshot(): Snapshot = Snapshot(disabledIds(), uninstalledIds())
}
