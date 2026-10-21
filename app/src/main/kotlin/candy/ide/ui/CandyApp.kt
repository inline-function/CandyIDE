package candy.ide.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import candy.ide.data.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import candy.ide.fs.CandyFs
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.vm.CandyViewModel

enum class Screen { Projects, Editor, Settings, Scheme, ProjectSettings, IdeLog, Developers, Terminal, Agent, Git }

@Composable
fun CandyApp(vm: CandyViewModel = viewModel()) {
    val ctx = LocalContext.current
    // 首次使用：必须选择一个目录作为 IDE 目录（SAF），否则退出
    var pendingFirstUse by remember { mutableStateOf(!CandyFs.isReady(ctx)) }
    if (pendingFirstUse) {
        val picker = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                CandyFs.setWorkspace(ctx, uri, migrate = true)
                pendingFirstUse = false
            }
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("欢迎使用 CandyIDE") },
            text = {
                Text(
                    "第一次使用需要选择一个目录作为 IDE 目录。\n" +
                    "你可以在存储目录下创建一个名为 CandyIDE 的目录，然后选中它。\n" +
                    "工程文件将保存在这个目录里，可以直接用系统文件管理器查看。",
                )
            },
            confirmButton = {
                TextButton(onClick = { picker.launch(null) }) { Text("同意并选择") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // 不同意：直接退出 IDE
                    candy.ide.MainActivity::class.java.let { _ -> (ctx as? android.app.Activity)?.finish() }
                }) { Text("退出") }
            },
        )
    }
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

    // 活动恢复（从别的 APP/后台回来）：驱动编辑页检测文件是否被外部修改、刷新文件树
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    when (vm.screen.value) {
        Screen.Projects -> ProjectsScreen(vm)
        Screen.Editor -> EditorScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.Scheme -> SchemeScreen(vm)
        Screen.ProjectSettings -> vm.settingsProject.value?.let { ProjectSettingsScreen(vm, it) }
        Screen.Developers -> DevelopersScreen(vm)
        Screen.IdeLog -> IdeLogScreen(vm)
        Screen.Terminal -> TerminalScreen(vm)
        Screen.Agent -> AgentScreen(vm)
        Screen.Git -> GitScreen(vm)
    }

    // 全局安装反馈：等待框 / 成功「是否打开」/ 失败提示
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

@Composable
private fun CompanionRow(name: String, url: String, installed: Boolean, onOpen: () -> Unit) {
    Surface(
        color = candy.ide.ui.theme.CandySurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (installed) "✓" else "✗",
                color = if (installed) candy.ide.ui.theme.CandyPrimary else androidx.compose.ui.graphics.Color(0xFFE5656E),
                fontSize = 16.sp,
            )
            Column(Modifier.weight(1f)) {
                Text(name, color = candy.ide.ui.theme.CandyText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(url, color = candy.ide.ui.theme.CandyTextDim, fontSize = 10.sp)
            }
            Text("打开", color = candy.ide.ui.theme.CandyPrimary, fontSize = 12.sp)
        }
    }
}
