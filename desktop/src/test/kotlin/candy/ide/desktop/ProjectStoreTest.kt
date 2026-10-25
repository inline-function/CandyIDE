package candy.ide.desktop

import candy.ide.desktop.data.ProjectStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val isolation = SettingsIsolationRule()

    private fun store(): ProjectStore {
        val root = tmp.newFolder("projects")
        Settings.workspaceRoot = root.absolutePath
        return ProjectStore
    }

    @Test fun listProjects_empty() {
        val s = store()
        assertTrue(s.listProjects().isEmpty())
    }

    @Test fun create_and_list_js() {
        val s = store()
        val p = s.createProject("hello", ProjectStore.TYPE_JS)
        assertNotNull(p)
        assertEquals(ProjectStore.TYPE_JS, s.typeOf(p!!))
        assertTrue(File(p, "main.subl").isFile)
        assertEquals(1, s.listProjects().size)
    }

    @Test fun create_script_and_md() {
        val s = store()
        val script = s.createProject("s1", ProjectStore.TYPE_SCRIPT)!!
        assertEquals(ProjectStore.TYPE_SCRIPT, s.typeOf(script))
        assertTrue(File(script, "hello.subs").isFile)
        val md = s.createProject("m1", ProjectStore.TYPE_MD)!!
        assertEquals(ProjectStore.TYPE_MD, s.typeOf(md))
        assertTrue(File(md, "README.md").isFile)
    }

    @Test fun create_invalid_name() {
        val s = store()
        assertNull(s.createProject("", ProjectStore.TYPE_JS))
        assertNull(s.createProject("a/b", ProjectStore.TYPE_JS))
        assertNull(s.createProject("a:b", ProjectStore.TYPE_JS))
    }

    @Test fun create_duplicate() {
        val s = store()
        s.createProject("dup", ProjectStore.TYPE_JS)
        assertNull(s.createProject("dup", ProjectStore.TYPE_JS))
    }

    @Test fun typeOf_infers_from_content() {
        val s = store()
        val dir = File(s.projectsRoot(), "infer").apply { mkdirs() }
        assertEquals(ProjectStore.TYPE_SCRIPT, s.typeOf(dir))
        File(dir, "x.subl").writeText("fun main() {}")
        assertEquals(ProjectStore.TYPE_JS, s.typeOf(dir))
        File(dir, "x.subl").writeText("@composable fun v() {}")
        assertEquals(ProjectStore.TYPE_JSAPK, s.typeOf(dir))
    }

    @Test fun touchOpened_sorts_first() {
        val s = store()
        val a = s.createProject("a", ProjectStore.TYPE_JS)!!
        val b = s.createProject("b", ProjectStore.TYPE_JS)!!
        Thread.sleep(15)
        s.touchOpened(a)
        assertEquals(listOf(a, b), s.listProjects())
        Thread.sleep(15)
        s.touchOpened(b)
        assertEquals(listOf(b, a), s.listProjects())
    }

    @Test fun deleteProject() {
        val s = store()
        val p = s.createProject("gone", ProjectStore.TYPE_JS)!!
        assertTrue(s.deleteProject(p))
        assertFalse(p.exists())
        assertTrue(s.listProjects().isEmpty())
    }

    @Test fun rename() {
        val s = store()
        val p = s.createProject("old", ProjectStore.TYPE_JS)!!
        assertTrue(s.rename(p, "new"))
        assertFalse(p.exists())
        assertEquals(1, s.listProjects().size)
        assertEquals("new", s.listProjects().first().name)
    }
}