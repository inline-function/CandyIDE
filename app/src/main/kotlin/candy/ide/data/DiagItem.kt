package candy.ide.data

/**
 * 一条语言诊断：来自 sublimation 的 LspAnalysis。
 * 行列与编辑器下标一致（0-based）；severity 用 sora DiagnosticRegion 的常量。
 */
data class DiagItem(
    val severity: Short,
    val line: Int,
    val char: Int,
    val endLine: Int,
    val endChar: Int,
    val message: String,
)
