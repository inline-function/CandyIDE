package candy.plugin.android.platform

import candy.ide.fs.CandyPath
import candy.ide.plugin.CandyPlugin
import candy.ide.plugin.PluginHost
import candy.ide.plugin.PluginManifest
import candy.plugin.android.AndroidAssets
import candy.plugin.android.project.AndroidProjectType
import sugared.functor.lsp.LspExtension
import java.io.File

/**
 * 平台插件的最小语言服务扩展：平台插件「先有实体」。
 * stdlib/procs 目录为 null（或不存在）时不贡献任何符号，仅占注册表一席；
 * 平台库落地后改为真实目录即可（宿主侧无需改动）。
 */
class PlatformLspExtension(
    override val name: String,
    private val stdlib: File?,
    private val procs: File?,
    initialEnabled: Boolean,
) : LspExtension {
    override val stdlibDir: File? get() = stdlib?.takeIf { it.isDirectory }
    override val processorsDir: File? get() = procs?.takeIf { it.isDirectory }

    /** 宿主按工程类型切换（语义同 AndroidLspExtension.enabled） */
    @Volatile
    var enabled: Boolean = initialEnabled

    override fun isEnabled(): Boolean = enabled
}

/**
 * SfA：Sublimation 安卓运行时（系统插件）。
 * 语言服务注入 @android 内建与安卓标准库（stdlib + 处理器脚本）——与 android-project
 * 插件共用同一份物化资产；jsapk 工程类型由 android-project 插件登记。
 * 装载时按当前已开工程立即决定开关（插件晚于 openProject 完成装载时补位）。
 */
class SublimationAndroidPlugin : CandyPlugin {

    override val manifest = PluginManifest(
        id = ID,
        name = "SfA · 安卓运行时",
        version = "1.0.0",
        description = "Sublimation for Android：安卓运行时把 @android 内建与安卓标准库注入语言服务；" +
            "jsapk 工程的编辑期补全/诊断/预览由本插件的 LSP 扩展提供",
        author = "Sublimation",
        entryClass = "candy.plugin.android.platform.SublimationAndroidPlugin",
        system = true,
        contributions = listOf("lsp-extension:android-stdlib"),
    )

    private var ext: PlatformLspExtension? = null

    override fun setup(host: PluginHost) {
        // 与 android-project 插件同一物化资产（幂等：版本标记命中即不重抽）
        AndroidAssets.setup(host)
        val e = PlatformLspExtension(
            name = "android-stdlib",
            stdlib = AndroidAssets.stdlib(),
            procs = AndroidAssets.procs(),
            initialEnabled = false,
        )
        ext = e
        host.registerLspExtension(e)
        // 补位：装载晚于工程打开时按当前工程类型定开关
        host.currentProjectDir()?.let { dir -> onProjectTypeChanged(host.projectTypeOf(dir), dir) }
    }

    override fun onProjectTypeChanged(typeId: String, dir: CandyPath?) {
        ext?.enabled = typeId == AndroidProjectType.TYPE_ID
    }

    companion object { const val ID = "sublimation-android" }
}

/**
 * SfG：Sublimation 游戏库（系统插件，实体占位）。
 * KAPLAY 风格的游戏运行时：语言服务扩展已注册，符号库构建中。
 */
class SublimationGamePlugin : CandyPlugin {

    override val manifest = PluginManifest(
        id = ID,
        name = "SfG · 游戏库",
        version = "0.1.0",
        description = "Sublimation for Games：KAPLAY 风格游戏库运行时；" +
            "语言服务扩展实体已注册，游戏符号库构建中",
        author = "Sublimation",
        entryClass = "candy.plugin.android.platform.SublimationGamePlugin",
        system = true,
        contributions = listOf("lsp-extension:sublimation-game"),
    )

    override fun setup(host: PluginHost) {
        host.registerLspExtension(PlatformLspExtension("sublimation-game", null, null, initialEnabled = true))
    }

    companion object { const val ID = "sublimation-game" }
}

/**
 * SfW：Sublimation WASM 运行时（系统插件，实体占位）。
 * wasm 编译目标的运行时：语言服务扩展已注册，符号库构建中。
 */
class SublimationWasmPlugin : CandyPlugin {

    override val manifest = PluginManifest(
        id = ID,
        name = "SfW · WASM 运行时",
        version = "0.1.0",
        description = "Sublimation for WASM：wasm 编译目标运行时；" +
            "语言服务扩展实体已注册，符号库构建中",
        author = "Sublimation",
        entryClass = "candy.plugin.android.platform.SublimationWasmPlugin",
        system = true,
        contributions = listOf("lsp-extension:sublimation-wasm"),
    )

    override fun setup(host: PluginHost) {
        host.registerLspExtension(PlatformLspExtension("sublimation-wasm", null, null, initialEnabled = true))
    }

    companion object { const val ID = "sublimation-wasm" }
}
