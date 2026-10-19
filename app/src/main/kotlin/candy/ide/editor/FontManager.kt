package candy.ide.editor

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import candy.ide.R
import java.io.File

/**
 * 编辑器字体管理：内置 JetBrains Mono（res/font，OFL-1.1）+ 用户导入的字体（files/candy/fonts 下的 .ttf / .otf）。
 * 字体 id："jetbrains"=内置（默认）/ "system"=系统 / 其它=导入字体的文件名（不含扩展名）
 */
object FontManager {

    private const val DIR = "fonts"

    /** id -> 显示名（内置 + 已导入） */
    fun listAll(ctx: Context): List<Pair<String, String>> {
        val imported = importedDir(ctx).listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc") }
            ?.sortedBy { it.nameWithoutExtension }
            ?.map { it.nameWithoutExtension to it.nameWithoutExtension }
            ?: emptyList()
        val builtin = listOf(
            "jetbrains" to "JetBrains Mono（内置）",
            "system" to "系统默认",
        )
        return builtin + imported
    }

    /** id -> Typeface；null 表示用 sora 默认 */
    fun typeface(ctx: Context, id: String): Typeface? = when {
        id == "system" -> null
        id == "jetbrains" -> runCatching { ResourcesCompat.getFont(ctx, R.font.jetbrainsmono_regular) }.getOrNull()
        else -> {
            val f = File(importedDir(ctx), if (id.contains('.')) id else id + ".ttf")
            if (f.isFile) runCatching { Typeface.createFromFile(f) }.getOrNull() else null
        }
    }

    /** 从系统文件选择器导入字体，成功返回显示名（不含扩展名） */
    fun importFromUri(ctx: Context, uri: Uri, displayName: String): String? {
        val raw = displayName.replace(Regex("[^A-Za-z0-9_.-]"), "_").trim()
        val name = if (raw.isNotEmpty()) raw else "font"
        val target = File(importedDir(ctx), name)
        importedDir(ctx).mkdirs()
        return runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return null
            target.nameWithoutExtension
        }.getOrNull()
    }

    private fun importedDir(ctx: Context): File = File(candy.ide.engine.Engine.root(ctx), DIR)
}
