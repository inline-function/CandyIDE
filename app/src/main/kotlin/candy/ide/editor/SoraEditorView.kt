package candy.ide.editor

import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import androidx.compose.ui.viewinterop.AndroidView
import candy.ide.data.Settings
import candy.ide.lang.SublimationAnalyzer
import candy.ide.lang.SublimationLanguage
import candy.ide.vm.CandyViewModel
import io.github.rosemoe.sora.event.EventManager
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.Unsubscribe
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorContextMenuCreator
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import java.io.File
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion

/**
 * 把 SoraEditor 的 CodeEditor（View）包进 Compose。
 * - file 变化时重新载入文本
 * - 设置（配色/字体/字号/换行）变化即重应用
 * - 长按交给 IDE 自己的菜单（替代 sora 内置粘贴工具条）；光标处的诊断悬浮窗用 sora 内置组件
 * - 拿到 editor 实例后通过 onEditorReady 抛给上层（供 保存/运行/诊断定位 读正文）
 */
@Composable
fun SoraEditorView(
    modifier: Modifier,
    file: File?,
    vm: CandyViewModel,
    textProvider: (File) -> String = { it.readText() },
    onEditorReady: (CodeEditor) -> Unit,
    onLongPress: (x: Float, y: Float, line: Int, column: Int) -> Unit = { _, _, _, _ -> },
    onDoubleClick: (x: Float, y: Float, line: Int, column: Int) -> Unit = { _, _, _, _ -> },
    // 补全条目长按：position → 条目处理（跳转定义 / 关键词模板）
    completionLongClick: (label: String, kind: CompletionItemKind?) -> Unit = { _, _ -> },
    onEditorClick: () -> Unit = {},
    onCursorChange: (line: Int, column: Int) -> Unit = { _, _ -> },
    onTextChange: (String) -> Unit = {},
) {
    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var layoutInstalled by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf<File?>(null) }
    val ctx = LocalContext.current

    // 读一次 State：这几个键变化会触发 update 重跑
    val scheme = Schemes.current
    val fontId = Settings.fontId
    val fontSize = Settings.fontSize
    val wordWrap = Settings.wordWrap

    AndroidView(
        modifier = modifier,
        factory = { fctx ->
            CodeEditor(fctx).apply {
                Schemes.applyTo(this)
                FontManager.typeface(fctx, fontId)?.let { setTypefaceText(it) }
                val fname = vm.currentFile.value?.name
                val editorRef = this
                setEditorLanguage(
                    if (fname != null && (fname.endsWith(".subl") || fname.endsWith(".subs"))) {
                        SublimationLanguage(
                            uri = { vm.currentFile.value?.name },
                            lsp = { vm.analysis() },
                            // 补全前同步给 LSP 的是编辑器实时正文；拿不到时退回磁盘内容
                            latestText = { editorRef.text?.toString() ?: "" },
                            analyzer = SublimationAnalyzer { text -> vm.onAnalyzed(text) },
                        )
                    } else {
                        io.github.rosemoe.sora.lang.EmptyLanguage()
                    }
                )
                setTextSize(fontSize.toFloat())
                isWordwrap = wordWrap
                isLineNumberEnabled = true

                // 补全弹窗换带长按的布局（sora 默认布局无长按回调）
                val ac = getComponent(EditorAutoCompletion::class.java)
                if (ac != null && !layoutInstalled) {
                    layoutInstalled = true
                    val lp = candy.ide.editor.LongPressCompletionLayout()
                    lp.onItemLongClick = { pos ->
                        // 条目数据从补全列表的 adapter 取（EditorCompletionAdapter.getItem）
                        val ad = lp.getCompletionList().adapter
                        val item = ad?.let { runCatching { it.getItem(pos) }.getOrNull() }
                            as? io.github.rosemoe.sora.lang.completion.CompletionItem
                        if (item != null) {
                            completionLongClick(item.label?.toString() ?: "", item.kind)
                        }
                    }
                    ac.setLayout(lp)
                }
                // 光标停在诊断位置时的悬浮提示：用 sora 内置组件（读 setDiagnostics 的数据）
                getComponent(EditorDiagnosticTooltipWindow::class.java)?.isEnabled = true
                // 关掉 sora 自带的长按粘贴工具条与系统上下文菜单，改成 IDE 自己的菜单
                getComponent(EditorTextActionWindow::class.java)?.isEnabled = false
                getComponent(EditorContextMenuCreator::class.java)?.isEnabled = false
                // 长按期间长按选词会派发 SelectionChangeEvent：抑制其误判为双击
                var lastLongPressAt = 0L
                var doubleFired = false   // 同一次双击只让 ClickEvent 与 SelectionChangeEvent 其中之一弹菜单
                subscribeEvent(LongPressEvent::class.java, object : EventReceiver<LongPressEvent> {
                    override fun onReceive(event: LongPressEvent, unsubscribe: Unsubscribe) {
                        lastLongPressAt = android.os.SystemClock.uptimeMillis()
                        onLongPress(event.x, event.y, event.line, event.column)
                    }
                })
                // 点击编辑器任意处：上层用来关掉长按菜单等浮层；
                // 同处 300ms 内连点两次 = 双击 → 快捷操作菜单（sora 无 DoubleTapEvent，计时模拟）
                var lastClickAt = 0L
                var lastClickX = 0f
                var lastClickY = 0f
                subscribeEvent(ClickEvent::class.java, object : EventReceiver<ClickEvent> {
                    override fun onReceive(event: ClickEvent, unsubscribe: Unsubscribe) {
                        val now = android.os.SystemClock.uptimeMillis()
                        val isDouble = now - lastClickAt < 300 &&
                            Math.abs(event.x - lastClickX) < 60f && Math.abs(event.y - lastClickY) < 60f
                        lastClickAt = now
                        lastClickX = event.x
                        lastClickY = event.y
                        if (isDouble) {
                            lastClickAt = 0L
                            doubleFired = true
                            onDoubleClick(event.x, event.y, event.line, event.column)
                        } else {
                            onEditorClick()
                        }
                    }
                })
                // 光标移动：上层用来在诊断位置弹悬浮提示；
                // 双击选词检测：sora 的双击不派发第二次 ClickEvent（第二下手势被 onDoubleTap
                // 吞去做 selectWord），改在选区事件里抓「由collapsed变成选区」的状态跳变
                var lastCollapsed = true
                var selfSelection = false
                subscribeEvent(SelectionChangeEvent::class.java, object : EventReceiver<SelectionChangeEvent> {
                    override fun onReceive(event: SelectionChangeEvent, unsubscribe: Unsubscribe) {
                        val left = event.left
                        onCursorChange(left.line, left.column)
                        val collapsed = event.left == event.right
                         // 双击（或长按选词之外的手段）产生了非折叠选区：上层弹快捷操作菜单
                        // 长按选词后 800ms 内不当作双击（长按菜单与双击菜单互不串扰）
                        val afterLongPress = android.os.SystemClock.uptimeMillis() - lastLongPressAt < 800
                        if (!collapsed && lastCollapsed && !selfSelection && !isInLongSelect && !afterLongPress && !doubleFired) {
                            onDoubleClick(0f, 0f, left.line, left.column)
                        }
                        doubleFired = false
                        lastCollapsed = collapsed
                        selfSelection = false
                    }
                })
                // 回车换行：问 LSP 要 onTypeFormatting 的智能缩进，再应用回编辑器
                var indentApplying = false
                subscribeEvent(ContentChangeEvent::class.java, object : EventReceiver<ContentChangeEvent> {
                    override fun onReceive(event: ContentChangeEvent, unsubscribe: Unsubscribe) {
                        onTextChange(text.toString())
                        if (indentApplying) return
                        if (event.action != ContentChangeEvent.ACTION_INSERT) return
                        if (event.changedText.toString() != "\n") return
                        val targetLine = event.changeStart.line + 1
                        post {
                            runCatching {
                                val content = text
                                val edit = vm.indentEditsOnEnter(content.toString(), targetLine)?.firstOrNull() ?: return@runCatching
                                val start = runCatching { content.getCharIndex(edit.range.start.line, edit.range.start.character) }.getOrNull() ?: return@runCatching
                                val end = runCatching { content.getCharIndex(edit.range.end.line, edit.range.end.character) }.getOrNull() ?: return@runCatching
                                if (start > end || end > content.toString().length) return@runCatching
                                indentApplying = true
                                try {
                                    content.replace(start, end, edit.newText)
                                } finally {
                                    indentApplying = false
                                }
                                // 光标落到插入文本第一段行尾（缩进位）
                                val col = edit.newText.indexOf('\n').let { if (it < 0) edit.newText.length else it }
                                runCatching { setSelection(edit.range.start.line, col, true) }
                            }
                        }
                    }
                })
                fixImeTouch(fctx)
            }
        },
        update = { ed ->
            if (editor != ed) {
                editor = ed
                onEditorReady(ed)
            }
            Schemes.applyTo(ed)
            FontManager.typeface(ctx, fontId)?.let { ed.setTypefaceText(it) }
            ed.setTextSize(fontSize.toFloat())
            ed.isWordwrap = wordWrap
            if (loaded != file) {
                loaded = file
                val text = if (file == null) "" else runCatching { textProvider(file) }.getOrDefault("")
                ed.setText(text)
            }
        }
    )

    // 注意键必须是 Unit：键变化时 onDispose 闭包读到的 editor 已是新实例，
    // 会误把正在用的编辑器 release()（release 后 released=true，布局收尾回调全部跳过，
    // layoutBusy 卡住、isEditable() 假 → 输入法弹得出但打不进字、诊断也不画）
    DisposableEffect(Unit) {
        onDispose { editor?.release() }
    }
}

/**
 * 输入法修复：
 * Sora 在单指抬起时调用 imm.showSoftInput，但 IME 只服务于「已取得焦点」的 View。
 * 在 Compose 的 AndroidView 里，View 不会因为被点按而自动抢焦点，
 * 于是表现为「光标能动、键盘不弹」。
 * 在 ACTION_DOWN 时主动 requestFocus（抬起时 sora 自己负责 showSoftInput）。
 */
private fun CodeEditor.fixImeTouch(ctx: Context) {
    val imm = ctx.getSystemService(InputMethodManager::class.java)
    setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                runCatching { v.requestFocus() }
            }
        }
        false   // 不消费：sora 自己的触摸处理照常走
    }
}
