package candy.ide.editor

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import io.github.rosemoe.sora.widget.component.CompletionLayout
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * 补全弹窗布局：复制 sora 的 DefaultCompletionLayout，补上「条目长按」回调。
 * sora 0.23.7 的默认布局只注册了 OnItemClickListener，长按无响应；
 * 转到定义 / 关键词展开等二级操作从这里触发。
 */
class LongPressCompletionLayout : CompletionLayout {
    private var listView: ListView? = null
    private var progressBar: ProgressBar? = null
    private var rootView: LinearLayout? = null
    private var editorAutoCompletion: EditorAutoCompletion? = null
    private var enabledAnimation = false

    /** 条目长按：position 对应 EditorCompletionAdapter 的条目 */
    var onItemLongClick: ((position: Int) -> Unit)? = null

    /** 最后一次 attach 的条目（由 EditorCompletionAdapter 在 attachValues 时写入） */
    @Volatile var attachedItems: List<io.github.rosemoe.sora.lang.completion.CompletionItem>? = null

    override fun setEditorCompletion(completion: EditorAutoCompletion) {
        editorAutoCompletion = completion
    }

    override fun inflate(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // 填满弹窗给定宽度：条目水波纹与背景色才会左右充满补全框
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 0; bottomMargin = 0 }
        }
        val list = ListView(context).apply {
            visibility = View.VISIBLE
            // 条目必须填满补全框宽度：ListView 自身宽度由外层弹窗给定，
            // 子条目在 listWidth 基础上 MATCH_PARENT 才能左右充满 + 水波纹铺满
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            dividerHeight = 0
            isVerticalScrollBarEnabled = true
            scrollBarSize = 6
            // ListView 的行布局条目宽度由 adapter 的 item view 决定；
            // 真正的「充满」靠选择模式保持按下态水波纹
            choiceMode = ListView.CHOICE_MODE_NONE
        }
        root.addView(bar)
        root.addView(list)
        progressBar = bar
        listView = list
        rootView = root

        // 短按：交给 sora 的 EditorAutoCompletion 执行补全（与默认布局一致）
        list.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                editorAutoCompletion?.select(position)
            }
        // 长按：跳转定义 / 关键词模板
        list.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { _, _, position, _ ->
                onItemLongClick?.invoke(position)
                true
            }
        return root
    }

    override fun getCompletionList(): AdapterView<*> = listView!!

    override fun setLoading(loading: Boolean) {
        progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onApplyColorScheme(colorScheme: EditorColorScheme) {
        listView?.apply {
            setBackgroundColor(colorScheme.getColor(EditorColorScheme.COMPLETION_WND_BACKGROUND))
        }
        rootView?.apply {
            setBackgroundColor(colorScheme.getColor(EditorColorScheme.COMPLETION_WND_BACKGROUND))
        }
    }

    override fun ensureListPositionVisible(position: Int, increment: Int) {
        val lv = listView ?: return
        if (lv.adapter == null) return
        if (position + increment > lv.adapter.count - 1) {
            lv.setSelectionFromTop(position, 0)
        } else {
            lv.setSelectionFromTop(position + increment, 0)
        }
    }

    override fun setEnabledAnimation(enabled: Boolean) {
        enabledAnimation = enabled
    }
}
