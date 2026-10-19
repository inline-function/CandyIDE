package candy.ide.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * JS 执行器：用系统 WebView 跑 sublimation 编译出的 JS。
 * 零额外依赖；console.log 直达 onLine，未捕获异常走 ERROR 级别。
 * WebView 必须在主线程创建与调用，用完即 destroy()。
 *
 * prelude：readLine/readFile 等内建 IO 编译出 Buffer + require('fs')，
 * WebView 里这俩不存在——注入一个最小垫片（与 jsapk 模板运行时同构）：
 * IDE 内 readLine 直接当 EOF（Err），真输入对话框在打包 APK 里才有。
 */
object JsRuntime {

    private val mainFinish = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var finished = false

    /** WebView 注入桥：JS 调 readln() 弹输入框；不提供输入（null）即 EOF */
    private class CandyBridge(val onInput: (() -> String?)?) {
        @android.webkit.JavascriptInterface
        fun readln(): String = onInput?.invoke() ?: ""
    }

    /** node 垫片：Buffer.alloc + require('fs')（readSync 恒返回 EOF）+ process.argv */
    private fun prelude(): String = """
        (function(){
          function FakeBuffer(n){ this.length = n; this._ = new Array(n); }
          // node Buffer 写入的是字节，toString('utf8') 负责解码——必须把 charCode 还原成字符，
          // 否则 readLine 会把 "21" 变成 "5049"（charCode 直接拼接）
          FakeBuffer.prototype.slice = function(a, b){
            var arr = this._.slice(a, b);
            var fake = { length: arr.length, _: arr };
            fake.toString = function(){ return arr.map(function(c){ return String.fromCharCode((c | 0) & 0xff); }).join(""); };
            return fake;
          };
          if (!globalThis.Buffer) globalThis.Buffer = { alloc: function(n){ return new FakeBuffer(n); } };
          if (!globalThis.require) globalThis.require = function(m){
            if (m !== 'fs') throw new Error('require: 不支持的模块 ' + m);
            return { readSync: function(_fd, b, off, len, _pos){
              var s = CandyBridge.readln();
              if (!s) return -1;
              var n = Math.min(s.length, len);
              for (var i = 0; i < n; i++) b._[off + i] = s.charCodeAt(i);
              return n;
            } };
          };
          if (!globalThis.process) globalThis.process = { argv: [] };
        })();
    """.trimIndent()

    /** JS → 原生收尾桥（evaluateJavascript 异步回调里调） */
    private class FinishBridge(val onDone: () -> Unit) {
        @android.webkit.JavascriptInterface
        fun finish() { onDone() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun run(ctx: Context, js: String, onLine: (String) -> Unit, onDone: () -> Unit, onInput: (() -> String?)? = null) {
        val wv = WebView(ctx)
        finished = false
        try {
            wv.settings.javaScriptEnabled = true
            wv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    val m = consoleMessage ?: return true
                    val text = m.message() ?: ""
                    android.util.Log.i("CandyEngine", text)
                    onLine(
                        when (m.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR, ConsoleMessage.MessageLevel.WARNING ->
                                "[" + m.messageLevel().name + "] " + text
                            else -> text
                        }
                    )
                    return true
                }
            }
            // 生成的 JS 末尾为 `main();`：main 是 async function 时该调用立即返回 Promise，
            // 同步执行的顶层 IIFE 结束 ≠ 程序结束——等 main 的 Promise 落定（成功/失败）再收尾。
            // 注入输入桥（addJavascriptInterface 的对象成员即 JS 全局名）
            wv.addJavascriptInterface(CandyBridge(onInput), "CandyBridge")
            wv.addJavascriptInterface(FinishBridge {
                // JS binder 线程回调：WebView 操作必须切回主线程，否则 "Java exception was raised"
                mainFinish.post {
                    if (!finished) { finished = true; mainFinish.removeCallbacksAndMessages(null); wv.destroy(); onDone() }
                }
            }, "__bridgeFinish")
            // 三段式：prologue（先注册 error/超时监听）→ 用户 JS（尾部 main(); 改成存结果到全局）→
            // trailer（拿结果 Promise 注册 then）。prologue 在前，用户代码同步抛错也能被 error 监听接住。
            // main 是 async function，返回 Promise；同步 main（非 async）则 __dsh_r 为 undefined，立即 finish。
            val prologue = """
                (function(){
                  var __done = false;
                  globalThis.__dsh_finish = function(err){
                    if (__done) return; __done = true;
                    if (err) console.error('[错误] ' + err);
                    __bridgeFinish.finish();
                  };
                  window.addEventListener('error', function(e){ globalThis.__dsh_finish(e.message); });
                  window.addEventListener('unhandledrejection', function(e){ globalThis.__dsh_finish('' + (e.reason || e)); });
                  setTimeout(function(){ globalThis.__dsh_finish('[程序结束] 超时保护（300s）'); }, 300000);
                })();
            """.trimIndent()
            val inner = js.replace(
                Regex("""main\(\);\s*\n\}\)\(\);\s*$"""),
                "globalThis.__dsh_r = main();\n})();",
            )
            val trailer = """
                Promise.resolve(globalThis.__dsh_r).then(
                  function(){ globalThis.__dsh_finish(); },
                  function(e){ globalThis.__dsh_finish('' + (e || e)); }
                );
            """.trimIndent()
            wv.evaluateJavascript(prelude() + "\n" + prologue + "\n" + inner + "\n" + trailer) { _ -> /* 收尾由 __bridgeFinish 驱动 */ }
            // 兜底：JS 侧超时保护失效（WebView 崩溃等极端情况）才强制收尾，避免等待输入时误杀
            mainFinish.postDelayed({ if (!finished) { finished = true; wv.destroy(); onDone() } }, 320000)
        } catch (t: Throwable) {
            wv.destroy()
            onDone()
            throw t
        }
    }
}
