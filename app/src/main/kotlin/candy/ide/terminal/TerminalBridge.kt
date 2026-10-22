package candy.ide.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import candy.ide.jsapk.ShizukuShell
import java.io.File

/**
 * 终端通道：三级降级
 *  1) Shizuku（已装并授权）—— 直接执行 shell，输出实时返回（真终端能力）
 *  2) Termux RUN_COMMAND —— 把命令交给 Termux 跑（需 Termux 授予 RUN_COMMAND 权限）
 *  3) 打开 Termux —— 意图跳过去手动敲
 *
 * Termux 命令输出走公共目录中转：/sdcard/Download/.candy_term/out.txt
 * （两个 App 都能访问的交集位置；Termux 默认有存储权，IDE 走 MANAGE_EXTERNAL_STORAGE）
 */
object TerminalBridge {

    const val RELAY_DIR = "/sdcard/Download/.candy_term"
    const val OUT_FILE = "$RELAY_DIR/out.txt"

    enum class Channel { SHIZUKU, TERMUX, TERMUX_UNSUITABLE, NONE }

    data class Result(val channel: Channel, val output: String)

    /** 执行一条命令：优先 Shizuku，其次 Termux，都不行返回提示。[cwd] 指定工作目录
     *  [forceTermux] = true 时一律走 Termux（agent 的 shell 用：Shizuku 的 shell 环境没有 git 等工具链） */
    fun exec(ctx: Context, cmd: String, cwd: String? = null, forceTermux: Boolean = false): Result {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return Result(Channel.NONE, "")
        // git 命令必须走 Termux：Shizuku 的 shell 用户被 SELinux 挡在 /data/data/com.termux 之外，
        // PATH 里也没有 git，Shizuku 通道跑 git 必定 "command not found"
        val isGitCmd = trimmed.startsWith("git ") || trimmed == "git" || trimmed.startsWith("git\t")
        if (forceTermux || isGitCmd) {
            // 强制 Termux / git：先探 RUN_COMMAND 通道，没通直接给开启提示（避免空等轮询像卡住）
            if (!candy.ide.git.GitManager.runCommandReadyBlocking(ctx)) {
                return Result(Channel.TERMUX_UNSUITABLE,
                    "Termux 通道未就绪（未授权、未允许外部应用调用，或 Termux 没在运行）\n去开启：打开 Termux 一次；或在 Termux 里执行：\necho 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings")
            }
            val via = runGitViaTermux(ctx, trimmed, cwd)
            if (via != null) return via
        }
        if (!forceTermux && ShizukuShell.exec(arrayOf("sh", "-c", "echo ok")) != null) {
            // 1) Shizuku
            val full = if (cwd != null) "cd \"" + cwd + "\" && " + trimmed else trimmed
            val out = ShizukuShell.exec(arrayOf("sh", "-c", full)) ?: "（无输出）"
            return Result(Channel.SHIZUKU, out)
        }
        // 2) Termux RUN_COMMAND：把命令包成脚本，输出重定向到中转文件
        if (isTermuxInstalled(ctx)) {
            if (!hasRunCommandGrant(ctx)) return Result(Channel.TERMUX_UNSUITABLE,
                "Termux 未授予 RUN_COMMAND 权限\n请在 系统设置→应用→CandyIDE→权限 里授予，或改用 Shizuku")

            val relay = File(RELAY_DIR)
            relay.mkdirs()
            File("$RELAY_DIR/cmd.sh").writeText(
                buildString {
                    append("#!/system/bin/sh\n")
                    if (cwd != null) append("cd \"\"" + cwd + "\"\" && \n")
                    append(trimmed).append(" > out.txt 2>&1\n")
                },
            )
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("$RELAY_DIR/cmd.sh"))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", cwd ?: RELAY_DIR)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            val sent = runCatching { ctx.startService(intent); true }.getOrElse { false }
            if (sent) return Result(Channel.TERMUX, "已提交到 Termux 执行，输出写入 $OUT_FILE\n（若限权被拒，请在 Termux 内授权 RUN_COMMAND，或改用 Shizuku）")
        }
        // 3) 打开 Termux
        return Result(Channel.NONE, "Shizuku 与 Termux 均不可用\n（装 Termux：设置 → 伴生软件）")
    }

    /** 读 Termux 中转输出（调一次后轮询） */
    fun readRelay(): String? {
        val f = File(OUT_FILE)
        if (!f.isFile) return null
        val txt = runCatching { f.readText() }.getOrNull() ?: return null
        f.delete()
        return txt
    }

    /** 把 git 命令交给 Termux 执行：写脚本 -> RUN_COMMAND -> 中转输出文件（与 GitManager 同机制） */
        /** 把 git 命令交给 Termux 执行：写脚本 -> RUN_COMMAND -> 中转输出文件（与 GitManager 同机制） */
    private fun runGitViaTermux(ctx: Context, cmd: String, cwd: String?): Result? {
        if (!isTermuxInstalled(ctx)) return null
        if (!hasRunCommandGrant(ctx)) return Result(Channel.TERMUX_UNSUITABLE,
            "Termux 未授予 RUN_COMMAND 权限\nIDE 里的 git 命令必须走 Termux（Shizuku 的 shell 看不见 Termux 的 git）\n请在系统设置→应用→CandyIDE→权限 里授予")
        val relay = File(RELAY_DIR)
        relay.mkdirs()
        File(OUT_FILE).delete()
        File("$RELAY_DIR/.term_done").delete()
        val script = buildString {
            append("#!/data/data/com.termux/files/usr/bin/sh\n")
            if (cwd != null) append("cd \"" + cwd + "\"\n")
            append(cmd).append(" > ").append(OUT_FILE).append(" 2>&1\n")
            append("echo done > $RELAY_DIR/.term_done\n")
        }
        File("$RELAY_DIR/git_via.sh").writeText(script)
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("$RELAY_DIR/git_via.sh"))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", cwd ?: RELAY_DIR)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        val sent = runCatching { ctx.startService(intent); true }.getOrElse { false }
        if (!sent) return Result(Channel.TERMUX_UNSUITABLE, "无法提交到 Termux（RUN_COMMAND 被拒）")
        return Result(Channel.TERMUX, "已提交到 Termux 执行 git，输出写入 $OUT_FILE")
    }
    /** CandyIDE 是否已被授予 Termux RUN_COMMAND（dangerous 级运行时权限） */
    fun hasRunCommandGrant(ctx: Context): Boolean = runCatching {
        ctx.checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isTermuxInstalled(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo("com.termux", 0)
        true
    }.getOrDefault(false)

    fun openTermux(ctx: Context): Boolean = runCatching {
        val launch = ctx.packageManager.getLaunchIntentForPackage("com.termux")
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launch)
            true
        } else false
    }.getOrDefault(false)

    fun openShizuku(ctx: Context) {
        runCatching {
            val launch = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                ?: return@runCatching
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launch)
        }
    }

    /** 下载 Shizuku / Termux：跳浏览器到官方发布页 */
    fun download(ctx: Context, which: Companion) {
        val url = when (which) {
            Companion.SHIZUKU -> "https://github.com/RikkaApps/Shizuku/releases/latest"
            Companion.TERMUX -> "https://github.com/termux/termux-app/releases/latest"
        }
        runCatching {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }

    enum class Companion { SHIZUKU, TERMUX }
}
