package candy.jsapk.runtime

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Looper
import android.text.InputType
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sublimation JS-apk 控制台运行时：像 cmd 一样的黑底等宽字体输出。
 * 生成代码里 readLine 内联 require('fs').readSync —— WebView 没有 node，
 * 这里提供最小 polyfill：readSync 桥到「程序请求输入」对话框（带输入法），
 * 同步阻塞 JS 线程直到用户按确定。
 * 生成产物尾部是 main();——补 .then/.catch，跑完直接 finish() 退出应用。
 */
class MainActivity : Activity() {

    private lateinit var log: TextView
    private val lines = StringBuilder()
    private var finished = false
    @Volatile private var currentDialog: AlertDialog? = null

    @JavascriptInterface
    fun report(text: String) {
        runOnUiThread { appendLine(text) }
    }

    /** readLine 的同步实现：JS 线程 latch 阻塞，UI 线程弹输入框（自带输入法） */
    @JavascriptInterface
    fun readln(): String {
        if (Looper.myLooper() == Looper.getMainLooper()) return ""
        val latch = CountDownLatch(1)
        val result = arrayOf("" as String)
        runOnUiThread {
            val edit = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                hint = "在这里输入，回车或按确定提交"
                // 回车直接提交，与 cmd 一致
                setOnEditorActionListener { v, _, _ ->
                    result[0] = v.text.toString()
                    currentDialog?.dismiss()
                    latch.countDown()
                    true
                }
            }
            val dlg = AlertDialog.Builder(this)
                .setTitle("程序请求输入")
                .setView(edit)
                .setPositiveButton("确定") { _, _ ->
                    result[0] = edit.text.toString(); latch.countDown()
                }
                .setOnCancelListener {
                    result[0] = ""; latch.countDown()
                }
                .create()
            // 已弃用的窗口焦点标志：防 IME 隐藏后 dialog 不弹出（第二次 readln 的关键修复）
            @Suppress("DEPRECATION")
            dlg.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            currentDialog = dlg
            dlg.show()
            edit.requestFocus()
        }
        latch.await(5, TimeUnit.MINUTES)
        return result[0]
    }

    @JavascriptInterface
    fun done() {
        runOnUiThread {
            if (finished) return@runOnUiThread
            finished = true
            appendLine("")
            appendLine("[程序结束]")
            appendLine("（应用留在本页，可查看输出；需要时按返回键退出）")
        }
    }

    private fun appendLine(text: String) {
        if (lines.isNotEmpty()) lines.append('\n')
        lines.append(text)
        log.text = lines.toString()
        log.post { (log.parent.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildConsole())

        val web = WebView(this).apply { visibility = View.GONE }
        web.settings.javaScriptEnabled = true
        web.addJavascriptInterface(object : Any() {
            @JavascriptInterface fun report(t: String) = this@MainActivity.report(t)
            @JavascriptInterface fun readln() = this@MainActivity.readln()
            @JavascriptInterface fun done() = this@MainActivity.done()
        }, "CandyBridge")
        web.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(cm: android.webkit.ConsoleMessage?): Boolean {
                if (cm == null) return false
                val tag = when (cm.messageLevel()) {
                    android.webkit.ConsoleMessage.MessageLevel.ERROR -> "[错误] "
                    android.webkit.ConsoleMessage.MessageLevel.WARNING -> "[警告] "
                    else -> ""
                }
                report(tag + cm.message())
                return true
            }
        }

        val js = runCatching { assets.open("app.js").bufferedReader().use { it.readText() } }
            .getOrDefault("")
        if (js.isEmpty()) {
            report("**[运行时]** assets/app.js 为空：请在 CandyIDE 里先打包一次")
            return
        }
        web.evaluateJavascript(prelude() + "\n" + patchDone(js), null)
    }

    private fun buildConsole(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0C0C0C"))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        log = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(Color.parseColor("#C9D1D9"))
            text = ""
            setBackgroundColor(Color.parseColor("#0C0C0C"))
        }
        container.addView(log)
        scroll.addView(container)
        return scroll
    }

    /** node 沙盒最小 polyfill：Buffer / require('fs') / process */
    private fun prelude(): String = """(function(){
        function FakeBuffer(){ this.s=""; }
        FakeBuffer.prototype.slice = function(a, b){ var self = this;
          return { toString: function(){ return self.s.substring(a, b); } }; };
        globalThis.Buffer = { alloc: function(n){ return new FakeBuffer(); } };
        globalThis.require = function(m){
            if (m === 'fs') return {
                readSync: function(fd, b, off, len, pos){
                    var s = CandyBridge.readln();
                    if (s === null || s === undefined) return -1;
                    b.s = s;
                    return s.length;
                },
                existsSync: function(p){ return false; },
                readFileSync: function(p){ return ""; },
                writeFileSync: function(p){},
                appendFileSync: function(p){}
            };
            throw new Error('JS-apk 沙盒里 require(\"' + m + '\") 不可用');
        };
        globalThis.process = { argv: [] };
        globalThis.onerror = function(msg, src, line, col){
            try { CandyBridge.report('[错误] ' + msg + ' @' + line + ':' + col); } catch(e){}
        };
    })();"""

    /** 生成产物尾部 main(); —— 接上完成回调：跑完（或崩掉）都退出应用 */
    private fun patchDone(js: String): String {
        // 出错时只报错不退出：错误留在屏上，用户看完自己退
        val hook = "main().then(function(){ CandyBridge.done(); }, function(e){ CandyBridge.report('[错误] ' + (e && e.message || String(e))); })"
        return js.replace("main();\n})();", hook + "\n})();")
            .replace("\n  main();\n", "\n  " + hook + "\n")
    }
}
