package candy.ide.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import candy.ide.engine.Engine
import java.io.File

/** 文件树节点（expanded 为可观察状态，点击目录即触发重绘；默认全部展开） */
class TreeNode(val file: File, val isDir: Boolean) {
    val name: String = file.name
    var expanded by mutableStateOf(true)
    val children: MutableList<TreeNode> = mutableListOf()
}

object ProjectStore {

    const val TYPE_JS = "js"          // JS 平台工程：编译 → JS → WebView 执行
    const val TYPE_SCRIPT = "script"  // 脚本工程：解释器直接执行（语言服务支持待补）
    const val TYPE_JSAPK = "jsapk"    // JS-apk 工程：编译 → JS → 注进模板 APK → 安装

    private const val META = "candy.project"

    fun projectsRoot(ctx: Context): File = File(Engine.root(ctx), "projects")

    /** 最近打开时间标记文件（dotfile：模块扫描只收 .subl/.subs/.settings，不受影响） */
    private const val OPEN_MARKER = ".candy_open"

    /** 打开工程时调用：记下时间，主页排序用它 */
    fun touchOpened(dir: File) {
        runCatching { File(dir, OPEN_MARKER).writeText(System.currentTimeMillis().toString()) }
    }

    private fun lastOpened(dir: File): Long =
        runCatching { File(dir, OPEN_MARKER).readText().trim().toLong() }.getOrDefault(0L)

    /** 最近活跃时间（上次打开标记 > 最近改动 > 0）：主页「上次编辑于 xx 前」用它 */
    fun lastEdited(dir: File): Long =
        maxOf(lastOpened(dir), dir.lastModified())

    /** 列出全部工程（= 目录）：按上次打开时间倒序（没打开过的退到目录修改时间） */
    fun listProjects(ctx: Context): List<File> =
        projectsRoot(ctx).listFiles { f -> f.isDirectory }
            ?.sortedByDescending { if (lastOpened(it) > 0) lastOpened(it) else it.lastModified() } ?: emptyList()

    /** 合法名称才创建；返回工程目录 */
    fun create(ctx: Context, name: String): File? {
        if (name.isBlank() || name.any { it in "\\/:*?\"<>|" }) return null
        val dir = File(projectsRoot(ctx), name)
        return if (dir.mkdirs() || dir.isDirectory) dir else null
    }

    /** 写工程类型标记 */
    fun writeType(dir: File, type: String) = runCatching {
        File(dir, META).writeText("type=" + type)
    }

    /** 读工程类型：有标记读标记，否则按内容里有无 .subl 推断，兜底 JS 平台 */
    fun typeOf(dir: File): String {
        val meta = File(dir, META)
        if (meta.isFile) {
            val raw = runCatching { meta.readText() }.getOrNull() ?: ""
            val t = raw.substringAfter("type=", "").trim()
            if (t == TYPE_JS || t == TYPE_SCRIPT || t == TYPE_JSAPK) return t
        }
        val hasSubl = dir.walkTopDown().any { it.isFile && it.name.endsWith(".subl") }
        return if (hasSubl) TYPE_JS else TYPE_SCRIPT
    }

    /** 重命名工程目录（返回是否成功） */
    fun rename(dir: File, newName: String): Boolean {
        if (newName.isBlank() || newName.any { it in "\\/:*?\"<>|" }) return false
        val target = File(dir.parentFile, newName)
        if (target.exists()) return false
        return dir.renameTo(target)
    }

    // ---- 文件树操作 ----

    fun createFile(parent: File, name: String): File? {
        if (validName(name)) return null
        val f = File(parent, name)
        if (f.exists()) return null
        f.parentFile?.mkdirs()   // 已存在目录 mkdirs() 返回 false，不能拿返回值当判据
        return if (f.createNewFile()) f else null
    }

    fun createDir(parent: File, name: String): File? {
        if (validName(name)) return null
        return File(parent, name).takeIf { !it.exists() && it.mkdirs() }
    }

    fun renameFile(f: File, newName: String): Boolean {
        if (validName(newName)) return false
        val target = File(f.parentFile, newName)
        return !target.exists() && f.renameTo(target)
    }

    /** 合法名字返回 false（用反逻辑少打点字） */
    private fun validName(name: String): Boolean =
        name.isBlank() || name.any { it in "\\/:*?\"<>|" } || name in listOf(".", "..", ".git")

    fun deleteFile(f: File): Boolean = f.deleteRecursively()
}

/** 构建工程目录的文件树：目录在前，同类内按名字排序 */
fun buildTree(dir: File): List<TreeNode> {
    val files = dir.listFiles() ?: return emptyList()
    val nodes = files.map { TreeNode(it, it.isDirectory) }
        .sortedWith(compareByDescending<TreeNode> { it.isDir }.thenBy { it.name })
    for (n in nodes) {
        if (n.isDir) n.children.addAll(buildTree(n.file))
    }
    return nodes
}
