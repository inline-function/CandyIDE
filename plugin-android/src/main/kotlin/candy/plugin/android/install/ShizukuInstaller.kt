package candy.plugin.android.install

import candy.ide.fs.CandyPath
import candy.ide.plugin.contrib.AppInstaller
import candy.plugin.android.jsapk.JsApkBuilder

/** 静默安装通道：Shizuku 流式安装（installViaShizuku 自 :app 迁入） */
class ShizukuInstaller : AppInstaller {
    override val id: String = "shizuku"
    override fun tryInstall(apk: CandyPath, pkg: String?): Pair<Boolean, String> =
        JsApkBuilder.installViaShizuku(apk)
}
