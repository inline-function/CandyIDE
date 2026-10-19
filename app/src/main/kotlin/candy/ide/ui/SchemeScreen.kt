package candy.ide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import candy.ide.editor.Schemes
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeScreen(vm: CandyViewModel) {
    // 改色未保存时拦截返回：提示保存/丢弃/继续编辑
    val original = remember { Schemes.current }
    var pendingExit by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    // 编辑副本；内置方案保存时自动另存为自定义方案
    var editing by remember { mutableStateOf(Schemes.current.copy(builtin = false, id = Schemes.current.id + "-copy")) }
    var editingToken by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    // 脏检查：与进入页面时的原始配色比对（编辑副本 vs 当前生效方案）
    val dirty = editing.colors != original.colors || editing.flags != original.flags
    BackHandler { if (dirty) pendingExit = true else vm.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配色：" + Schemes.current.name) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) pendingExit = true else vm.goBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                actions = {
                    if (Schemes.current.builtin) {
                        TextButton(onClick = {
                            // 重置：回到打包时的原始配色，编辑副本也同步清零
                            vm.resetScheme(Schemes.current.id)
                            editing = Schemes.current.copy(builtin = false, id = Schemes.current.id + "-copy")
                        }) { Text("重置", color = CandyTextDim) }
                    } else {
                        TextButton(onClick = {
                            vm.deleteScheme(Schemes.current.id)
                            vm.goBack()
                        }) { Text("删除", color = Color(0xFFFF5370)) }
                    }
                    // 导出当前方案为 .caco（SAF 另存为）
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val cacoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
                    ) { uri ->
                        if (uri != null) vm.exportScheme(Schemes.current.id, uri)
                    }
                    TextButton(onClick = {
                        cacoLauncher.launch(Schemes.current.name + ".caco")
                    }) { Text("导出 .caco", color = CandySecondary) }
                    TextButton(onClick = { showSaveDialog = true }) { Text("保存为新方案", color = CandyPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg)
            )
        },
        containerColor = CandyBg
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(Schemes.TOKEN_LABELS) { (token, label) ->
                val color = editing.colors[token] ?: 0xFFBFBFBF.toInt()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = CandyText, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val flag = editing.flags[token] ?: 0
                    CompactToggle("B", flag and 1 != 0) { bold2 ->
                        editing = editing.copy(flags = editing.flags.setFlag(token, bold2, 1))
                    }
                    CompactToggle("I", flag and 2 != 0) { italic2 ->
                        editing = editing.copy(flags = editing.flags.setFlag(token, italic2, 2))
                    }
                    Spacer(Modifier.size(8.dp))
                    Surface(
                        onClick = { editingToken = token },
                        shape = CircleShape,
                        color = Color(color),
                        modifier = Modifier.size(30.dp)
                    ) {}
                }
            }
        }
    }

    editingToken?.let { token ->
        val label = Schemes.TOKEN_LABELS.first { it.first == token }.second
        ColorPickerDialog(
            title = label,
            initial = editing.colors[token] ?: 0xFFBFBFBF.toInt(),
            onConfirm = { newColor ->
                editing = editing.copy(colors = editing.colors + (token to newColor))
                editingToken = null
            },
            onDismiss = { editingToken = null }
        )
    }

    if (pendingExit) {
        AlertDialog(
            onDismissRequest = { pendingExit = false },
            title = { Text("保存配色修改？") },
            text = { Text("当前的配色调整尚未保存。", color = CandyTextDim) },
            confirmButton = {
                TextButton(onClick = {
                    pendingExit = false
                    showSaveDialog = true
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingExit = false; vm.goBack() }) { Text("不保存") }
                    TextButton(onClick = { pendingExit = false }) { Text("继续编辑") }
                }
            },
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
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val id = name.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.lowercase()
                        val scheme = editing.copy(id = id, name = name.trim())
                        Schemes.saveCustom(ctx, scheme)
                        vm.setScheme(id)
                        showSaveDialog = false
                        vm.goBack()
                    }
                ) { Text("保存并应用") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("取消") } }
        )
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
private fun CompactToggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!on) },
        color = if (on) CandyPrimary else CandySurface,
        shape = CircleShape,
        modifier = Modifier.size(26.dp)
    ) {
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (on) Color.White else CandyTextDim,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val controller = rememberColorPickerController()
    var color by remember { mutableStateOf(Color(initial)) }

    LaunchedEffect(Unit) {
        runCatching { controller.selectByColor(color, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    controller = controller,
                    onColorChanged = { envelope: ColorEnvelope -> color = envelope.color }
                )
                Spacer(Modifier.size(8.dp))
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    controller = controller
                )
                Spacer(Modifier.size(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = color,
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {}
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "#" + Integer.toHexString(toArgbInt(color) and 0xFFFFFF).padStart(6, '0').uppercase(),
                            color = CandyTextDim
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(toArgbInt(color)) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** Compose Color → ARGB Int（不依赖 compose 的 toArgb 扩展符号） */
private fun toArgbInt(c: Color): Int = android.graphics.Color.argb(
    (c.alpha * 255f + 0.5f).toInt(), (c.red * 255f + 0.5f).toInt(),
    (c.green * 255f + 0.5f).toInt(), (c.blue * 255f + 0.5f).toInt()
)
