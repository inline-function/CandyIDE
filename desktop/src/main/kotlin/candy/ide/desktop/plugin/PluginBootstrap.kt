package candy.ide.desktop.plugin

import candy.ide.desktop.DesktopEngine
import candy.ide.desktop.IdeState
import candy.ide.desktop.OutputLine
import candy.ide.desktop.plugin.host.DesktopPluginHost
import candy.ide.desktop.plugin.PluginRegistry

/**
 * 插件装载入口（对齐安卓端 CandyViewModel.bootstrap 里的 PluginLoader.loadAll 一节）。
 * 在主窗口启动时由后台线程调用一次（装载失败只记日志，不影响 IDE）。
 * 装载完成后：
 *  1. 把当前工程类型再通知一遍插件（装载晚于 openProject 时补位，否则 SfA 的
 *     android-stdlib 扩展 enabled 保持 false、补全缺安卓函数）；
 *  2. 把已注册的语言服务扩展补注册进 LSP 分析器（lsp 尚未初始化时，其惰性初始化会自取；
 *     单个扩展经 registerLspExtension 直达，与首轮分析时序解耦）。
 */
object PluginBootstrap {

    @Volatile
    private var loaded = false

    fun load(state: IdeState) {
        if (loaded) return
        loaded = true
        val host = DesktopPluginHost(state)
        runCatching { PluginLoader.loadAll(host, BuiltInPluginIndex::resolve) }.onFailure {
            state.log("**[插件]** 装载失败：" + (it.message ?: it.javaClass.simpleName), OutputLine.Kind.Error)
        }
        // 插件晚于 openProject 完成装载时补位：把当前工程类型再通知一遍
        state.currentProject?.let { dir ->
            val curType = candy.ide.desktop.data.ProjectStore.typeOf(dir)
            runCatching {
                PluginRegistry.plugins.forEach { it.instance?.onProjectTypeChanged(curType, dir) }
            }
        }
        DesktopEngine.syncLspExtensions()
    }
}
