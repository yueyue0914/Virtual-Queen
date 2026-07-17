package com.dianziqueen.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 相册加密库：
 * - 选取系统相册图：在**原 Uri 位置**原地加密覆盖，App 内只保存记录；
 * - 列表始终模糊显示，积分查看可临时解密预览；
 * - 赎回：在原位置解密恢复明文，并删除 App 内记录；
 * - 旧版仅存于 App 私有目录的 `.qenc` 仍兼容（无源 Uri 时赎回导出到 Pictures/QueenRedeemed）。
 */
object QueenAlbumVault {

    private const val TAG = "AlbumVault"
    private const val VAULT_DIR = "queen_album"
    private const val KEY_FILE_NAME = "album_sha256_master.key"
    private const val KEY_HASH_FILE_NAME = "album_sha256_master.key.sha256"
    private const val PHOTOS_DIR = "photos_enc"
    private const val RECORDS_DIR = "records"
    private const val FILE_EXT = ".qenc"
    private const val RECORD_EXT = ".json"
    private const val MAGIC = "QALB1"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val MASTER_KEY_LEN = 32
    private const val REDEEM_GALLERY_SUBDIR = "QueenRedeemed"
    private const val SEIZE_GALLERY_SUBDIR = "QueenSeized"

    fun ensureMasterKey(context: Context): Boolean {
        val keyFile = masterKeyFile(context)
        if (keyFile.exists() && keyFile.length() >= MASTER_KEY_LEN) return true
        return generateMasterKey(context)
    }

    fun hasMasterKey(context: Context): Boolean {
        val f = masterKeyFile(context)
        return f.exists() && f.length() >= MASTER_KEY_LEN
    }

    /**
     * 在图片原 Uri 处原地加密，并写入 App 记录。
     * @return 照片 id；失败返回 null
     */
    fun seizeInPlace(context: Context, uri: Uri): String? {
        if (!ensureMasterKey(context)) return null
        PhotoDeleteHelper.takePersistableAccess(context, uri)
        val plain = readUriBytes(context, uri) ?: return null
        if (plain.isEmpty()) return null
        // 已是本库加密包则拒绝重复扣押
        if (looksLikeVaultBlob(plain)) {
            QueenLogger.w(TAG, "uri already vault-encrypted: $uri")
            return null
        }
        val encrypted = try {
            encrypt(plain, readMasterKey(context))
        } catch (e: Exception) {
            QueenLogger.e(TAG, "encrypt failed", e)
            return null
        }
        if (!writeUriBytes(context, uri, encrypted)) {
            QueenLogger.w(TAG, "in-place write failed: $uri")
            return null
        }
        val id = "${System.currentTimeMillis()}_${1000 + SecureRandom().nextInt(9000)}"
        if (!saveRecord(context, id, uri, sniffMime(plain))) {
            // 记录失败则尽量把明文写回，避免用户丢图
            writeUriBytes(context, uri, plain)
            return null
        }
        QueenLogger.i(TAG, "seized in place id=$id uri=$uri")
        return id
    }

    /**
     * 无系统 Uri 的字节（如相机拍摄）：先写入系统相册，再原地加密并记入 App。
     */
    fun seizeFromPlainBytes(context: Context, plain: ByteArray): String? {
        if (!ensureMasterKey(context)) return null
        if (plain.isEmpty()) return null
        val uri = insertPlainThenGetUri(context, plain) ?: return null
        return seizeInPlace(context, uri)
            ?: run {
                // seize 失败时至少删掉刚插入的明文，避免残留
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) { }
                null
            }
    }

    /** 兼容旧调用：写入 App 私有加密副本（无原 Uri）。 */
    fun importPlainBytes(context: Context, plain: ByteArray): String? {
        if (!ensureMasterKey(context)) return null
        return try {
            val encrypted = encrypt(plain, readMasterKey(context))
            val id = "${System.currentTimeMillis()}"
            photosDir(context).mkdirs()
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
        val ids = linkedSetOf<String>()
        val recDir = recordsDir(context)
        if (recDir.exists()) {
            recDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(RECORD_EXT) }
                ?.forEach { ids.add(it.name.removeSuffix(RECORD_EXT)) }
        }
        val encDir = photosDir(context)
        if (encDir.exists()) {
            encDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(FILE_EXT) }
                ?.forEach { ids.add(it.name.removeSuffix(FILE_EXT)) }
        }
        return ids.sortedDescending()
    }

    fun sourceUri(context: Context, photoId: String): Uri? {
        val rec = loadRecord(context, photoId) ?: return null
        val s = rec.optString("uri", "")
        return if (s.isBlank()) null else Uri.parse(s)
    }

    /** 赎回后记录已删除；列表中的项一律未赎回。 */
    @Suppress("UNUSED_PARAMETER")
    fun isRedeemed(context: Context, photoId: String): Boolean = false

    fun decryptToBytes(context: Context, photoId: String): ByteArray? {
        if (!hasMasterKey(context)) return null
        val master = try {
            readMasterKey(context)
        } catch (_: Exception) {
            return null
        }
        // 优先从原位置读取加密包
        sourceUri(context, photoId)?.let { uri ->
            val blob = readUriBytes(context, uri) ?: return@let
            return try {
                decrypt(blob, master)
            } catch (e: Exception) {
                QueenLogger.w(TAG, "decrypt from uri failed id=$photoId", e)
                null
            }
        }
        // 旧版：App 私有 .qenc
        val file = photoFile(context, photoId)
        if (!file.exists()) return null
        return try {
            decrypt(file.readBytes(), master)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 赎回：原位置解密恢复明文，并删除 App 内记录（及遗留 .qenc）。
     * 无源 Uri 的旧数据：导出到 Pictures/QueenRedeemed 后删除私有副本。
     */
    fun redeemAndRestore(context: Context, photoId: String): Boolean {
        val plain = decryptToBytes(context, photoId) ?: return false
        val uri = sourceUri(context, photoId)
        val ok = if (uri != null) {
            writeUriBytes(context, uri, plain)
        } else {
            exportPlainToSystemGallery(context, plain, photoId) != null
        }
        if (!ok) return false
        deleteRecord(context, photoId)
        photoFile(context, photoId).delete()
        redeemedMarkerFile(context, photoId).delete()
        QueenLogger.i(TAG, "redeemed & restored id=$photoId uri=$uri")
        return true
    }

    /** @deprecated 使用 [redeemAndRestore] */
    fun redeemPhotoToGallery(context: Context, photoId: String): Uri? {
        val uri = sourceUri(context, photoId)
        return if (redeemAndRestore(context, photoId)) uri else null
    }

    fun redeemPhoto(context: Context, photoId: String): Boolean =
        redeemAndRestore(context, photoId)

    /**
     * 销毁：不恢复明文，删除原位置加密文件（或私有副本）并清除 App 记录。
     */
    fun destroyPhoto(context: Context, photoId: String): Boolean {
        sourceUri(context, photoId)?.let { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                QueenLogger.w(TAG, "destroy uri delete failed: $uri", e)
                // 写空/损坏块作为降级
                writeUriBytes(context, uri, MAGIC.toByteArray(Charsets.US_ASCII))
            }
        }
        deleteRecord(context, photoId)
        photoFile(context, photoId).delete()
        redeemedMarkerFile(context, photoId).delete()
        return true
    }

    fun deletePhoto(context: Context, photoId: String): Boolean =
        destroyPhoto(context, photoId)

    fun exportPlainToSystemGallery(
        context: Context,
        plain: ByteArray,
        photoId: String,
    ): Uri? {
        val app = context.applicationContext
        val (mime, ext) = sniffImageType(plain)
        val displayName = "queen_redeem_${photoId}.$ext"
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + REDEEM_GALLERY_SUBDIR,
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(plain)
                out.flush()
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            QueenLogger.e(TAG, "export to gallery failed", e)
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) { }
            null
        }
    }

    // region IO / crypto

    private fun insertPlainThenGetUri(context: Context, plain: ByteArray): Uri? {
        val (mime, ext) = sniffImageType(plain)
        val resolver = context.applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "queen_capture_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + SEIZE_GALLERY_SUBDIR,
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(plain)
                out.flush()
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            QueenLogger.e(TAG, "insert plain failed", e)
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) { }
            null
        }
    }

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            QueenLogger.w(TAG, "readUri failed: $uri", e)
            null
        }
    }

    private fun writeUriBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean {
        val resolver = context.contentResolver
        val modes = arrayOf("wt", "rwt", "w")
        for (mode in modes) {
            try {
                resolver.openOutputStream(uri, mode)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: continue
                return true
            } catch (e: Exception) {
                QueenLogger.d(TAG, "write mode=$mode failed: ${e.message}")
            }
        }
        // 部分机型 openOutputStream(uri) 无 mode 参数可用
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } != null
        } catch (e: Exception) {
            QueenLogger.w(TAG, "writeUri failed: $uri", e)
            false
        }
    }

    private fun looksLikeVaultBlob(bytes: ByteArray): Boolean {
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        if (bytes.size <= magic.size) return false
        for (i in magic.indices) {
            if (bytes[i] != magic[i]) return false
        }
        return true
    }

    private fun sniffMime(bytes: ByteArray): String = sniffImageType(bytes).first

    private fun sniffImageType(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg" to "jpg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png" to "png"
        }
        if (bytes.size >= 6 &&
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()
        ) {
            return "image/gif" to "gif"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
        ) {
            return "image/webp" to "webp"
        }
        return "image/jpeg" to "jpg"
    }

    private fun saveRecord(context: Context, id: String, uri: Uri, mime: String): Boolean {
        return try {
            recordsDir(context).mkdirs()
            val json = JSONObject()
                .put("uri", uri.toString())
                .put("mime", mime)
                .put("createdAt", System.currentTimeMillis())
            recordFile(context, id).writeText(json.toString())
            true
        } catch (e: Exception) {
            QueenLogger.e(TAG, "saveRecord failed", e)
            false
        }
    }

    private fun loadRecord(context: Context, id: String): JSONObject? {
        val f = recordFile(context, id)
        if (!f.exists()) return null
        return try {
            JSONObject(f.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteRecord(context: Context, id: String) {
        recordFile(context, id).delete()
    }

    private fun generateMasterKey(context: Context): Boolean {
        return try {
            vaultRoot(context).mkdirs()
            val master = ByteArray(MASTER_KEY_LEN).also { SecureRandom().nextBytes(it) }
            masterKeyFile(context).writeBytes(master)
            keyHashFile(context).writeText(sha256Hex(master))
            photosDir(context).mkdirs()
            recordsDir(context).mkdirs()
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

    private fun deriveAesKey(masterKey: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(masterKey)

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

    private fun recordsDir(context: Context): File =
        File(vaultRoot(context), RECORDS_DIR)

    private fun photoFile(context: Context, id: String): File =
        File(photosDir(context), "$id$FILE_EXT")

    private fun recordFile(context: Context, id: String): File =
        File(recordsDir(context), "$id$RECORD_EXT")

    private fun redeemedMarkerFile(context: Context, id: String): File =
        File(photosDir(context), "$id.redeemed")
}
