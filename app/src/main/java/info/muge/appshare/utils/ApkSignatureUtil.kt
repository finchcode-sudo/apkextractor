package info.muge.appshare.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import java.util.jar.JarFile

/**
 * APK 签名信息数据类
 */
data class ApkSignatureInfo(
    // 签名方案版本
    val signatureSchemes: Set<SignatureScheme> = emptySet(),
    // 证书信息
    val subject: String = "",
    val issuer: String = "",
    val serialNumber: String = "",
    val notBefore: Date? = null,
    val notAfter: Date? = null,
    // 公钥信息
    val publicKeyFormat: String = "",
    val publicKeyAlgorithm: String = "",
    val publicKeyExponent: String = "",
    val modulusSize: String = "",
    val modulus: String = "",
    // 签名算法信息
    val signatureAlgorithm: String = "",
    val signatureAlgorithmOID: String = "",
    // 指纹
    val md5: String = "",
    val sha1: String = "",
    val sha256: String = "",
    // 证书原始数据 (DER, HEX)
    val certHex: String = "",
    // 是否有效
    val isValid: Boolean = true,
    val errorMessage: String = ""
)

/**
 * 签名方案枚举
 */
enum class SignatureScheme(val version: Int, val displayName: String) {
    V1(1, "JAR Signing (v1)"),
    V2(2, "APK Signature Scheme v2"),
    V3(3, "APK Signature Scheme v3"),
    V31(31, "APK Signature Scheme v3.1"),
    V4(4, "APK Signature Scheme v4");

    companion object {
        fun fromVersion(version: Int): SignatureScheme? {
            return entries.find { it.version == version }
        }
    }
}

/**
 * APK 签名工具类
 * 提供签名方案检测、证书信息提取等功能
 */
object ApkSignatureUtil {

    private const val TAG = "ApkSignatureUtil"

    // APK Signing Block 魔数
    private const val APK_SIGNING_BLOCK_MAGIC = "APK Sig Block 42"

    // 签名方案 ID (使用 Long 类型以支持超出 Int 范围的值)
    private const val SIGNATURE_SCHEME_V2_ID: Long = 0x7109871a
    private const val SIGNATURE_SCHEME_V3_ID: Long = 0xf05368c0L
    private const val SIGNATURE_SCHEME_V31_ID: Long = 0x1b93ad61

    // ZIP / APK Signing Block 相关常量（参考 LibChecker 的 ApkSignatureSchemeDetector 实现）
    private const val ZIP_EOCD_SIGNATURE = 0x06054b50
    private const val ZIP_CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
    private const val ZIP_CENTRAL_DIRECTORY_HEADER_SIZE = 46
    private const val ZIP_CENTRAL_DIRECTORY_FILE_NAME_LENGTH_OFFSET = 28
    private const val ZIP_CENTRAL_DIRECTORY_EXTRA_LENGTH_OFFSET = 30
    private const val ZIP_CENTRAL_DIRECTORY_COMMENT_LENGTH_OFFSET = 32
    private const val ZIP_EOCD_MIN_SIZE = 22
    private const val ZIP_EOCD_CENTRAL_DIR_SIZE_OFFSET = 12
    private const val ZIP_EOCD_CENTRAL_DIR_OFFSET = 16
    private const val ZIP_EOCD_COMMENT_LENGTH_OFFSET = 20
    private const val ZIP_MAX_COMMENT_SIZE = 65535
    private const val MAX_ZIP_CENTRAL_DIRECTORY_SIZE = 64L * 1024L * 1024L
    private const val APK_SIGNING_BLOCK_FOOTER_SIZE = 24
    private const val MAX_APK_SIGNING_BLOCK_SIZE = 32L * 1024L * 1024L

    /**
     * 获取完整的 APK 签名信息
     */
    fun getFullSignatureInfo(apkPath: String, packageInfo: PackageInfo? = null): ApkSignatureInfo {
        return try {
            val schemes = detectSignatureSchemes(apkPath)
            // 优先从 PackageInfo 的签名字节解析证书（对仅 v2/v3 签名、无 v1 的 APK 依然有效）
            var certInfo = packageInfo?.let { getCertificateInfoFromPackageInfo(it) }
            if (certInfo == null || certInfo.first.isEmpty()) {
                certInfo = getCertificateInfo(apkPath)
            }
            val fingerprints = packageInfo?.let { getSignatureFingerprints(it) }
                ?: getSignatureFingerprintsFromApk(apkPath)

            ApkSignatureInfo(
                signatureSchemes = schemes,
                subject = certInfo.first,
                issuer = certInfo.second,
                serialNumber = certInfo.third,
                notBefore = certInfo.fourth,
                notAfter = certInfo.fifth,
                publicKeyFormat = certInfo.sixth,
                publicKeyAlgorithm = certInfo.seventh,
                publicKeyExponent = certInfo.eighth,
                modulusSize = certInfo.ninth,
                modulus = certInfo.tenth,
                signatureAlgorithm = certInfo.eleventh,
                signatureAlgorithmOID = certInfo.twelfth,
                md5 = fingerprints.first,
                sha1 = fingerprints.second,
                sha256 = fingerprints.third,
                certHex = certInfo.thirteenth,
                isValid = certInfo.first.isNotEmpty()
            )
        } catch (e: Exception) {
            LogUtil.logException("Failed to get signature info", e, TAG)
            ApkSignatureInfo(
                isValid = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 检测 APK 使用的签名方案
     * 算法参考 LibChecker 的 ApkSignatureSchemeDetector：
     * 直接解析 ZIP Central Directory + APK Signing Block，避免用 JarFile 解析可能带来的异常，
     * 并对 EOCD 位置做注释长度校验，避免在二进制数据中误判魔数。
     */
    fun detectSignatureSchemes(apkPath: String): Set<SignatureScheme> {
        val schemes = mutableSetOf<SignatureScheme>()
        try {
            val file = File(apkPath)
            if (!file.exists() || !file.canRead()) return schemes

            RandomAccessFile(file, "r").use { raf ->
                val centralDir = findCentralDirectory(raf) ?: return schemes

                if (hasJarSignature(raf, centralDir)) {
                    schemes.add(SignatureScheme.V1)
                }

                val blockIds = readApkSigningBlockIds(raf, centralDir.offset)
                if (SIGNATURE_SCHEME_V2_ID.toInt() in blockIds) schemes.add(SignatureScheme.V2)
                if (SIGNATURE_SCHEME_V3_ID.toInt() in blockIds) schemes.add(SignatureScheme.V3)
                if (SIGNATURE_SCHEME_V31_ID.toInt() in blockIds) schemes.add(SignatureScheme.V31)
            }

            if (File("${file.absolutePath}.idsig").exists()) {
                schemes.add(SignatureScheme.V4)
            }
        } catch (e: Exception) {
            LogUtil.e("Error detecting signature schemes", e, TAG)
        }
        return schemes
    }

    private data class ZipCentralDirectory(val offset: Long, val size: Long)

    /**
     * 从文件末尾查找 EOCD (End Of Central Directory)，并校验注释长度与实际剩余字节数一致，
     * 避免误将数据里偶然出现的魔数字节当成真正的 EOCD。
     */
    private fun findCentralDirectory(raf: RandomAccessFile): ZipCentralDirectory? {
        val fileLength = raf.length()
        if (fileLength < ZIP_EOCD_MIN_SIZE) return null

        val readSize = minOf(fileLength, (ZIP_EOCD_MIN_SIZE + ZIP_MAX_COMMENT_SIZE).toLong()).toInt()
        val buffer = ByteArray(readSize)
        raf.seek(fileLength - readSize)
        raf.readFully(buffer)

        for (offset in (readSize - ZIP_EOCD_MIN_SIZE) downTo 0) {
            if (readIntLE(buffer, offset).toInt() == ZIP_EOCD_SIGNATURE &&
                readUnsignedShortLE(buffer, offset + ZIP_EOCD_COMMENT_LENGTH_OFFSET) == readSize - offset - ZIP_EOCD_MIN_SIZE
            ) {
                return ZipCentralDirectory(
                    offset = readUnsignedIntLE(buffer, offset + ZIP_EOCD_CENTRAL_DIR_OFFSET),
                    size = readUnsignedIntLE(buffer, offset + ZIP_EOCD_CENTRAL_DIR_SIZE_OFFSET)
                )
            }
        }
        return null
    }

    /**
     * 检测是否存在 v1 (JAR) 签名：直接解析 Central Directory 记录，查找 META-INF 目录下 .RSA / .DSA / .EC 文件
     */
    private fun hasJarSignature(raf: RandomAccessFile, centralDirectory: ZipCentralDirectory): Boolean {
        if (centralDirectory.size <= 0L || centralDirectory.size > MAX_ZIP_CENTRAL_DIRECTORY_SIZE) return false

        val header = ByteArray(ZIP_CENTRAL_DIRECTORY_HEADER_SIZE)
        var remaining = centralDirectory.size
        raf.seek(centralDirectory.offset)
        while (remaining >= ZIP_CENTRAL_DIRECTORY_HEADER_SIZE) {
            raf.readFully(header)
            if (readIntLE(header, 0).toInt() != ZIP_CENTRAL_DIRECTORY_SIGNATURE) return false

            val fileNameLength = readUnsignedShortLE(header, ZIP_CENTRAL_DIRECTORY_FILE_NAME_LENGTH_OFFSET)
            val extraLength = readUnsignedShortLE(header, ZIP_CENTRAL_DIRECTORY_EXTRA_LENGTH_OFFSET)
            val commentLength = readUnsignedShortLE(header, ZIP_CENTRAL_DIRECTORY_COMMENT_LENGTH_OFFSET)
            val entrySize = ZIP_CENTRAL_DIRECTORY_HEADER_SIZE + fileNameLength + extraLength + commentLength
            if (entrySize > remaining) return false

            val fileName = ByteArray(fileNameLength)
            raf.readFully(fileName)
            val name = String(fileName, Charsets.US_ASCII).uppercase()
            if (name.startsWith("META-INF/") &&
                (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))
            ) {
                return true
            }

            raf.seek(raf.filePointer + extraLength + commentLength)
            remaining -= entrySize
        }
        return false
    }

    /**
     * 读取 APK Signing Block 中的签名方案 ID 集合
     */
    private fun readApkSigningBlockIds(raf: RandomAccessFile, centralDirOffset: Long): Set<Int> {
        if (centralDirOffset < APK_SIGNING_BLOCK_FOOTER_SIZE) return emptySet()

        val footer = ByteArray(APK_SIGNING_BLOCK_FOOTER_SIZE)
        raf.seek(centralDirOffset - APK_SIGNING_BLOCK_FOOTER_SIZE)
        raf.readFully(footer)

        val magic = footer.copyOfRange(8, 24)
        if (!magic.contentEquals(APK_SIGNING_BLOCK_MAGIC.toByteArray())) return emptySet()

        val blockSize = readLongLE(footer, 0)
        val totalSize = blockSize + 8
        if (blockSize < APK_SIGNING_BLOCK_FOOTER_SIZE ||
            totalSize > centralDirOffset ||
            totalSize > MAX_APK_SIGNING_BLOCK_SIZE
        ) {
            return emptySet()
        }

        val block = ByteArray(totalSize.toInt())
        raf.seek(centralDirOffset - totalSize)
        raf.readFully(block)
        if (readLongLE(block, 0) != blockSize) return emptySet()

        val ids = mutableSetOf<Int>()
        var offset = 8
        val pairsEnd = block.size - APK_SIGNING_BLOCK_FOOTER_SIZE
        while (offset < pairsEnd) {
            if (offset + 8 > pairsEnd) break
            val pairSize = readLongLE(block, offset)
            offset += 8
            if (pairSize < 4 || pairSize > (pairsEnd - offset).toLong()) break
            ids += readIntLE(block, offset).toInt()
            offset += pairSize.toInt()
        }
        return ids
    }

    /**
     * 从 PackageInfo 的签名字节解析证书信息
     * 不依赖 v1(JAR) 签名，对仅使用 v2/v3 签名的 APK（如大多数 Google Play 应用）同样有效
     */
    private fun getCertificateInfoFromPackageInfo(packageInfo: PackageInfo): CertInfo? {
        return try {
            val signatures: Array<out Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            val signatureBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
            val x509cert = certFactory.generateCertificate(
                java.io.ByteArrayInputStream(signatureBytes)
            ) as X509Certificate

            buildCertInfo(x509cert)
        } catch (e: Exception) {
            LogUtil.e("Error getting certificate info from PackageInfo", e, TAG)
            null
        }
    }

    /**
     * 从 X509Certificate 提取展示用的证书信息
     */
    private fun buildCertInfo(x509cert: X509Certificate): CertInfo {
        val subject = x509cert.subjectDN.toString()
        val issuer = x509cert.issuerDN.toString()
        val serial = x509cert.serialNumber.toString()
        val notBefore = x509cert.notBefore
        val notAfter = x509cert.notAfter
        val sigAlgorithm = x509cert.sigAlgName ?: ""
        val sigAlgorithmOID = x509cert.sigAlgOID ?: ""

        var pubKeyExponent = ""
        var modulusSize = ""
        var modulus = ""
        val publicKey = x509cert.publicKey
        val pubKeyFormat = publicKey.format ?: ""
        val pubKeyAlgorithm = publicKey.algorithm ?: ""
        if (publicKey is java.security.interfaces.RSAPublicKey) {
            pubKeyExponent = publicKey.publicExponent.toString()
            modulusSize = "${publicKey.modulus.bitLength()} bit"
            modulus = publicKey.modulus.toString(16)
        }

        val certHex = x509cert.encoded.joinToString("") { byte ->
            "%02x".format(byte)
        }

        return CertInfo(
            subject, issuer, serial, notBefore, notAfter,
            pubKeyFormat, pubKeyAlgorithm, pubKeyExponent, modulusSize, modulus,
            sigAlgorithm, sigAlgorithmOID, certHex
        )
    }

    /**
     * 从 APK 文件获取证书信息
     */
    private fun getCertificateInfo(apkPath: String): CertInfo {
        var subject = ""
        var issuer = ""
        var serial = ""
        var notBefore: Date? = null
        var notAfter: Date? = null
        var pubKeyFormat = ""
        var pubKeyAlgorithm = ""
        var pubKeyExponent = ""
        var modulusSize = ""
        var modulus = ""
        var sigAlgorithm = ""
        var sigAlgorithmOID = ""
        var certHex = ""

        try {
            JarFile(apkPath).use { jarFile ->
                val jarEntry = jarFile.getJarEntry("AndroidManifest.xml")
                if (jarEntry != null) {
                    val readBuffer = ByteArray(8192)
                    BufferedInputStream(jarFile.getInputStream(jarEntry)).use { input ->
                        while (input.read(readBuffer) != -1) { }
                    }
                    val certs = jarEntry.certificates
                    if (!certs.isNullOrEmpty()) {
                        val x509cert = certs[0] as X509Certificate
                        val info = buildCertInfo(x509cert)
                        subject = info.first
                        issuer = info.second
                        serial = info.third
                        notBefore = info.fourth
                        notAfter = info.fifth
                        pubKeyFormat = info.sixth
                        pubKeyAlgorithm = info.seventh
                        pubKeyExponent = info.eighth
                        modulusSize = info.ninth
                        modulus = info.tenth
                        sigAlgorithm = info.eleventh
                        sigAlgorithmOID = info.twelfth
                        certHex = info.thirteenth
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e("Error getting certificate info", e, TAG)
        }

        return CertInfo(
            subject, issuer, serial, notBefore, notAfter,
            pubKeyFormat, pubKeyAlgorithm, pubKeyExponent, modulusSize, modulus,
            sigAlgorithm, sigAlgorithmOID, certHex
        )
    }

    /**
     * 从 PackageInfo 获取签名指纹
     */
    private fun getSignatureFingerprints(packageInfo: PackageInfo): Triple<String, String, String> {
        return try {
            val signatures: Array<out Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9+ 使用新的签名 API
                val signingInfo = packageInfo.signingInfo
                signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val signatureBytes = signatures[0].toByteArray()
                Triple(
                    getHexString(MessageDigest.getInstance("MD5").digest(signatureBytes)),
                    getHexString(MessageDigest.getInstance("SHA1").digest(signatureBytes)),
                    getHexString(MessageDigest.getInstance("SHA256").digest(signatureBytes))
                )
            } else {
                Triple("", "", "")
            }
        } catch (e: Exception) {
            LogUtil.e("Error getting signature fingerprints", e, TAG)
            Triple("", "", "")
        }
    }

    /**
     * 从 APK 文件直接获取签名指纹（作为备用方法）
     */
    private fun getSignatureFingerprintsFromApk(apkPath: String): Triple<String, String, String> {
        return try {
            JarFile(apkPath).use { jarFile ->
                val jarEntry = jarFile.getJarEntry("AndroidManifest.xml")
                if (jarEntry != null) {
                    val readBuffer = ByteArray(8192)
                    BufferedInputStream(jarFile.getInputStream(jarEntry)).use { input ->
                        while (input.read(readBuffer) != -1) { }
                    }
                    val certs = jarEntry.certificates
                    if (!certs.isNullOrEmpty()) {
                        val certBytes = certs[0].encoded
                        Triple(
                            getHexString(MessageDigest.getInstance("MD5").digest(certBytes)),
                            getHexString(MessageDigest.getInstance("SHA1").digest(certBytes)),
                            getHexString(MessageDigest.getInstance("SHA256").digest(certBytes))
                        )
                    } else {
                        Triple("", "", "")
                    }
                } else {
                    Triple("", "", "")
                }
            }
        } catch (e: Exception) {
            LogUtil.e("Error getting fingerprints from APK", e, TAG)
            Triple("", "", "")
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private fun getHexString(bytes: ByteArray): String {
        return bytes.joinToString(":") { byte ->
            "%02X".format(byte)
        }
    }

    /**
     * 小端序读取 int (返回 Long 以支持无符号比较)
     */
    private fun readIntLE(data: ByteArray, offset: Int): Long {
        return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    /**
     * 小端序读取 unsigned short
     */
    private fun readUnsignedShortLE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    /**
     * 小端序读取 unsigned int，返回 Long
     */
    private fun readUnsignedIntLE(data: ByteArray, offset: Int): Long {
        return readIntLE(data, offset) and 0xFFFFFFFFL
    }

    /**
     * 小端序读取 long (8字节)
     */
    private fun readLongLE(data: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = value or ((data[offset + i].toLong() and 0xFFL) shl (8 * i))
        }
        return value
    }
}

/**
 * 证书信息辅助类
 */
private data class CertInfo(
    val first: String,      // subject
    val second: String,     // issuer
    val third: String,      // serial
    val fourth: Date?,      // notBefore
    val fifth: Date?,       // notAfter
    val sixth: String,      // publicKeyFormat
    val seventh: String,    // publicKeyAlgorithm
    val eighth: String,     // publicKeyExponent
    val ninth: String,      // modulusSize
    val tenth: String,      // modulus
    val eleventh: String,   // signatureAlgorithm
    val twelfth: String,    // signatureAlgorithmOID
    val thirteenth: String  // certHex
)
