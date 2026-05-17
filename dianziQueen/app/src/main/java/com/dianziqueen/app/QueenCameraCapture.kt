package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object QueenCameraCapture {

    fun createOutputFile(context: Context, prefix: String = "queen_capture"): Pair<File, Uri> {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return file to uri
    }

    fun buildCaptureIntent(context: Context, outputUri: Uri): Intent =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(
                context.contentResolver,
                "capture",
                outputUri,
            )
        }

    fun canCapture(context: Context, outputUri: Uri): Boolean {
        val intent = buildCaptureIntent(context, outputUri)
        return intent.resolveActivity(context.packageManager) != null
    }
}
