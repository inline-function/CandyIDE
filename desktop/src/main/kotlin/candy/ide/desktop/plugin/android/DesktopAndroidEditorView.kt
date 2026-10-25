package candy.ide.desktop.plugin.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import candy.ide.desktop.plugin.contrib.EditorViewContribution
import candy.ide.desktop.plugin.android.ui.ComposePreviewPanel
import candy.ide.desktop.plugin.preview.ComposePreviewState

/**
 * 编辑器视图扩展（对齐安卓端 candy.plugin.android.ui.AndroidEditorView；桌面端容器为编辑器右侧栏）：
 * 把布局预览面板挂回宿主编排——面板内容（树/渲染/属性/应用写回）由插件提供，
 * 容器（侧栏尺寸与位置、关闭、应用写回编排）由宿主 EditorScreen 提供。
 */
class DesktopAndroidEditorView : EditorViewContribution {
    override val id: String = "compose-preview"

    @Composable
    override fun FloatingPanel(
        state: ComposePreviewState,
        modifier: Modifier,
        onClose: () -> Unit,
        onApply: (String) -> Unit,
    ) = ComposePreviewPanel(
        state = state,
        onClose = onClose,
        onApply = onApply,
        modifier = modifier,
    )
}
