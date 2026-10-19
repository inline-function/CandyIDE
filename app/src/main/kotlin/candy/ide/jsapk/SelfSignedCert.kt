package candy.ide.jsapk

import java.math.BigInteger
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.io.ByteArrayInputStream
import java.util.Date

/**
 * 极小 X.509 v3 自签名证书生成器（无 BouncyCastle 依赖）。
 * 只支持 RSA + SHA256WithRSA、单一 CN、无扩展。
 */
object SelfSignedCert {

    private const val OID_SHA256_RSA = "1.2.840.113549.1.1.11"
    private const val OID_RSA = "1.2.840.113549.1.1.1"
    private const val OID_CN = "2.5.4.3"

    fun make(pair: KeyPair, commonName: String, notBefore: Date, notAfter: Date): X509Certificate {
        val tbs = buildTbs(pair, commonName, notBefore, notAfter)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(pair.private)
        sig.update(tbs)
        val sigBytes = sig.sign()
        // Certificate ::= SEQUENCE { tbsCertificate, sigAlgId, signatureValue }
        val out = DerBuf()
        out.seq {
            out.raw(tbs)
            out.seq { out.oid(OID_SHA256_RSA); out.nulls() }
            out.bitString(sigBytes)
        }
        val der = out.toByteArray()
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    private fun buildTbs(pair: KeyPair, cn: String, notBefore: Date, notAfter: Date): ByteArray {
        val spki = pair.public.encoded  // SubjectPublicKeyInfo（标准 DER）
        val serial = BigInteger.valueOf(System.currentTimeMillis()).abs()
        val out = DerBuf()
        out.seq {
            // version [0] EXPLICIT v3
            out.ctx0 { out.int_(3) }
            out.bigInt(serial)
            out.seq { out.oid(OID_SHA256_RSA); out.nulls() }
            // issuer = CN
            out.seq { out.set { out.seq { out.oid(OID_CN); out.str(cn) } } }
            out.seq { out.time(notBefore); out.time(notAfter) }
            // subject = CN（自签名）
            out.seq { out.set { out.seq { out.oid(OID_CN); out.str(cn) } } }
            out.raw(spki)
        }
        return out.toByteArray()
    }
}

/** 简易 DER 写入器：元素按写入顺序拼接 */
internal class DerBuf {
    private val parts = ArrayList<Pair<Int, ByteArray>>()  // (tag, content)
    fun toByteArray(): ByteArray {
        var total = 0
        val encoded = ArrayList<ByteArray>()
        for ((tag, content) in parts) {
            // tag=-1：raw 元素按原样拼接（如已编码好的 SubjectPublicKeyInfo）
            val e = if (tag < 0) content else byteArrayOf(tag.toByte()) + derLen(content.size) + content
            encoded.add(e)
            total += e.size
        }
        val res = ByteArray(total)
        var p = 0
        for (e in encoded) { e.forEach { res[p++] = it } }
        return res
    }
    fun raw(b: ByteArray) { parts.add(Pair(-1, b)) }
    fun seq(block: DerBuf.() -> Unit) { parts.add(Pair(0x30, DerBuf().apply(block).toByteArray())) }
    fun set(block: DerBuf.() -> Unit) { parts.add(Pair(0x31, DerBuf().apply(block).toByteArray())) }
    fun ctx0(block: DerBuf.() -> Unit) { parts.add(Pair(0xA0, DerBuf().apply(block).toByteArray())) }
    fun oid(s: String) { parts.add(Pair(0x06, encodeOid(s))) }
    fun int_(v: Int) { parts.add(Pair(0x02, encodeInt(BigInteger.valueOf(v.toLong())))) }
    fun bigInt(v: BigInteger) { parts.add(Pair(0x02, encodeInt(v))) }
    fun str(s: String) { parts.add(Pair(0x0C, s.toByteArray(Charsets.UTF_8))) }
    fun nulls() { parts.add(Pair(0x05, ByteArray(0))) }
    fun bitString(b: ByteArray) { parts.add(Pair(0x03, byteArrayOf(0) + b)) }
    fun time(d: Date) {
        // 2049 年前用 UTCTime（YYMMDD…），之后用 GeneralizedTime（YYYYMMDD…）
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { time = d }
        val year = c.get(java.util.Calendar.YEAR)
        if (year < 2050) {
            val s = String.format("%02d%02d%02d%02d%02d%02dZ", year % 100,
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH),
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
            parts.add(Pair(0x17, s.toByteArray(Charsets.US_ASCII)))
        } else {
            val s = String.format("%04d%02d%02d%02d%02d%02dZ", year,
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH),
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
            parts.add(Pair(0x18, s.toByteArray(Charsets.US_ASCII)))
        }
    }
    private fun derLen(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    }
    private fun encodeOid(s: String): ByteArray {
        val arcs = s.split(".").map { it.toLong() }
        val out = ArrayList<Byte>()
        out.add((arcs[0] * 40 + arcs[1]).toByte())
        for (i in 2 until arcs.size) {
            var v = arcs[i]
            val tmp = ArrayList<Byte>()
            tmp.add((v and 0x7F).toByte())
            v = v shr 7
            while (v > 0) {
                tmp.add(((v and 0x7F) or 0x80).toByte())
                v = v shr 7
            }
            for (j in tmp.size - 1 downTo 0) out.add(tmp[j])
        }
        return out.toByteArray()
    }
    private fun encodeInt(v: BigInteger): ByteArray {
        if (v.signum() == 0) return byteArrayOf(0)
        var b = v.toByteArray()
        // 去掉前导零，并保证正数最高位为 0（否则前置 0x00）
        if (b[0] == 0.toByte() && b.size > 1) b = b.copyOfRange(1, b.size)
        if ((b[0].toInt() and 0x80) != 0) b = byteArrayOf(0) + b
        return b
    }
}