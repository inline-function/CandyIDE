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
import candy.ide.ui.theme.CandyTextDim
import candy.ide.ui.theme.CandyPrimary
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

enum class Screen { Projects, Editor, Settings, Scheme, ProjectSettings, IdeLog, Developers, Terminal, Agent, Git, ProjectLinks, OpenSource, Stdlib, Virt }

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
    // Termux RUN_COMMAND 运行时权限申请：Termux 已装但未授权时弹系统授权框，
    // 授权后立即刷新协同状态（git 就绪检查依赖它）
    val runCmdLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result["com.termux.permission.RUN_COMMAND"] == true) {
            vm.refreshCompanions(ctx)
        }
    }
    LaunchedEffect(vm.termuxInstalled.value) {
        if (vm.termuxInstalled.value && !candy.ide.terminal.TerminalBridge.hasRunCommandGrant(ctx)) {
            runCmdLauncher.launch(arrayOf("com.termux.permission.RUN_COMMAND"))
        }
        if (vm.termuxInstalled.value) vm.probeRunCommand(ctx)
    }

    // ---- 协同应用检测（首次使用）----
    var companionDismissed by remember { mutableStateOf(Settings.companionDialogShown) }
    var gitInitDeferred by remember { mutableStateOf(candy.ide.data.Settings.gitInitDeferred) }

    // 进出活动时刷新协同应用安装状态（对话框里的叉/勾跟着更新）
    val lifecycleOwner2 = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner2) {
        vm.refreshCompanions(ctx)
        val observer2 = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshCompanions(ctx)
        }
        lifecycleOwner2.lifecycle.addObserver(observer2)
        onDispose { lifecycleOwner2.lifecycle.removeObserver(observer2) }
    }

    val workspaceReady = !pendingFirstUse
    val needCompanion = workspaceReady && !companionDismissed &&
        !(vm.shizukuInstalled.value && vm.termuxInstalled.value)
    if (needCompanion) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("尚未安装协同应用") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CandyIDE 需要协同应用才能提供终端与 git 能力。安装后回到 IDE 会自动更新状态。", fontSize = 12.sp)
                    CompanionRow(
                        name = "Shizuku",
                        url = "https://shizuku.rikka.app/download",
                        installed = vm.shizukuInstalled.value,
                        onOpen = { vm.openUrl(ctx, "https://shizuku.rikka.app/download") },
                    )
                    CompanionRow(
                        name = "Termux",
                        url = "https://f-droid.org/packages/com.termux/",
                        installed = vm.termuxInstalled.value,
                        onOpen = { vm.openUrl(ctx, "https://f-droid.org/packages/com.termux/") },
                    )
                    // Termux 已装但 RUN_COMMAND 通道未通（默认不允许外部应用调用）
                    if (vm.termuxInstalled.value && !vm.runCmdReady.value) {
                        Surface(
                            color = candy.ide.ui.theme.CandySurfaceVariant,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp)
                                    .clickable { vm.openTermuxExternalSetup(ctx) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("开启 Termux 外部调用", color = CandyText, fontSize = 13.sp)
                                    Text(
                                        "Termux 默认不允许外部应用执行命令。命令已复制，点击跳转 Termux 粘贴执行一次，之后再回车一次即可。",
                                        color = CandyTextDim, fontSize = 10.sp,
                                    )
                                }
                                Text("去开启", color = CandyPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    Settings.companionDialogShown = true
                    Settings.persist(ctx)
                    companionDismissed = true
                }) { Text("稍后") }
            },
        )
    }

    // git 初始化引导：Termux 已装但 git 未装（首进工程 / 检测刷新发现刚装好 Termux）
    // git 初始化引导：只在 RUN_COMMAND 通道通（能真判定 git 装没装）且确实未装时弹；
    // 通道没通时根本无法检测，不问（避免「Termux 尚未授权」被误报成「尚未初始化 git」）
    LaunchedEffect(vm.termuxInstalled.value, vm.gitReady.value, vm.runCmdReady.value) {
        if (workspaceReady && vm.termuxInstalled.value && vm.runCmdReady.value && !vm.gitReady.value &&
            !gitInitDeferred && !candy.ide.data.Settings.gitInitDeferred &&
            !vm.gitInitDialog.value && !vm.gitInstallNeeded.value
        ) {
            vm.gitInitDialog.value = true
        }
    }
    // 通道未通：引导开启 Termux 外部调用（命令已复制，跳过去粘贴执行一次）
    if (vm.termuxSetupNeeded.value) {
        AlertDialog(
            onDismissRequest = { vm.termuxSetupNeeded.value = false },
            title = { Text("需要开启 Termux 外部调用") },
            text = {
                Text("Termux 默认不允许其它应用执行命令，且需要它在运行。\n若已执行过开启命令，可能是 Termux 没在运行——点击「去开启」打开 Termux 一次再回来。\n首次开启：粘贴执行命令（系统询问存储权限时同意），之后再回车一次即可。\n\ntermux-setup-storage && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.termuxSetupNeeded.value = false
                    vm.openTermuxExternalSetup(ctx)
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = { vm.termuxSetupNeeded.value = false }) { Text("稍后") }
            },
        )
    }
    if (vm.gitInstallNeeded.value) {
        AlertDialog(
            onDismissRequest = { vm.gitInstallNeeded.value = false },
            title = { Text("尚未安装 git") },
            text = { Text("git 操作需要 Termux 里的 git。要现在安装吗？\n（会调用 Termux 执行 pkg install git）") },
            confirmButton = {
                TextButton(onClick = {
                    vm.gitInstallNeeded.value = false
                    vm.installGit(ctx) { ok -> vm.gitResult.value = if (ok) "git 安装成功" else "git 安装失败" }
                }) { Text("安装") }
            },
            dismissButton = {
                TextButton(onClick = { vm.gitInstallNeeded.value = false }) { Text("稍后") }
            },
        )
    }
    if (vm.gitInitDialog.value) {
        AlertDialog(
            onDismissRequest = { vm.gitInitDialog.value = false; gitInitDeferred = true; candy.ide.data.Settings.gitInitDeferred = true; candy.ide.data.Settings.persist(ctx) },
            title = { Text("尚未初始化 git") },
            text = { Text("检测到 Termux 但还没有安装 git，是否初始化 git？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.gitInitDialog.value = false
                    vm.installGit(ctx)
                }) { Text("初始化") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.gitInitDialog.value = false
                    gitInitDeferred = true
                    candy.ide.data.Settings.gitInitDeferred = true
                    candy.ide.data.Settings.persist(ctx)
                }) { Text("稍后") }
            },
        )
    }
    // git 安装/操作的加载框
    if (vm.gitBusy.value) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text(vm.gitMsg.value.ifBlank { "正在处理…" })
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
        Screen.Virt -> candy.ide.ui.VirtScreen(vm)
        Screen.Git -> GitScreen(vm)
        Screen.ProjectLinks -> LinkListScreen(
            vm = vm,
            title = "项目地址",
            links = listOf(
                LinkItem("CandyIDE（本 IDE）", "https://github.com/inline-function/CandyIDE"),
                LinkItem("Sublimation（语言引擎）", "https://github.com/inline-function/Sublimation"),
            ),
        )
        Screen.OpenSource -> LinkListScreen(
            vm = vm,
            title = "开源组件",
            links = listOf(
                LinkItem("SoraEditor", "https://github.com/Rosemoe/sora-editor"),
                LinkItem("commonmark-java", "https://github.com/commonmark/commonmark-java"),
                LinkItem("ColorPicker", "https://github.com/koral--/android-gpuimage"),
                LinkItem("Shizuku", "https://github.com/RikkaApps/Shizuku"),
            ),
        )
        Screen.Stdlib -> StdlibViewerScreen(vm)
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
