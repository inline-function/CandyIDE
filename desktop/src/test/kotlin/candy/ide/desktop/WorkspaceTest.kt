package candy.ide.desktop

import candy.ide.desktop.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 工程文件树工具（排序/隐藏文件过滤/zip 解压） */
class WorkspaceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun children_sorted_dirs_first_and_hidden_skipped() {
        val proj = tmp.newFolder("proj")
        File(proj, "b.subs").writeText("1")
        File(proj, "a.md").writeText("2")
        File(proj, ".hidden").writeText("3")
        File(proj, "sub").mkdirs()
        val names = Workspace.children(proj).map { it.name to it.isDir }
        assertEquals(listOf("sub" to true, "a.md" to false, "b.subs" to false), names)
    }

    @Test fun walk_sources_skips_build_and_hidden() {
        val proj = tmp.newFolder("proj")
        File(proj, "a.subs").writeText("1")
        File(proj, "b.subl").writeText("2")
        File(proj, "c.txt").writeText("3")
        val build = File(proj, "build"); build.mkdirs(); File(build, "out.subs").writeText("4")
        val git = File(proj, ".git"); git.mkdirs(); File(git, "x.subs").writeText("5")
        val rels = Workspace.walkSources(proj).map { it.second }.sorted()
        assertEquals(listOf("a.subs", "b.subl"), rels)
    }

    @Test fun extract_archive() {
        val proj = tmp.newFolder("proj")
        val zip = File(proj, "res.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("d/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("d/a.subs"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
        }
        val dest = File(proj, "res")
        assertEquals(true, Workspace.extractArchive(zip, dest))
        assertEquals(true, File(dest, "d").isDirectory)
        assertEquals("hello", File(dest, "d/a.subs").readText())
        // 损坏的 zip 返回 false 而不是抛
        val bad = File(proj, "bad.zip")
        bad.writeText("not a zip")
        assertEquals(false, Workspace.extractArchive(bad, File(proj, "bad")))
    }

    @Test fun zip_to_roundtrip() {
        val proj = tmp.newFolder("proj")
        File(proj, "a.subs").writeText("hello")
        val sub = File(proj, "sub"); sub.mkdirs()
        File(sub, "b.md").writeText("doc")
        File(proj, ".secret").writeText("no")
        val outdir = tmp.newFolder("out")
        val out = File(outdir, "proj.capr")
        assertEquals(true, Workspace.zipTo(proj, out))
        val dest = File(outdir, "unpacked")
        assertEquals(true, Workspace.extractArchive(out, dest))
        assertEquals(true, File(dest, "a.subs").isFile)
        assertEquals("hello", File(dest, "a.subs").readText())
        assertEquals(true, File(dest, "sub/b.md").isFile)
        // 隐藏文件不入归档
        assertEquals(false, File(dest, ".secret").exists())
    }
}
