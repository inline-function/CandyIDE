package candy.ide.desktop.plugin

import candy.ide.desktop.IdeState
import candy.ide.desktop.data.ProjectStore
import candy.ide.desktop.plugin.android.AndroidAssets
import candy.ide.desktop.plugin.contrib.ProjectTypeContribution
import candy.ide.desktop.plugin.host.DesktopPluginHost
import candy.ide.desktop.plugin.preview.PreviewCodeGen
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 插件框架（desktop 照抄安卓端）：清单解析、注册表去重、内置插件、工程类型贡献、资产索引资源 */
class PluginFrameworkTest {

    @Test
    fun parses_manifest_fields() {
        val m = PluginManifest.parse(
            """{"id":"android-project","name":"安卓工程支持","version":"1.0.0",
               "description":"d","author":"CandyIDE","entryClass":"x.Foo",
               "contributions":["project-type:jsapk","lsp-extension:android-stdlib"],
               "minIdeVersion":"2.0.0"}"""
        )
        assertEquals("android-project", m.id)
        assertEquals("安卓工程支持", m.name)
        assertEquals("1.0.0", m.version)
        assertEquals("CandyIDE", m.author)
        assertEquals("x.Foo", m.entryClass)
        assertEquals(2, m.contributions.size)
        assertEquals("2.0.0", m.minIdeVersion)
    }

    @Test
    fun manifest_defaults_when_minimal() {
        val m = PluginManifest.parse("""{"id":"p2"}""")
        assertEquals("p2", m.id)
        assertEquals("p2", m.name)          // name 空时回退 id
        assertEquals("1.0.0", m.version)
        assertNull(m.author)
        assertTrue(m.contributions.isEmpty())
    }

    @Test
    fun registry_registers_and_dedups_by_id() {
        PluginRegistry.registerProjectType(TestType("dup-type"))
        PluginRegistry.registerProjectType(TestType("dup-type"))
        assertEquals(1, PluginRegistry.projectTypes().count { it.typeId == "dup-type" })
        assertEquals("dup-type", PluginRegistry.projectType("dup-type")?.typeId)
        assertNull(PluginRegistry.projectType("no-such-type"))
    }

    @Test
    fun builtin_index_resolves_android_project_with_aligned_contributions() {
        val p = BuiltInPluginIndex.resolve("android-project")
        assertNotNull(p)
        val m = p!!.manifest
        assertEquals("android-project", m.id)
        // 桌面端裁剪后的贡献清单（与资源 candy-plugin.json 一致；t50 后 lsp-extension 由 SfA 接管）
        assertEquals(3, m.contributions.size)
        assertTrue(m.contributions.contains("project-type:jsapk"))
        assertTrue(m.contributions.contains("editor-view:compose-preview"))
        assertFalse(m.contributions.contains("lsp-extension:android-stdlib"))
        // 安卓端独有贡献不在桌面端清单
        assertFalse(m.contributions.contains("app-installer:shizuku"))
        assertFalse(m.contributions.contains("settings-section:litea-sdk"))
    }

    @Test
    fun builtin_index_resolves_platform_system_plugins() {
        // 三平台插件实体化（t50 对齐）：SfA/SfG/SfW 均能解析、均为系统插件、贡献各自的语言服务扩展
        for ((id, ext) in listOf(
            "sublimation-android" to "lsp-extension:android-stdlib",
            "sublimation-game" to "lsp-extension:sublimation-game",
            "sublimation-wasm" to "lsp-extension:sublimation-wasm",
        )) {
            val p = BuiltInPluginIndex.resolve(id)
            assertNotNull("内置索引应能解析 $id", p)
            val m = p!!.manifest
            assertEquals(id, m.id)
            assertTrue("$id 应声明 system", m.system)
            assertTrue("SystemPlugins 名单兜底 $id", SystemPlugins.isSystem(m))
            assertEquals(listOf(ext), m.contributions)
        }
    }

    @Test
    fun android_project_type_claims_and_scaffolds_like_android_side() {
        val t = candy.ide.desktop.plugin.android.DesktopAndroidProjectType()
        val tmp = File(System.getProperty("java.io.tmpdir"), "candy-plugin-test-" + System.nanoTime())
        tmp.mkdirs()
        try {
            assertFalse(t.claimsProject(tmp))
            File(tmp, "main.subl").writeText("fun main(): Null { }")
            assertFalse(t.claimsProject(tmp))     // 无 @composable/@android 不认领
            File(tmp, "main.subl").writeText("@composable\nfun content(): Box { }")
            assertTrue(t.claimsProject(tmp))

            val proj = File(tmp, "proj").apply { mkdirs() }
            t.scaffold(proj, "proj")
            val main = File(proj, "main.subl")
            assertTrue(main.isFile)
            val body = main.readText()
            assertTrue(body.contains("@composable"))
            assertTrue(body.contains("layout(content)"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun projectstore_typeOf_recognizes_plugin_type_and_claims() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "candy-plugin-type-" + System.nanoTime())
        tmp.mkdirs()
        try {
            // 无标记 + 无特征文件：兜底脚本工程
            assertEquals(ProjectStore.TYPE_SCRIPT, ProjectStore.typeOf(tmp))
            // 无标记 + @composable 源码：插件认领
            File(tmp, "main.subl").writeText("@composable\nfun content(): Box { }")
            assertEquals("jsapk", ProjectStore.typeOf(tmp))
            // 标记插件类型：直接读标记
            File(tmp, "candy.project").writeText("type=jsapk")
            assertEquals("jsapk", ProjectStore.typeOf(tmp))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun host_materializes_assets_from_classpath_with_version_marker() {
        // 内置 android-project 插件资产索引必须在 classpath 上（genPluginIndex 任务产出）
        val index = resourceText("candy-plugins/android-project/assets-index.txt")
        assertNotNull("assets-index.txt 缺失——检查 genPluginIndex 任务是否执行", index)
        assertTrue(index!!.isNotEmpty())
        // 应列出 android/stdlib 与 android/procs 下的资产
        assertTrue(index.contains("android/stdlib/"))
        assertTrue(index.contains("android/procs/"))

        // 清单同样在 classpath 上
        val manifest = resourceText("candy-plugins/android-project/candy-plugin.json")
        assertNotNull(manifest)
        val m = PluginManifest.parse(manifest!!)
        assertEquals("android-project", m.id)
    }

    @Test
    fun loader_reads_plugin_index_resource() {
        val ids = resourceText("candy-plugins/plugin-index.txt")
        assertNotNull("plugin-index.txt 缺失——检查 genPluginIndex 任务是否执行", ids)
        // 四个内置插件：android-project + 三平台系统插件
        for (id in listOf("android-project", "sublimation-android", "sublimation-game", "sublimation-wasm")) {
            assertTrue("plugin-index.txt 应含 $id", ids!!.contains(id))
        }
    }

    /**
     * t50 对齐：assetRoot 为「资源根下全路径」（candy-plugins/android-project/android）时，
     * 宿主规范化掉前缀物化到 plugins/<id>/android（t16 落盘布局不变，旧标记继续有效），
     * 索引内每个文件都真实落盘、版本标记正确。
     */
    @Test
    fun host_materializes_full_prefixed_asset_path_from_classpath() {
        val s = IdeState()
        val data = File(System.getProperty("java.io.tmpdir"), "candy-host-test-" + System.nanoTime()).apply { mkdirs() }
        // 把插件数据目录重定向到临时目录（避免写真实 %APPDATA%）
        val host = object : DesktopPluginHost(s) {
            override fun pluginDataDir(pluginId: String): File = File(data, pluginId)
        }
        try {
            val root = host.materializeAssets(
                pluginId = AndroidAssets.PLUGIN_ID,
                assetRoot = AndroidAssets.ASSET_PATH,   // "candy-plugins/android-project/android"
                version = AndroidAssets.VERSION,
            )
            assertEquals(File(File(data, AndroidAssets.PLUGIN_ID), "android").absolutePath, root.absolutePath)
            assertTrue("stdlib 应物化", File(root, "stdlib").isDirectory)
            assertTrue("procs 应物化", File(root, "procs").isDirectory)
            // 索引里的每个文件都应真实落盘
            val index = resourceText("candy-plugins/" + AndroidAssets.PLUGIN_ID + "/assets-index.txt")!!
            for (line in index.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
                val rel = line.removePrefix("android/")
                assertTrue("物化缺失资产：$line", File(root, rel).isFile)
            }
            // 版本标记
            val marker = File(root.parentFile, root.name + ".version")
            assertEquals(AndroidAssets.VERSION.toString(), marker.readText().trim())
            // 幂等：版本一致不重抽，目录仍在
            val root2 = host.materializeAssets(AndroidAssets.PLUGIN_ID, AndroidAssets.ASSET_PATH, AndroidAssets.VERSION)
            assertEquals(root.absolutePath, root2.absolutePath)
            assertTrue(File(root, "stdlib").isDirectory)
        } finally {
            data.deleteRecursively()
        }
    }

    /** 占位工程类型（仅测试注册/路由；不认领工程，不污染其它测试的 typeOf） */
    private class TestType(override val typeId: String) : ProjectTypeContribution {
        override fun displayName(): String = "测试类型"
        override fun description(): String = "测试"
        override fun scaffold(dir: File, name: String) {}
        override fun claimsProject(dir: File): Boolean = false
        override fun runShort(host: PluginHost) {}
        override fun packageProject(host: PluginHost, dir: File) {}
    }
}
