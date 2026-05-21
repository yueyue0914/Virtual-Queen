package com.dianziqueen.app

import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 从系统相册选取并加密入库后，删除用户选中的那张原图（仅针对相册选取，不含 App 内相机缓存）。
 */
object QueenGallerySourceEraser {

    private const val TAG = "QueenGallerySourceEraser"

    class Helper(private val activity: ComponentActivity) {
        private val deleteConfirmLauncher: ActivityResultLauncher<IntentSenderRequest> =
            activity.registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                if (result.resultCode != ComponentActivity.RESULT_OK) {
                    Log.w(TAG, "user declined system delete confirmation")
                }
            }

        fun eraseAfterVaultImport(uri: Uri) {
            deletePickedImage(activity, uri, deleteConfirmLauncher)
        }
    }

    fun deletePickedImage(
        context: Context,
        uri: Uri,
        intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>? = null,
    ) {
        val resolver = context.contentResolver
        try {
            if (resolver.delete(uri, null, null) > 0) return
        } catch (e: RecoverableSecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                intentSenderLauncher != null
            ) {
                try {
                    val pending = MediaStore.createDeleteRequest(resolver, listOf(uri))
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(pending.intentSender).build(),
                    )
                    return
                } catch (e2: Exception) {
                    Log.w(TAG, "createDeleteRequest failed", e2)
                }
            } else {
                Log.w(TAG, "RecoverableSecurityException, no launcher", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "direct delete failed", e)
        }
        tryDeleteMediaStoreUri(context, uri)
    }

    private fun tryDeleteMediaStoreUri(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.w(TAG, "media store delete failed: $uri", e)
            false
        }
    }
}
