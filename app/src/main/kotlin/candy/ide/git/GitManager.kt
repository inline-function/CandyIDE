package candy.ide.git

import android.content.Context
import android.net.Uri
import candy.ide.fs.CandyPath
import candy.ide.terminal.TerminalBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Git 操作引擎：所有 git 命令都交给协同 App 执行（Termux 的 git 二进制；Shizuku 降级），
 * 工作目录用工程的真实文件系统路径（SAF 的 /tree/primary:... 映射到 /storage/emulated/0/...）。
 *
 * 同步语义：写命令脚本 -> Termux RUN_COMMAND 执行 -> 轮询中转输出文件（带超时）。
 * 远端操作（fetch/push/pull）经 Termux 的网络栈，仓库 remote 由设置页配置。
 */
object GitManager {

    @Volatile
    var appCtx: android.content.Context? = null

    /** 调试输出：写 files/candy/logs/agent_debug.log（adb logcat 与设备日志页都能看到） */
    private fun dbg(msg: String) {
        val c = appCtx ?: return
        runCatching {
            val dir = java.io.File(candy.ide.engine.Engine.root(c), "logs")
            dir.mkdirs()
            val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            java.io.File(dir, "agent_debug.log").appendText("[" + ts + "] " + msg + "\n")
        }
    }

    private const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin/git"
    private const val TERMUX_PKG = "/data/data/com.termux/files/usr/bin/pkg"
    private const val TERMUX_PKG_PROCESS = "com.termux"
    private const val RELAY_DIR = TerminalBridge.RELAY_DIR
    private const val OUT_FILE = TerminalBridge.OUT_FILE
    private const val GIT_MARKER = "$RELAY_DIR/.git_done"

    data class GitResult(val ok: Boolean, val output: String) {
        val exitCode: Int get() = if (ok) 0 else 1
    }

    data class CommitInfo(
        val hash: String,
        val time: String,
        val message: String,
    )

    /** RUN_COMMAND 通道是否可用：发 echo 探针，1.5s 内中转文件有内容即通。
     *  Termux 默认不允许外部应用调用（~/.termux/termux.properties 需 allow-external-apps=true），
     *  没开启时服务收 intent 但不执行 —— 探针就是为这个区分。 */
    /** 同步探针（TerminalBridge 等非协程场景用） */
    fun runCommandReadyBlocking(ctx: Context, timeoutMs: Long = 1500): Boolean {
        if (!isTermuxInstalled(ctx) || !TerminalBridge.hasRunCommandGrant(ctx)) return false
        val relay = File(RELAY_DIR)
        runCatching { relay.mkdirs() }
        File(OUT_FILE).delete()
        File("$RELAY_DIR/.git_done").delete()
        runCatching {
            File("$RELAY_DIR/probe.sh").writeText(
                "#!/data/data/com.termux/files/usr/bin/sh\necho candyalive > " + OUT_FILE + "\necho done > $RELAY_DIR/.git_done\n",
            )
        }.getOrElse { return false }
        val intent = android.content.Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("$RELAY_DIR/probe.sh"))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", RELAY_DIR)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        val sent = runCatching { ctx.startService(intent); true }.getOrElse { false }
        if (!sent) { dbg("通道探针：startService 失败"); return false }
        var waited = 0L
        while (waited < timeoutMs) {
            Thread.sleep(60)
            waited += 60
            if (File(OUT_FILE).isFile) {
                val ok = runCatching { File(OUT_FILE).readText() }.getOrDefault("").contains("candyalive")
                dbg("通道探针：命中（" + waited + "ms）")
                File(OUT_FILE).delete()
                File("$RELAY_DIR/.git_done").delete()
                return ok
            }
        }
        val termuxRunning = runCatching {
            val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.any { it.processName == TERMUX_PKG_PROCESS } == true
        }.getOrDefault(false)
        dbg("通道探针：超时未响应（" + (if (termuxRunning) "Termux 在运行，未开启 allow-external-apps？" else "Termux 未在运行") + "）")
        return false
    }

    suspend fun runCommandReady(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        runCommandReadyBlocking(ctx)
    }

    // ---- 安装检测 ----

    fun isShizukuInstalled(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    }.getOrDefault(false)

    fun isTermuxInstalled(ctx: Context): Boolean = TerminalBridge.isTermuxInstalled(ctx)

    /** Termux 里有没有装 git：执行 `git --version`，执行成功即已装 */
    suspend fun isGitReady(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isTermuxInstalled(ctx)) { dbg("git 就绪检查：Termux 未安装"); return@withContext false }
        if (!TerminalBridge.hasRunCommandGrant(ctx)) {
            dbg("git 就绪检查：RUN_COMMAND 未授权")
            return@withContext false
        }
        try {
            val r = execViaTermux(ctx, null, TERMUX_BIN, "--version")
            dbg("git 就绪检查：ok=" + r.ok + " out=" + r.output.take(120))
            r.ok && r.output.contains("git version")
        } catch (t: Throwable) {
            dbg("git 就绪检查异常：" + t)
            false
        }
    }

    /** 安装 git：pkg install git -y（输出走中转文件，UI 显示加载框等待完成） */
    suspend fun installGit(ctx: Context): GitResult = withContext(Dispatchers.IO) {
        dbg("开始安装 git：pkg install git -y")
        val r = execViaTermux(ctx, null, TERMUX_PKG, "install", "git", "-y")
        dbg("安装 git 结果：ok=" + r.ok + " out=" + r.output.take(200))
        r
    }

    // ---- 命令执行 ----

    /** 经 Termux RUN_COMMAND 同步执行一条命令（脚本输出写入中转文件并轮询） */
    private suspend fun execViaTermux(ctx: Context, cwd: String?, executable: String, vararg args: String): GitResult {
        if (!isTermuxInstalled(ctx)) { dbg("exec：Termux 未安装"); return GitResult(false, "Termux 未安装") }
        if (!TerminalBridge.hasRunCommandGrant(ctx)) { dbg("exec：RUN_COMMAND 未授权"); return GitResult(false, "Termux 未授予 RUN_COMMAND 权限") }
        val relay = File(RELAY_DIR)
        relay.mkdirs()
        // 旧的输出/完成标记清掉
        File(OUT_FILE).delete()
        File(GIT_MARKER).delete()
        val cmdLine = (listOf(executable) + args.toList()).joinToString(" ") { shellQuote(it) }
        File("$RELAY_DIR/git_cmd.sh").writeText(
            "#!/data/data/com.termux/files/usr/bin/sh\n" +
                (if (cwd != null) "cd " + shellQuote(cwd) + "\n" else "") +
                cmdLine + " > " + shellQuote(OUT_FILE) + " 2>&1\n" +
                "echo done > " + shellQuote(GIT_MARKER) + "\n",
        )
        val intent = android.content.Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("$RELAY_DIR/git_cmd.sh"))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", cwd ?: RELAY_DIR)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        val sent = runCatching { ctx.startService(intent); true }.getOrElse { false }
        if (!sent) { dbg("exec：startService 失败"); return GitResult(false, "无法提交到 Termux（RUN_COMMAND 被拒）") }
        dbg("exec：已提交到 Termux，等待完成标记…")
        // 轮询完成标记（git --version 秒回；pkg install 可能要 60s+）
        var waited = 0
        val timeoutMs = 240_000L
        var poll = 10L
        while (waited < timeoutMs) {
            delay(poll)
            waited += poll.toInt()
            if (File(GIT_MARKER).isFile) {
                val out = runCatching { File(OUT_FILE).readText() }.getOrDefault("")
                File(OUT_FILE).delete()
                File(GIT_MARKER).delete()
                val ok = !out.contains("fatal:", ignoreCase = true) &&
                    !out.contains("E:", ignoreCase = true)
                return GitResult(ok, out.trim())
            }
            poll = (poll * 1.5).toLong().coerceAtMost(300L)
        }
        return GitResult(false, "命令执行超时（Termux 无响应）")
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** 在工程目录跑 git（cwd 为真实文件路径） */
    private suspend fun git(ctx: Context, cwd: String, vararg args: String): GitResult =
        execViaTermux(ctx, cwd, TERMUX_BIN, *args)

    // ---- 工作区路径 ----

    /** 工程的真实文件系统路径；SAF primary: 映射到 /storage/emulated/0/，Local 直接用 */
    fun realPath(project: CandyPath): String? {
        val fsPath = runCatching { project.fsPath() }.getOrNull()
        if (fsPath != null) return fsPath
        return null
    }

    // ---- 高层操作 ----

    /** 初始化仓库（--allow-empty 提交让后续操作干净） */
    suspend fun initRepo(ctx: Context, project: CandyPath, initialMessage: String? = null): GitResult {
        val cwd = realPath(project) ?: return GitResult(false, "无法确定工程的真实路径（SAF 目录树不支持）")
        var r = git(ctx, cwd, "init")
        if (!r.ok) return r
        if (initialMessage != null) {
            // 首次提交（如果有文件就提交，没有就建空提交占位）
            r = git(ctx, cwd, "add", "-A")
            r = git(ctx, cwd, "commit", "-m", initialMessage, "--allow-empty")
        }
        return r
    }

    suspend fun configIdentity(ctx: Context, cwd: String, name: String, email: String): GitResult {
        var r = git(ctx, cwd, "config", "user.name", name)
        if (!r.ok) return r
        return git(ctx, cwd, "config", "user.email", email)
    }

    suspend fun setRemote(ctx: Context, cwd: String, url: String): GitResult {
        if (url.isBlank()) return GitResult(true, "")
        return git(ctx, cwd, "remote", "set-url", "origin", url)
    }

    /** 提交全部改动 */
    suspend fun commitAll(ctx: Context, project: CandyPath, message: String): GitResult {
        val cwd = realPath(project) ?: return GitResult(false, "无法确定工程的真实路径")
        var r = git(ctx, cwd, "add", "-A")
        if (!r.ok) return r
        r = git(ctx, cwd, "commit", "-m", message)
        if (!r.ok && r.output.contains("nothing to commit")) return GitResult(true, "没有需要提交的改动")
        return r
    }

    /** 推送到云端 */
    suspend fun push(ctx: Context, project: CandyPath): GitResult {
        val cwd = realPath(project) ?: return GitResult(false, "无法确定工程的真实路径")
        val branch = currentBranch(ctx, cwd)
        return git(ctx, cwd, "push", "-u", "origin", branch.ifBlank { "master" })
    }

    /** 从云端拉取 */
    suspend fun pull(ctx: Context, project: CandyPath): GitResult {
        val cwd = realPath(project) ?: return GitResult(false, "无法确定工程的真实路径")
        return git(ctx, cwd, "pull")
    }

    /** 云端是否有更新：fetch 后比对本地与远程领先数 */
    suspend fun fetchAndCheckAhead(ctx: Context, project: CandyPath): UpdateInfo {
        val cwd = realPath(project) ?: return UpdateInfo(error = "无法确定工程的真实路径")
        val fr = git(ctx, cwd, "fetch", "origin")
        if (!fr.ok) return UpdateInfo(error = if (fr.output.isBlank()) "fetch 失败" else fr.output)
        val branch = currentBranch(ctx, cwd).ifBlank { "master" }
        val behind = runCatching {
            val out = git2(ctx, cwd, "rev-list", "--count", "HEAD..origin/" + branch)
            out.output.trim().toIntOrNull() ?: 0
        }.getOrDefault(0)
        val ahead = runCatching {
            val out = git2(ctx, cwd, "rev-list", "--count", "origin/" + branch + "..HEAD")
            out.output.trim().toIntOrNull() ?: 0
        }.getOrDefault(0)
        return UpdateInfo(behind = behind, ahead = ahead)
    }

    data class UpdateInfo(val behind: Int = 0, val ahead: Int = 0, val error: String? = null)

    /** 丢弃：还原所有未提交修改（工作区与暂存区） */
    suspend fun discardChanges(ctx: Context, project: CandyPath): GitResult {
        val cwd = realPath(project) ?: return GitResult(false, "无法确定工程的真实路径")
        var r = git(ctx, cwd, "reset", "--hard", "HEAD")
        if (r.ok) r = git(ctx, cwd, "clean", "-fd")
        return r
    }

    /** 回退到某个提交 */
    suspend fun resetTo(ctx: Context, project: CandyPath, ref: String): GitResult {
        val cwd = realPath(project) ?: return GitResult(false, "无法确定工程的真实路径")
        var r = git(ctx, cwd, "reset", "--hard", ref)
        if (r.ok) r = git(ctx, cwd, "clean", "-fd")
        return r
    }

    /** 提交历史（时间倒序） */
    suspend fun log(ctx: Context, project: CandyPath): List<CommitInfo> {
        val cwd = realPath(project) ?: return emptyList()
        val r = git2(ctx, cwd, "log", "--pretty=format:%H|%cI|%s", "-50")
        if (!r.ok) return emptyList()
        return r.output.lines().mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size == 3) CommitInfo(parts[0], parts[1], parts[2]) else null
        }
    }

    /** 本地提交计数（用于“第 n 次推送”默认存档名） */
    suspend fun commitCount(ctx: Context, project: CandyPath): Int {
        val cwd = realPath(project) ?: return 0
        val r = git2(ctx, cwd, "rev-list", "--count", "HEAD")
        return r.output.trim().toIntOrNull() ?: 0
    }

    private suspend fun git2(ctx: Context, cwd: String, vararg args: String): GitResult =
        execViaTermux(ctx, cwd, TERMUX_BIN, *args)

    private suspend fun currentBranch(ctx: Context, cwd: String): String {
        val r = git2(ctx, cwd, "rev-parse", "--abbrev-ref", "HEAD")
        return r.output.trim()
    }

    /** 仓库是否已初始化：.git 目录存在 */
    fun isRepoInitialized(project: CandyPath): Boolean {
        val cwd = realPath(project) ?: return false
        return File(cwd, ".git").isDirectory
    }
}

/** CandyPath 真实文件路径扩展：Local 实现直接给路径；Saf 的 primary: 映射到共享存储 */
fun CandyPath.fsPath(): String? = when (this) {
    is CandyPath.Local -> file.absolutePath
    is CandyPath.Saf -> {
        // uri content://com.android.externalstorage.documents/tree/primary%3ACandyIDE/document/primary%3ACandyIDE%2Fproj
        val decoded = Uri.decode(uri().path ?: "")
        val afterDoc = decoded.substringAfter("/document/", "")
        val primary = afterDoc.substringAfter("primary:", "")
        if (afterDoc.startsWith("primary:") && primary.isNotEmpty()) "/storage/emulated/0/" + primary else null
    }
}
