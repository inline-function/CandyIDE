package candy.ide.desktop.plugin.android

import sugared.functor.lsp.LspExtension
import java.io.File

/**
 * 语言服务扩展（对齐安卓端 candy.plugin.android.lsp.AndroidLspExtension，t14 契约）：
 * 把 android/stdlib 与 android/procs 并入 LSP 分析，工程类型为 jsapk 时启用
 * （@android 内建与处理器脚本的编辑期支持）。
 * 目录由插件物化的资产而来：%APPDATA%\CandyIDE\plugins\android-project\android/{stdlib,procs}。
 */
class DesktopAndroidLspExtension(
    private val stdlib: File,
    private val procs: File,
) : LspExtension {
    override val name: String = ID
    override val stdlibDir: File? get() = stdlib.takeIf { it.isDirectory }
    override val processorsDir: File? get() = procs.takeIf { it.isDirectory }

    /** 启停由宿主按工程类型通知插件（onProjectTypeChanged） */
    @Volatile
    var enabled: Boolean = false

    override fun isEnabled(): Boolean = enabled

    companion object {
        const val ID = "android-stdlib"
    }
}
