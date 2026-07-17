package com.dianziqueen.app

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
import androidx.annotation.RequiresApi

/**
 * 从系统相册选取并加密入库后，删除系统相册中的原图。
 * 适配 Scoped Storage：SAF 持久授权 + MediaStore 解析 + 系统删除确认框（Android 11+）。
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
                val ok = result.resultCode == android.app.Activity.RESULT_OK
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

    /** 选取相册后尽早调用，尽量拿到可写持久授权（[OpenDocument] / [OpenMultipleDocuments] 场景）。 */
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
        val hostActivity = context as? ComponentActivity

        if (DocumentsContract.isDocumentUri(app, uri)) {
            when (tryDeleteDocument(app, uri, confirmLauncher, hostActivity)) {
                DELETE_OK -> return DeleteResult.DELETED
                DELETE_NEED_CONFIRM -> return DeleteResult.NEEDS_USER_CONFIRM
                DELETE_ZERO -> Log.d(TAG, "DocumentsContract 删除未成功，尝试 MediaStore: $uri")
            }
        }

        val targets = buildDeleteTargetUris(app, uri)
        for (target in targets) {
            when (tryDeleteViaMediaStore(app, target, confirmLauncher, hostActivity)) {
                DELETE_OK -> return DeleteResult.DELETED
                DELETE_NEED_CONFIRM -> return DeleteResult.NEEDS_USER_CONFIRM
                DELETE_ZERO -> Log.d(TAG, "MediaStore delete 未成功，尝试下一 Uri: $target")
            }
        }

        if (confirmLauncher != null && hostActivity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaUris = targets.filter { isMediaStoreImageUri(it) }
            if (mediaUris.isNotEmpty()) {
                when (launchCreateDeleteRequest(app, mediaUris, confirmLauncher)) {
                    DELETE_NEED_CONFIRM -> return DeleteResult.NEEDS_USER_CONFIRM
                }
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

    private fun isMediaStoreImageUri(uri: Uri): Boolean {
        val authority = uri.authority.orEmpty()
        return authority == "media" || authority.startsWith("media.")
    }

    private fun buildDeleteTargetUris(context: Context, uri: Uri): List<Uri> {
        val ordered = LinkedHashSet<Uri>()
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
        if (isMediaStoreImageUri(uri)) {
            return uri
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            resolveFromDocumentId(context, DocumentsContract.getDocumentId(uri))?.let { return it }
        }
        queryImageUriByDataColumn(context, uri)?.let { return it }
        return queryImageUriFromProviderMetadata(context, uri)
    }

    private fun resolveFromDocumentId(context: Context, docId: String): Uri? {
        when {
            docId.startsWith("image:") -> {
                val id = docId.substringAfter("image:").substringBefore('/').toLongOrNull()
                    ?: return null
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
            }
            docId.startsWith("raw:") -> {
                val path = docId.substringAfter("raw:")
                queryImageUriByPath(context, path)
                    ?: queryImageUriByRelativeFilePath(context, path)
            }
            docId.contains(':') -> {
                val storagePrefix = docId.substringBefore(':')
                if (storagePrefix == "primary" || storagePrefix == "home") {
                    val rel = docId.substringAfter(':')
                    queryImageUriByRelativeFilePath(context, rel)
                } else {
                    null
                }
            }
            else -> null
        }?.let { return it }
        return null
    }

    private fun queryImageUriByPath(context: Context, path: String): Uri? {
        if (path.isBlank()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryImageUriByRelativeFilePath(context, path)?.let { return it }
        }
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

    /** Android 10+：用 RELATIVE_PATH + DISPLAY_NAME 定位（DATA 列常为空）。 */
    private fun queryImageUriByRelativeFilePath(context: Context, filePath: String): Uri? {
        if (filePath.isBlank()) return null
        val normalized = filePath.trimStart('/')
        val fileName = normalized.substringAfterLast('/')
        if (fileName.isBlank()) return null
        val dir = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        val relativePath = when {
            dir.isEmpty() -> null
            dir.endsWith('/') -> dir
            else -> "$dir/"
        }
        val projection = arrayOf(MediaStore.Images.Media._ID)
        return if (relativePath != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val selection =
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(fileName, relativePath),
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )
        } else {
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(fileName),
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )
        }?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0),
                )
            } else {
                null
            }
        }
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

    /** 厂商相册 ContentProvider：尝试从元数据列反查 MediaStore。 */
    private fun queryImageUriFromProviderMetadata(context: Context, uri: Uri): Uri? {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.DATA)
            }
        }.toTypedArray()
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                if (idIdx >= 0) {
                    val id = cursor.getLong(idIdx)
                    if (id > 0L) {
                        return ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                    }
                }
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val rel = if (pathIdx >= 0) cursor.getString(pathIdx) else null
                    if (!name.isNullOrBlank() && !rel.isNullOrBlank()) {
                        return queryImageUriByRelativeFilePath(context, rel + name)
                    }
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val data = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    if (!data.isNullOrBlank()) {
                        return queryImageUriByPath(context, data)
                    }
                }
                if (!name.isNullOrBlank()) {
                    return queryImageUriByRelativeFilePath(context, name)
                }
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "厂商 Uri 元数据查询失败: $uri — ${e.message}")
            null
        }
    }

    private fun tryDeleteDocument(
        context: Context,
        uri: Uri,
        confirmLauncher: ActivityResultLauncher<IntentSenderRequest>?,
        hostActivity: ComponentActivity?,
    ): Int {
        if (!DocumentsContract.isDocumentUri(context, uri)) return DELETE_ZERO
        return try {
            if (DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                Log.i(TAG, "DocumentsContract 删除成功: $uri")
                DELETE_OK
            } else {
                DELETE_ZERO
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                e is RecoverableSecurityException
            ) {
                return requestRecoverableDelete(context, uri, e, confirmLauncher, hostActivity)
            }
            Log.d(TAG, "DocumentsContract SecurityException: $uri — ${e.message}")
            DELETE_ZERO
        } catch (e: Exception) {
            Log.d(TAG, "DocumentsContract 删除失败: $uri — ${e.message}")
            DELETE_ZERO
        }
    }

    private fun tryDeleteViaMediaStore(
        context: Context,
        uri: Uri,
        confirmLauncher: ActivityResultLauncher<IntentSenderRequest>?,
        hostActivity: ComponentActivity?,
    ): Int {
        val resolver = context.contentResolver
        try {
            val rows = resolver.delete(uri, null, null)
            if (rows > 0) {
                Log.i(TAG, "MediaStore 删除成功: $uri (rows=$rows)")
                return DELETE_OK
            }
            if (confirmLauncher != null && hostActivity != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                isMediaStoreImageUri(uri)
            ) {
                return launchCreateDeleteRequest(context, listOf(uri), confirmLauncher)
            }
            return DELETE_ZERO
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                e is RecoverableSecurityException
            ) {
                return requestRecoverableDelete(context, uri, e, confirmLauncher, hostActivity)
            }
            Log.d(TAG, "MediaStore SecurityException: $uri — ${e.message}")
            return DELETE_ZERO
        } catch (e: UnsupportedOperationException) {
            Log.d(TAG, "此 Uri 不支持 delete: $uri")
            return DELETE_ZERO
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore 删除失败: $uri — ${e.message}")
            return DELETE_ZERO
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun launchCreateDeleteRequest(
        context: Context,
        uris: List<Uri>,
        confirmLauncher: ActivityResultLauncher<IntentSenderRequest>,
    ): Int {
        if (uris.isEmpty()) return DELETE_ZERO
        return try {
            val pending = MediaStore.createDeleteRequest(context.contentResolver, uris)
            confirmLauncher.launch(
                IntentSenderRequest.Builder(pending.intentSender).build(),
            )
            Log.i(TAG, "已请求系统删除确认 (${uris.size} 项): $uris")
            DELETE_NEED_CONFIRM
        } catch (e: Exception) {
            Log.w(TAG, "createDeleteRequest 失败: $uris", e)
            DELETE_ZERO
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestRecoverableDelete(
        context: Context,
        uri: Uri,
        e: RecoverableSecurityException,
        confirmLauncher: ActivityResultLauncher<IntentSenderRequest>?,
        hostActivity: ComponentActivity?,
    ): Int {
        if (confirmLauncher != null && hostActivity != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            isMediaStoreImageUri(uri)
        ) {
            when (launchCreateDeleteRequest(context, listOf(uri), confirmLauncher)) {
                DELETE_NEED_CONFIRM -> return DELETE_NEED_CONFIRM
            }
        }
        if (confirmLauncher != null) {
            try {
                val sender = e.userAction?.actionIntent?.intentSender
                if (sender != null) {
                    confirmLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    Log.i(TAG, "RecoverableSecurityException 系统确认删除: $uri")
                    return DELETE_NEED_CONFIRM
                }
            } catch (e2: Exception) {
                Log.w(TAG, "RecoverableSecurityException 回退失败", e2)
            }
        }
        return DELETE_ZERO
    }
}
