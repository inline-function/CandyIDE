package candy.ide.desktop.plugin.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.plugin.PluginHost
import candy.ide.desktop.plugin.contrib.ProjectSettingsSectionContribution
import candy.ide.desktop.plugin.host.DesktopPluginHost
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import java.io.File

/**
 * jsapk 工程的工程设置分区（桌面端裁剪替代安卓端 project-settings-section:jsapk 的打包配置）：
 * 安卓端这里是 jsapk.json 打包参数配置；桌面端不做打包，改为能力说明——
 * 告知 jsapk 工程在 Windows 端可编辑/可诊断/可预览/可按 JS 运行，APK 打包需安卓端。
 */
class JsApkInfoSection : ProjectSettingsSectionContribution {
    override val id: String = "jsapk-info"
    override val sortKey: Int = 100

    override fun appliesTo(host: PluginHost, dir: File): Boolean =
        host.projectTypeOf(dir) == DesktopAndroidProjectType.TYPE_ID

    @Composable
    override fun Content(host: PluginHost, dir: File) {
        val h = host as? DesktopPluginHost
        Surface(
            color = CandySurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("APK 打包（安卓端专属）", color = CandyText, fontSize = 13.sp)
                Spacer(Modifier.size(4.dp))
                Text(
                    "Windows 端不支持 jsapk 打包链（模板注入、v2 签名、Shizuku 静默安装为安卓端能力）。\n" +
                    "桌面端可继续编辑该工程：实时诊断、Ctrl+Shift+P 布局预览、编译后由 Node 运行。\n" +
                    "需要 APK 时，把工程导出归档（.capr），在安卓端 CandyIDE 导入打开即可打包。",
                    color = CandyTextDim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "安卓标准库与注解处理器已随 android-project 插件提供（编辑期支持）。",
                    color = CandyTextDim,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
