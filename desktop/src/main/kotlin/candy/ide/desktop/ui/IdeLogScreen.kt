package candy.ide.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.IdeState
import candy.ide.desktop.OutputLine
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim

/** IDE 日志（对应 Android 端 IdeLogScreen，设置页进入） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeLogScreen(state: IdeState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IDE 日志") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = candy.ide.desktop.DesktopScreen.Settings }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                actions = {
                    IconButton(onClick = { state.clearLogs() }) {
                        Text("清空", color = CandyTextDim, style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg),
            )
        },
        containerColor = CandyBg,
    ) { padding ->
        if (state.logLines.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("还没有日志记录", color = CandyTextDim) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.logLines) { line ->
                    SelectionContainer {
                        Text(
                            line.text,
                            color = when (line.kind) {
                                OutputLine.Kind.Error -> CandyError
                                OutputLine.Kind.Success -> CandySecondary
                                else -> CandyText
                            },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
