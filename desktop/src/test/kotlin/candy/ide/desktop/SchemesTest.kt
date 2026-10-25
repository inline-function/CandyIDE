package candy.ide.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 配色方案：内置、自定义存储/读取/删除、当前方案切换 */
class SchemesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Rule fun isolation() = SettingsIsolationRule()

    @Test fun builtins_are_shipped() {
        val ids = Schemes.BUILTINS.map { it.id }
        assertTrue(ids.contains("fuhuan"))
        assertTrue(ids.contains("default"))
        // token 列表非空且每个内置方案都有全部 token 颜色
        assertTrue(Schemes.TOKEN_LABELS.isNotEmpty())
        Schemes.BUILTINS.forEach { s ->
            Schemes.TOKEN_LABELS.forEach { (t, _) ->
                assertNotNull("builtin ${s.id} lacks $t", s.colors[t])
            }
        }
    }

    @Test fun custom_save_list_delete() {
        Settings.workspaceRoot = tmp.newFolder("projects").absolutePath
        val scheme = Scheme("mine", "我的", false,
            colors = Schemes.BUILTINS[0].colors.toMap(), flags = mapOf("keyword" to 3))
        Schemes.saveCustom(scheme)
        // 列表能读到
        val list = Schemes.listCustom()
        assertEquals(1, list.size)
        assertEquals("mine", list[0].id)
        assertEquals("我的", list[0].name)
        // 粗斜体标记回读
        assertEquals(3, list[0].flags["keyword"])
        assertNull(list[0].flags["text"])
        // byId 能解析
        val loaded = Schemes.byId("mine")
        assertEquals("mine", loaded.id)
        assertEquals("我的", loaded.name)
        // 删除后消失
        Schemes.deleteCustom("mine")
        assertEquals(0, Schemes.listCustom().size)
    }

    @Test fun select_and_current_updates() {
        val before = Schemes.current.id
        Schemes.select("default")
        assertEquals("default", Schemes.current.id)
        assertEquals("default", Settings.schemeId)
        Schemes.select("fuhuan")
        assertEquals("fuhuan", Schemes.current.id)
        Schemes.select(before)
    }

    @Test fun byId_unknown_falls_back_to_first_builtin() {
        assertEquals(Schemes.BUILTINS[0].id, Schemes.byId("nope").id)
        // 旧名迁移
        assertEquals("fuhuan", Schemes.byId("darcula").id)
        assertEquals("default", Schemes.byId("candy").id)
    }

    @Test fun colorOf_defaults_for_missing_token() {
        Schemes.select("fuhuan")
        assertEquals(0xFF1E1F22.toInt(), Schemes.colorOf("background"))
        assertEquals(0xFFBFBFBF.toInt(), Schemes.colorOf("not-a-token"))
    }
}
