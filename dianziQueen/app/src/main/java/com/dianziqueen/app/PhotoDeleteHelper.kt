package com.dianziqueen.app

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 从系统相册选取并加密入库后，用 [ContentResolver.delete] 删除原图。
 * 适配 Scoped Storage：解析 MediaStore Uri、持久化读写授权、系统删除确认框。
 */
object PhotoDeleteHelper {

    private const val TAG = "PhotoDelete"

    enum class DeleteResult {
        /** 已直接删除 */
        DELETED,
        /** 已弹出系统删除确认（Android 11+），结果见 [Helper] 回调 */
        NEEDS_USER_CONFIRM,
        /** 未能删除（权限/Uri/厂商限制） */
        FAILED,
    }

    class Helper(private val activity: ComponentActivity) {
        private var pendingUri: Uri? = null
        private var pendingCallback: ((Boolean) -> Unit)? = null

        private val deleteConfirmLauncher: ActivityResultLauncher<IntentSenderRequest> =
            activity.registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                val ok = result.resultCode == Activity.RESULT_OK
                if (!ok) {
                    Log.w(TAG, "用户取消系统删除确认: $pendingUri")
                }
                pendingCallback?.invoke(ok)
                pendingCallback = null
                pendingUri = null
            }

        /**
         * 加密保存成功后调用；[onComplete] 参数为是否已从系统相册删除原图。
         */
        fun deleteAfterVaultImport(uri: Uri, onComplete: (Boolean) -> Unit) {
            when (deleteOriginalPhoto(activity, uri, deleteConfirmLauncher)) {
                DeleteResult.DELETED -> onComplete(true)
                DeleteResult.NEEDS_USER_CONFIRM -> {
                    pendingUri = uri
                    pendingCallback = onComplete
                }
                DeleteResult.FAILED -> onComplete(false)
            }
        }
    }

    /** 选取相册后尽早调用，尽量拿到可写持久授权（[OpenDocument] 场景）。 */
    fun takePersistableAccess(context: Context, uri: Uri) {
        if (uri.scheme != "content") return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "已持久化 Uri 读写授权: $uri")
        } catch (e: SecurityException) {
            Log.d(TAG, "无法持久化 Uri 授权（部分机型/选取方式不支持）: ${e.message}")
        }
    }

    fun deleteOriginalPhoto(
        context: Context,
        uri: Uri,
        confirmLauncher: ActivityResultLauncher<IntentSenderRequest>? = null,
    ): DeleteResult {
        val app = context.applicationContext
        val targets = buildDeleteTargetUris(app, uri)
        for (target in targets) {
            val rows = tryDeleteDelete(app, target, confirmLauncher)
            when (rows) {
                DELETE_OK -> return DeleteResult.DELETED
                DELETE_NEED_CONFIRM -> return DeleteResult.NEEDS_USER_CONFIRM
                DELETE_ZERO -> Log.d(TAG, "delete 返回 0，尝试下一 Uri: $target")
            }
        }
        Log.w(TAG, "所有删除途径均失败，原始 Uri=$uri targets=$targets")
        return DeleteResult.FAILED
    }

    fun deleteOriginalPhotos(context: Context, uris: List<Uri>): Int {
        var count = 0
        for (uri in uris) {
            if (deleteOriginalPhoto(context, uri) == DeleteResult.DELETED) count++
        }
        return count
    }

    private const val DELETE_OK = 1
    private const val DELETE_NEED_CONFIRM = 2
    private const val DELETE_ZERO = 0

    private fun buildDeleteTargetUris(context: Context, uri: Uri): List<Uri> {
        val ordered = LinkedHashSet<Uri>()
        // document Uri 不支持 ContentResolver.delete，优先走 MediaStore 解析结果
        resolveMediaStoreImageUri(context, uri)?.let { ordered.add(it) }
        if (!DocumentsContract.isDocumentUri(context, uri)) {
            ordered.add(uri)
        }
        return ordered.toList()
    }

    /**
     * 将相册/document Uri 解析为 MediaStore 图片 Uri（content://media/.../images/media/id）。
     */
    fun resolveMediaStoreImageUri(context: Context, uri: Uri): Uri? {
        if (uri.scheme != "content") return null
        val authority = uri.authority.orEmpty()
        if (authority == "media" || authority.startsWith("media.")) {
            return uri
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            when {
                docId.startsWith("image:") -> {
                    val id = docId.substringAfter("image:").toLongOrNull() ?: return null
                    return ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                }
                docId.startsWith("raw:") -> {
                    val path = docId.substringAfter("raw:")
                    return queryImageUriByPath(context, path)
                }
            }
        }
        return queryImageUriByDataColumn(context, uri)
    }

    private fun queryImageUriByPath(context: Context, path: String): Uri? {
        if (path.isBlank()) return null
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DATA}=?"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(path),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
            }
        }
        return null
    }

    private fun queryImageUriByDataColumn(context: Context, uri: Uri): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idCol)
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
            }
        }
        return null
    }

    private fun tryDeleteDelete(
        context: Context,
        uri: Uri,
        confirmLauncher: ActivityResultLauncher<IntentSenderRequest>?,
    ): Int {
        val resolver = context.contentResolver
        try {
            val rows = resolver.delete(uri, null, null)
            if (rows > 0) {
                Log.i(TAG, "原始照片已成功删除: $uri (rows=$rows)")
                return DELETE_OK
            }
            return DELETE_ZERO
        } catch (e: RecoverableSecurityException) {
            return requestSystemDelete(context, uri, e, confirmLauncher)
        } catch (e: SecurityException) {
            Log.d(TAG, "SecurityException 删除失败: $uri — ${e.message}")
            return DELETE_ZERO
        } catch (e: UnsupportedOperationException) {
            Log.d(TAG, "此 Uri 不支持 delete，尝试下一途径: $uri")
            return DELETE_ZERO
        } catch (e: Exception) {
            Log.w(TAG, "删除失败: $uri — ${e.message}")
            return DELETE_ZERO
        }
    }

    private fun requestSystemDelete(
        context: Context,
        uri: Uri,
        e: RecoverableSecurityException,
        confirmLauncher: ActivityResultLauncher<IntentSenderRequest>?,
    ): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && confirmLauncher != null &&
            context is ComponentActivity
        ) {
            try {
                val pending = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri),
                )
                confirmLauncher.launch(
                    IntentSenderRequest.Builder(pending.intentSender).build(),
                )
                Log.i(TAG, "已请求系统删除确认: $uri")
                return DELETE_NEED_CONFIRM
            } catch (e2: Exception) {
                Log.w(TAG, "createDeleteRequest 失败: $uri", e2)
            }
        }
        if (confirmLauncher != null) {
            try {
                val sender = e.userAction?.actionIntent?.intentSender
                if (sender != null) {
                    confirmLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    Log.i(TAG, "RecoverableSecurityException 用户确认删除: $uri")
                    return DELETE_NEED_CONFIRM
                }
            } catch (e2: Exception) {
                Log.w(TAG, "RecoverableSecurityException 回退失败", e2)
            }
        }
        return DELETE_ZERO
    }
}
