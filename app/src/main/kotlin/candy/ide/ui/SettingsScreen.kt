package candy.ide.ui

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import candy.ide.editor.FontManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import candy.ide.data.Settings
import candy.ide.data.ShizukuManager
import candy.ide.terminal.TerminalBridge
import candy.ide.editor.Schemes
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CandyViewModel) {
    BackHandler { vm.goBack() }
    val ctx = LocalContext.current
    var schemeMenu by remember { mutableStateOf(false) }
    var shizukuState by remember { mutableStateOf<ShizukuManager.State?>(null) }
    var agentEnabled by remember { mutableStateOf(Settings.agentEnabled) }
    var agentUri by remember { mutableStateOf(Settings.agentBaseUrl) }
    var agentModel by remember { mutableStateOf(Settings.agentModel) }
    var agentKey by remember { mutableStateOf(Settings.agentKey) }
    var shizukuInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 进出活动计数：驱动各权限开关刷新状态（回来后同步）
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var pendingTermuxForGit by remember { mutableStateOf(false) }
    var pendingInstallGit by remember { mutableStateOf(false) }
    var termuxJustInstalled by remember { mutableStateOf(false) }
    val runCmdPermLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result["com.termux.permission.RUN_COMMAND"] == true) {
            // 授权成功：如果 Termux 已装，直接进入“是否安装 git”
            if (vm.termuxInstalled.value) pendingInstallGit = true
        }
    }
    var pendingReset by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var pendingResetConfirm by remember { mutableStateOf(false) }
    var pendingDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shizukuState = ShizukuManager.state()
        shizukuInfo = ShizukuManager.version()?.let { v -> ShizukuManager.privilegeUid()?.let { u -> v to u } }
    }

    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault("?")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { vm.goBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg)
            )
        },
        containerColor = CandyBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { SectionTitle("编辑器") }
            item {
                var fontMenu by remember { mutableStateOf(false) }
                val fonts = FontManager.listAll(ctx)
                Surface(color = CandySurface, onClick = { fontMenu = true }) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("字体", color = CandyText, modifier = Modifier.weight(1f))
                        Box {
                            Text(
                                fonts.firstOrNull { it.first == Settings.fontId }?.second ?: Settings.fontId,
                                color = CandyTextDim,
                            )
                            DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                                fonts.forEach { (id, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            vm.setFont(id)
                                            fontMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val raw = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "font"
                    val name = FontManager.importFromUri(ctx, uri, raw)
                    if (name != null) {
                        vm.setFont(name)
                        Toast.makeText(ctx, "字体已导入：" + name, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "导入失败，请选 .ttf/.otf 字体文件", Toast.LENGTH_SHORT).show()
                    }
                }
                Surface(color = CandySurface, onClick = { pickFont.launch(arrayOf("font/*", "application/*", "*/*")) }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("导入字体…", color = CandyText, modifier = Modifier.weight(1f))
                        Text(".ttf / .otf", color = CandyTextDim)
                    }
                }
            }
            item {
                Surface(color = CandySurface, onClick = { schemeMenu = true }) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("配色方案", color = CandyText, modifier = Modifier.weight(1f))
                        Box {
                            Text(Schemes.current.name, color = CandyTextDim)
                            DropdownMenu(expanded = schemeMenu, onDismissRequest = { schemeMenu = false }) {
                                vm.schemeIds().forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            vm.setScheme(id)
                                            schemeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Surface(color = CandySurface, onClick = { vm.goScheme() }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("编辑当前方案颜色", color = CandyText, modifier = Modifier.weight(1f))
                        Text("自定义", color = CandyTextDim)
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("字号", color = CandyText, modifier = Modifier.width(64.dp))
                    Slider(
                        value = Settings.fontSize.toFloat(),
                        onValueChange = { vm.setFontSize(it.toInt()) },
                        valueRange = 10f..30f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(Settings.fontSize.toString() + " sp", color = CandyTextDim, modifier = Modifier.width(44.dp))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自动换行", color = CandyText, modifier = Modifier.weight(1f))
                    Switch(checked = Settings.wordWrap, onCheckedChange = { vm.setWordWrap(it) })
                }
            }
            item { SectionTitle("Shizuku 权限") }
            item {
                Surface(color = CandySurface) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("授权 IDE Shizuku 权限以获取更多功能", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text(
                                text = when (shizukuState) {
                                    ShizukuManager.State.RUNNING_GRANTED -> {
                                        val info = shizukuInfo
                                        "已授权 · " + (if (info?.second == 0) "ROOT" else "ADB") +
                                            if (info != null) " · 服务 v" + info.first else ""
                                    }
                                    ShizukuManager.State.RUNNING_NOT_GRANTED -> "已连接，但尚未授权"
                                    ShizukuManager.State.PRE_V11 -> "Shizuku 版本过低（需 v11+）"
                                    ShizukuManager.State.NOT_RUNNING -> "未运行 — 请先安装并启动 Shizuku"
                                    null -> "检测中…"
                                },
                                color = CandyTextDim, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        val state = shizukuState
                        if (state == ShizukuManager.State.NOT_RUNNING || state == ShizukuManager.State.PRE_V11) {
                            TextButton(onClick = {
                                runCatching {
                                    ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        ?.let { ctx.startActivity(it) }
                                }
                            }) { Text("打开 Shizuku") }
                        } else if (state == ShizukuManager.State.RUNNING_NOT_GRANTED) {
                            TextButton(onClick = {
                                runCatching { ShizukuManager.requestPermission(0) { granted ->
                                    if (granted) shizukuState = ShizukuManager.State.RUNNING_GRANTED
                                    else shizukuState = ShizukuManager.state()
                                    shizukuInfo = ShizukuManager.version()?.let { v ->
                                        ShizukuManager.privilegeUid()?.let { u -> v to u }
                                    }
                                } }
                            }) { Text("授予") }
                        } else {
                            IconButton(onClick = {
                                shizukuState = ShizukuManager.state()
                                shizukuInfo = ShizukuManager.version()?.let { v ->
                                    ShizukuManager.privilegeUid()?.let { u -> v to u }
                                }
                            }) { Icon(Icons.Default.Refresh, contentDescription = "重新检测", tint = CandySecondary) }
                        }
                    }
                }
            }
            // Termux RUN_COMMAND 授权（Shizuku 分类下，git/终端能力都依赖它）
            item {
                Surface(color = CandySurface) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Termux 授权", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text(
                                if (!candy.ide.terminal.TerminalBridge.isTermuxInstalled(ctx))
                                    "Termux 未安装（设置 → 伴生软件 下载）"
                                else if (candy.ide.terminal.TerminalBridge.hasRunCommandGrant(ctx))
                                    "已授权 RUN_COMMAND ✓"
                                else "未授权 —— git 与终端的 Termux 通道需要它",
                                color = CandyTextDim, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (candy.ide.terminal.TerminalBridge.isTermuxInstalled(ctx) &&
                            !candy.ide.terminal.TerminalBridge.hasRunCommandGrant(ctx)
                        ) {
                            TextButton(onClick = {
                                runCmdPermLauncher.launch(arrayOf("com.termux.permission.RUN_COMMAND"))
                            }) { Text("请求授权") }
                        }
                    }
                }
            }
            // 外部文件访问权限：跳系统「所有文件访问」设置页，状态即时回读
            item {
                var extGranted by remember(resumeTick) {
                    mutableStateOf(
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                            android.os.Environment.isExternalStorageManager()
                        else
                            ctx.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED,
                    )
                }
                val extLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { extGranted = android.os.Environment.isExternalStorageManager() }
                val permLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    extGranted = result.values.all { it }
                }
                Surface(color = CandySurface) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("开启外部文件访问权限", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text(
                                if (extGranted) "已开启：可读写内部存储的公共目录" else "未开启：只能访问应用沙盒",
                                color = CandyTextDim, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = extGranted,
                            onCheckedChange = { asked ->
                                if (!asked) return@Switch
                                val sdk = android.os.Build.VERSION.SDK_INT
                                if (sdk >= android.os.Build.VERSION_CODES.R) {
                                    // 安卓 11+：跳「所有文件访问」设置页
                                    runCatching {
                                        val intent = android.content.Intent(
                                            "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                                            android.net.Uri.parse("package:" + ctx.packageName),
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        extLauncher.launch(intent)
                                    }
                                } else {
                                    // 安卓 10 及以下：申请普通读写权限
                                    runCatching {
                                        permLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
            // 静默 APK 安装：JS-apk 打包完直接装，跳过确认对话框
            item {
                Surface(color = CandySurface) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("静默 APK 安装", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text(
                                "开：JS-apk 打包成功后经 Shizuku 直接安装；关：弹「是否安装」对话框",
                                color = CandyTextDim, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = Settings.silentInstall,
                            onCheckedChange = {
                                Settings.silentInstall = it
                                Settings.persist(ctx)
                            },
                        )
                    }
                }
            }
            item {
                Text(
                    "Shizuku 已授权后可用于：访问沙盒外文件、执行 shell 级命令等（能力随版本逐步开放）。",
                    color = CandyTextDim, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            // ───────── LiteASDK：subl → APK 端上构建的精简安卓工具链 ─────────
            item {
                val pickSdk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) vm.importLiteASdk(uri)
                }
                val importing by vm.liteaImporting
                val ready = vm.liteaSdkReady()
                var showPathDialog by remember { mutableStateOf(false) }
                Surface(color = CandySurface, onClick = {
                    if (!importing) showPathDialog = true
                }, onLongClick = {
                    // 长按走 SAF 文件选择器（部分厂商选择器会劫持点按）
                    if (!importing) pickSdk.launch(arrayOf("*/*"))
                }) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("导入 LiteASDK 工具链", color = if (ready) CandyText else CandyPrimary)
                            Spacer(Modifier.size(2.dp))
                            Text(
                                text = if (ready) "已导入 · 端上 subl→apk 构建链路可用（再点一次即覆盖重导）" else "aarch64 精简安卓工具链：aapt2/d8/apksigner/zipalign + android.jar，解压到 IDE 私有目录",
                                color = CandyTextDim, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (importing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CandyPrimary)
                        } else {
                            Text(text = if (ready) "重新导入" else "未导入", color = CandyTextDim)
                        }
                    }
                    }
                }
                if (showPathDialog) {
                    var pathInput by remember { mutableStateOf("/sdcard/Download/LiteASDK.zip") }
                    AlertDialog(
                        onDismissRequest = { showPathDialog = false },
                        title = { Text("导入 LiteASDK") },
                        text = {
                            Column {
                                Text(
                                    "填 zip 包在手机上的完整路径（点按走系统文件选择器被劫持时用这）。长按导入行则走系统选择器。",
                                    color = CandyTextDim, style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.size(8.dp))
                                OutlinedTextField(
                                    value = pathInput,
                                    onValueChange = { pathInput = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showPathDialog = false
                                vm.importLiteASdkFromPath(pathInput.trim())
                            }) { Text("导入") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPathDialog = false }) { Text("取消") }
                        },
                    )
                }
            }
            item {
                val msg = vm.liteaImportMsg.value
                if (msg != null) AlertDialog(
                    onDismissRequest = { vm.liteaImportMsg.value = null },
                    title = { Text("LiteASDK") },
                    text = { Text(msg, color = CandyText) },
                    confirmButton = {
                        TextButton(onClick = { vm.liteaImportMsg.value = null }) { Text("知道了") }
                    },
                )
            }
            item { SectionTitle("诊断") }
            item {
                Surface(color = CandySurface, onClick = { vm.goIdeLog() }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("IDE 日志", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("编辑器交互事件；落盘 logs/ide.log（超 1MB 自动截断尾部）", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CandyTextDim)
                    }
                }
            }
            item { SectionTitle("AI agent（ADK）") }
            item {
                Surface(color = CandySurface) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("开启 AGENT 支持", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("开启后编辑页出现悬浮球：点开是小聊天窗，可进全屏对话页", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = agentEnabled,
                            onCheckedChange = {
                                agentEnabled = it
                                Settings.agentEnabled = it
                                Settings.persist(ctx)
                            },
                        )
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = agentUri,
                        onValueChange = { agentUri = it; Settings.agentBaseUrl = it; Settings.persist(ctx) },
                        label = { Text("Base URL", color = CandyTextDim) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = agentModel,
                        onValueChange = { agentModel = it; Settings.agentModel = it; Settings.persist(ctx) },
                        label = { Text("模型", color = CandyTextDim) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = agentKey,
                        onValueChange = { agentKey = it; Settings.agentKey = it; Settings.persist(ctx) },
                        label = { Text("API Key", color = CandyTextDim) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(8.dp))
                    // 采样温度：0~2 拖动条
                    var agentTemp by remember { mutableStateOf(Settings.agentTemperature) }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Temperature", color = CandyTextDim, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(String.format("%.1f", agentTemp), color = CandyPrimary, fontSize = 13.sp)
                        }
                        androidx.compose.material3.Slider(
                            value = agentTemp,
                            onValueChange = { agentTemp = (Math.round(it * 10) / 10f).coerceIn(0f, 1f) },
                            onValueChangeFinished = {
                                Settings.agentTemperature = agentTemp
                                Settings.persist(ctx)
                            },
                            valueRange = 0f..1f,
                            steps = 19,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    // 流式输出：可选项
                    var agentStream by remember { mutableStateOf(Settings.agentStream) }
                    var streamExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(color = CandySurface, onClick = { streamExpanded = true }) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Stream", color = CandyText)
                                    Text(
                                        if (agentStream) "流式输出（SSE，逐字返回）" else "整包返回（等待完整响应）",
                                        color = CandyTextDim, style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CandyTextDim)
                            }
                        }
                        DropdownMenu(expanded = streamExpanded, onDismissRequest = { streamExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("流式输出（SSE）") },
                                onClick = {
                                    agentStream = true
                                    Settings.agentStream = true
                                    Settings.persist(ctx)
                                    streamExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("整包返回") },
                                onClick = {
                                    agentStream = false
                                    Settings.agentStream = false
                                    Settings.persist(ctx)
                                    streamExpanded = false
                                },
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    var agentPrompt by remember { mutableStateOf(Settings.agentPrompt) }
                    OutlinedTextField(
                        value = agentPrompt,
                        onValueChange = { agentPrompt = it; Settings.agentPrompt = it; Settings.persist(ctx) },
                        label = { Text("初始提示词（留空用内置默认）", color = CandyTextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Spacer(Modifier.size(8.dp))
                    // 无障碍权限：agent 后续操作 IDE 界面要用
                    var a11yOn by remember { mutableStateOf(isAccessibilityEnabled(ctx)) }
                    // 从系统设置返回时刷新开关状态
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
                            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                a11yOn = isAccessibilityEnabled(ctx)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    var showA11yHelp by remember { mutableStateOf(false) }
                    Surface(color = CandySurface, onClick = { showA11yHelp = true }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("无障碍权限", color = CandyText)
                                Spacer(Modifier.size(2.dp))
                                Text(
                                    if (a11yOn) "已开启 — agent 可操作 IDE 界面" else "未开启 — 以后 agent 操作界面会用到",
                                    color = CandyTextDim, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = a11yOn, onCheckedChange = { showA11yHelp = true })
                        }
                    }
                    if (showA11yHelp) {
                        AlertDialog(
                            onDismissRequest = { showA11yHelp = false },
                            title = { Text("开启 CandyIDE 无障碍服务") },
                            text = {
                                Column {
                                    Text("这是 CandyIDE 自己的无障碍服务（不是系统的辅助功能），开启步骤：", color = CandyText, fontSize = 13.sp)
                                    Spacer(Modifier.size(6.dp))
                                    Text("1. 点「打开系统设置」进入无障碍页\n2. 找到 CandyIDE 的服务条目（通常在「已下载的服务」/「高级」里）\n3. 打开开关并确认授权\n4. 返回 IDE，状态会自动刷新", color = CandyTextDim, fontSize = 12.sp)
                                    if (a11yOn) {
                                        Spacer(Modifier.size(6.dp))
                                        Text("当前状态：已开启", color = CandyPrimary, fontSize = 12.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showA11yHelp = false
                                    openCandyA11ySettings(ctx)
                                }) { Text("打开系统设置") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showA11yHelp = false
                                    a11yOn = isAccessibilityEnabled(ctx)
                                }) { Text("返回并刷新状态") }
                            },
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                }
            }
            item { SectionTitle("内置工程（只读）") }
            item {
                Surface(color = CandySurface, onClick = { vm.openBuiltinProject(ctx, "lang-doc", "语言文档") }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("语言文档", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("Sublimation 形式化文档 · wiki · 根文档", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = CandyTextDim, modifier = Modifier.size(20.dp))
                    }
                }
            }
            item {
                Surface(color = CandySurface, onClick = { vm.openBuiltinProject(ctx, "ide-doc", "IDE 文档") }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("IDE 文档", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("CandyIDE 使用手册（编辑器 · 终端 · AI 助手）", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Description, contentDescription = null, tint = CandyTextDim, modifier = Modifier.size(20.dp))
                    }
                }
            }
            item { SectionTitle("伴生软件") }
            item {
                Surface(color = CandySurface, onClick = { TerminalBridge.download(ctx, TerminalBridge.Companion.SHIZUKU) }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("下载 Shizuku", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("提供 adb 级能力：终端直连执行、静默安装", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CandySecondary)
                    }
                }
            }
            item {
                Surface(color = CandySurface, onClick = { TerminalBridge.download(ctx, TerminalBridge.Companion.TERMUX) }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("下载 Termux", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("Android 终端模拟器：装 git 、跑 shell（编辑页长按第 4 按钮连接）", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CandySecondary)
                    }
                }
            }
            item { SectionTitle("关于") }
            item { SettingsRow("CandyIDE", "v" + versionName) }
            item { EntryRow("项目地址", "CandyIDE · Sublimation") { vm.goProjectLinks() } }
            item { EntryRow("开源组件", "SoraEditor · commonmark-java · ColorPicker · Shizuku") { vm.goOpenSource() } }

            item {
                Surface(color = CandySurface, onClick = { vm.goDevelopers() }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("开发者", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("QQ / X / B站 / QQ群 —— 点击复制标识", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CandySecondary)
                    }
                }
            }
            item { SectionTitle("git 协同") }
            item {
                Surface(color = CandySurface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("个人信息（提交署名）", color = CandyText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(2.dp))
                        Text("远程仓库地址在编辑页 git 面板里，每个工程独立保存", color = CandyTextDim, fontSize = 11.sp)
                        Spacer(Modifier.size(8.dp))
                        OutlinedTextField(
                            value = Settings.gitUserName,
                            onValueChange = { Settings.gitUserName = it; Settings.persist(ctx) },
                            label = { Text("用户名", color = CandyTextDim) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(8.dp))
                        OutlinedTextField(
                            value = Settings.gitUserEmail,
                            onValueChange = { Settings.gitUserEmail = it; Settings.persist(ctx) },
                            label = { Text("邮箱", color = CandyTextDim) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Surface(color = CandySurface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("修改文件自动提交", color = CandyText)
                                Spacer(Modifier.size(2.dp))
                                Text("保存 / 删除 / 新建时以“修改 xx.xx 文件”的名义自动提交", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = Settings.gitAutoCommit, onCheckedChange = { Settings.gitAutoCommit = it; Settings.persist(ctx) })
                        }
                        Spacer(Modifier.size(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("自动同步云端", color = CandyText)
                                Spacer(Modifier.size(2.dp))
                                Text("进入工程自动拉取；开启自动提交时一并推送到云端（手动推送不受影响）", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = Settings.gitAutoSync, onCheckedChange = { Settings.gitAutoSync = it; Settings.persist(ctx) })
                        }
                    }
                }
            }
            item {
                Surface(color = CandySurface, onClick = {
                    when {
                        vm.gitReady.value -> Toast.makeText(ctx, "您已安装git", Toast.LENGTH_SHORT).show()
                        !vm.termuxInstalled.value -> pendingTermuxForGit = true
                        !candy.ide.terminal.TerminalBridge.hasRunCommandGrant(ctx) -> {
                            // 没授权：提示去「Shizuku 权限」分类请求 Termux 授权
                            Toast.makeText(ctx, "请先请求 Termux 授权（设置 → Shizuku 权限 → Termux 授权），授权后再装 git", Toast.LENGTH_LONG).show()
                        }
                        else -> vm.installGit(ctx) { ok ->
                            if (!ok) Toast.makeText(ctx, "未安装成功，可能 Termux 没在运行", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("安装 git", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text(
                                when {
                                    vm.gitReady.value -> "已安装 ✓"
                                    !vm.termuxInstalled.value -> "需要先安装 Termux"
                                    !candy.ide.terminal.TerminalBridge.hasRunCommandGrant(ctx) ->
                                        "需要先请求 Termux 授权（本页 Shizuku 分类里）"
                                    !vm.runCmdReady.value ->
                                        "已授权 ✓，但 Termux 未在运行或未允许外部调用（点击重试，或先打开 Termux 一次）"
                                    else -> "通过 Termux 安装（pkg install git）"
                                },
                                color = CandyTextDim, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item { SectionTitle("结束") }
            item {
                Surface(color = CandySurface, onClick = { vm.closeIde(ctx) }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("关闭 IDE", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("结束所有页面并杀掉后台进程，下次从桌面打开即为冷启动", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Surface(color = CandySurface, onClick = { pendingReset = true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("重置 IDE", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("删除整个 IDE 目录并清空应用数据（目录授权会保留），相当于卸载重装", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Surface(color = CandySurface, onClick = { pendingDelete = true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("删除 IDE", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("删除 IDE 目录并撤销目录授权，然后跳转系统卸载页面", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
// 安装 git 需要先装 Termux
    if (pendingTermuxForGit) {
        // Termux 装好了：自动关闭并进入"是否安装 git"
        LaunchedEffect(vm.termuxInstalled.value) {
            if (vm.termuxInstalled.value) {
                pendingTermuxForGit = false
                pendingInstallGit = true
            }
        }
        AlertDialog(
            onDismissRequest = { pendingTermuxForGit = false },
            title = { Text("需要先安装 Termux") },
            text = {
                Text("安装 git 需要协同应用 Termux。\n安装后回到 IDE 会自动继续。\n下载页：f-droid.org/packages/com.termux/")
            },
            confirmButton = {
                TextButton(onClick = { vm.openUrl(ctx, "https://f-droid.org/packages/com.termux/") }) { Text("打开下载页") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTermuxForGit = false }) { Text("稍后") }
            },
        )
    }
    // 询问是否安装 git
    if (pendingInstallGit) {
        LaunchedEffect(vm.termuxInstalled.value) {
            if (!vm.termuxInstalled.value) pendingInstallGit = false
        }
        AlertDialog(
            onDismissRequest = { pendingInstallGit = false },
            title = { Text("是否安装 git") },
            text = { Text("将通过 Termux 执行 pkg install git，需要网络。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingInstallGit = false
                    vm.installGit(ctx) { ok ->
                        vm.gitResult.value = if (ok) "git 安装成功" else "git 安装失败"
                    }
                }) { Text("安装") }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstallGit = false }) { Text("稍后") }
            },
        )
    }
    // 重置 IDE：两级确认（第二级需输入 CandyIDE）
    if (pendingReset) {
        var resetInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingReset = false },
            title = { Text("重置 IDE") },
            text = {
                Text("重置IDE会导致IDE当前所处的IDE目录被删除，IDE自身的私有目录也会被删除，仅保留IDE被授予的权限，是否继续？")
            },
            confirmButton = {
                TextButton(onClick = { pendingResetConfirm = true }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { pendingReset = false }) { Text("取消") } },
        )
    }
    if (pendingResetConfirm) {
        var confirmInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingResetConfirm = false },
            title = { Text("最后一步") },
            text = {
                Column {
                    Text("这是最后一步操作，完成之后不可逆，如果您想重置IDE，请输入 CandyIDE，然后点击确定")
                    Spacer(Modifier.size(8.dp))
                    TextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it },
                        singleLine = true,
                        isError = confirmInput.isNotEmpty() && confirmInput != "CandyIDE",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (confirmInput == "CandyIDE") {
                            pendingResetConfirm = false
                            vm.resetIde(ctx)
                        }
                    },
                    enabled = confirmInput == "CandyIDE",
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pendingResetConfirm = false }) { Text("取消") } },
        )
    }
    // 删除（卸载）IDE：两级确认
    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("卸载 IDE") },
            text = {
                Text("卸载IDE会导致IDE当前所处的IDE目录被删除，IDE自身的私有目录也会被删除，仅保留IDE被授予的权限，是否继续？")
            },
            confirmButton = {
                TextButton(onClick = { pendingDeleteConfirm = true }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = false }) { Text("取消") } },
        )
    }
    if (pendingDeleteConfirm) {
        var confirmInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingDeleteConfirm = false },
            title = { Text("最后一步") },
            text = {
                Column {
                    Text("这是最后一步操作，完成之后不可逆，如果您想卸载IDE，请输入 CandyIDE，然后点击确定")
                    Spacer(Modifier.size(8.dp))
                    TextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it },
                        singleLine = true,
                        isError = confirmInput.isNotEmpty() && confirmInput != "CandyIDE",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (confirmInput == "CandyIDE") {
                            pendingDeleteConfirm = false
                            vm.deleteIde(ctx)
                        }
                    },
                    enabled = confirmInput == "CandyIDE",
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteConfirm = false }) { Text("取消") } },
        )
    }
}

data class LinkItem(val name: String, val url: String)

/** 通用链接列表页（项目地址 / 开源组件） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LinkListScreen(vm: candy.ide.vm.CandyViewModel, title: String, links: List<LinkItem>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler { vm.screen.value = Screen.Settings }
    androidx.compose.material3.Scaffold(
        containerColor = candy.ide.ui.theme.CandyBg,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text(title, color = candy.ide.ui.theme.CandyText) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { vm.screen.value = Screen.Settings }) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = candy.ide.ui.theme.CandyTextDim,
                        )
                    }
                },
            )
        },
    ) { pad ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            links.forEach { l ->
                androidx.compose.material3.Surface(
                    color = candy.ide.ui.theme.CandySurface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    onClick = {
                        runCatching {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(l.url),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                        }.onFailure {
                            android.widget.Toast.makeText(ctx, "没有可打开链接的应用", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
                            androidx.compose.material3.Text(l.name, color = candy.ide.ui.theme.CandyText, fontSize = 15.sp)
                            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(2.dp))
                            androidx.compose.material3.Text(
                                l.url.removePrefix("https://"),
                                color = candy.ide.ui.theme.CandyTextDim,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = candy.ide.ui.theme.CandyTextDim,
                        )
                    }
                }
            }
        }
    }
}

/** 设置条目（带箭头，点进入子页） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EntryRow(title: String, subtitle: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(color = candy.ide.ui.theme.CandySurface, onClick = onClick) {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
                androidx.compose.material3.Text(title, color = candy.ide.ui.theme.CandyText)
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(2.dp))
                androidx.compose.material3.Text(
                    subtitle,
                    color = candy.ide.ui.theme.CandyTextDim,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = candy.ide.ui.theme.CandyTextDim,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = CandyPrimary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsRow(key: String, value: String) {
    Surface(color = CandySurface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(key, color = CandyText, modifier = Modifier.weight(1f))
            Text(value, color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 无障碍服务是否已启用（检测 CandyIDE 服务是否在系统启用列表里） */
private fun isAccessibilityEnabled(ctx: android.content.Context): Boolean {
    return runCatching {
        val enabled = android.provider.Settings.Secure.getInt(
            ctx.contentResolver,
            android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) return@runCatching false
        val list = android.provider.Settings.Secure.getString(
            ctx.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return@runCatching false
        list.contains(ctx.packageName)
    }.getOrDefault(false)
}

/** 打开系统无障碍设置；CandyIDE 自己的服务条目通常在「已下载的服务」/「高级」里。 */
private fun openCandyA11ySettings(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

