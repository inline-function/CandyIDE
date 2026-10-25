package candy.ide.desktop.plugin.contrib

import java.io.File

/**
 * 静默安装通道贡献点（对齐安卓端 AppInstaller；桌面端为接口对齐保留——
 * Windows 端不做 APK 安装，宿主不提供 requestInstall，也不会有任何插件注册本通道）。
 * 安卓端宿主的安装 UI 按注册顺序尝试：通道不可用（via=false）时降级系统安装器。
 */
interface AppInstaller {
    val id: String

    /** 返回 (via, msg)：via=true 表示通道已尝试（msg 为安装结果/原因），via=false 表示通道不可用 */
    fun tryInstall(apk: File, pkg: String?): Pair<Boolean, String>
}
