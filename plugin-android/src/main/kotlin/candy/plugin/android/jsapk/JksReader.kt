package candy.plugin.android.jsapk

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * 最小 JKS（Java KeyStore）读取器：取出第一个私钥条目（RSA + X.509）。
 *
 * Android 平台 KeyStore 引擎不认 "JKS" 类型（无 BouncyCastle provider），
 * 这里按 Oracle KeyProtector 的公开算法手工解码：
 *   protectedKey = salt(20) + Y + sha1(pwd_utf16be || plainKey)(20)
 *   xorKey 链：d0 = salt, di = sha1(pwd || d(i-1))，plainKey = Y XOR xorKey
 *   plainKey 即 PKCS#8 编码的 RSA 私钥。
 */
object JksReader {

    data class KeyMaterial(val key: PrivateKey, val cert: X509Certificate)

    fun read(jksFile: candy.ide.fs.CandyPath, password: CharArray): KeyMaterial? = runCatching {
        val buf = jksFile.inputStream().use { it.readBytes() }
        parse(buf, utf16be(password))
    }.getOrNull()

    private fun utf16be(password: CharArray): ByteArray {
        val out = ByteArray(password.size * 2)
        for (i in password.indices) {
            out[i * 2] = (password[i].code shr 8).toByte()
            out[i * 2 + 1] = password[i].code.toByte()
        }
        return out
    }

    private fun parse(data: ByteArray, pwd: ByteArray): KeyMaterial {
        val dis = DataInputStream(ByteArrayInputStream(data))
        val magic = dis.readInt()
        if (magic != 0xFFFFFEED.toInt() && magic != 0xFEEDFEED.toInt()) error("不是 JKS 格式")
        if (dis.readInt() != 2) error("仅支持 JKS version 2")
        val count = dis.readInt()
        for (i in 0 until count) {
            val tag = dis.readInt()
            dis.readUTF()  // alias
            dis.readLong()  // 创建时间
            if (tag == 1) {
                val keyLen = dis.readInt()
                val enc = ByteArray(keyLen)
                dis.readFully(enc)
                val chainLen = dis.readInt()
                var cert: X509Certificate? = null
                for (c in 0 until chainLen) {
                    // v2：[UTF 类型字符串] + [int 长度 + DER]
                    val certType = dis.readUTF()
                    val cf = CertificateFactory.getInstance(certType)
                    val clen = dis.readInt()
                    val cbuf = ByteArray(clen)
                    dis.readFully(cbuf)
                    if (cert == null) cert = cf.generateCertificate(ByteArrayInputStream(cbuf)) as X509Certificate
                }
                val key = recoverKey(enc, pwd) ?: error("无法解出私钥（密码错误？）")
                return KeyMaterial(key, cert ?: error("证书链为空"))
            } else {
                // 信任证书条目：4 字节长度 + DER 体
                val clen = dis.readInt()
                dis.skipBytes(clen)
            }
        }
        error("keystore 里没有私钥条目")
    }


    /** 从 EncryptedPrivateKeyInfo 中取出 protectedKey（OCTET STRING 内容） */
    private fun derContent(buf: ByteArray, off: Int): Triple<Int, Int, Int> {
        // 返回 (contentLen, contentOff, tag)
        val tag = buf[off].toInt() and 0xFF
        var p = off + 1
        var first = buf[p].toInt() and 0xFF
        p++
        var len = 0
        if (first < 0x80) len = first
        else {
            val n = first and 0x7F
            for (k in 0 until n) len = (len shl 8) or (buf[p].toInt() and 0xFF).also { p++ }
        }
        return Triple(len, p, tag)
    }

    private fun recoverKey(enc: ByteArray, pwd: ByteArray): PrivateKey? = runCatching {
        // EncryptedPrivateKeyInfo ::= SEQUENCE { algId SEQUENCE, encData OCTET STRING }
        val (algLen, algOff, _) = derContent(enc, 0)
        val (dataLen, dataOff, dataTag) = derContent(enc, algOff + algLen)
        if (dataTag != 0x04) return@runCatching null
        val protectedKey = enc.copyOfRange(dataOff, dataOff + dataLen)
        val saltLen = 20
        val digestLen = 20
        if (protectedKey.size <= saltLen + digestLen) return@runCatching null
        val salt = protectedKey.copyOfRange(0, saltLen)
        val encrKeyLen = protectedKey.size - saltLen - digestLen
        val encrKey = protectedKey.copyOfRange(saltLen, saltLen + encrKeyLen)
        val storeHash = protectedKey.copyOfRange(saltLen + encrKeyLen, protectedKey.size)
        var numRounds = encrKeyLen / digestLen
        if (encrKeyLen % digestLen != 0) numRounds++
        val xorKey = ByteArray(encrKeyLen)
        val md = MessageDigest.getInstance("SHA-1")
        var digest = salt
        var xorOffset = 0
        for (i in 0 until numRounds) {
            md.update(pwd)
            md.update(digest)
            digest = md.digest()
            val n = if (i < numRounds - 1) digestLen else xorKey.size - xorOffset
            System.arraycopy(digest, 0, xorKey, xorOffset, n)
            xorOffset += digestLen
        }
        val plainKey = ByteArray(encrKeyLen) { idx -> (encrKey[idx].toInt() xor xorKey[idx].toInt()).toByte() }
        md.update(pwd)
        md.update(plainKey)
        if (!md.digest().contentEquals(storeHash)) return@runCatching null
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(plainKey))
    }.getOrNull()
}