package candy.ide.desktop.plugin

import java.io.File

/**
 * CandyIDE 插件（桌面宿主侧，v1 =「随 desktop 编译的内置插件」；对齐安卓端 candy.ide.plugin.CandyPlugin）。
 *
 * 生命周期：
 *  1. [PluginLoader] 读 candy-plugins/plugin-index.txt 得到插件 id，再读各插件清单；
 *  2. 宿主用内置索引（BuiltInPluginIndex）按 manifest.id 解析到本接口的实现；
 *  3. 在 IO 线程调用 [setup]——插件经 [PluginHost] 注册贡献点、物化插件资产；
 *  4. 工程打开时收到 [onProjectTypeChanged] 通知（如按工程类型启停语言服务扩展）。
 *
 * 与安卓端的差异仅在于路径抽象：桌面端统一用 java.io.File（无 SAF/Context）。
 */
interface CandyPlugin {
    val manifest: PluginManifest

    /** 装配（IO 线程）：注册贡献点、物化插件资产。抛异常仅记录到 IDE 日志，不阻断其它插件 */
    fun setup(host: PluginHost)

    /** 当前工程类型变化（打开工程时触发）；typeId 为 candy.project 里 type= 的取值 */
    fun onProjectTypeChanged(typeId: String, dir: File?) {}

    /** 卸载（v1 预留：内置插件无法卸载，仅回收资源） */
    fun dispose(host: PluginHost) {}
}
