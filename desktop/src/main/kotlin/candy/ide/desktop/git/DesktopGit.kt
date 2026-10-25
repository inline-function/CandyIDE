package candy.ide.desktop.git

import java.io.File

/** 桌面端 git 封装（对应安卓端 candy.ide.git.GitManager，直接调用本机 git） */
object DesktopGit {
    data class GitResult(val ok: Boolean, val output: String)
    data class CommitInfo(val sha: String, val date: String, val subject: String)
    data class UpdateInfo(val behind: Int = 0, val ahead: Int = 0, val error: String? = null)

    fun hasRepo(project: File): Boolean = File(project, ".git").exists()

    /** 执行 git 命令（合并 stderr，超时 60s） */
    fun run(cwd: File, vararg args: String): GitResult = runCatching {
        val p = ProcessBuilder(listOf("git") + args.toList())
            .directory(cwd)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        val code = p.waitFor()
        GitResult(code == 0, out.trim())
    }.getOrElse { GitResult(false, it.message ?: it.javaClass.simpleName) }

    fun currentBranch(cwd: File): String =
        run(cwd, "rev-parse", "--abbrev-ref", "HEAD").output.takeIf { it.isNotBlank() && !it.startsWith("fatal") } ?: ""

    /** 初始化仓库（--allow-empty 提交让后续操作干净） */
    fun initRepo(project: File, initialMessage: String? = null): GitResult {
        var r = run(project, "init")
        if (!r.ok) return r
        if (initialMessage != null) {
            run(project, "add", "-A")
            r = run(project, "commit", "-m", initialMessage, "--allow-empty")
        }
        return r
    }

    fun configIdentity(cwd: File, name: String, email: String): GitResult {
        val r = run(cwd, "config", "user.name", name)
        if (!r.ok) return r
        return run(cwd, "config", "user.email", email)
    }

    fun setRemote(cwd: File, url: String): GitResult {
        if (url.isBlank()) return GitResult(true, "")
        // 已有 origin 就改地址；没有就新增（先走 set-url，失败再 add）
        val set = run(cwd, "remote", "set-url", "origin", url)
        if (set.ok) return set
        return run(cwd, "remote", "add", "origin", url)
    }

    /** 提交全部改动 */
    fun commitAll(project: File, message: String): GitResult {
        var r = run(project, "add", "-A")
        if (!r.ok) return r
        r = run(project, "commit", "-m", message)
        if (!r.ok && r.output.contains("nothing to commit")) return GitResult(true, "没有需要提交的改动")
        return r
    }

    fun push(project: File): GitResult {
        val branch = currentBranch(project)
        return run(project, "push", "-u", "origin", branch.ifBlank { "master" })
    }

    fun pull(project: File): GitResult = run(project, "pull")

    /** 云端是否有更新：fetch 后比对本地与远程领先数 */
    fun fetchAndCheckAhead(project: File): UpdateInfo {
        val fr = run(project, "fetch", "origin")
        if (!fr.ok) return UpdateInfo(error = if (fr.output.isBlank()) "fetch 失败" else fr.output)
        val branch = currentBranch(project).ifBlank { "master" }
        val behind = runCatching {
            run(project, "rev-list", "--count", "HEAD..origin/" + branch).output.trim().toIntOrNull() ?: 0
        }.getOrDefault(0)
        val ahead = runCatching {
            run(project, "rev-list", "--count", "origin/" + branch + "..HEAD").output.trim().toIntOrNull() ?: 0
        }.getOrDefault(0)
        return UpdateInfo(behind = behind, ahead = ahead)
    }

    /** 丢弃：还原所有未提交修改（工作区与暂存区） */
    fun discardChanges(project: File): GitResult {
        var r = run(project, "reset", "--hard", "HEAD")
        if (r.ok) r = run(project, "clean", "-fd")
        return r
    }

    /** 回退到某个提交 */
    fun resetTo(project: File, ref: String): GitResult {
        var r = run(project, "reset", "--hard", ref)
        if (r.ok) r = run(project, "clean", "-fd")
        return r
    }

    /** 提交历史（时间倒序，前 50 条） */
    fun log(project: File): List<CommitInfo> {
        val r = run(project, "log", "--pretty=format:%H|%cI|%s", "-50")
        if (!r.ok) return emptyList()
        return r.output.lines().mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size == 3) CommitInfo(parts[0], parts[1], parts[2]) else null
        }
    }

    /** 本地提交计数（用于「第 n 次推送」默认存档名） */
    fun commitCount(project: File): Int =
        runCatching { run(project, "rev-list", "--count", "HEAD").output.trim().toIntOrNull() ?: 0 }.getOrDefault(0)
}
