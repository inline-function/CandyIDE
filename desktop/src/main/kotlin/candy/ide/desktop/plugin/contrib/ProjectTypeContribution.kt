package candy.ide.desktop.plugin.contrib

import candy.ide.desktop.plugin.PluginHost
import java.io.File

/**
 * 工程类型贡献点（对齐安卓端，路径抽象换为 java.io.File）：
 * 插件以此接管一种工程类型（candy.project 里 type= 的取值）。
 * 宿主内置「JS 平台 / 脚本 / MD」三类；插件可新增类型（如 android-project 的 jsapk）。
 */
interface ProjectTypeContribution {

    /** 类型 id（candy.project 的 type 值，如 "jsapk"） */
    val typeId: String

    /** 显示名（编辑器标题、工程卡片） */
    fun displayName(): String

    /** 新建工程对话框里的说明行 */
    fun description(): String

    /** 新建工程样板文件（工程目录已创建，只写内容） */
    fun scaffold(dir: File, name: String)

    /** 无类型标记时按内容认领工程（true = 归本类型；宿主在内置推断之前询问） */
    fun claimsProject(dir: File): Boolean

    /** 短按「运行」（桌面端 android-project：一律按 JS 控制台运行；安卓端有 jsapk.json/虚拟桥接分支） */
    fun runShort(host: PluginHost)

    /** 打包 APK（桌面端 android-project：给出平台不可用说明；安卓端为完整打包链路） */
    fun packageProject(host: PluginHost, dir: File)
}
