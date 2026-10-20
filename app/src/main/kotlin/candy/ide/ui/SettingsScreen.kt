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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import candy.ide.data.Settings
import candy.ide.data.ShizukuManager
import candy.ide.data.Settings
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
            // 外部文件访问权限：跳系统「所有文件访问」设置页，状态即时回读
            item {
                var extGranted by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
                val extLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { extGranted = android.os.Environment.isExternalStorageManager() }
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
                            onCheckedChange = {
                                if (!extGranted) runCatching {
                                    val intent = android.content.Intent(
                                        "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                                        android.net.Uri.parse("package:" + ctx.packageName),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    extLauncher.launch(intent)
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
            item { SettingsRow("语言引擎", "Sublimation（编译 → JS → WebView）") }
            item { SettingsRow("开源组件", "SoraEditor · commonmark-java · ColorPicker · Shizuku API") }
            item {
                Surface(color = CandySurface, onClick = {
                    runCatching {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/inline-function/Sublimation"),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    }.onFailure { Toast.makeText(ctx, "没有可打开链接的应用", Toast.LENGTH_SHORT).show() }
                }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("项目地址", color = CandyText)
                            Spacer(Modifier.size(2.dp))
                            Text("github.com/inline-function/Sublimation", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CandySecondary)
                    }
                }
            }
            item { SectionTitle("开发者与交流") }
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
