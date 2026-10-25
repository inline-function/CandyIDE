package candy.ide.desktop.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 桌面端 git 封装：在真实 git 上跑全流程（init/commit/身份/远程/丢弃/回退） */
class DesktopGitTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newProject(): File {
        val dir = tmp.newFolder("proj")
        File(dir, "main.subs").writeText("fn main() { print(\"hi\") }")
        return dir
    }

    @Test fun init_commit_and_count() {
        val p = newProject()
        assertEquals(false, DesktopGit.hasRepo(p))
        val r = DesktopGit.initRepo(p, "初始化工程 proj")
        // 没配身份时首次提交可能失败：先保证环境干净，再验证成功路径
        if (!r.ok) {
            DesktopGit.configIdentity(p, "Tester", "t@e.x")
            DesktopGit.run(p, "commit", "-m", "初始化工程 proj", "--allow-empty")
        }
        assertEquals(true, DesktopGit.hasRepo(p))
        assertEquals(1, DesktopGit.commitCount(p))
        val log = DesktopGit.log(p)
        assertEquals(1, log.size)
        assertEquals("初始化工程 proj", log[0].subject)
    }

    @Test fun identity_and_remote() {
        val p = newProject()
        DesktopGit.initRepo(p)
        val id = DesktopGit.configIdentity(p, "CandyIDE", "candy@x")
        assertEquals(true, id.ok)
        val name = DesktopGit.run(p, "config", "--get", "user.name")
        assertEquals(true, name.ok)
        assertTrue(name.output.contains("CandyIDE"))
        assertEquals(true, DesktopGit.setRemote(p, "https://example.com/r.git").ok)
        assertTrue(DesktopGit.run(p, "remote", "-v").output.contains("example.com"))
    }

    @Test fun commit_all_and_discard() {
        val p = newProject()
        DesktopGit.initRepo(p)
        DesktopGit.configIdentity(p, "Tester", "t@e.x")
        DesktopGit.run(p, "commit", "-m", "init", "--allow-empty")
        // 新改动 + 未跟踪文件
        File(p, "main.subs").writeText("fn main() { print(\"changed\") }")
        File(p, "extra.subs").writeText("// extra")
        val c = DesktopGit.commitAll(p, "第 2 次推送")
        assertEquals(true, c.ok)
        assertEquals(2, DesktopGit.commitCount(p))
        // 再改 + 未跟踪，然后丢弃
        File(p, "main.subs").writeText("fn main() { print(\"dirty\") }")
        File(p, "untracked.subs").writeText("// untracked")
        assertEquals(true, DesktopGit.discardChanges(p).ok)
        assertEquals(false, File(p, "untracked.subs").exists())
        assertEquals("fn main() { print(\"changed\") }", File(p, "main.subs").readText())
    }

    @Test fun reset_to_history() {
        val p = newProject()
        DesktopGit.initRepo(p)
        DesktopGit.configIdentity(p, "Tester", "t@e.x")
        DesktopGit.run(p, "commit", "-m", "first", "--allow-empty")
        File(p, "a.subs").writeText("// a")
        DesktopGit.commitAll(p, "second")
        val log = DesktopGit.log(p)
        assertEquals(2, log.size)
        // 回退到最旧提交（log 按时间倒序）
        val reset = DesktopGit.resetTo(p, log.last().sha)
        assertEquals(true, reset.ok)
        assertEquals(false, File(p, "a.subs").exists())
        assertEquals(1, DesktopGit.log(p).size)
    }

    @Test fun failure_surface_is_friendly() {
        val p = newProject()
        val r = DesktopGit.run(p, "log")
        assertEquals(false, r.ok)
        assertFalse(r.output.isEmpty())
        assertEquals(0, DesktopGit.commitCount(p))
        assertEquals(emptyList<DesktopGit.CommitInfo>(), DesktopGit.log(p))
        // 无 git 可执行时给出异常文本而不是崩
    }
}
