package candy.plugin.android.jsapk

/**
 * 二进制 AndroidManifest.xml / resources.arsc 微编辑器。
 *
 * 模板 APK 用固定的「candy.jsapk.runtime」发布；每个 JS-apk 工程打包时按
 * 工程配置（包名 / 应用名 / 版本号 / 版本名）改写这两个二进制段，而无需在设备上
 * 跑 aapt。
 *
 * 二进制 XML 结构（实证于 aapt2 35 输出，并用字节级往返校验）：
 *   - 文件 chunk：type=0x0003, headerSize=8, size=u32（全部小端）
 *   - 字符串池 chunk（@8）：type=0x0001, headerSize=28, size, count,
 *     styleCount, flags, stringsStart, stylesStart；字符串 = u16 长度 +
 *     UTF-16LE 数据 + u16 结束符，条目紧挨
 *   - 属性（20 字节）：nameIdx u32@0, rawValueIdx u32@4, typedValue@8：
 *     size u16, res0 u8, dataType u8@11, data u32@12
 *   - chunk 尺寸必须 4 字节对齐，池不足补零
 *
 * arsc 的 ResTable_package chunk（type=0x0200）包头里包名是定长 128 个
 * UTF-16 字符的字段（@+12），换任意 ≤127 字符的包名都不牵动其它结构。
 */
object AxmlEditor {

    private fun sh(b: ByteArray, p: Int): Int = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)
    private fun ui(b: ByteArray, p: Int): Int = sh(b, p) or (sh(b, p + 2) shl 16)
    private fun pi(b: ByteArray, p: Int, v: Int) {
        b[p] = v.toByte(); b[p + 1] = (v shr 8).toByte(); b[p + 2] = (v shr 16).toByte(); b[p + 3] = (v shr 24).toByte()
    }
    private fun ps16(b: ByteArray, p: Int, v: Int) { b[p] = v.toByte(); b[p + 1] = (v shr 8).toByte() }

    /**
     * 改写二进制 manifest：[stringEdits] 按「原字符串内容 → 新内容」替换池里
     * 的字符串（同内容多处会全部替换），[intEdits] 按属性名改 int 型属性值。
     * 字符串内容变化只会让池整体重建，池里其它条目的索引保持不变。
     */
    fun patchManifest(
        manifest: ByteArray,
        stringEdits: Map<String, String>,
        intEdits: Map<String, Int> = emptyMap(),
    ): ByteArray {
        require(sh(manifest, 0) == 0x0003) { "不是二进制 XML" }
        val poolHsz = sh(manifest, 8 + 2)
        require(poolHsz == 28) { "字符串池 headerSize=$poolHsz" }
        val poolSize = ui(manifest, 8 + 4)
        val count = ui(manifest, 8 + 8)
        val styleCount = ui(manifest, 8 + 12)
        require(styleCount == 0) { "带样式的字符串池不支持" }
        val flags = ui(manifest, 8 + 16)
        val stringsStart = ui(manifest, 8 + 20)
        val utf8 = flags and 0x100 != 0
        val strings = ArrayList<String>(count)
        val offsets = IntArray(count)
        for (i in 0 until count) {
            val off = ui(manifest, 8 + 28 + i * 4)
            offsets[i] = off
            val p = 8 + stringsStart + off
            val len = sh(manifest, p)
            strings += if (utf8) String(manifest, p + 2, len, Charsets.UTF_8)
                       else String(manifest, p + 2, len * 2, Charsets.UTF_16LE)
        }
        // 字符串替换（保持索引）
        var changed = 0
        for (i in strings.indices) {
            val rep = stringEdits[strings[i]] ?: continue
            strings[i] = rep
            changed++
        }
        require(changed > 0 || intEdits.isNotEmpty()) { "没有命中的替换" }
        var newPool = buildPool(strings, utf8, styleCount)
        val pad = (4 - newPool.size % 4) % 4
        if (pad > 0) {
            val padded = newPool + ByteArray(pad)
            pi(padded, 4, padded.size)
            newPool = padded
        }
        val rest = manifest.size - 8 - poolSize
        val out = ByteArray(8 + newPool.size + rest)
        System.arraycopy(manifest, 0, out, 0, 8)
        pi(out, 4, out.size)
        System.arraycopy(newPool, 0, out, 8, newPool.size)
        System.arraycopy(manifest, 8 + poolSize, out, 8 + newPool.size, rest)
        // int 属性改写（在重建后的字节上按新池尺寸遍历）
        if (intEdits.isNotEmpty()) patchIntAttrs(out, newPool.size, strings, intEdits)
        return out
    }

    private fun buildPool(strings: List<String>, utf8: Boolean, styleCount: Int): ByteArray {
        val enc = strings.map { s -> encode(s, utf8) }
        val header = 28 + 4 * strings.size
        val total = header + enc.sumOf { it.size }
        val out = ByteArray(total)
        ps16(out, 0, 0x0001)
        ps16(out, 2, 28)
        pi(out, 4, total)
        pi(out, 8, strings.size)
        pi(out, 12, styleCount)
        pi(out, 16, if (utf8) 0x100 else 0)
        pi(out, 20, header)
        pi(out, 24, 0)
        var cursor = header
        for (i in strings.indices) {
            pi(out, 28 + i * 4, cursor - header)
            System.arraycopy(enc[i], 0, out, cursor, enc[i].size)
            cursor += enc[i].size
        }
        return out
    }

    private fun encode(s: String, utf8: Boolean): ByteArray {
        val data = if (utf8) s.toByteArray(Charsets.UTF_8) else s.toByteArray(Charsets.UTF_16LE)
        val len = if (utf8) data.size else s.length
        val out = ByteArray(2 + data.size + (if (utf8) 1 else 2))
        ps16(out, 0, len)
        System.arraycopy(data, 0, out, 2, data.size)
        return out
    }

    /** 遍历 START_ELEMENT chunk，按属性名（池字符串）改 int 型属性值 */
    private fun patchIntAttrs(
        b: ByteArray,
        newPoolSize: Int,
        strings: List<String>,
        intEdits: Map<String, Int>,
    ) {
        val nameToIdx = HashMap<String, Int>()
        strings.forEachIndexed { i, s -> nameToIdx[s] = i }
        var pos = 8 + newPoolSize
        val total = b.size
        val done = HashSet<String>()
        while (pos + 8 <= total) {
            val t = sh(b, pos)
            val sz = ui(b, pos + 4)
            if (sz <= 0) break
            if (t == 0x0102) {
                val attrStart = sh(b, pos + 24)
                val attrSize = sh(b, pos + 26)
                val attrCount = sh(b, pos + 28)
                for (k in 0 until attrCount) {
                    val base = pos + attrStart + k * attrSize
                    if (base + attrSize > pos + sz) break
                    val nameIdx = ui(b, base)
                    val dtype = b[base + 11].toInt() and 0xFF
                    val name = strings.getOrNull(nameIdx) ?: continue
                    val value = intEdits[name] ?: continue
                    require(dtype == 0x10) { "属性 $name 不是 int 型" }
                    pi(b, base + 12, value)
                    done += name
                }
            }
            pos += sz
        }
        intEdits.keys.forEach { name ->
            require(name in done) { "找不到 int 属性：$name" }
        }
    }

    /** 改写 arsc 里的包名（ResTable_package 的定长 char16[128] 字段） */
    fun patchArscPackage(arsc: ByteArray, pkg: String) {
        require(sh(arsc, 0) == 0x0002) { "不是 resources.arsc" }
        val tableHsz = sh(arsc, 2)
        var pos = tableHsz
        var patched = 0
        val data = pkg.toByteArray(Charsets.UTF_16LE)
        require(data.size <= 256) { "包名太长" }
        while (pos + 12 <= arsc.size) {
            val pt = sh(arsc, pos)
            val psz = ui(arsc, pos + 4)
            if (psz <= 0) break
            if (pt == 0x0200) {
                java.util.Arrays.fill(arsc, pos + 12, pos + 12 + 256, 0.toByte())
                System.arraycopy(data, 0, arsc, pos + 12, data.size)
                patched++
            }
            pos += psz
        }
        require(patched == 1) { "arsc 里有 $patched 个 package chunk（期望 1）" }
    }

    /** 合法包名检查：java 包名规则 */
    fun validPackage(pkg: String): Boolean {
        if (pkg.length > 127) return false
        val seg = pkg.split('.')
        if (seg.isEmpty() || seg.any { it.isEmpty() }) return false
        val segPat = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        return seg.all { segPat.matches(it) }
    }
}