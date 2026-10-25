package candy.ide.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.IdeState
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim

/** 关于 / 开源信息（对应 Android 端 Developers + OpenSource 的桌面端合并页） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(state: IdeState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = candy.ide.desktop.DesktopScreen.Settings }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
            )
        },
        containerColor = CandyBg,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CandySurface,
                    modifier = Modifier.size(72.dp),
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource("icon.png"),
                        contentDescription = "CandyIDE",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text("CandyIDE Desktop", color = CandyText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("1.0.0", color = CandyTextDim, fontSize = 12.sp)
            }

            Section("简介") {
                InfoCard("CandyIDE 是面向 sublimation 语言的轻量 IDE，Windows 桌面端与 Android 端共享同一套语言引擎（词法/语法/解释器/LSP），界面与配色一一对应。")
            }

            Section("开源依赖") {
                DepRow(Icons.Filled.Code, "Compose Multiplatform", "UI 框架（Apache-2.0）")
                DepRow(Icons.Filled.Code, "sublimation", "语言引擎：编译器/解释器/LSP（自研）")
                DepRow(Icons.Filled.Code, "commonmark-java", "Markdown 渲染（Apache-2.0）")
                DepRow(Icons.Filled.Public, "sora-editor", "Android 端编辑器组件（LGPL-2.1，仅 Android 端）")
            }

            Section("反馈") {
                DepRow(Icons.Filled.BugReport, "问题反馈", "通过 Android 端「关于」页或源码仓库 Issue")
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = CandyPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        content()
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(color = CandySurface, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(text, color = CandyTextDim, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun DepRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, sub: String) {
    Surface(color = CandySurface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = CandySecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = CandyText, fontSize = 13.sp)
                Text(sub, color = CandyTextDim, fontSize = 11.sp)
            }
        }
    }
}
