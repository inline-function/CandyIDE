package candy.ide.plugin.contrib

import candy.ide.fs.CandyPath

/**
 * 静默安装通道贡献点：宿主的安装 UI 按注册顺序尝试，
 * 通道不可用（via=false）时降级系统安装器。
 */
interface AppInstaller {
    val id: String

    /** 返回 (via, msg)：via=true 表示通道已尝试（msg 为安装结果/原因），via=false 表示通道不可用 */
    fun tryInstall(apk: CandyPath, pkg: String?): Pair<Boolean, String>
}
