package candy.ide.desktop.ui

import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.UIManager

/** 平台能力：目录选择器 / 资源管理器 / 浏览器（Windows 桌面） */
object Platform {

    /** 原生目录选择（JFileChooser，避免 javafx 依赖） */
    fun pickDirectory(title: String): File? {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        return runCatching {
            val chooser = JFileChooser()
            chooser.dialogTitle = title
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }.getOrNull()
    }

    fun openInExplorer(f: File) {
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(f) }
    }

    fun openInBrowser(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }
}
