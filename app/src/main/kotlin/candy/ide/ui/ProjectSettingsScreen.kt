package candy.ide.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import candy.ide.vm.CandyViewModel
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.ui.theme.CandySurface

/**
 * 工程设置：重命名工程、压缩归档、工程类型；
 * 打包（APK）等扩展分区由插件贡献（android-project 的 jsapk 分区：
 * 包名/应用名/版本/图标/证书 + 立即打包）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsScreen(vm: CandyViewModel, dir: candy.ide.fs.CandyPath) {
    BackHandler { vm.goBack() }
    var renaming by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工程设置") },
                navigationIcon = {
                    IconButton(onClick = { vm.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
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
            item { SectionRow("工程类型", vm.projectTypeLabel(dir)) }

            // 插件贡献的工程设置分区（如 android-project 的「打包（APK）」）
            val host = vm.pluginHost
            for (section in candy.ide.plugin.PluginRegistry.projectSettingsSections()) {
                if (runCatching { section.appliesTo(host, dir) }.getOrDefault(false)) {
                    item(key = "plugin-" + section.id) { section.Content(host, dir) }
                }
            }
        }
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
