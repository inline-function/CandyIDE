package candy.plugin.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import candy.ide.plugin.contrib.EditorViewContribution
import candy.ide.plugin.preview.ComposePreviewState

/** 编辑器视图扩展：把预览三件套挂回宿主编排（容器与导航归宿主） */
class AndroidEditorView : EditorViewContribution {
    override val id: String = "compose-preview"

    @Composable
    override fun FloatingPanel(
        state: ComposePreviewState,
        onToggleFullscreen: () -> Unit,
        onClose: () -> Unit,
        onApply: (String) -> Unit,
        onDrag: (IntOffset) -> Unit,
    ) = ComposePreviewPanel(
        state = state,
        fullscreen = false,
        editEnabled = false,
        onToggleFullscreen = onToggleFullscreen,
        onClose = onClose,
        onApply = onApply,
        onDrag = onDrag,
    )

    @Composable
    override fun VirtScreen(state: ComposePreviewState?, onBack: () -> Unit) =
        candy.plugin.android.ui.VirtScreen(state, onBack)

    @Composable
    override fun PreviewScreen(
        state: ComposePreviewState?,
        onBack: () -> Unit,
        onApply: (String, ComposePreviewState) -> Unit,
    ) = candy.plugin.android.ui.PreviewScreen(state, onBack, onApply)
}
