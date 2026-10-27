package candy.ide.plugin

import candy.plugin.android.AndroidProjectPlugin
import candy.plugin.android.platform.SublimationAndroidPlugin
import candy.plugin.android.platform.SublimationGamePlugin
import candy.plugin.android.platform.SublimationWasmPlugin
import candy.plugin.android.project.AndroidProjectType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sugared.functor.lsp.LspAnalysis
import sugared.functor.lsp.Position
import java.io.File

/**
 * t50 回归测试：插件→语言服务扩展→LSP 补全 的完整通路（断点曾为
 * AndroidProjectPlugin.setup 因资产路径前缀缺失抛异常 → 全部贡献未注册）。
 *
 * 此处覆盖：setup 注册工程类型/编辑器视图/语言服务扩展 → 工程类型通知启停 →
 * LspAnalysis 注册扩展 → 补全含/不含安卓运行时符号（jsapk 工程场景）。
 * 人工验证（真机/模拟器）步骤见任务 t50 输出。
 */
class PluginLspChainTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** PluginRegistry 是全局单例：清空各贡献列表，免得两个用例互相污染（注册表按 id/name 去重） */
    @org.junit.Before
    fun resetPluginRegistry() {
        PluginRegistry::class.java.declaredFields.forEach { f ->
            f.isAccessible = true
            (f.get(PluginRegistry) as? MutableList<*>)?.clear()
        }
    }

    private fun newHost(): FakePluginHost {
        val root = tmp.newFolder("materialized")
        val stdlibDir = File(root, "stdlib").apply { mkdirs() }
        File(stdlibDir, "runtime.subl").writeText(
                "// 测试用安卓运行时符号（真实资产在 android/stdlib/*.subl）\n" +
                "@android @impure fun droidCall(body: @impure (Any) => Null): Null\n" +
                "fun helperCount(): Int\n",
            )
        File(root, "procs").mkdirs()
        return FakePluginHost(root)
    }

    @Test
    fun fullChain_jsapkProjectSeesAndroidSymbolsInCompletion() {
        val host = newHost()

        // 1) android-project 装配：jsapk 工程类型 + 预览编辑器视图 + 设置/安装贡献
        AndroidProjectPlugin().setup(host)
        assertNotNull(
            "editor-view 应已注册（Composable 预览面板可开的前提）",
            PluginRegistry.editorView(),
        )
        assertNotNull("jsapk 工程类型应已注册", PluginRegistry.projectType(AndroidProjectType.TYPE_ID))

        // 2) SfA 平台插件装配：android-stdlib 语言服务扩展（默认关，等工程类型通知）
        val sfa = SublimationAndroidPlugin().apply { setup(host) }
        val ext = PluginRegistry.lspExtensions().first { it.name == "android-stdlib" }
        assertFalse("扩展默认不启用", ext.isEnabled())

        // 3) 打开 jsapk 工程的等价动作：宿主按工程类型通知插件
        sfa.onProjectTypeChanged(AndroidProjectType.TYPE_ID, null)
        assertTrue("jsapk 工程应启用 android-stdlib 扩展", ext.isEnabled())

        // 4) LSP 分析链（与 CandyViewModel.analysis() 同构：注册全部插件扩展）
        val analysis = LspAnalysis().apply {
            loadPrelude()
            PluginRegistry.lspExtensions().forEach { registerExtension(it) }
        }
        analysis.updateDocument("demo.subl", "fun demo(): Int { 42 }\n")

        val labels = analysis.completionFor("demo.subl", Position(0, 0)).items.map { it.label }.toSet()
        assertTrue("补全应包含安卓运行时符号 droidCall", "droidCall" in labels)
        assertTrue("补全应包含安卓运行时符号 helperCount", "helperCount" in labels)

        // 5) 切回非安卓工程：扩展停用，补全不再含安卓符号
        sfa.onProjectTypeChanged("script", null)
        val labelsOff = analysis.completionFor("demo.subl", Position(0, 0)).items.map { it.label }.toSet()
        assertFalse("停用后补全不再含安卓符号", "droidCall" in labelsOff)
        assertFalse("停用后补全不再含安卓符号", "helperCount" in labelsOff)
    }

    @Test
    fun platformPlugins_areSystemAndRegistered() {
        val host = newHost()

        SublimationAndroidPlugin().setup(host)
        SublimationGamePlugin().setup(host)
        SublimationWasmPlugin().setup(host)

        val names = PluginRegistry.lspExtensions().map { it.name }.toSet()
        assertTrue("SfA 扩展 android-stdlib 已注册", "android-stdlib" in names)
        assertTrue("SfG 扩展 sublimation-game 已注册", "sublimation-game" in names)
        assertTrue("SfW 扩展 sublimation-wasm 已注册", "sublimation-wasm" in names)

        // 系统插件保护（t49 契约）：清单 system 标志 + 名单兜底
        for (m in listOf(
            SublimationAndroidPlugin().manifest,
            SublimationGamePlugin().manifest,
            SublimationWasmPlugin().manifest,
        )) {
            assertTrue("清单声明 system 插件", m.system)
            assertTrue("SystemPlugins 名单兜底", SystemPlugins.isSystem(m))
        }
    }
}
