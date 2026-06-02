package com.dianziqueen.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 统一相机拍摄：兼容 Android 11+ 包可见性、华米 OV 等不写 EXTRA_OUTPUT 的相机 App。
 */
object QueenCameraCapture {

    private const val TAG = "QueenCameraCapture"

    fun createOutputFile(context: Context, prefix: String = "queen_capture"): Pair<File, Uri> {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return file to uri
    }

    /** 是否有可响应 [ACTION_IMAGE_CAPTURE] 的相机 App（不写入 FileProvider，仅用于 UI 提示）。 */
    fun isCaptureAvailable(context: Context): Boolean =
        queryCaptureActivities(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE)).isNotEmpty()

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

    /**
     * 构建可 launch 的拍摄 Intent，并向各相机 App 显式授予 FileProvider Uri（OEM 兼容）。
     * 仅返回 [resolveActivity] 非空的 Intent，并依次尝试：完整输出 / 无 clipData / 显式组件 / 缩略图回退。
     */
    fun launchableCaptureIntent(context: Context, outputUri: Uri): Intent? {
        val pm = context.packageManager
        val handlers = queryCaptureActivities(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        if (handlers.isEmpty()) return null

        val candidates = listOf(
            buildCaptureIntent(context, outputUri),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )

        for (base in candidates) {
            if (base.hasExtra(MediaStore.EXTRA_OUTPUT)) {
                grantUriToCaptureApps(context, outputUri, base)
            }
            resolveForLaunch(pm, base)?.let { return base }

            for (info in handlers) {
                val ai = info.activityInfo
                val explicit = Intent(base).setClassName(ai.packageName, ai.name)
                if (base.hasExtra(MediaStore.EXTRA_OUTPUT)) {
                    grantUriPermission(context, ai.packageName, outputUri)
                }
                resolveForLaunch(pm, explicit)?.let { return explicit }
            }
        }
        return null
    }

    /**
     * 读取拍摄结果：优先输出文件，其次 result Uri / 缩略图 extra（部分国产机只回传缩略图）。
     */
    fun readCaptureBytes(
        context: Context,
        outputFile: File?,
        outputUri: Uri?,
        resultIntent: Intent?,
    ): ByteArray? {
        if (outputFile != null && outputFile.exists() && outputFile.length() > 0L) {
            readFileBytes(outputFile)?.let { return it }
        }
        if (outputUri != null) {
            readUriBytes(context, outputUri)?.let { return it }
        }
        resultIntent?.data?.let { uri ->
            readUriBytes(context, uri)?.let { return it }
        }
        @Suppress("DEPRECATION")
        val thumb = resultIntent?.extras?.get("data")
        if (thumb is Bitmap) {
            return try {
                ByteArrayOutputStream().use { out ->
                    thumb.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.toByteArray().takeIf { it.isNotEmpty() }
                }
            } finally {
                if (!thumb.isRecycled) thumb.recycle()
            }
        }
        return null
    }

    fun cleanupCapture(context: Context, outputFile: File?, outputUri: Uri?) {
        try {
            outputFile?.delete()
        } catch (_: Exception) { }
        if (outputUri == null) return
        try {
            context.contentResolver.delete(outputUri, null, null)
        } catch (_: Exception) { }
        try {
            val probe = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            for (info in queryCaptureActivities(context, probe)) {
                val pkg = info.activityInfo.packageName
                try {
                    context.revokeUriPermission(
                        pkg,
                        outputUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    fun handleCaptureResult(
        context: Context,
        resultCode: Int,
        resultIntent: Intent?,
        outputFile: File?,
        outputUri: Uri?,
        onSuccess: (ByteArray) -> Unit,
        onFailure: () -> Unit,
    ) {
        if (resultCode != Activity.RESULT_OK) {
            cleanupCapture(context, outputFile, outputUri)
            onFailure()
            return
        }
        val bytes = readCaptureBytes(context, outputFile, outputUri, resultIntent)
        cleanupCapture(context, outputFile, outputUri)
        if (bytes == null || bytes.isEmpty()) {
            Log.w(TAG, "capture result empty (file=${outputFile?.length()}, uri=$outputUri)")
            onFailure()
        } else {
            onSuccess(bytes)
        }
    }

    private fun resolveForLaunch(
        pm: PackageManager,
        intent: Intent,
    ): android.content.ComponentName? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val info = pm.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ) ?: return null
            val ai = info.activityInfo
            return android.content.ComponentName(ai.packageName, ai.name)
        }
        @Suppress("DEPRECATION")
        return intent.resolveActivity(pm)
    }

    private fun readFileBytes(file: File): ByteArray? =
        try {
            file.readBytes().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "read file failed: ${e.message}")
            null
        }

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? =
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "read uri failed: ${e.message}")
            null
        }

    private fun grantUriPermission(context: Context, packageName: String, outputUri: Uri) {
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.grantUriPermission(packageName, outputUri, flags)
        } catch (e: Exception) {
            Log.w(TAG, "grantUri to $packageName failed: ${e.message}")
        }
    }

    private fun grantUriToCaptureApps(context: Context, outputUri: Uri, intent: Intent) {
        try {
            for (info in queryCaptureActivities(context, intent)) {
                grantUriPermission(context, info.activityInfo.packageName, outputUri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "grantUriToCaptureApps failed: ${e.message}")
        }
    }

    private fun queryCaptureActivities(
        context: Context,
        intent: Intent,
    ): List<android.content.pm.ResolveInfo> {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }
}
