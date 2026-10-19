package candy.ide.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.vm.CandyViewModel

enum class Screen { Projects, Editor, Settings, Scheme, ProjectSettings, IdeLog, Developers }

@Composable
fun CandyApp(vm: CandyViewModel = viewModel()) {
    // 外部文件接收结果（jks/caco/capr）
    vm.ingestResult.value?.let { res ->
        AlertDialog(
            onDismissRequest = { vm.ingestResult.value = null },
            confirmButton = {
                TextButton(onClick = { vm.ingestResult.value = null }) { Text("知道了") }
            },
            title = { Text("接收文件") },
            text = { Text(res) },
        )
    }
    if (vm.ingesting.value) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("正在接收文件…")
                }
            },
        )
    }
    LaunchedEffect(Unit) {
        vm.bootstrap()
    }
    when (vm.screen.value) {
        Screen.Projects -> ProjectsScreen(vm)
        Screen.Editor -> EditorScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.Scheme -> SchemeScreen(vm)
        Screen.ProjectSettings -> vm.settingsProject.value?.let { ProjectSettingsScreen(vm, it) }
        Screen.Developers -> DevelopersScreen(vm)
        Screen.IdeLog -> IdeLogScreen(vm)
    }

    // 全局安装反馈：等待框 / 成功「是否打开」/ 失败提示
    val ctx = LocalContext.current
    // 轻量提示：显示 2 秒后自动清空
    val toastMsg = vm.toast.value
    if (toastMsg.isNotBlank()) {
        LaunchedEffect(toastMsg) {
            kotlinx.coroutines.delay(2000)
            if (vm.toast.value == toastMsg) vm.toast.value = ""
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { vm.toast.value = "" }, properties = androidx.compose.ui.window.DialogProperties(false, false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CandySurfaceVariant,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            ) {
                Text(toastMsg, color = CandyText, modifier = Modifier.padding(14.dp))
            }
        }
    }
    if (vm.archiving.value) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("正在压缩工程…")
                }
            },
        )
    }
    if (vm.installing.value) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("正在安装…")
                }
            },
        )
    }
    vm.installedPkg.value?.let { pkg ->
        AlertDialog(
            onDismissRequest = { vm.installedPkg.value = null },
            title = { Text("安装成功") },
            text = { Text("安装完成，是否立即打开？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.installedPkg.value = null
                    val launch = runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
                    if (launch != null) ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else android.widget.Toast.makeText(ctx, "已安装，可在桌面查看", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("打开") }
            },
            dismissButton = { TextButton(onClick = { vm.installedPkg.value = null }) { Text("稍后") } },
        )
    }
    vm.installError.value?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.installError.value = null },
            title = { Text("安装失败") },
            text = { Text("Shizuku 不可用：$msg\n请在设置里连接 Shizuku，或用其它应用安装。") },
            confirmButton = { TextButton(onClick = { vm.installError.value = null }) { Text("确定") } },
        )
    }
}
