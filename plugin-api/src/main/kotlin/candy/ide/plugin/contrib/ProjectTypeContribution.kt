package candy.ide.plugin.contrib

import candy.ide.fs.CandyPath

/**
 * 工程类型贡献点：插件以此接管一种工程类型（candy.project 里 type= 的取值）。
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
    fun scaffold(dir: CandyPath, name: String)

    /** 无类型标记时按内容认领工程（true = 归本类型；宿主在内置推断之前询问） */
    fun claimsProject(dir: CandyPath): Boolean

    /** 短按「运行」（如 jsapk：有 jsapk.json 走 JS 控制台，否则进虚拟桥接环境） */
    fun runShort(host: candy.ide.plugin.PluginHost)

    /** 打包 APK（长按「运行」、工程设置页、工程列表触发） */
    fun packageProject(host: candy.ide.plugin.PluginHost, dir: CandyPath)
}
