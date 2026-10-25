package candy.ide.desktop.plugin

import java.io.InputStream

/** classpath 资源读取助手（桌面端无法像 Android assets 那样列目录，索引由构建期生成） */
internal fun resourceStream(path: String): InputStream? =
    runCatching { Thread.currentThread().contextClassLoader?.getResourceAsStream(path) }.getOrNull()
        ?: java.lang.ClassLoader.getSystemResourceAsStream(path)

internal fun resourceText(path: String): String? =
    resourceStream(path)?.use { it.readBytes().toString(Charsets.UTF_8).trim() }
