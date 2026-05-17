package com.dianziqueen.app

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 本 App 相册：主密钥存于应用私有目录；照片以 AES-256-GCM 加密落盘，
 * AES 密钥由主密钥经 SHA-256 派生（满足「SHA-256 加密」语义且可解密展示）。
 */
object QueenAlbumVault {

    private const val VAULT_DIR = "queen_album"
    private const val KEY_FILE_NAME = "album_sha256_master.key"
    private const val KEY_HASH_FILE_NAME = "album_sha256_master.key.sha256"
    private const val PHOTOS_DIR = "photos_enc"
    private const val FILE_EXT = ".qenc"
    private const val MAGIC = "QALB1"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val MASTER_KEY_LEN = 32

    fun ensureMasterKey(context: Context): Boolean {
        val keyFile = masterKeyFile(context)
        if (keyFile.exists() && keyFile.length() >= MASTER_KEY_LEN) return true
        return generateMasterKey(context)
    }

    fun hasMasterKey(context: Context): Boolean {
        val f = masterKeyFile(context)
        return f.exists() && f.length() >= MASTER_KEY_LEN
    }

    fun importPlainBytes(context: Context, plain: ByteArray): String? {
        if (!ensureMasterKey(context)) return null
        return try {
            val encrypted = encrypt(plain, readMasterKey(context))
            val id = "${System.currentTimeMillis()}"
            photoFile(context, id).writeBytes(encrypted)
            id
        } catch (_: Exception) {
            null
        }
    }

    fun importPlainStream(context: Context, input: InputStream): String? {
        return importPlainBytes(context, input.readBytes())
    }

    fun listPhotoIds(context: Context): List<String> {
        val dir = photosDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(FILE_EXT) }
            ?.map { it.name.removeSuffix(FILE_EXT) }
            ?.sortedDescending()
            ?: emptyList()
    }

    fun decryptToBytes(context: Context, photoId: String): ByteArray? {
        if (!hasMasterKey(context)) return null
        val file = photoFile(context, photoId)
        if (!file.exists()) return null
        return try {
            decrypt(file.readBytes(), readMasterKey(context))
        } catch (_: Exception) {
            null
        }
    }

    fun isRedeemed(context: Context, photoId: String): Boolean =
        redeemedMarkerFile(context, photoId).exists()

    /** 赎回：仅在本 App 加密库内标记，不写入系统相册。 */
    fun redeemPhoto(context: Context, photoId: String): Boolean {
        if (!photoFile(context, photoId).exists()) return false
        if (isRedeemed(context, photoId)) return true
        return try {
            redeemedMarkerFile(context, photoId).writeText("1")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deletePhoto(context: Context, photoId: String): Boolean {
        redeemedMarkerFile(context, photoId).delete()
        return photoFile(context, photoId).delete()
    }

    private fun generateMasterKey(context: Context): Boolean {
        return try {
            vaultRoot(context).mkdirs()
            val master = ByteArray(MASTER_KEY_LEN).also { SecureRandom().nextBytes(it) }
            masterKeyFile(context).writeBytes(master)
            val hashHex = sha256Hex(master)
            keyHashFile(context).writeText(hashHex)
            photosDir(context).mkdirs()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readMasterKey(context: Context): ByteArray {
        val bytes = masterKeyFile(context).readBytes()
        require(bytes.size >= MASTER_KEY_LEN) { "invalid master key" }
        return bytes.copyOf(MASTER_KEY_LEN)
    }

    private fun deriveAesKey(masterKey: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(masterKey)
    }

    private fun encrypt(plain: ByteArray, masterKey: ByteArray): ByteArray {
        val aesKey = deriveAesKey(masterKey)
        val iv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        val ciphertext = cipher.doFinal(plain)
        return MAGIC.toByteArray(Charsets.US_ASCII) + iv + ciphertext
    }

    private fun decrypt(blob: ByteArray, masterKey: ByteArray): ByteArray {
        val magicBytes = MAGIC.toByteArray(Charsets.US_ASCII)
        require(blob.size > magicBytes.size + GCM_IV_LEN) { "truncated blob" }
        for (i in magicBytes.indices) {
            require(blob[i] == magicBytes[i]) { "bad magic" }
        }
        var offset = magicBytes.size
        val iv = blob.copyOfRange(offset, offset + GCM_IV_LEN)
        offset += GCM_IV_LEN
        val ciphertext = blob.copyOfRange(offset, blob.size)
        val aesKey = deriveAesKey(masterKey)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    private fun vaultRoot(context: Context): File =
        File(context.filesDir, VAULT_DIR)

    private fun masterKeyFile(context: Context): File =
        File(vaultRoot(context), KEY_FILE_NAME)

    private fun keyHashFile(context: Context): File =
        File(vaultRoot(context), KEY_HASH_FILE_NAME)

    private fun photosDir(context: Context): File =
        File(vaultRoot(context), PHOTOS_DIR)

    private fun photoFile(context: Context, id: String): File =
        File(photosDir(context), "$id$FILE_EXT")

    private fun redeemedMarkerFile(context: Context, id: String): File =
        File(photosDir(context), "$id.redeemed")
}
