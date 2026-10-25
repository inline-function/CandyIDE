package candy.plugin.android.jsapk

import android.util.Base64
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * APK v1（JAR）签名器：手写最小 PKCS#7 SignedData + MANIFEST.MF/CERT.SF。
 *
 * 输出遵循 jarsigner 的经典产物：
 *  - META-INF/MANIFEST.MF：每个条目的 SHA-256 摘要
 *  - META-INF/CERT.SF：MANIFEST 的摘要（带条目级摘要，与 apksigner 对齐）
 *  - META-INF/CERT.RSA：PKCS#7 SignedData，签名 CERT.SF，证书内嵌
 *
 * 安全说明：所有 JS-apk 共用一把内置密钥（candy.jsapk.runtime 包名），
 * 仅保证「包能装上、运行时校验一致」，不是正式开发签名。
 */
object ApkSigner {
    @JvmStatic private var cacheDirHook: java.io.File? = null
    @JvmStatic fun attachCache(dir: java.io.File) { cacheDirHook = dir }
    private fun ctxCache(apk: candy.ide.fs.CandyPath): java.io.File =
        cacheDirHook ?: java.io.File(System.getProperty("java.io.tmpdir", "/data/local/tmp"))

    private const val ALIAS_CERT = "candy-plugins/android-project/sdk/jsapk/candy_jsapk.cert.der"
    private const val ALIAS_KEY = "candy-plugins/android-project/sdk/jsapk/candy_jsapk.key.pk8"

    /** 从 APK 读出全部条目（不含旧 META-INF）的未压缩字节 + 重新打包成已签名 APK */
    fun signV1(apk: candy.ide.fs.CandyPath, entries: List<Pair<String, ByteArray>>): Unit = rezip(apk, entries)
    /**
     * APK Signature Scheme v2：整包 1MB 分块摘要，签名块插进 Central Directory 前。
     * Android 11+（尤其 MagicOS/EMUI）对没带 v2 块的 APK 直接判「No signature found in
     * package of version 2 or newer」，v1-only 装不上；v1 保留给旧系统。
     */
    /**
     * APK Signature Scheme v2：整包 1MB 分块摘要，签名块插进 Central Directory 前。
     * Android 11+（尤其 MagicOS/EMUI）对没带 v2 块的 APK 直接判「No signature found in
     * package of version 2 or newer」，v1-only 装不上；v1 保留给旧系统。
     */
    fun signV2(apk: candy.ide.fs.CandyPath) {
        val key = loadKey() ?: throw RuntimeException("签名密钥不可用")
        val cert = loadCert() ?: throw RuntimeException("签名证书不可用")
        val raw = apk.inputStream().use { it.readBytes() }
        val eocd = locateEocd(raw) ?: throw RuntimeException("找不到 EOCD")
        val cdOff = ui32(raw, eocd + 16)
        val cdSize = ui32(raw, eocd + 12)

        // AOSP apksig 算法（与 apksigner 实测字节逐位校验过）：
        //  contents 三段 = [0, blockStart) + CD + EOCD，EOCD 的 CD 偏移字段改写成 blockStart
        //  chunk digest = SHA256(0xa5 || uint32LE(chunkLen) || data)
        //  root = SHA256(0x5a || uint32LE(chunkCount) || concat(chunkDigests))
        // digest 字段定长 32，先用占位 digest 定出块长，再回填真 digest（长度不变）。
        val placeholder = ByteArray(32)
        var block = buildV2Block(placeholder, key, cert)
        val patchedEocd = raw.copyOfRange(eocd, raw.size).also { pi32(it, 16, cdOff) }
        val chunkSize = 1024 * 1024
        val md = MessageDigest.getInstance("SHA-256")
        val dig = ArrayList<ByteArray>()
        fun digestRange(src: ByteArray, start: Int, len: Int) {
            var p = start
            var remain = len
            while (remain > 0) {
                val n = minOf(chunkSize, remain)
                md.reset()
                md.update(0xA5.toByte())
                md.update(u32(n))
                md.update(src, p, n)
                dig.add(md.digest())
                p += n
                remain -= n
            }
        }
        digestRange(raw, 0, cdOff)
        digestRange(raw, cdOff, cdSize)
        digestRange(patchedEocd, 0, patchedEocd.size)
        md.reset()
        md.update(0x5a.toByte())
        md.update(u32(dig.size))
        for (d in dig) md.update(d)
        val digestField = md.digest()
        // 块长不变：用真 digest 重建一次签名块
        block = buildV2Block(digestField, key, cert)
        // CD 前插块；EOCD 的 CD 偏移指向新位置
        val out = ByteArray(raw.size + block.size)
        System.arraycopy(raw, 0, out, 0, cdOff)
        System.arraycopy(block, 0, out, cdOff, block.size)
        System.arraycopy(raw, cdOff, out, cdOff + block.size, raw.size - cdOff)
        pi32(out, eocd + 16 + block.size, cdOff + block.size)
        apk.outputStream().use { it.write(out) }
    }

    private fun buildV2Block(digestField: ByteArray, key: PrivateKey, cert: X509Certificate): ByteArray {
        // 算法 0x0103 = RSASSA-PKCS1-v1_5 + SHA-256
        val digestEntry = concatV(u32(0x0103), lp(digestField))
        val signedData = concatV(
            lpSeq(listOf(digestEntry)),
            lpSeq(listOf(cert.encoded)),
            lpSeq(emptyList()),
        )
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(key)
        sig.update(signedData)
        val sigEntry = concatV(u32(0x0103), lp(sig.sign()))
        val signer = concatV(lp(signedData), lpSeq(listOf(sigEntry)), lp(cert.publicKey.encoded))
        val payload = lpSeq(listOf(signer))
        val pair = concatV(u64((4 + payload.size).toLong()), u32(0x7109871a), payload)
        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        val innerSize = pair.size + 8 + magic.size
        return concatV(u64(innerSize.toLong()), pair, u64(innerSize.toLong()), magic)
    }
    private fun locateEocd(raw: ByteArray): Int? {
        var i = raw.size - 22
        while (i >= 0) {
            if (raw[i] == 0x50.toByte() && raw[i + 1] == 0x4B.toByte() && raw[i + 2] == 0x05.toByte() && raw[i + 3] == 0x06.toByte()) return i
            i--
        }
        return null
    }

    private fun ui32(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)

    private fun pi32(b: ByteArray, p: Int, v: Int) {
        b[p] = v.toByte(); b[p + 1] = (v shr 8).toByte(); b[p + 2] = (v shr 16).toByte(); b[p + 3] = (v shr 24).toByte()
    }

    private fun u32(v: Int): ByteArray = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun u64(v: Long): ByteArray =
        byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte(), (v shr 32).toByte(), (v shr 40).toByte(), (v shr 48).toByte(), (v shr 56).toByte())

    /** v2 的 length-prefixed：uint32 LE 长度 + 数据 */
    private fun lp(data: ByteArray): ByteArray {
        val out = ByteArray(4 + data.size)
        pi32(out, 0, data.size)
        System.arraycopy(data, 0, out, 4, data.size)
        return out
    }

    /** v2 的序列：u32(内容总长) + 每个元素的 lp（对照 AOSP apksigner 实测字节） */
    private fun lpSeq(items: List<ByteArray>): ByteArray {
        val lped = ArrayList<ByteArray>(items.size)
        var sum = 0
        for (it in items) {
            val e = lp(it)
            lped.add(e)
            sum += e.size
        }
        val out = ByteArray(4 + sum)
        pi32(out, 0, sum)
        var off = 4
        for (e in lped) {
            System.arraycopy(e, 0, out, off, e.size)
            off += e.size
        }
        return out
    }

    private fun concatV(vararg parts: ByteArray): ByteArray {
        var sum = 0
        for (p in parts) sum += p.size
        val out = ByteArray(sum)
        var off = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, off, p.size)
            off += p.size
        }
        return out
    }

    // ---- 重建 zip：META-INF 在前 ----

    private fun rezip(apk: candy.ide.fs.CandyPath, entries: List<Pair<String, ByteArray>>) {
        val parent = apk.parent ?: throw RuntimeException("产物目录异常")
        // 签名过程产物落在系统缓存目录（SAF 不能被 ZipFile 直接读）
        val tmp = java.io.File(ctxCache(apk), "sign-${apk.name}.tmp")
        val manifest = buildManifest(entries)
        val (sf, rsa) = buildSignedPieces(manifest) ?: throw RuntimeException("签名初始化失败")
        val jarBytes = manifest.toByteArray(Charsets.UTF_8)
        val counter = CountingOutputStream(tmp.outputStream())
        java.util.zip.ZipOutputStream(counter).use { zos ->
            put(zos, counter, "META-INF/MANIFEST.MF", jarBytes)
            put(zos, counter, "META-INF/CERT.SF", sf)
            put(zos, counter, "META-INF/CERT.RSA", rsa)
            for ((name, data) in entries) put(zos, counter, name, data)
        }
        // 原文件替换：outputStream 的 "wt" 模式本身就是截断覆盖写，直接覆盖即可。
        // SAF 上先 exists()/delete() 会让 document uri 失效（isChildDocument 找不到物理文件而抛异常），
        // 也不能用 "rws" 之类随机写模式（provider 不支持）。
        tmp.inputStream().use { ins -> apk.outputStream().use { out -> ins.copyTo(out) } }
        tmp.delete()
    }

    /**
     * R+（targetSdk≥30）安装校验要求 resources.arsc 未压缩且数据起点 4 字节对齐，
     * 否则报 INSTALL_PARSE_FAILED（-124）。其余条目仍走 DEFLATED。
     */
    private class CountingOutputStream(out: java.io.OutputStream) : java.io.FilterOutputStream(out) {
        var count = 0
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private fun put(zos: java.util.zip.ZipOutputStream, counter: CountingOutputStream, name: String, data: ByteArray) {
        val ze = java.util.zip.ZipEntry(name)
        if (name == "resources.arsc") {
            ze.method = java.util.zip.ZipEntry.STORED
            ze.size = data.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(data)
            ze.crc = crc.value
            // local header 起 30 字节 + 名长后是数据起点，extra 填充到 4 字节对齐
            val pad = (4 - (counter.count + 30 + name.length) % 4) % 4
            if (pad > 0) ze.setExtra(ByteArray(pad))
        } else {
            ze.method = java.util.zip.ZipEntry.DEFLATED
        }
        zos.putNextEntry(ze)
        zos.write(data)
        zos.closeEntry()
    }

    // ---- MANIFEST / SF ----

    private fun buildManifest(entries: List<Pair<String, ByteArray>>): String {
        val sb = StringBuilder()
        sb.append("Manifest-Version: 1.0\r\n")
        sb.append("Created-By: CandyIDE jsapk\r\n")
        for ((name, data) in entries) {
            val b64 = Base64.encodeToString(sha256(data), Base64.NO_WRAP)
            sb.append("\r\nName: ").append(name).append("\r\n")
            sb.append("SHA-256-Digest: ").append(b64).append("\r\n")
        }
        return sb.toString()
    }

    private fun buildSignedPieces(manifest: String): Pair<ByteArray, ByteArray>? = runCatching {
        val key = loadKey() ?: return null
        val cert = loadCert() ?: return null
        val mfBytes = manifest.toByteArray(Charsets.UTF_8)
        // SF：主摘要 + 与 apksigner 一致的条目级摘要
        val sf = StringBuilder()
            .append("Signature-Version: 1.0\r\n")
            .append("Created-By: CandyIDE jsapk\r\n")
            .append("SHA-256-Digest-Manifest: ").append(b64(sha256(mfBytes))).append("\r\n")
        for (section in manifest.split("\r\n\r\n").drop(1)) {
            if (section.isBlank()) continue
            // 一个 section 的字节：把结尾 CRLFCRLF 计入摘要（apksigner 行为）
            val secBytes = (section + "\r\n\r\n").toByteArray(Charsets.UTF_8)
            val nameLine = section.lineSequence().firstOrNull { it.startsWith("Name: ") }?.removePrefix("Name: ") ?: continue
            sf.append("\r\n").append("Name: ").append(nameLine).append("\r\n")
            sf.append("SHA-256-Digest: ").append(b64(sha256(secBytes))).append("\r\n")
        }
        val sfBytes = sf.toString().toByteArray(Charsets.UTF_8)
        val rsa = pkcs7SignedData(sfBytes, key, cert)
        sfBytes to rsa
    }.getOrNull()

    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    // ---- 密钥与证书（随应用发布的内置签名材料）----

    @Volatile private var keyCache: PrivateKey? = null
    @Volatile private var certCache: X509Certificate? = null

    fun loadCert(): X509Certificate? {
        if (customCert != null) return customCert
        certCache?.let { return it }
        val c = runCatching {
            val one = readAssetViaContext()?.first ?: return null
            CertificateFactory.getInstance("X.509").generateCertificate(one.inputStream()) as X509Certificate
        }.getOrNull() ?: return null
        certCache = c
        return c
    }

    /** 使用自定义证书（JKS 解出的密钥对），传 null 恢复内置证书 */
    @Volatile private var customKey: PrivateKey? = null
    @Volatile private var customCert: X509Certificate? = null

    fun useCustomKey(key: PrivateKey?, cert: X509Certificate?) {
        customKey = key
        customCert = cert
        // 清掉 PKCS#7 片段缓存
        keyCache = null
        certCache = null
    }

    /** 从 JKS 文件加载自定义证书；失败返回原因字符串，成功返回 null */
    fun loadJks(jksFile: candy.ide.fs.CandyPath, password: CharArray): String? {
        val m = JksReader.read(jksFile, password)
        if (m == null) return "无法读取 JKS（密码错误或格式不受支持）"
        if (!m.key.algorithm.equals("RSA", true)) return "仅支持 RSA 密钥"
        useCustomKey(m.key, m.cert)
        return null
    }

    fun loadKey(): PrivateKey? {
        customKey?.let { return it }
        keyCache?.let { return it }
        val k = runCatching {
            val two = readAssetViaContext()?.second ?: return null
            KeyFactory.getInstance("RSA").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(two))
        }.getOrNull() ?: return null
        keyCache = k
        return k
    }

    @Volatile private var ctxRef: android.content.Context? = null
    fun attach(ctx: android.content.Context) { ctxRef = ctx }

    private fun readAssetViaContext(): Pair<ByteArray, ByteArray>? {
        val ctx = ctxRef ?: return null
        val a = ctx.assets.open(ALIAS_CERT).use { it.readBytes() }
        val b = ctx.assets.open(ALIAS_KEY).use { it.readBytes() }
        return a to b
    }

    // ---- PKCS#7 SignedData（手写 DER）----

    private fun pkcs7SignedData(content: ByteArray, key: PrivateKey, cert: X509Certificate): ByteArray {
        val sha256Oid = byteArrayOf(0x60, 0x86.toByte(), 0x48.toByte(), 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)  // 2.16.840.1.101.3.4.2.1
        val rsaOid = byteArrayOf(0x2A, 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)
        val dataOid = byteArrayOf(0x2A, 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01)
        val contentTypeAttrOid = byteArrayOf(0x2A, 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x09, 0x03)
        val messageDigestAttrOid = byteArrayOf(0x2A, 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x09, 0x04)

        // authenticatedAttributes：签名对象是 SET{...} 的完整 DER（含 0x31 tag），
        // 线上编码用 [0] 隐式 tagged：A0 节点直接装着各 Attribute（jarsigner/Android 按 A0→31 改 tag 后吃它的孩子）
        val attrContentType = derSeq(derOid(contentTypeAttrOid), derSet(derOid(dataOid)))
        val attrDigest = derSeq(derOid(messageDigestAttrOid), derSet(derOctet(sha256(content))))
        val authAttrsDer = derSet(attrContentType, attrDigest)  // 签名对象
        val authAttrs = concat(attrContentType, attrDigest)     // 线上 [0] 内容（剥掉 SET 外壳）

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(key)
        sig.update(authAttrsDer)
        val signature = sig.sign()

        val signerInfo = derSeq(
            derInt(1),
            derSeq(derRaw(cert.issuerX500Principal.encoded), derTL(0x02, cert.serialNumber.toByteArray())),
            derSeq(derOid(sha256Oid), derNull()),
            derTag(0xA0.toByte(), authAttrs),
            derSeq(derOid(rsaOid), derNull()),
            derOctet(signature),
        )

        val signedData = derSeq(
            derInt(1),
            derSet(derSeq(derOid(sha256Oid), derNull())),
            derSeq(derOid(dataOid)),
            derTag(0xA0.toByte(), derRaw(cert.encoded)),
            derSet(signerInfo),
        )
        val contentInfo = derSeq(derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)), derTag(0xA0.toByte(), signedData))
        return contentInfo
    }

    // ---- 极简 DER ----

    private fun derLen(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        else -> {
            val body = ArrayList<Byte>()
            var v = n
            while (v > 0) { body.add(0, (v and 0xFF).toByte()); v = v ushr 8 }
            val out = ByteArray(body.size + 1)
            out[0] = (0x80 or body.size).toByte()
            body.forEachIndexed { i, b -> out[i + 1] = b }
            out
        }
    }

    private fun derTL(tag: Byte, content: ByteArray): ByteArray {
        val len = derLen(content.size)
        return ByteArray(1 + len.size + content.size).also {
            it[0] = tag; System.arraycopy(len, 0, it, 1, len.size); System.arraycopy(content, 0, it, 1 + len.size, content.size)
        }
    }

    private fun derRaw(b: ByteArray): ByteArray = b
    private fun derSeq(vararg parts: ByteArray): ByteArray = derTL(0x30, concat(*parts))
    private fun derSet(vararg parts: ByteArray): ByteArray = derTL(0x31, concat(*parts))
    private fun derOctet(b: ByteArray): ByteArray = derTL(0x04, b)
    private fun derOid(oid: ByteArray): ByteArray = derTL(0x06, oid)
    private fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)
    private fun derInt(v: Int): ByteArray = derTL(0x02, if (v < 0x80) byteArrayOf(v.toByte()) else byteArrayOf(0x00, (v ushr 8).toByte(), v.toByte()))
    private fun derTag(tag: Byte, content: ByteArray): ByteArray = derTL(tag, content)
    private fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var off = 0
        for (p in parts) { System.arraycopy(p, 0, out, off, p.size); off += p.size }
        return out
    }
}
