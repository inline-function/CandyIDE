package candy.ide.plugin.preview

/** @composable 布局预览状态：函数名 + 声明行范围 + 视图树 JSON（来自 LSP composableTreeJson） */
data class ComposePreviewState(
    val fnName: String,
    val start: Int,
    val end: Int,
    val treeJson: String,
)
