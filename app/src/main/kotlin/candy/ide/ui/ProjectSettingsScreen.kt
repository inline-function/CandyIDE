package candy.ide.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import candy.ide.data.ProjectStore
import candy.ide.jsapk.AxmlEditor
import candy.ide.jsapk.JsApkConfig
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel

/**
 * 工程设置：重命名工程；JS-apk 工程的包名 / 应用名 / 版本号 / 版本名
 * （重命名入口从主页工程卡迁到这里）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsScreen(vm: CandyViewModel, dir: candy.ide.fs.CandyPath) {
    BackHandler { vm.goBack() }
    val type = remember(dir) { ProjectStore.typeOf(dir) }
    val isJsApk = type == ProjectStore.TYPE_JSAPK
    // JS 平台工程（控制台模式）与脚本工程也支持整套 APK 配置：包名/应用名/图标/入口
    val showApk = type == ProjectStore.TYPE_JSAPK || type == ProjectStore.TYPE_JS || type == ProjectStore.TYPE_SCRIPT
    var renaming by remember { mutableStateOf(false) }
    val cfg = remember(dir) {
        val c = JsApkConfig.load(dir)
        // JS 平台工程没配过时，预填 ASCII 包名（工程名里的汉字不合法）和应用名
        if (type == ProjectStore.TYPE_JS && dir.find("jsapk.json")?.isFile() != true)
            c.copy(pkg = JsApkConfig.DEFAULT_PACKAGE + "." + vm.asciiPackageSeed(dir.name), label = dir.name)
        else c
    }
    var pkg by remember { mutableStateOf(cfg.pkg) }
    var label by remember { mutableStateOf(cfg.label) }
    var versionCode by remember { mutableIntStateOf(cfg.versionCode) }
    var versionName by remember { mutableStateOf(cfg.versionName) }
    var entry by remember { mutableStateOf(cfg.entry) }
    var certPath by remember { mutableStateOf(cfg.certPath ?: "") }
    var certPass by remember { mutableStateOf(cfg.certPass ?: "") }
    var certMsg by remember { mutableStateOf<String?>(null) }
    var showGenCert by remember { mutableStateOf(false) }
    val certPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // 复制到工程内 certificates/ 目录，存相对路径
            val rel = vm.importCertInto(dir, uri)
            if (rel != null) { certPath = rel; certMsg = "已导入证书：" + rel } else certMsg = "证书导入失败"
        }
    }
    var msg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工程设置") },
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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionTitle("工程") }
            item {
                Surface(color = CandySurface, onClick = { renaming = true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("重命名工程", color = CandyText, modifier = Modifier.weight(1f))
                        Text(dir.name, color = CandyTextDim)
                    }
                }
            }
            item {
                val ctx2 = LocalContext.current
                val caprLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    if (uri != null) vm.exportProjectArchive(dir, uri)
                }
                Surface(color = CandySurface, onClick = { caprLauncher.launch(dir.name + ".capr") }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("压缩工程为 .capr", color = CandyText, modifier = Modifier.weight(1f))
                        Text("导出归档", color = CandyTextDim)
                    }
                }
            }
            item { SectionRow("工程类型", when (type) {
                ProjectStore.TYPE_JSAPK -> "安卓工程"
                ProjectStore.TYPE_SCRIPT -> "脚本工程"
                ProjectStore.TYPE_MD -> "MD工程"
                else -> "JS工程"
            }) }
            if (showApk) {
                item { SectionTitle(if (isJsApk) "打包（APK）" else "打包（APK · 控制台模式）") }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = pkg,
                            onValueChange = { pkg = it },
                            label = { Text("包名") },
                            singleLine = true,
                            isError = pkg.isNotBlank() && !AxmlEditor.validPackage(pkg),
                            supportingText = { Text(if (pkg.isNotBlank() && !AxmlEditor.validPackage(pkg)) "合法包名：字母开头的标识符以点分段" else "安装后就是应用的包名；同名包互相覆盖") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(8.dp))
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("应用名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(8.dp))
                        OutlinedTextField(
                            value = versionCode.toString(),
                            onValueChange = { versionCode = it.toIntOrNull() ?: versionCode },
                            label = { Text("版本号") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(8.dp))
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("版本名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(8.dp))
                        OutlinedTextField(
                            value = entry,
                            onValueChange = { entry = it },
                            label = { Text("入口文件") },
                            singleLine = true,
                            supportingText = { Text("工程里的 .subl 文件，需含 fun main()") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(8.dp))
                        val ctx = LocalContext.current
                        var iconFile by remember(dir) { mutableStateOf(dir.find("icon.png")) }
                        val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            if (uri != null) runCatching {
                                val target = dir.find("icon.png") ?: dir.child("icon.png")
                                ctx.contentResolver.openInputStream(uri)?.use { input ->
                                    target.outputStream().use { input.copyTo(it) }
                                }
                                iconFile = target
                                msg = "图标已保存（下次打包生效）"
                            }.onFailure { msg = "图标读取失败：${it.message ?: ""}" }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            iconFile?.let { f ->
                                val bmp = remember(f) { runCatching { BitmapFactory.decodeStream(f.inputStream()) }.getOrNull() }
                                bmp?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "应用图标",
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.size(12.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("应用图标", color = CandyText)
                                Spacer(Modifier.size(2.dp))
                                Text("放在工程根目录 icon.png（下次打包生效）；建议方形 144×144 以上",
                                    color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { iconPicker.launch("image/*") }) { Text("选择图片") }
                        }
                        Spacer(Modifier.size(12.dp))
                        Row {
                            TextButton(onClick = {
                                val ok = JsApkConfig.save(dir, JsApkConfig(
                                entry = entry,
                                pkg = pkg.ifBlank { JsApkConfig.DEFAULT_PACKAGE },
                                label = label.ifBlank { cfg.label },
                                versionCode = versionCode.coerceAtLeast(1),
                                versionName = versionName.ifBlank { "1.0" },
                                icon = if (dir.find("icon.png")?.isFile() == true) "icon.png" else null,
                                certPath = certPath.ifBlank { null },
                                certPass = certPass.ifBlank { null },
                            ))
                                msg = if (ok) "已保存" else "包名不合法"
                            }) { Text("保存配置") }
                            Spacer(Modifier.size(8.dp))
                            Surface(
                                color = CandyPrimary,
                                shape = MaterialTheme.shapes.small,
                                // 以表单当前值为准（与「保存配置」无关——省得用户忘点保存）
                                onClick = { vm.buildApkIn(dir, JsApkConfig(
                                    entry = entry,
                                    pkg = pkg.ifBlank { JsApkConfig.DEFAULT_PACKAGE },
                                    label = label.ifBlank { cfg.label },
                                    versionCode = versionCode.coerceAtLeast(1),
                                    versionName = versionName.ifBlank { "1.0" },
                                    icon = if (dir.find("icon.png")?.isFile() == true) "icon.png" else null,
                                    certPath = certPath.ifBlank { null },
                                    certPass = certPass.ifBlank { null },
                                )) },
                            ) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = CandyBg, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("立即打包 APK", color = CandyBg)
                                }
                            }
                        }
                        // ---- 自定义签名证书 ----
                        Spacer(Modifier.size(12.dp))
                        Text("签名证书（可选）", color = CandyText, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (certPath.isBlank()) "未选择：使用 CandyIDE 内置证书" else "工程内 $certPath",
                            color = CandyTextDim,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.size(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { certPicker.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = CandySecondary),
                            ) { Text("选择 .jks 证书", color = CandyBg) }
                            TextButton(onClick = { showGenCert = true }) {
                                Text("生成新证书…", color = CandyPrimary)
                            }
                            if (certPath.isNotBlank()) {
                                TextButton(onClick = { certPath = ""; certPass = "" }) { Text("清除", color = CandyTextDim) }
                            }
                        }
                        if (certPath.isNotBlank()) {
                            Spacer(Modifier.size(6.dp))
                            OutlinedTextField(
                                value = certPass,
                                onValueChange = { certPass = it },
                                label = { Text("证书密码") },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        certMsg?.let {
                            Spacer(Modifier.size(4.dp))
                            Text(it, color = CandySecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        msg?.let {
                            Spacer(Modifier.size(8.dp))
                            Text(it, color = CandySecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showGenCert) {
        var gAlias by remember { mutableStateOf("candy") }
        var gPass by remember { mutableStateOf("") }
        var gCn by remember { mutableStateOf("CandyIDE") }
        var gYears by remember { mutableStateOf("25") }
        var gErr by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showGenCert = false },
            title = { Text("生成自签名证书") },
            text = {
                Column {
                    OutlinedTextField(value = gAlias, onValueChange = { gAlias = it }, label = { Text("别名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(6.dp))
                    OutlinedTextField(value = gPass, onValueChange = { gPass = it }, label = { Text("密码（至少 6 位）") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(6.dp))
                    OutlinedTextField(value = gCn, onValueChange = { gCn = it }, label = { Text("名称（CN）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(6.dp))
                    OutlinedTextField(value = gYears, onValueChange = { gYears = it.filter { c -> c.isDigit() } }, label = { Text("有效期（年）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    gErr?.let { Spacer(Modifier.size(6.dp)); Text(it, color = CandySecondary, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rel = vm.generateCert(dir, gAlias, gPass, gCn, gYears.toIntOrNull() ?: 25)
                    if (rel.startsWith("certificates/")) {
                        certPath = rel
                        certPass = gPass
                        certMsg = "已生成并选用：" + rel
                        showGenCert = false
                    } else gErr = rel
                }) { Text("生成") }
            },
            dismissButton = { TextButton(onClick = { showGenCert = false }) { Text("取消") } },
        )
    }

    if (renaming) {
        RenameDialog(
            title = "重命名工程",
            initial = dir.name,
            onConfirm = { newName ->
                vm.renameProject(dir, newName)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun SectionRow(key: String, value: String) {
    Surface(color = CandySurface, onClick = {}) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(key, color = CandyText, modifier = Modifier.weight(1f))
            Text(value, color = CandyTextDim)
        }
    }
}
