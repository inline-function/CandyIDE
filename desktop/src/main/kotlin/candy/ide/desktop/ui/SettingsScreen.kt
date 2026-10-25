package candy.ide.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.DesktopEngine
import candy.ide.desktop.RunMode
import candy.ide.desktop.Settings
import candy.ide.desktop.wordWrap
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim

/**
 * 设置页（对齐安卓端 SettingsScreen 的分区与行风格：LazyColumn 条目 → Surface 行 + Switch/Slider）；
 * 安卓特有的 Shizuku/Termux/LiteASDK/无障碍/伴生软件分类不移植，桌面特有项以「引擎与路径」「运行模式」分类保留。
 */
@Composable
fun SettingsScreen(state: candy.ide.desktop.IdeState) {
    var subRoot by remember { mutableStateOf(Settings.sublimationRoot) }
    var nodePath by remember { mutableStateOf(Settings.nodePath) }
    var workspace by remember { mutableStateOf(Settings.workspaceRoot) }
    var fontSize by remember { mutableIntStateOf(Settings.fontSize) }
    var runMode by remember { mutableStateOf(Settings.runMode) }
    var agentUrl by remember { mutableStateOf(Settings.agentBaseUrl) }
    var agentKey by remember { mutableStateOf(Settings.agentKey) }
    var agentModel by remember { mutableStateOf(Settings.agentModel) }
    var agentTemp by remember { mutableFloatStateOf(Settings.agentTemperature) }
    var agentStream by remember { mutableStateOf(Settings.agentStream) }
    var agentEntry by remember { mutableStateOf(Settings.agentEnabled) }
    var agentPrompt by remember { mutableStateOf(Settings.agentPrompt) }
    var gitUser by remember { mutableStateOf(Settings.gitUserName) }
    var gitEmail by remember { mutableStateOf(Settings.gitUserEmail) }
    var gitAutoCommit by remember { mutableStateOf(Settings.gitAutoCommit) }
    var gitAutoSync by remember { mutableStateOf(Settings.gitAutoSync) }
    var wordWrap by remember { mutableStateOf(Settings.wordWrap) }
    var streamExpanded by remember { mutableStateOf(false) }
    var pendingReset by remember { mutableStateOf(false) }
    var pendingResetConfirm by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf(false) }

    val engineRoot = remember { DesktopEngine.sublimationRoot }
    val node = remember { DesktopEngine.nodeCommand }
    val coreOk = remember { DesktopEngine.coreDir.isDirectory }
    // git 可用性（系统 git；安卓端是 Termux 安装，桌面直接探测）
    val gitVer = remember {
        runCatching {
            val p = ProcessBuilder(listOf("git", "--version")).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            if (p.waitFor() == 0 && out.startsWith("git version")) out.removePrefix("git version ") else null
        }.getOrNull()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = CandyBg) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { state.screen = candy.ide.desktop.DesktopScreen.Projects }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回主页", tint = CandyText)
                }
                Text("设置", style = MaterialTheme.typography.headlineSmall, color = CandyText, fontWeight = FontWeight.Bold)
            }

            // ───────── 编辑器（对齐安卓端「编辑器」分类，字体项除外：桌面端固定 JBM + Noto） ─────────
            SectionTitle("编辑器")
            EntryRow("配色方案", candy.ide.desktop.Schemes.current.name) {
                state.screen = candy.ide.desktop.DesktopScreen.Scheme
            }
            EntryRow("编辑当前方案颜色", "自定义") {
                state.screen = candy.ide.desktop.DesktopScreen.Scheme
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("字号", color = CandyText, modifier = Modifier.width(64.dp))
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.toInt() },
                    onValueChangeFinished = {
                        Settings.fontSize = fontSize
                        state.toast = "字号已保存：" + fontSize
                    },
                    valueRange = 10f..28f,
                    modifier = Modifier.weight(1f),
                )
                Text(fontSize.toString() + " sp", color = CandyTextDim, modifier = Modifier.width(48.dp))
            }
            SwitchRow(
                title = "自动换行",
                subtitle = "开启后编辑器按窗口宽度折行（关闭则横向滚动）",
                checked = wordWrap,
                onCheckedChange = {
                    wordWrap = it
                    Settings.wordWrap = it
                    state.wordWrap = it
                },
            )

            EntryRow("标准库", "sublimation/core 源码双栏浏览") {
                state.screen = candy.ide.desktop.DesktopScreen.Stdlib
            }
            EntryRow("关于 CandyIDE", "版本与开源依赖声明") {
                state.screen = candy.ide.desktop.DesktopScreen.About
            }

            // ───────── 诊断 ─────────
            SectionTitle("诊断")
            EntryRow(
                "IDE 日志",
                "运行与引擎事件；落盘 logs/ide.log（超 1MB 自动截断尾部）",
            ) { state.screen = candy.ide.desktop.DesktopScreen.IdeLog }

            // ───────── AI 助手（对齐安卓端「AI agent（ADK）」分类，无障碍项不适用） ─────────
            SectionTitle("AI 助手")
            SwitchRow(
                title = "开启 AGENT 支持",
                subtitle = "开启后主页与编辑器窗口出现助手入口，可全页对话",
                checked = agentEntry,
                onCheckedChange = {
                    agentEntry = it
                    Settings.agentEnabled = it
                },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                LabeledField("Base URL", agentUrl, { agentUrl = it }) {
                    Settings.agentBaseUrl = agentUrl.trim(); state.toast = "端点已保存"
                }
                Spacer(Modifier.size(6.dp))
                LabeledField("模型", agentModel, { agentModel = it }) {
                    Settings.agentModel = agentModel.trim(); state.toast = "模型已保存"
                }
                Spacer(Modifier.size(6.dp))
                LabeledField("API Key", agentKey, { agentKey = it }, password = true) {
                    Settings.agentKey = agentKey.trim(); state.toast = "Key 已保存"
                }
            }
            Spacer(Modifier.size(2.dp))
            // 访问权：只读/工程/工作区/完全 循环切换
            val scopeLabel = remember {
                candy.ide.desktop.agent.AgentTools.AgentScope.entries
                    .firstOrNull { it.key == Settings.agentScope }?.label
            }
            Surface(color = CandySurface, onClick = {
                val next = candy.ide.desktop.agent.AgentTools.AgentScope.entries
                val cur = next.firstOrNull { it.key == Settings.agentScope } ?: candy.ide.desktop.agent.AgentTools.AgentScope.PROJECT
                val n = next[(next.indexOf(cur) + 1) % next.size]
                Settings.agentScope = n.key
                state.toast = "访问权：" + n.label
            }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("访问权", color = CandyText)
                        Spacer(Modifier.size(2.dp))
                        Text(scopeLabel ?: "", color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CandyTextDim)
                }
            }
            // 采样温度
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Temperature", color = CandyTextDim, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(String.format("%.1f", agentTemp), color = CandyPrimary, fontSize = 13.sp)
                }
                Slider(
                    value = agentTemp,
                    onValueChange = { agentTemp = (Math.round(it * 10) / 10f).coerceIn(0f, 1f) },
                    onValueChangeFinished = { Settings.agentTemperature = agentTemp },
                    valueRange = 0f..1f,
                    steps = 9,
                )
            }
            // 流式输出：下拉
            Box {
                Surface(color = CandySurface, onClick = { streamExpanded = true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            streamExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("整包返回") },
                        onClick = {
                            agentStream = false
                            Settings.agentStream = false
                            streamExpanded = false
                        },
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Spacer(Modifier.size(6.dp))
                LabeledField("初始提示词（留空用内置默认）", agentPrompt, { agentPrompt = it }, singleLine = false) {
                    Settings.agentPrompt = agentPrompt
                    state.toast = "提示词已保存"
                }
            }
            // 插件贡献的设置分区（对齐安卓端 SettingsScreen：按 sortKey 升序插入；
            // 桌面端内置 android-project 暂无设置分区——LiteASDK 导入为安卓端专属能力，不移植）
            for (section in candy.ide.desktop.plugin.PluginRegistry.settingsSections()) {
                SectionTitle(section.title)
                section.Content(candy.ide.desktop.plugin.host.DesktopPluginHost(state))
            }
            HorizontalDivider()
            Text(
                "CandyIDE Desktop · 与 Android 端共享 sublimation 语言引擎与主题" +
                "（开源依赖：Compose Multiplatform / commonmark-java，均为 Apache-2.0）",
                color = CandyTextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    // 关闭 IDE 确认
    if (pendingExit) {
        AlertDialog(
            onDismissRequest = { pendingExit = false },
            title = { Text("关闭 IDE") },
            text = { Text("将关闭全部编辑器窗口并退出进程，未保存的修改会先自动保存。是否继续？", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    pendingExit = false
                    state.saveAllDirty()
                    kotlin.system.exitProcess(0)
                }) { Text("关闭并退出", color = CandyError) }
            },
            dismissButton = { TextButton(onClick = { pendingExit = false }) { Text("取消") } },
        )
    }

    // 重置 IDE：两级确认（第二级需输入 CandyIDE，与安卓端一致）
    if (pendingReset) {
        AlertDialog(
            onDismissRequest = { pendingReset = false },
            title = { Text("重置 IDE") },
            text = { Text("重置将删除工程根目录下的全部工程与桌面端配置（端点/Key/配色/身份），不可恢复，是否继续？", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = { pendingReset = false; pendingResetConfirm = true }) { Text("继续", color = CandyError) }
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
                    Text("这是最后一步操作，完成之后不可逆，如果您想重置 IDE，请输入 CandyIDE，然后点击确定", color = CandyTextDim)
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
                            runCatching {
                                java.io.File(Settings.workspaceRoot).listFiles()?.forEach { it.deleteRecursively() }
                                runCatching { java.util.prefs.Preferences.userRoot().node("candy/ide/desktop").clear() }
                                state.toast = "已重置，即将退出"
                            }
                            state.saveAllDirty()
                            kotlin.system.exitProcess(0)
                        }
                    },
                    enabled = confirmInput == "CandyIDE",
                ) { Text("确定", color = CandyError) }
            },
            dismissButton = { TextButton(onClick = { pendingResetConfirm = false }) { Text("取消") } },
        )
    }
}

private fun mDesc(m: RunMode) = when (m) {
    RunMode.Auto -> "自动：.subs 走解释器，工程编译后交 Node 运行"
    RunMode.Node -> "Node：始终编译并运行 JS（需 Node.js）"
    RunMode.Interpreter -> "解释器：始终进程内直连解释执行"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = CandyPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        content()
    }
}

/** 分区标题（对齐安卓端 SectionTitle） */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = CandyPrimary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun StatusRow(name: String, value: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ok) "✓" else "✗", color = if (ok) CandySecondary else CandyError, fontSize = 14.sp)
        Column {
            Text(name, color = CandyText, fontSize = 12.sp)
            Text(value, color = CandyTextDim, fontSize = 11.sp)
        }
    }
}

/** 带副标题 + 右箭头的条目行（对齐安卓端 EntryRow） */
@Composable
private fun EntryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(color = CandySurface, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = CandyText)
                Spacer(Modifier.size(2.dp))
                Text(subtitle, color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CandyTextDim)
        }
    }
}

/** 开关行（对齐安卓端：标题 + 副说明 + Switch） */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color = CandySurface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = CandyText)
                Spacer(Modifier.size(2.dp))
                Text(subtitle, color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    password: Boolean = false,
    singleLine: Boolean = true,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = CandyTextDim, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = CandyText),
            )
            Button(onApply, colors = ButtonDefaults.buttonColors(containerColor = CandyPrimary)) { Text("保存") }
        }
    }
}