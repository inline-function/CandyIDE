package candy.ide.jsapk

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

/**
 * 生成自签名 RSA 证书并写成 JKS（Java KeyStore version 2）。
 *
 * 与 [JksReader] 对称：容器 magic 0xFEEDFEED，私钥条目按
 *   tag(4)=1 + alias(UTF) + date(8) + protectedPrivKeyDER + certChain
 * 其中 protectedPrivKey 是 EncryptedPrivateKeyInfo（OID 1.3.6.1.4.1.42.2.17.1.1，
 * params=NULL），encryptedData = salt(20) + Y + sha1(pwd_utf16be || plainKey)(20)，
 * xorKey 链 d0=salt、di=sha1(pwd||d(i-1))，Y = PKCS#8 异或 xorKey。
 */
object JksWriter {

    /** 生成并落盘，返回 keystore 文件 */
    fun generate(
        target: File,
        alias: String,
        password: CharArray,
        commonName: String,
        validityYears: Int,
        keySize: Int = 2048,
    ): Result<File> = runCatching {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(keySize, SecureRandom())
        val pair: KeyPair = kpg.generateKeyPair()
        val notBefore = Date()
        val notAfter = Date(System.currentTimeMillis() + validityYears.toLong() * 365 * 24 * 3600 * 1000)
        val cert = SelfSignedCert.make(pair, commonName, notBefore, notAfter)
        val protectedKey = protect(pair.private.encoded, password)
        target.parentFile?.mkdirs()
        // 先把条目序列化进内存，再追加整库 HMAC（DigestInputStream 的验证顺序要求）
        val body = java.io.ByteArrayOutputStream().apply {
            DataOutputStream(this).use { dos ->
                dos.writeInt(0xFEEDFEED.toInt())
                dos.writeInt(2)
                dos.writeInt(1)
                // 私钥条目
                dos.writeInt(1)
                dos.writeUTF(alias)
                dos.writeLong(notBefore.time)
                dos.writeInt(protectedKey.size)
                dos.write(protectedKey)
                // 证书链：v2 格式为 [UTF 类型字符串] + [int 长度 + DER]
                dos.writeInt(1)
                dos.writeUTF("X.509")
                val der = cert.encoded
                dos.writeInt(der.size)
                dos.write(der)
            }
        }.toByteArray()
        val h = MessageDigest.getInstance("SHA-1")
        h.update(utf16be(password))
        h.update("Mighty Aphrodite".toByteArray(Charsets.UTF_8))
        h.update(body)
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { it.write(body + h.digest()) }
        target
    }

    private fun protect(pkcs8: ByteArray, password: CharArray): ByteArray {
        val pwd = utf16be(password)
        val salt = ByteArray(20).also { SecureRandom().nextBytes(it) }
        val md = MessageDigest.getInstance("SHA-1")
        // 生成 xorKey 链
        val xorKey = ByteArray(pkcs8.size)
        var digest: ByteArray = salt
        var off = 0
        while (off < xorKey.size) {
            md.update(pwd)
            md.update(digest)
            digest = md.digest()
            val n = Math.min(20, xorKey.size - off)
            System.arraycopy(digest, 0, xorKey, off, n)
            off += n
        }
        val y = ByteArray(pkcs8.size) { i -> (pkcs8[i].toInt() xor xorKey[i].toInt()).toByte() }
        md.update(pwd)
        md.update(pkcs8)
        val hash = md.digest()
        // R = salt + Y + hash
        return salt + y + hash
    }

    private fun utf16be(password: CharArray): ByteArray {
        val out = ByteArray(password.size * 2)
        for (i in password.indices) {
            out[i * 2] = (password[i].code shr 8).toByte()
            out[i * 2 + 1] = password[i].code.toByte()
        }
        return out
    }
}