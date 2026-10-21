package candy.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.git.GitManager
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import candy.ide.vm.CandyViewModel

/** git 页：推送（短按本地提交 / 长按推送云端）与丢弃（短按还原 / 长按版本回退） */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GitScreen(vm: CandyViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.refreshCommitCount(ctx) }
    var commitName by remember { mutableStateOf("") }
    var showCommit by remember { mutableStateOf(false) }
    var showPushConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val gitResult = vm.gitResult.value
    val busy = vm.gitBusy.value
    val behind = vm.cloudBehind.intValue

    androidx.activity.compose.BackHandler { vm.screen.value = Screen.Editor }
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("git", fontWeight = FontWeight.Bold, color = CandyPrimary, fontSize = 20.sp)
                    Spacer(Modifier.size(8.dp))
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CandyPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp))
                        Text(vm.gitMsg.value.ifBlank { "处理中…" }, color = CandyTextDim, fontSize = 12.sp)
                    }
                }
            }
            if (behind > 0) {
                item {
                    Surface(color = CandySurface, shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("云端有更新", color = CandyPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.size(4.dp))
                            Text("云端领先 $behind 个提交，建议先拉取再继续编辑。", color = CandyTextDim, fontSize = 12.sp)
                            Spacer(Modifier.size(8.dp))
                            TextButton(onClick = { vm.gitPull(ctx) }) { Text("拉取更新") }
                        }
                    }
                }
            }
            item {
                // 推送：短按本地提交；长按推送云端
                Surface(color = CandySurface, shape = RoundedCornerShape(10.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp)
                            .combinedClickable(
                                onClick = { commitName = vm.nextCommitName(); showCommit = true },
                                onLongClick = { commitName = vm.nextCommitName(); showPushConfirm = true },
                            )
                    ) {
                        Text("推送 (Commit)", color = CandyText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(4.dp))
                        Text("短按：保存到本地（填写存档名）\n长按：保存并推送到云端 (push)",
                            color = CandyTextDim, fontSize = 12.sp)
                    }
                }
            }
            item {
                // 丢弃：短按还原未提交修改；长按版本回退
                Surface(color = CandySurface, shape = RoundedCornerShape(10.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp)
                            .combinedClickable(
                                onClick = { showDiscardConfirm = true },
                                onLongClick = { showHistory = true },
                            )
                    ) {
                        Text("丢弃 (Discard)", color = CandyText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(4.dp))
                        Text("短按：丢弃还未提交的修改，还原到最近保存\n长按：选择提交版本回退 (Reset)",
                            color = CandyTextDim, fontSize = 12.sp)
                    }
                }
            }
            gitResult?.let { r ->
                item {
                    Surface(color = CandySurface, shape = RoundedCornerShape(10.dp)) {
                        Text(r, color = CandyTextDim, fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
            }
        }
    }

    // 本地提交对话框
    if (showCommit) {
        AlertDialog(
            onDismissRequest = { showCommit = false },
            title = { Text("保存到本地") },
            text = {
                TextField(value = commitName, onValueChange = { commitName = it }, singleLine = true,
                    label = { Text("存档名") })
            },
            confirmButton = {
                TextButton(onClick = {
                    showCommit = false
                    vm.gitCommit(ctx, commitName.ifBlank { vm.nextCommitName() }, pushToRemote = true) { msg -> vm.gitResult.value = msg }
                }) { Text("保存并推送") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showCommit = false
                        vm.gitCommit(ctx, commitName.ifBlank { vm.nextCommitName() }, pushToRemote = false) {
                            vm.gitResult.value = it
                        }
                    }) { Text("仅保存本地") }
                    TextButton(onClick = { showCommit = false }) { Text("取消") }
                }
            },
        )
    }

    // 长按推送云端确认
    if (showPushConfirm) {
        AlertDialog(
            onDismissRequest = { showPushConfirm = false },
            title = { Text("推送到云端") },
            text = {
                Text("将把当前工程内容提交并推送到远程仓库。\n存档名：" + commitName)
            },
            confirmButton = {
                TextButton(onClick = {
                    showPushConfirm = false
                    vm.gitCommit(ctx, commitName.ifBlank { vm.nextCommitName() }, pushToRemote = true) { msg -> vm.gitResult.value = msg }
                }) { Text("推送") }
            },
            dismissButton = {
                TextButton(onClick = { showPushConfirm = false }) { Text("取消") }
            },
        )
    }

    // 丢弃确认
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("丢弃修改") },
            text = { Text("将丢弃所有还未提交（未塞进本地仓库）的修改，并还原到最近的保存。不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    vm.gitDiscard(ctx) { msg -> vm.gitResult.value = msg }
                }) { Text("同意，丢弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("取消") }
            },
        )
    }

    // 版本回退列表
    if (showHistory) {
        var commits by remember { mutableStateOf<List<GitManager.CommitInfo>>(emptyList()) }
        LaunchedEffect(Unit) { vm.gitLog(ctx) { commits = it } }
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("选择回退版本") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(commits) { c ->
                        Surface(color = CandyBg, shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)
                                .combinedClickable(onClick = {
                                    showHistory = false
                                    vm.gitReset(ctx, c.hash) { msg -> vm.gitResult.value = msg }
                                })
                            ) {
                                Text(c.message.ifBlank { "（无存档名）" }, color = CandyText, fontSize = 13.sp)
                                Text(c.time + "  " + c.hash.take(7), color = CandyTextDim, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showHistory = false }) { Text("取消") } },
        )
    }

    // 冲突解决
    vm.cloudConflict.value?.let { conflict ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("拉取冲突") },
            text = { Text("云端与本地都有新的修改，发生了冲突。\n\n" + conflict) },
            confirmButton = {
                TextButton(onClick = { vm.cloudConflict.value = null }) { Text("保留本地") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.cloudConflict.value = null
                        vm.gitDiscard(ctx) { vm.gitPull(ctx) }
                    }) { Text("用云端覆盖") }
                }
            },
        )
    }
}

