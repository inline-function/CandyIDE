package candy.ide.fs

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * IDE 工作区（存储目录）管理：SAF 授权的目录持久化，首次使用前不可用。
 * 私有目录数据（sdk 模板/stdlib/内置文档/日志/设置）始终留在 files/candy。
 */
object CandyFs {
    private const val PREF = "candy_fs"
    private const val KEY_TREE = "workspace_tree_uri"
    private const val KEY_NAME = "workspace_name"
    private const val KEY_MIGRATED = "workspace_migrated"

    fun treeUri(ctx: Context): Uri? {
        val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TREE, null) ?: return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }

    fun isReady(ctx: Context): Boolean = workspace(ctx) != null

    fun workspace(ctx: Context): CandyPath? {
        val uri = treeUri(ctx) ?: return null
        val doc = DocumentFile.fromTreeUri(ctx, uri) ?: return null
        if (!doc.canWrite()) return null
        return CandyPath.Saf(ctx, doc)
    }

    fun workspaceName(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_NAME, "CandyIDE")!!

    /** 记录授权（持久化 URI 权限），可选把旧工程搬进来 */
    fun setWorkspace(ctx: Context, uri: Uri, migrate: Boolean) {
        runCatching {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_TREE, uri.toString())
            putString(KEY_NAME, DocumentFile.fromTreeUri(ctx, uri)?.name ?: "CandyIDE")
        }.apply()
        if (migrate) migrateFromPrivate(ctx)
    }

    /** 把私有目录里的旧工程搬进新工作区（只搬一次） */
    fun migrateFromPrivate(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_MIGRATED, false)) return
        val ws = workspace(ctx) ?: return
        val oldProjects = File(candy.ide.engine.Engine.root(ctx), "projects")
        if (oldProjects.isDirectory) {
            val projRoot = projectRoot(ctx) ?: return
            oldProjects.listFiles()?.forEach { proj ->
                if (projRoot.listFiles()?.any { it.name == proj.name } == true) return@forEach
                copyInto(ctx, projRoot, proj)
            }
        }
        sp.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    /** 把一个本地 File 复制进 SAF 目标目录（递归建目录） */
    private fun copyInto(ctx: Context, target: CandyPath, file: File) {
        if (file.isDirectory) {
            val dir = target.listFiles()?.firstOrNull { it.name == file.name }
                ?: target.createDirectory(file.name) ?: return
            file.listFiles()?.forEach { copyInto(ctx, dir, it) }
        } else {
            val existing = target.listFiles()?.firstOrNull { it.name == file.name }
            val dest = existing ?: ((target as? CandyPath.Saf)?.doc?.createFile("*/*", file.name)
                ?.let { CandyPath.Saf(ctx, it) }) ?: return
            dest.outputStream().use { out -> file.inputStream().use { it.copyTo(out) } }
        }
    }

    /** 工程根目录：工作区/projects（没有则创建） */
    fun projectRoot(ctx: Context): CandyPath? {
        val ws = workspace(ctx) ?: return null
        val proj = ws.listFiles()?.firstOrNull { it.name == "projects" }
        return proj ?: ws.createDirectory("projects")
    }

    /** 私有根（sdk/stdlib/builtin/logs/settings） */
    fun privateRoot(ctx: Context): CandyPath = CandyPath.Local(File(ctx.filesDir, "candy"))

    /** 清空工作区全部内容（重置/删除 IDE 用，保留授权） */
    fun wipeWorkspace(ctx: Context) {
        val ws = workspace(ctx) ?: return
        ws.listFiles()?.forEach { it.delete() }
    }

    /** 重置标记，允许再次迁移 */
    fun resetMigrationFlag(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_MIGRATED, false).apply()
    }
}