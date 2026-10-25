package candy.ide.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.IdeState
import candy.ide.desktop.Schemes
import candy.ide.desktop.Scheme
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim

/**
 * 配色方案（与 Android 端 SchemeScreen 对齐）：
 * - 方案列表（内置 + 自定义），点击即时应用并热更新所有编辑器
 * - token 级编辑：颜色 + 粗/斜体标记
 * - 内置方案改动保存时自动另存为自定义方案（不动内置）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeScreen(state: IdeState) {
    val original = remember { Schemes.current }
    var editing by remember { mutableStateOf(Schemes.current.copy(builtin = false, id = Schemes.current.id + "-copy")) }
    var editingToken by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配色：" + Schemes.current.name) },
                navigationIcon = {
                    IconButton(onClick = { state.screen = candy.ide.desktop.DesktopScreen.Settings }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                actions = {
                    if (Schemes.current.builtin) {
                        TextButton(onClick = {
                            // 重置：回到内置方案的原始配色；不提示，直接应用
                            Schemes.select(Schemes.current.id)
                            editing = Schemes.current.copy(builtin = false, id = Schemes.current.id + "-copy")
                            state.toast = "已重置为内置配色"
                        }) { Text("重置", color = CandyTextDim) }
                    } else {
                        IconButton(onClick = {
                            Schemes.deleteCustom(Schemes.current.id)
                            Schemes.select("fuhuan")
                            state.toast = "已删除自定义方案"
                            state.screen = candy.ide.desktop.DesktopScreen.Settings
                        }) { Icon(Icons.Filled.Delete, contentDescription = "删除方案", tint = CandyError) }
                    }
                    TextButton(onClick = { showSaveDialog = true }) { Text("保存为新方案", color = CandyPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
            )
        },
        containerColor = CandyBg,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            // 方案列表
            Section("方案") {
                Schemes.BUILTINS.forEach { s -> SchemeRow(s, Schemes.current.id == s.id) { Schemes.select(s.id); state.toast = "已应用：" + s.name } }
                Schemes.listCustom().forEach { s -> SchemeRow(s, Schemes.current.id == s.id) { Schemes.select(s.id); state.toast = "已应用：" + s.name } }
                if (Schemes.listCustom().isEmpty()) {
                    Text("（暂无自定义方案，下方修改后点「保存为新方案」）", color = CandyTextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                }
            }

            // token 编辑（副本上改；内置方案不会被覆盖）
            Section("配色项（在副本上编辑）") {
                Schemes.TOKEN_LABELS.forEach { (token, label) ->
                    val color = editing.colors[token] ?: 0xFFBFBFBF.toInt()
                    val flag = editing.flags[token] ?: 0
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = CandyText, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        CompactToggle("B", flag and 1 != 0) { bold ->
                            editing = editing.copy(flags = editing.flags.setFlag(token, bold, 1))
                        }
                        Spacer(Modifier.size(6.dp))
                        CompactToggle("I", flag and 2 != 0) { italic ->
                            editing = editing.copy(flags = editing.flags.setFlag(token, italic, 2))
                        }
                        Spacer(Modifier.size(10.dp))
                        Surface(
                            onClick = { editingToken = token },
                            shape = CircleShape,
                            color = Color(color),
                            modifier = Modifier.size(28.dp),
                        ) {}
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    editingToken?.let { token ->
        val label = Schemes.TOKEN_LABELS.first { it.first == token }.second
        ColorEditDialog(
            title = label,
            initial = editing.colors[token] ?: 0xFFBFBFBF.toInt(),
            onConfirm = { newColor ->
                editing = editing.copy(colors = editing.colors + (token to newColor))
                editingToken = null
            },
            onDismiss = { editingToken = null },
        )
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf(editing.name) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存配色方案") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("方案名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val id = name.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.lowercase()
                        Schemes.saveCustom(editing.copy(id = id, name = name.trim(), builtin = false))
                        Schemes.select(id)
                        showSaveDialog = false
                        state.toast = "已保存并应用：" + name.trim()
                        state.screen = candy.ide.desktop.DesktopScreen.Settings
                    },
                ) { Text("保存并应用") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SchemeRow(scheme: Scheme, current: Boolean, onClick: () -> Unit) {
    Surface(color = CandySurface, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Palette, contentDescription = null, tint = CandyPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text(scheme.name, color = CandyText, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(if (scheme.builtin) "内置" else "自定义", color = CandyTextDim, fontSize = 11.sp)
            if (current) {
                Spacer(Modifier.size(8.dp))
                Icon(Icons.Filled.Check, contentDescription = "当前", tint = CandyPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Map<token, flag> 上把某 bit 置 1 / 去 0 */
private fun Map<String, Int>.setFlag(token: String, on: Boolean, bit: Int): Map<String, Int> {
    val cur = this[token] ?: 0
    val next = if (on) cur or bit else cur and bit.inv()
    val result = toMutableMap()
    if (next == 0) result.remove(token) else result[token] = next
    return result
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = CandyPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        content()
    }
}

@Composable
private fun CompactToggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!on) },
        color = if (on) CandyPrimary else CandySurface,
        shape = CircleShape,
        modifier = Modifier.size(24.dp),
    ) {
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (on) Color.White else CandyTextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 颜色编辑对话框：色块预览 + 十六进制输入 + RGB 三滑块 */
@Composable
private fun ColorEditDialog(
    title: String,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hex by remember { mutableStateOf(Integer.toHexString(initial and 0xFFFFFF).padStart(6, '0').uppercase()) }
    var r by remember { mutableStateOf((initial shr 16) and 0xFF) }
    var g by remember { mutableStateOf((initial shr 8) and 0xFF) }
    var b by remember { mutableStateOf(initial and 0xFF) }

    fun syncHex() {
        hex = Integer.toHexString((r shl 16) or (g shl 8) or b).padStart(6, '0').uppercase()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(Color(red = r, green = g, blue = b)),
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        val clean = v.filter { it.isHexDigit() }.take(6)
                        hex = clean.uppercase()
                        if (clean.length == 6) {
                            r = clean.substring(0, 2).toInt(16)
                            g = clean.substring(2, 4).toInt(16)
                            b = clean.substring(4, 6).toInt(16)
                        }
                    },
                    label = { Text("十六进制 RGB") },
                    singleLine = true,
                )
                SliderRow("R", r) { r = it; syncHex() }
                SliderRow("G", g) { g = it; syncHex() }
                SliderRow("B", b) { b = it; syncHex() }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm((0xFF shl 24) or (r shl 16) or (g shl 8) or b) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun Char.isHexDigit() = this in '0'..'9' || this in 'A'..'F' || this in 'a'..'f'

@Composable
private fun SliderRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = CandyText, fontSize = 12.sp, modifier = Modifier.size(16.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(value.toString().padStart(3, ' '), color = CandyTextDim, fontSize = 11.sp, modifier = Modifier.size(34.dp))
    }
}
