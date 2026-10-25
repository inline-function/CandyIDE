package candy.ide.fs

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 统一路径抽象：IDE 数据可能落在应用私有目录（Local）或用户选择的存储目录（SAF）。
 * 对外提供与 java.io.File 相似的 API，调用方无需关心底层实现。
 */
sealed interface CandyPath {
    val name: String
    val key: String
    val parent: CandyPath?
    val displayPath: String

    fun exists(): Boolean
    fun isDirectory(): Boolean
    fun isFile(): Boolean
    fun listFiles(): List<CandyPath>?
    fun walk(): Sequence<CandyPath>
    fun readText(): String
    fun writeText(text: String)
    fun appendText(text: String)
    fun mkdirs(): Boolean
    fun createNewFile(): Boolean
    fun createDirectory(name: String): CandyPath?
    fun delete(): Boolean
    fun renameTo(newName: String): Boolean
    fun lastModified(): Long
    fun length(): Long
    fun inputStream(): InputStream
    fun outputStream(): OutputStream
    /** 未创建就找不到则返回 null（不产生副作用） */
    fun find(name: String): CandyPath?
    /** get-or-create：文件不存在则创建空文件 */
    fun child(name: String): CandyPath
    fun uri(): Uri
    /** 重命名到目标（目标父级需已存在） */
    fun renameTo(target: CandyPath): Boolean
    /** 拷贝到目标（overwrite 控制覆盖） */
    fun copyTo(target: CandyPath, overwrite: Boolean = true): Boolean
    /** 递归删除（目录连同子树） */
    fun deleteRecursively(): Boolean
    val extension: String
    val nameWithoutExtension: String
    /** 相对路径遍历：(路径, 相对路径) —— 打包/编译按相对路径收集工程文件用 */
    fun walkRelative(): Sequence<Pair<CandyPath, String>>

    class Local(val file: File) : CandyPath {
        override val name: String get() = file.name
        override val key: String get() = file.absolutePath
        override val parent: CandyPath? get() = file.parentFile?.let { Local(it) }
        override val displayPath: String get() = file.path
        override fun exists(): Boolean = file.exists()
        override fun isDirectory(): Boolean = file.isDirectory
        override fun isFile(): Boolean = file.isFile
        override fun listFiles(): List<CandyPath>? = file.listFiles()?.map { Local(it) }
        override fun walk(): Sequence<CandyPath> = file.walkTopDown().map { Local(it) }
        override fun readText(): String = file.readText()
        override fun writeText(text: String) { file.writeText(text) }
        override fun appendText(text: String) { file.appendText(text) }
        override fun mkdirs(): Boolean = file.mkdirs() || file.isDirectory
        override fun createNewFile(): Boolean = runCatching { file.createNewFile() }.getOrDefault(false)
        override fun createDirectory(name: String): CandyPath? {
            val d = File(file, name)
            return if (d.exists() && d.isDirectory) Local(d) else if (d.mkdirs()) Local(d) else null
        }
        override fun delete(): Boolean = file.deleteRecursively()
        override fun renameTo(target: CandyPath): Boolean =
            (target as? Local)?.let { file.renameTo(it.file) } == true
        override fun copyTo(target: CandyPath, overwrite: Boolean): Boolean {
            val tf = (target as? Local)?.file ?: return false
            return runCatching { file.copyTo(tf, overwrite) }.isSuccess
        }
        override fun deleteRecursively(): Boolean = file.deleteRecursively()
        override fun equals(other: Any?): Boolean = (other as? Local)?.key == key
        override fun hashCode(): Int = key.hashCode()
        override fun renameTo(newName: String): Boolean = file.renameTo(File(file.parentFile, newName))
        override fun lastModified(): Long = file.lastModified()
        override fun length(): Long = file.length()
        override fun inputStream(): InputStream = file.inputStream()
        override fun outputStream(): OutputStream = file.outputStream()
        override fun find(name: String): CandyPath? {
            val f = File(file, name)
            return if (f.exists()) Local(f) else null
        }
        override fun child(name: String): CandyPath = Local(File(file, name))
        override fun uri(): Uri = Uri.fromFile(file)
        override val extension: String get() = file.extension
        override val nameWithoutExtension: String get() = file.nameWithoutExtension
        override fun walkRelative(): Sequence<Pair<CandyPath, String>> =
            file.walkTopDown().map { Local(it) to it.relativeTo(file).path.replace('\\', '/') }
        override fun toString(): String = file.path
    }

    class Saf(val ctx: Context, val doc: DocumentFile) : CandyPath {
        override val name: String get() = doc.name ?: ""
        override val key: String get() = doc.uri.toString()
        override val parent: CandyPath? get() = doc.parentFile?.let { Saf(ctx, it) }
        override val displayPath: String get() = doc.uri.path ?: name
        override fun exists(): Boolean = doc.exists()
        override fun isDirectory(): Boolean = doc.isDirectory
        override fun isFile(): Boolean = doc.isFile
        override fun listFiles(): List<CandyPath>? = doc.listFiles().map { Saf(ctx, it) }
        override fun walk(): Sequence<CandyPath> = sequence {
            yield(this@Saf)
            doc.listFiles().forEach { yieldAll(Saf(ctx, it).walk()) }
        }
        override fun readText(): String =
            ctx.contentResolver.openInputStream(doc.uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        override fun writeText(text: String) {
            ctx.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        override fun appendText(text: String) {
            ctx.contentResolver.openOutputStream(doc.uri, "wa")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        override fun mkdirs(): Boolean = doc.isDirectory
        private fun createDocFile(displayName: String): CandyPath? =
            doc.createFile("application/octet-stream", displayName)?.let { Saf(ctx, it) }
        override fun createNewFile(): Boolean = createDocFile(name) != null
        override fun createDirectory(name: String): CandyPath? {
            // SAF createFile/createDirectory 偶发返回 null（目录落盘异步）：短暂重试
            var tries = 0
            while (tries < 6) {
                val created = doc.createDirectory(name)
                if (created != null) return Saf(ctx, created)
                tries++
                if (tries < 6) Thread.sleep(150L)
            }
            return null
        }
        override fun delete(): Boolean = doc.delete()
        override fun renameTo(target: CandyPath): Boolean = doc.renameTo(target.name)
        override fun copyTo(target: CandyPath, overwrite: Boolean): Boolean = runCatching {
            if (target.exists() && !overwrite) return@runCatching false
            inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o) } }
            true
        }.getOrDefault(false)
        override fun deleteRecursively(): Boolean = doc.delete()
        override fun equals(other: Any?): Boolean = (other as? Saf)?.key == key
        override fun hashCode(): Int = key.hashCode()
        override fun renameTo(newName: String): Boolean = doc.renameTo(newName)
        override fun lastModified(): Long = doc.lastModified()
        override fun length(): Long = doc.length()
        override fun inputStream(): InputStream =
            ctx.contentResolver.openInputStream(doc.uri)!!
        override fun outputStream(): OutputStream =
            ctx.contentResolver.openOutputStream(doc.uri, "wt")!!
        override fun find(name: String): CandyPath? =
            doc.listFiles().firstOrNull { it.name == name }?.let { Saf(ctx, it) }
        override fun child(name: String): CandyPath {
            val found = doc.listFiles().firstOrNull { it.name == name }
            if (found != null) return Saf(ctx, found)
            // SAF 树里不存在则创建空文件（打包写 app.apk 前的占位）。
            // createDirectory 之后立即 createFile 在部分 provider 上偶发返回 null（目录落盘异步）：短暂重试
            var created: DocumentFile? = null
            var tries = 0
            while (created == null && tries < 6) {
                created = doc.createFile("application/octet-stream", name)
                if (created == null) {
                    tries++
                    if (tries < 6) Thread.sleep(150L)
                }
            }
            if (created == null) throw java.io.IOException("无法在该目录下创建 " + name + "（SAF 目录不可写）")
            return Saf(ctx, created)
        }
        override fun uri(): Uri = doc.uri
        override val extension: String
            get() = name.substringAfterLast('.', "")
        override val nameWithoutExtension: String
            get() = name.substringBeforeLast('.', name)
        override fun walkRelative(): Sequence<Pair<CandyPath, String>> = sequence {
            yield(this@Saf to "")
            doc.listFiles().forEach { yieldAll(walkRel(it, it.name ?: "")) }
        }
        private fun walkRel(d: DocumentFile, rel: String): Sequence<Pair<CandyPath, String>> = sequence {
            yield(Saf(ctx, d) to rel)
            d.listFiles().forEach { c -> yieldAll(walkRel(c, "$rel/${c.name}")) }
        }
        override fun toString(): String = displayPath
    }
}