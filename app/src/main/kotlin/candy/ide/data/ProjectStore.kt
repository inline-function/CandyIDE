package candy.ide.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import candy.ide.engine.Engine
import candy.ide.fs.CandyFs
import candy.ide.fs.CandyPath
import java.io.File

/** 文件树节点（expanded 为可观察状态，点击目录即触发重绘；默认全部展开） */
class TreeNode(val file: CandyPath, val isDir: Boolean) {
    val name: String = file.name
    var expanded by mutableStateOf(true)
    val children: MutableList<TreeNode> = mutableListOf()
}

object ProjectStore {

    const val TYPE_JS = "js"          // JS 平台工程：编译 → JS → WebView 执行
    const val TYPE_SCRIPT = "script"  // 脚本工程：解释器直接执行（语言服务支持待补）
    const val TYPE_JSAPK = "jsapk"    // JS-apk 工程：编译 → JS → 注进模板 APK → 安装
    const val TYPE_MD = "md"          // md 文档工程：打包成只读文档 APK（含 md 库 + 文档文件页）
    private const val BUILTIN_MARKER = ".candy_builtin"   // 内置工程标记（存在即只读）
    private const val META = "candy.project"
    private const val OPEN_MARKER = ".candy_open"

    /** 工程根目录：优先用用户选的存储目录，兜底私有目录 */
    fun projectsRoot(ctx: Context): CandyPath =
        CandyFs.projectRoot(ctx) ?: CandyPath.Local(File(Engine.root(ctx), "projects"))

    /** 打开工程时调用：记下时间，主页排序用它 */
    fun touchOpened(dir: CandyPath) {
        runCatching { dir.child(OPEN_MARKER).writeText(System.currentTimeMillis().toString()) }
    }

    private fun lastOpened(dir: CandyPath): Long =
        runCatching { dir.find(OPEN_MARKER)?.readText()?.trim()?.toLong() }.getOrNull() ?: 0L

    /** 最近活跃时间（上次打开标记 > 最近改动 > 0）：主页「上次编辑于 xx 前」用它 */
    fun lastEdited(dir: CandyPath): Long =
        maxOf(lastOpened(dir), dir.lastModified())

    /** 列出全部工程（= 目录）：按上次打开时间倒序（没打开过的退到目录修改时间） */
    fun listProjects(ctx: Context): List<CandyPath> =
        projectsRoot(ctx).listFiles()?.filter { it.isDirectory() }
            ?.sortedByDescending { if (lastOpened(it) > 0) lastOpened(it) else it.lastModified() } ?: emptyList()

    /** 合法名称才创建；返回工程目录 */
    fun create(ctx: Context, name: String): CandyPath? {
        if (name.isBlank() || name.any { it in "\\/:*?\"<>|" }) return null
        val root = projectsRoot(ctx)
        return root.find(name) ?: root.createDirectory(name)
    }

    /** 写工程类型标记 */
    fun writeType(dir: CandyPath, type: String) = runCatching {
        dir.child(META).writeText("type=" + type)
    }

    /** 内置工程根目录（语言文档 / IDE 文档）：只读，不可编辑（仍在私有目录） */
    fun builtinRoot(ctx: android.content.Context): CandyPath =
        CandyPath.Local(File(Engine.root(ctx), "builtin"))

    /** 是否为内置工程（工程目录里有 .candy_builtin 标记，一律只读） */
    fun isBuiltin(dir: CandyPath?): Boolean =
        dir != null && dir.find(BUILTIN_MARKER)?.isFile() == true

    /** 把工程标记为内置（只读） */
    fun markBuiltin(dir: CandyPath) = runCatching { dir.child(BUILTIN_MARKER).writeText("1") }

    /** 读工程类型：有标记读标记，否则按内容里有无 .subl 推断，兜底 JS 平台 */
    fun typeOf(dir: CandyPath): String {
        val meta = dir.find(META)
        if (meta?.isFile() == true) {
            val raw = runCatching { meta.readText() }.getOrNull() ?: ""
            val t = raw.substringAfter("type=", "").trim()
            if (t == TYPE_JS || t == TYPE_SCRIPT || t == TYPE_JSAPK || t == TYPE_MD) return t
        }
        val hasSubl = dir.walk().any { it.isFile() && it.name.endsWith(".subl") }
        if (hasSubl) return TYPE_JS
        val hasMd = dir.walk().any { it.isFile() && it.name.endsWith(".md") }
        return if (hasMd) TYPE_MD else TYPE_SCRIPT
    }

    /** 重命名工程目录（返回是否成功） */
    fun rename(dir: CandyPath, newName: String): Boolean {
        if (newName.isBlank() || newName.any { it in "\\/:*?\"<>|" }) return false
        val target = dir.parent?.find(newName)
        if (target != null) return false
        return dir.renameTo(newName)
    }

    // ---- 文件树操作 ----

    fun createFile(parent: CandyPath, name: String): CandyPath? {
        if (validName(name)) return null
        if (parent.find(name) != null) return null
        return parent.child(name)
    }

    fun createDir(parent: CandyPath, name: String): CandyPath? {
        if (validName(name)) return null
        if (parent.find(name) != null) return null
        return parent.createDirectory(name)
    }

    fun renameFile(f: CandyPath, newName: String): Boolean {
        if (validName(newName)) return false
        val target = f.parent?.find(newName)
        return target == null && f.renameTo(newName)
    }

    /** 合法名字返回 false（用反逻辑少打点字） */
    private fun validName(name: String): Boolean =
        name.isBlank() || name.any { it in "\\/:*?\"<>|" } || name in listOf(".", "..", ".git")

    fun deleteFile(f: CandyPath): Boolean = f.delete()
}

/** 构建工程目录的文件树：目录在前，同类内按名字排序 */
fun buildTree(dir: CandyPath): List<TreeNode> {
    val files = dir.listFiles() ?: return emptyList()
    val nodes = files.map { TreeNode(it, it.isDirectory()) }
        .sortedWith(compareByDescending<TreeNode> { it.isDir }.thenBy { it.name })
    for (n in nodes) {
        if (n.isDir) n.children.addAll(buildTree(n.file))
    }
    return nodes
}