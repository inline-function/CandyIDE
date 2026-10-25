package candy.ide.plugin.contrib

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import candy.ide.plugin.preview.ComposePreviewState

/**
 * 编辑器/视图扩展：@composable 布局预览三件套
 * （悬浮窗 + 虚拟桥接全屏 + 预览构建器全屏）。
 * 宿主负责容器（Surface、位移、返回键）与导航（Screen.Virt / Screen.Preview）。
 */
interface EditorViewContribution {

    val id: String

    /** 悬浮面板内容：宿主提供 Surface 容器、位移状态与拖动回调 */
    @Composable
    fun FloatingPanel(
        state: ComposePreviewState,
        onToggleFullscreen: () -> Unit,
        onClose: () -> Unit,
        onApply: (String) -> Unit,
        onDrag: (IntOffset) -> Unit,
    )

    /** 虚拟桥接环境全屏页（仅渲染不运行） */
    @Composable
    fun VirtScreen(state: ComposePreviewState?, onBack: () -> Unit)

    /** 布局预览全屏构建页（树编辑 → onApply 写回源码 → onBack 回编辑器） */
    @Composable
    fun PreviewScreen(state: ComposePreviewState?, onBack: () -> Unit, onApply: (String, ComposePreviewState) -> Unit)
}
