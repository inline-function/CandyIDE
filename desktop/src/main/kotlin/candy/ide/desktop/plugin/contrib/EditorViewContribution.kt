package candy.ide.desktop.plugin.contrib

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import candy.ide.desktop.plugin.preview.ComposePreviewState

/**
 * 编辑器/视图扩展（对齐安卓端 editor-view 贡献点）：@composable 布局预览。
 *
 * 桌面端与安卓端的容器差异：安卓端是「悬浮面板 + Virt 全屏 + 预览构建器全屏」三入口，
 * 宿主负责 Surface/位移/返回键与导航；桌面端并入编辑器右侧栏一个面板（多窗口平台特性），
 * 宿主提供侧栏容器（尺寸由 [modifier] 给定、应用写回与关闭由回调驱动）。
 * 因此本接口只保留 [FloatingPanel]——安卓端三入口在桌面端合并为侧栏，不再分别导航。
 */
interface EditorViewContribution {

    val id: String

    /** 侧栏面板内容：宿主提供容器（modifier 给定位置与尺寸）与关闭/应用回调 */
    @Composable
    fun FloatingPanel(
        state: ComposePreviewState,
        modifier: Modifier,
        onClose: () -> Unit,
        onApply: (String) -> Unit,
    )
}
