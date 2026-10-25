package candy.ide.desktop.plugin

import candy.ide.desktop.plugin.contrib.AppInstaller
import candy.ide.desktop.plugin.contrib.EditorViewContribution
import candy.ide.desktop.plugin.contrib.ProjectSettingsSectionContribution
import candy.ide.desktop.plugin.contrib.ProjectTypeContribution
import candy.ide.desktop.plugin.contrib.SettingsSectionContribution
import sugared.functor.lsp.LspExtension

/** 已装载的插件（清单 + 实例；instance 为 null 表示纯资产插件） */
data class LoadedPlugin(val manifest: PluginManifest, val instance: CandyPlugin?)

/**
 * 插件注册表（全局单例；对齐安卓端 candy.ide.plugin.PluginRegistry）：持有已装载插件与其贡献点。
 * 宿主在启动时装载并注册；UI/状态经查询接口取贡献。
 */
object PluginRegistry {

    @Volatile
    private var loaded: List<LoadedPlugin> = emptyList()

    private val projectTypes = mutableListOf<ProjectTypeContribution>()
    private val editorViews = mutableListOf<EditorViewContribution>()
    private val lspExtensions = mutableListOf<LspExtension>()
    private val settingsSections = mutableListOf<SettingsSectionContribution>()
    private val projectSettingsSections = mutableListOf<ProjectSettingsSectionContribution>()
    private val installers = mutableListOf<AppInstaller>()

    /** 宿主装载完毕（启动装载后置位；此前 UI 查询走降级） */
    @Volatile
    var ready: Boolean = false
        internal set

    internal fun setLoaded(list: List<LoadedPlugin>) {
        loaded = list
    }

    val plugins: List<LoadedPlugin> get() = loaded
    fun byId(id: String): LoadedPlugin? = loaded.firstOrNull { it.manifest.id == id }

    // ---- 注册（宿主侧调用；按 id 去重） ----

    fun registerProjectType(contrib: ProjectTypeContribution) {
        if (projectTypes.none { it.typeId == contrib.typeId }) projectTypes.add(contrib)
    }

    fun registerEditorView(contrib: EditorViewContribution) {
        if (editorViews.none { it.id == contrib.id }) editorViews.add(contrib)
    }

    fun registerLspExtension(extension: LspExtension) {
        if (lspExtensions.none { it.name == extension.name }) lspExtensions.add(extension)
    }

    fun registerSettingsSection(contrib: SettingsSectionContribution) {
        if (settingsSections.none { it.id == contrib.id }) settingsSections.add(contrib)
    }

    fun registerProjectSettingsSection(contrib: ProjectSettingsSectionContribution) {
        if (projectSettingsSections.none { it.id == contrib.id }) projectSettingsSections.add(contrib)
    }

    fun registerAppInstaller(installer: AppInstaller) {
        if (installers.none { it.id == installer.id }) installers.add(installer)
    }

    // ---- 查询（返回快照，UI 线程安全） ----

    fun projectTypes(): List<ProjectTypeContribution> = projectTypes.toList()
    fun projectType(typeId: String): ProjectTypeContribution? = projectTypes.firstOrNull { it.typeId == typeId }
    /** v1 单一编辑器视图扩展（@composable 布局预览） */
    fun editorView(): EditorViewContribution? = editorViews.firstOrNull()
    fun lspExtensions(): List<LspExtension> = lspExtensions.toList()
    fun settingsSections(): List<SettingsSectionContribution> = settingsSections.sortedBy { it.sortKey }
    fun projectSettingsSections(): List<ProjectSettingsSectionContribution> = projectSettingsSections.sortedBy { it.sortKey }
    fun installers(): List<AppInstaller> = installers.toList()
}
