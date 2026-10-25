package candy.ide.desktop

import java.io.File

/** 工程文件树与源码收集工具（工程增删/类型在 data/ProjectStore） */
object Workspace {

    /** 收集工程源码（编译根拼装用）：保持相对路径，跳过 build/ 与隐藏目录 */
    fun walkSources(project: File): List<Pair<File, String>> {
        val out = ArrayList<Pair<File, String>>()
        project.walkTopDown().forEach { f ->
            if (!f.isFile) return@forEach
            val rel = f.relativeTo(project).path.replace(File.separatorChar, '/')
            if (rel.startsWith("build/") || rel.startsWith(".")) return@forEach
            if (!rel.endsWith(".subl") && !rel.endsWith(".subs") && !rel.endsWith(".settings")) return@forEach
            out += f to rel
        }
        return out
    }

    /** 目录树节点（惰性展开） */
    data class TreeNode(val file: File, val name: String, val isDir: Boolean)

    /** 把目录压成 zip（导出工程归档 .capr 用；跳过隐藏文件） */
    fun zipTo(src: File, target: File): Boolean = runCatching {
        java.util.zip.ZipOutputStream(target.outputStream().buffered()).use { zos ->
            src.walkTopDown().forEach { f ->
                if (f == src || f.isDirectory || f.name.startsWith(".")) return@forEach
                val rel = f.relativeTo(src).path.replace(File.separatorChar, '/')
                zos.putNextEntry(java.util.zip.ZipEntry(rel))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        true
    }.getOrElse { target.delete(); false }

    /** 解压 zip 到 dest（工程内「解压到同名目录」用） */
    fun extractArchive(zip: File, dest: File): Boolean = runCatching {
        dest.mkdirs()
        java.util.zip.ZipFile(zip).use { z ->
            z.entries().toList().forEach { e ->
                val target = File(dest, e.name)
                if (e.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    z.getInputStream(e).use { it.copyTo(target.outputStream()) }
                }
            }
        }
        true
    }.getOrElse { false }

    fun children(dir: File): List<TreeNode> =
        dir.listFiles()?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { TreeNode(it, it.name, it.isDirectory) } ?: emptyList()
}
