package candy.ide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel

/** IDE 事件日志（设置页进入）：落盘于 files/candy/logs/ide.log */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeLogScreen(vm: CandyViewModel) {
    BackHandler { vm.goBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IDE 日志") },
                navigationIcon = {
                    IconButton(onClick = { vm.goBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = CandyText)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.clearIdeLog() }) {
                        Text("清空", color = CandyTextDim, style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CandyBg)
            )
        },
        containerColor = CandyBg
    ) { padding ->
        if (vm.ideLog.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有日志记录", color = CandyTextDim)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(vm.ideLog) { line ->
                    SelectionContainer {
                        Text(
                            line,
                            color = if (line.contains("失败") || line.contains("错误")) CandyTextDim else CandyText,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}