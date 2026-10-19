package candy.ide.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel

/** 开发者与联系方式：点击标识/群号即复制 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopersScreen(vm: CandyViewModel) {
    BackHandler { vm.goBack() }
    val ctx = LocalContext.current

    fun copy(label: String, value: String) {
        runCatching {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                ClipData.newPlainText(label, value),
            )
        }
        Toast.makeText(ctx, "已复制：" + value, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者") },
                navigationIcon = {
                    IconButton(onClick = { vm.goBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
            )
        },
        containerColor = CandyBg,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionTitle("开发者") }
            item {
                ContactRow(
                    platform = "QQ",
                    identity = "语法糖味函子酱",
                    number = "2589866838",
                    onCopy = { copy("QQ 号", "2589866838") },
                )
            }
            item {
                ContactRow(
                    platform = "X（推特）",
                    identity = "琳妮儿芳沈",
                    number = "liner_function",
                    onCopy = { copy("X 用户名", "liner_function") },
                )
            }
            item {
                ContactRow(
                    platform = "哔哩哔哩",
                    identity = "语法糖味函子酱",
                    number = "UID 3493257256372598",
                    onCopy = { copy("B 站 UID", "3493257256372598") },
                )
            }
            item { SectionTitle("交流群") }
            item {
                ContactRow(
                    platform = "QQ 群",
                    identity = "语法糖研究小组",
                    number = "1127795005",
                    onCopy = { copy("QQ 群号", "1127795005") },
                )
            }
        }
    }
}

@Composable
private fun ContactRow(platform: String, identity: String, number: String, onCopy: () -> Unit) {
    Surface(color = CandySurface) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onCopy),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(platform + " · " + identity, color = CandyText)
                Spacer(Modifier.size(2.dp))
                Text(number, color = CandyTextDim, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = CandySecondary, modifier = Modifier.size(18.dp))
        }
    }
}