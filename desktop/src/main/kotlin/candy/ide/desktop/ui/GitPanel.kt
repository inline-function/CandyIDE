package candy.ide.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.IdeState
import candy.ide.desktop.data.ProjectStore
import candy.ide.desktop.Settings
import candy.ide.desktop.git.DesktopGit
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyError
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandySecondary
import candy.ide.ui.theme.CandySurface
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Git 面板（对齐安卓端 GitScreen）：
 * 未初始化 → 一键 init（含身份 + 远程）；远程地址（每工程单独保存）；
 * 云端领先提示 + pull；提交（短按） / 推送 / 丢弃 / 版本回退（历史选择）；
 * 触摸端的短按/长按在桌面端拆成明确按钮。
 */
@Composable
fun GitPanel(
    state: IdeState,
    project: File,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var repoReady by remember { mutableStateOf(DesktopGit.hasRepo(project)) }
    var status by remember { mutableStateOf<String?>(null) }
    var commitCount by remember { mutableStateOf(0) }
    var behind by remember { mutableStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var remoteText by remember { mutableStateOf(ProjectStore.remoteOf(project)) }
    var commitMsg by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<DesktopGit.CommitInfo>>(emptyList()) }

    val scope = rememberCoroutineScope()

    fun nextCommitName() = "第 " + Math.max(1, commitCount + 1) + " 次推送"

    fun runGit(action: String, block: suspend () -> DesktopGit.GitResult) {
        busy = true
        msg = action + "…"
        scope.launch(Dispatchers.IO) {
            val r = block()
            busy = false
            msg = if (r.ok) action + "成功" else action + "失败：" + r.output.take(300)
            repoReady = DesktopGit.hasRepo(project)
            status = DesktopGit.run(project, "status", "--short").output
            commitCount = DesktopGit.commitCount(project)
        }
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            repoReady = DesktopGit.hasRepo(project)
            status = DesktopGit.run(project, "status", "--short").output
            commitCount = DesktopGit.commitCount(project)
            if (repoReady) {
                // fetch 比较云端领先数；无网络/无远程时静默置 -1（不提示）
                val info = DesktopGit.fetchAndCheckAhead(project)
                behind = if (info.error != null) -1 else info.behind
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = CandySurfaceVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Git", color = CandyText, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(6.dp))
                Text(project.name, color = CandyTextDim, fontSize = 11.sp)
                Spacer(Modifier.width(1f.dp))
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.width(12.dp).heightIn(max = 12.dp), color = CandyPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(msg, color = CandyTextDim, fontSize = 10.sp)
                }
                Spacer(Modifier.width(1f.dp))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭 Git 面板（Esc）", tint = CandyTextDim)
                }
            }
            HorizontalDivider(color = CandySurface)

            // 未初始化：一键初始化
            if (!repoReady) {
                Surface(color = CandySurface, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("尚未初始化 git 仓库", color = CandyPrimary, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Text("本工程还没有 .git，推送/丢弃会报错。先初始化（git init + 身份 + 远程）。", color = CandyTextDim, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            runGit("初始化") {
                                val r = DesktopGit.initRepo(project, "初始化工程 " + project.name)
                                if (r.ok) DesktopGit.configIdentity(project, Settings.gitUserName, Settings.gitUserEmail)
                                if (r.ok && remoteText.isNotBlank()) DesktopGit.setRemote(project, remoteText)
                                r
                            }
                        }) { Text("初始化 git 仓库") }
                    }
                }
            }

            // 远程仓库（本工程）
            Surface(color = CandySurface, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("远程仓库（本工程）", color = CandyText, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = remoteText,
                        onValueChange = { remoteText = it },
                        label = { Text("https / ssh 地址", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CandyText),
                    )
                    Spacer(Modifier.width(6.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            ProjectStore.setRemote(project, remoteText.trim())
                            if (repoReady && remoteText.isNotBlank()) {
                                runGit("设置远程") { DesktopGit.setRemote(project, remoteText.trim()) }
                            }
                            state.toast = "已保存本工程的远程仓库地址"
                        }) { Text("保存地址") }
                    }
                }
            }

            // 云端有更新
            if (repoReady && behind > 0) {
                Surface(color = CandySurface, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("云端有更新", color = CandyPrimary, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Text("云端领先 $behind 个提交，建议先拉取再继续编辑。", color = CandyTextDim, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { runGit("拉取") { DesktopGit.pull(project) } }) { Text("拉取更新") }
                    }
                }
            }

            // 状态摘要
            val st = status
            if (st == null) {
                Text("读取中…", color = CandyTextDim, fontSize = 11.sp)
            } else if (st.isBlank()) {
                Text("工作区干净（无改动）", color = CandySecondary, fontSize = 11.sp)
            } else {
                Text("改动：", color = CandyTextDim, fontSize = 11.sp)
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(st.split("\n").filter { it.isNotBlank() }) { l ->
                        Text(l, color = CandyText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // 提交
            OutlinedTextField(
                value = commitMsg,
                onValueChange = { commitMsg = it },
                label = { Text("提交信息", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = CandyText),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val name = commitMsg.ifBlank { nextCommitName() }
                    runGit("提交") {
                        if (!repoReady) DesktopGit.GitResult(false, "尚未初始化 git 仓库")
                        else {
                            // 没配过身份就用全局设置兜底
                            DesktopGit.configIdentity(project, Settings.gitUserName, Settings.gitUserEmail)
                            DesktopGit.commitAll(project, name)
                        }
                    }
                    commitMsg = ""
                }) { Text("提交") }
                TextButton(onClick = {
                    val name = commitMsg.ifBlank { nextCommitName() }
                    runGit("推送") {
                        DesktopGit.commitAll(project, name)
                        DesktopGit.push(project)
                    }
                    commitMsg = ""
                }) { Text("提交并推送") }
                TextButton(onClick = { showHistory = true; history = DesktopGit.log(project) }) { Text("回退") }
                TextButton(onClick = { showDiscardConfirm = true }) { Text("丢弃") }
                TextButton(onClick = { refresh() }) { Text("刷新") }
            }

            // 结果行
            if (msg.isNotBlank()) {
                Text(msg, color = CandyTextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // 丢弃确认
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("丢弃修改") },
            text = { Text("将丢弃所有未提交的修改（含未跟踪的新文件），还原到最近一次提交。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    runGit("丢弃") { DesktopGit.discardChanges(project) }
                }) { Text("丢弃") }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("取消") } },
        )
    }

    // 历史回退
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("回退到历史提交") },
            text = {
                if (history.isEmpty()) Text("没有提交历史", color = CandyTextDim)
                else LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(history) { c ->
                        TextButton(onClick = {
                            showHistory = false
                            runGit("回退") { DesktopGit.resetTo(project, c.sha) }
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(c.subject, color = CandyText, fontSize = 12.sp)
                                Text(c.date.take(16) + " · " + c.sha.take(7), color = CandyTextDim, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("取消") } },
        )
    }
}
