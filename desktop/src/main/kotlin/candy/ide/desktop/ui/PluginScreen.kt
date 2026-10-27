package candy.ide.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.DesktopScreen
import candy.ide.desktop.IdeState
import candy.ide.desktop.plugin.LoadedPlugin
import candy.ide.desktop.plugin.PluginRegistry
import candy.ide.desktop.plugin.PluginStateStore
import candy.ide.desktop.plugin.SystemPlugins
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim

/**
 * 插件中心（对齐安卓端 PluginScreen）：圆角卡片列 + 三点菜单（停用/启用、卸载）；
 * 系统插件（SfA/SfG/SfW）无三点菜单；退出时状态有变化则提示重启。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScreen(state: IdeState) {
    // 进入时快照
    val initial = remember { PluginStateStore.snapshot() }
    var disabledIds by remember { mutableStateOf(PluginStateStore.disabledIds()) }
    var uninstalledIds by remember { mutableStateOf(PluginStateStore.uninstalledIds()) }
    var pendingUninstall by remember { mutableStateOf<LoadedPlugin?>(null) }
    var showRestart by remember { mutableStateOf(false) }

    fun changed(): Boolean = initial.diff(PluginStateStore.Snapshot(disabledIds, uninstalledIds))

    fun tryExit() {
        if (changed()) showRestart = true else state.screen = DesktopScreen.Projects
    }

    val visible = remember(disabledIds, uninstalledIds) {
        PluginRegistry.plugins.filter { it.manifest.id !in uninstalledIds }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件", color = CandyText) },
                navigationIcon = {
                    IconButton(onClick = { tryExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
            )
        },
        containerColor = CandyBg,
    ) { padding ->
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("正在装载插件…", color = CandyTextDim)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(visible.size) { i ->
                    PluginCard(visible[i], disabledIds, onDisable = { p, on ->
                        if (PluginStateStore.setDisabled(p.manifest.id, on)) {
                            disabledIds = PluginStateStore.disabledIds()
                        } else {
                            state.toast = "系统插件不可停用"
                        }
                    }, onUninstall = { p -> pendingUninstall = p })
                }
            }
        }
    }

    pendingUninstall?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text("卸载 " + p.manifest.name) },
            text = {
                Text(
                    "内置插件资产随应用发布：卸载后将停用并隐藏该插件（本次及以后不再装载）。\n" +
                    "下次构建去掉插件资产后才算真正卸载。系统插件不可卸载。",
                    fontSize = 13.sp, color = CandyTextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (PluginStateStore.setUninstalled(p.manifest.id)) {
                        uninstalledIds = PluginStateStore.uninstalledIds()
                    } else {
                        state.toast = "系统插件不可卸载"
                    }
                    pendingUninstall = null
                }) { Text("停用并隐藏", color = CandyPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) { Text("取消", color = CandyTextDim) }
            },
        )
    }

    if (showRestart) {
        AlertDialog(
            onDismissRequest = { showRestart = false },
            title = { Text("需要重启应用才能生效") },
            text = {
                Text(
                    "插件的停用/启用/卸载状态会在下次启动应用时应用。\n请关闭 CandyIDE 窗口后重新打开。",
                    fontSize = 13.sp, color = CandyTextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestart = false
                    state.screen = DesktopScreen.Projects
                }) { Text("知道了", color = CandyPrimary) }
            },
        )
    }
}

@Composable
private fun PluginCard(
    p: LoadedPlugin,
    disabledIds: Set<String>,
    onDisable: (LoadedPlugin, Boolean) -> Unit,
    onUninstall: (LoadedPlugin) -> Unit,
) {
    val m = p.manifest
    val system = SystemPlugins.isSystem(m)
    val disabled = m.id in disabledIds || p.disabled
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CandySurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(m.name, color = CandyText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("v" + m.version, color = CandyTextDim, fontSize = 11.sp)
                    if (system) Badge("系统插件")
                    if (disabled) Badge("已停用")
                }
                if (m.description.isNotEmpty()) {
                    Text(m.description, color = CandyTextDim, fontSize = 12.sp)
                }
                val footer = listOfNotNull(m.id.takeIf { it != m.name }, m.author?.let { "作者：" + it }).joinToString(" · ")
                if (footer.isNotEmpty()) Text(footer, color = CandyTextDim, fontSize = 10.sp)
            }
            if (!system) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "插件操作", tint = CandyTextDim)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (disabled) "启用" else "停用") },
                            onClick = { menuOpen = false; onDisable(p, !disabled) },
                        )
                        DropdownMenuItem(
                            text = { Text("卸载") },
                            onClick = { menuOpen = false; onUninstall(p) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        color = CandySurfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text,
            color = CandyTextDim,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
