package candy.ide.desktop.ui

/** classpath 资源读取（builtin 文档 / stdlib 由构建期 sync 进资源） */
object Resources {

    fun read(path: String): String? = runCatching {
        val cl = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
        cl.getResourceAsStream(path)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
    }.getOrNull()

    fun index(name: String): List<String> =
        read(name)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") } ?: emptyList()
}
