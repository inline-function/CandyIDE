package candy.ide.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IdeStateTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val isolation = SettingsIsolationRule()

    @Test fun open_edit_dirty_save() {
        val f = tmp.newFile("a.subl")
        f.writeText("old")
        val s = IdeState()
        s.open(f)
        assertEquals("old", s.activeText())
        assertFalse(s.dirty[f.absolutePath] == true)

        s.editText(f, "new")
        assertEquals("new", s.fileTexts[f.absolutePath])
        assertTrue(s.dirty[f.absolutePath] == true)

        assertTrue(s.save(f))
        assertFalse(s.dirty[f.absolutePath] == true)
        assertEquals("new", f.readText())

        // 再改成别的内容 -> 脏；改回刚保存的内容 -> 干净
        s.editText(f, "other")
        assertTrue(s.dirty[f.absolutePath] == true)
        s.editText(f, "new")
        assertFalse(s.dirty[f.absolutePath] == true)
    }

    @Test fun close_removes_buffers() {
        val f = tmp.newFile("b.subl")
        val s = IdeState()
        s.open(f)
        s.close(f)
        assertNull(s.fileTexts[f.absolutePath])
        assertNull(s.activeFile)
    }

    @Test fun openProject_opens_entry_and_tracks_window() {
        val proj = tmp.newFolder("proj")
        File(proj, "main.subl").writeText("fun main() {}")
        val s = IdeState()
        s.openProject(proj)
        assertEquals(proj, s.currentProject)
        assertEquals("main.subl", s.activeFile?.name)

        s.openProjectWindow(proj)
        assertEquals(1, s.openProjectWindows.size)
        s.openProjectWindow(proj) // 幂等
        assertEquals(1, s.openProjectWindows.size)
        s.closeProjectWindow(proj)
        assertEquals(0, s.openProjectWindows.size)
    }

    @Test fun output_and_log_split_lines() {
        val s = IdeState()
        s.output("a\nb\rc\r\nd")
        assertEquals(4, s.outputLines.size)
        s.log("e\nf")
        assertEquals(2, s.logLines.size)
        s.log("x", OutputLine.Kind.Error)
        assertEquals(OutputLine.Kind.Error, s.logLines.last().kind)
    }
}