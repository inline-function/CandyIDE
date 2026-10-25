package candy.ide.desktop

import candy.ide.desktop.data.ProjectStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 桌面端引擎管线冒烟测试：引擎定位 / 实时诊断 / 补全 / 解释器直连 / 工程编译 */
class EngineSmokeTest {

    @Test
    fun engineLocated() {
        assertNotNull("sublimation 根目录应自动探测到", DesktopEngine.sublimationRoot)
        assertTrue("core 标准库应存在", DesktopEngine.coreDir.isDirectory)
        println("engine root: " + DesktopEngine.sublimationRoot?.absolutePath)
    }

    @Test
    fun diagnosticsAnalyze() {
        val uri = "file:///smoke.subl"
        val bad = "fun add(a: Nat, b: Nat): Nat = \"oops\""
        val diags = DesktopEngine.analyze(uri, bad)
        assertTrue("类型错误应产生诊断，实际：" + diags, diags.isNotEmpty())
        val good = "fun add(a: Nat, b: Nat): Nat = a + b"
        val okDiags = DesktopEngine.analyze("file:///smoke2.subl", good)
        println("good diagnostics: " + okDiags.joinToString { it.message })
    }

    @Test
    fun completionWorks() {
        // 补全按词前缀过滤：输入 wh 时应给出 when 等
        DesktopEngine.analyze("file:///c.subl", "fun add(a: Nat, b: Nat): Nat = a + b\nwh")
        val items = DesktopEngine.completion("file:///c.subl", 1, 2)
        val labels = items.map { it.label }
        assertTrue("补全应非空：" + labels.take(10), items.isNotEmpty())
        assertTrue("when 关键字应在补全表中：" + labels.take(20), labels.any { it == "when" })
    }

    @Test
    fun interpreterRunsScript() {
        val dir = createTempProject()
        val script = File(dir, "hello.subs")
        val latch = CountDownLatch(1)
        val lines = mutableListOf<String>()
        var err: Throwable? = null
        DesktopEngine.runDirect(script,
            onLine = { lines += it },
            onDone = { t -> err = t; latch.countDown() },
        )
        assertTrue("脚本应执行完毕", latch.await(60, TimeUnit.SECONDS))
        assertEquals("脚本不应报错：" + (err?.message ?: ""), null, err)
        assertTrue("应打印 Hello：" + lines, lines.any { it.contains("Hello") })
        println("script output: " + lines.joinToString(" | "))
    }

    @Test
    fun compileProjectGeneratesJs() {
        val dir = createTempProject()
        val (bag, js) = DesktopEngine.compile(dir)
        assertFalse("工程编译应通过：\n" + bag.report(), bag.hasError)
        assertNotNull("应生成 JS", js)
        println("compiled js size: " + (js?.length ?: 0))
    }

    private fun createTempProject(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "candy-test-" + System.nanoTime())
        dir.mkdirs()
        File(dir, "hello.subs").writeText(ProjectStore.HELLO_SUBS)
        File(dir, "main.subl").writeText(ProjectStore.MAIN_SUBL)
        return dir
    }
}
