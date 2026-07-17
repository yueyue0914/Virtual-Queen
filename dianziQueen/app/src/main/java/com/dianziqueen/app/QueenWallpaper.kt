package com.dianziqueen.app

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

object QueenWallpaper {

    private const val TAG = "QueenWallpaper"

    /** 与 res/raw 中 wallpaper_1 … wallpaper_42 对应。 */
    fun collectRawWallpaperIds(): List<Int> =
        QueenRawAssets.wallpaperIds.toList()

    /** 可用于壁纸的 App 相册 id（已扣押、未赎回）。 */
    fun listWallpaperVaultPhotoIds(context: Context): List<String> {
        if (!QueenAlbumVault.hasMasterKey(context)) return emptyList()
        return QueenAlbumVault.listPhotoIds(context)
    }

    /**
     * 按轮换规则换下一张壁纸：有 App 相册图时「相册 → raw → 相册 → raw…」交替；
     * 无相册图时仅从 [res/raw] 随机（失败则程序生成）。
     */
    fun applyNextQueenWallpaper(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val hasVault = listWallpaperVaultPhotoIds(context).isNotEmpty()
        val useVault = hasVault && prefs.getBoolean(Prefs.QUEEN_WALLPAPER_NEXT_VAULT, true)

        val ok = when {
            useVault -> applyRandomWallpaperFromVault(context) ||
                applyRandomWallpaperFromRaw(context) ||
                applyGeneratedWallpaper(context)
            else -> applyRandomWallpaperFromRaw(context) ||
                (hasVault && applyRandomWallpaperFromVault(context)) ||
                applyGeneratedWallpaper(context)
        }

        if (ok && hasVault) {
            prefs.edit()
                .putBoolean(Prefs.QUEEN_WALLPAPER_NEXT_VAULT, !useVault)
                .apply()
        }
        if (!ok) {
            Log.w(TAG, "applyNextQueenWallpaper: all sources failed")
        }
        return ok
    }

    private fun applyGeneratedWallpaper(context: Context): Boolean {
        val (w, h) = wallpaperTargetSize(context)
        var bmp: Bitmap? = null
        return try {
            bmp = randomWallpaper(w, h)
            applyWallpaper(context, bmp)
        } catch (e: Exception) {
            Log.w(TAG, "generated wallpaper failed", e)
            false
        } finally {
            bmp?.let { recycleQuietly(it) }
        }
    }

    fun applyRandomWallpaperFromVault(context: Context): Boolean {
        val ids = listWallpaperVaultPhotoIds(context)
        if (ids.isEmpty()) return false
        for (photoId in ids.shuffled()) {
            var bmp: Bitmap? = null
            try {
                bmp = decodeVaultPhotoForWallpaper(context, photoId) ?: continue
                if (applyWallpaper(context, bmp)) {
                    Log.i(TAG, "vault wallpaper applied: $photoId")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "vault wallpaper failed: $photoId", e)
            } finally {
                bmp?.let { recycleQuietly(it) }
            }
        }
        return false
    }

    private fun decodeVaultPhotoForWallpaper(context: Context, photoId: String): Bitmap? {
        val plain = QueenAlbumVault.decryptToBytes(context, photoId) ?: return null
        val (targetW, targetH) = wallpaperTargetSize(context)
        return decodeBytesForWallpaper(plain, targetW, targetH)
    }

    fun applyRandomWallpaperFromRaw(context: Context): Boolean {
        val ids = collectRawWallpaperIds()
        if (ids.isEmpty()) return false
        val resId = ids.random()
        val res = context.resources

        // 华米 OV / HyperOS 上 setStream 常出现「设置成功但显示纯黑」，统一走 Bitmap。
        if (!RomPermissionUtils.isDomesticRom()) {
            try {
                res.openRawResource(resId).use { input ->
                    if (trySetWallpaperStream(context, input)) {
                        Log.i(TAG, "raw stream wallpaper applied: $resId")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "raw stream failed: $resId", e)
            }
        }

        var bmp: Bitmap? = null
        return try {
            val (targetW, targetH) = wallpaperTargetSize(context)
            val decoded = decodeResourceForWallpaper(res, resId, targetW, targetH) ?: return false
            bmp = decoded
            if (applyWallpaper(context, decoded)) {
                Log.i(TAG, "raw bitmap wallpaper applied: $resId (${targetW}x$targetH)")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "raw bitmap failed: $resId", e)
            false
        } finally {
            bmp?.let { recycleQuietly(it) }
        }
    }

    private fun trySetWallpaperStream(context: Context, input: java.io.InputStream): Boolean {
        val wm = WallpaperManager.getInstance(context)
        return try {
            wm.setStream(input, null, true, wallpaperFlags())
            true
        } catch (e: Exception) {
            Log.w(TAG, "setStream failed", e)
            false
        }
    }

    fun randomWallpaper(
        width: Int = 1080,
        height: Int = 1920,
        seed: Long = Random.nextLong(),
    ): Bitmap {
        val w = width.coerceAtLeast(720)
        val h = height.coerceAtLeast(1280)
        val rnd = Random(seed)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c0 = randomNeon(rnd)
        val c1 = randomNeon(rnd)
        val c2 = randomNeon(rnd)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(c0, c1, c2),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = w * 0.08f
            textAlign = Paint.Align.CENTER
            setShadowLayer(12f, 0f, 0f, 0xFF000000.toInt())
        }
        canvas.drawText("电子QUEEN", w / 2f, h * 0.45f, tp)
        tp.textSize = w * 0.035f
        tp.color = 0xCCFFFFFF.toInt()
        canvas.drawText("DIANZI · SYNC", w / 2f, h * 0.52f, tp)
        return bmp
    }

    private fun randomNeon(rnd: Random): Int {
        val h = rnd.nextFloat() * 360f
        val s = 0.65f + rnd.nextFloat() * 0.35f
        val v = 0.35f + rnd.nextFloat() * 0.45f
        return android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    }

    /** @return 是否已成功写入系统壁纸 */
    fun applyWallpaper(context: Context, bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
        val wm = WallpaperManager.getInstance(context)
        val (targetW, targetH) = wallpaperTargetSize(context)
        suggestDesiredDimensions(wm, targetW, targetH)

        var prepared: Bitmap? = null
        return try {
            prepared = prepareWallpaperBitmap(bitmap, targetW, targetH)
            if (prepared.isRecycled || prepared.width <= 0 || prepared.height <= 0) {
                return false
            }
            try {
                wm.setBitmap(prepared, null, true, wallpaperFlags())
                true
            } catch (e1: Exception) {
                Log.w(TAG, "setBitmap(flags) failed, retry legacy", e1)
                try {
                    @Suppress("DEPRECATION")
                    wm.setBitmap(prepared)
                    true
                } catch (e2: Exception) {
                    Log.e(TAG, "setBitmap legacy failed", e2)
                    false
                }
            }
        } finally {
            if (prepared != null && prepared !== bitmap) {
                recycleQuietly(prepared)
            }
        }
    }

    fun applyTemporaryFreedomWallpaper(context: Context) {
        val (width, height) = wallpaperTargetSize(context)
        val bmp = freedomWallpaperBitmap(width, height)
        try {
            context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(Prefs.WE_SET_WALLPAPER, true)
                .apply()
            applyWallpaper(context, bmp)
        } finally {
            recycleQuietly(bmp)
        }
    }

    private fun freedomWallpaperBitmap(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(0xFF0D1B2A.toInt(), 0xFF1B263B.toInt(), 0xFF415A77.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        val line1 = "你自由了，"
        val line2 = "暂时的。"
        val size = width * 0.11f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(size * 0.06f, 0f, size * 0.04f, 0xAA000000.toInt())
        }
        val cx = width / 2f
        val y1 = height * 0.42f
        val y2 = y1 + size * 1.25f
        canvas.drawText(line1, cx, y1, paint)
        canvas.drawText(line2, cx, y2, paint)
        paint.textSize = width * 0.028f
        paint.color = 0xCCB0BEC5.toInt()
        paint.isFakeBoldText = false
        canvas.drawText("DIANZI QUEEN · TEMPORARY RELEASE", cx, height * 0.58f, paint)
        return bmp
    }

    fun forceQueenWallpaper(context: Context) {
        try {
            val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
            if (!QueenWallpaperHelper.hasSetWallpaperPermission(context)) return
            prefs.edit().putBoolean(Prefs.WE_SET_WALLPAPER, true).apply()
            applyNextQueenWallpaper(context)
        } catch (e: Exception) {
            Log.e(TAG, "forceQueenWallpaper failed", e)
        }
    }

    /**
     * 壁纸按**当前屏幕真实宽高比**准备，不要用 desiredMinimumWidth（多为屏宽×2 的滑动壁纸缓冲），
     * 否则相册竖图居中裁切后会出现「人像被切偏 / 放大怪异 / 上下黑边感」。
     */
    private fun wallpaperTargetSize(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        var w = dm.widthPixels
        var h = dm.heightPixels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val bounds = context.getSystemService(WindowManager::class.java)
                    ?.currentWindowMetrics
                    ?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    w = bounds.width()
                    h = bounds.height()
                }
            } catch (_: Exception) {
            }
        }
        // 保持比例，最长边不超过 2560，兼顾清晰度与内存
        val longest = max(w, h).coerceAtLeast(1)
        if (longest > 2560) {
            val scale = 2560f / longest
            w = (w * scale).roundToInt().coerceAtLeast(1)
            h = (h * scale).roundToInt().coerceAtLeast(1)
        }
        // 过小则等比放大，勿分别 coerce 破坏宽高比
        val minW = 720
        val minH = 1280
        if (w < minW || h < minH) {
            val up = max(minW.toFloat() / w, minH.toFloat() / h)
            w = (w * up).roundToInt()
            h = (h * up).roundToInt()
        }
        return w to h
    }

    private fun wallpaperFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        } else {
            @Suppress("DEPRECATION")
            WallpaperManager.FLAG_SYSTEM
        }
    }

    @Suppress("DEPRECATION")
    private fun suggestDesiredDimensions(wm: WallpaperManager, width: Int, height: Int) {
        try {
            wm.suggestDesiredDimensions(width, height)
        } catch (e: Exception) {
            Log.d(TAG, "suggestDesiredDimensions ignored: ${e.message}")
        }
    }

    private fun decodeBytesForWallpaper(bytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            decodeOptionsForWallpaper(bounds, targetW, targetH),
        ) ?: return null
        return applyExifOrientation(bytes, decoded)
    }

    /** 手机拍摄的 JPEG 常靠 EXIF 标记方向，解码时不转会导致壁纸横竖/裁切全错。 */
    private fun applyExifOrientation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (out !== bitmap) recycleQuietly(bitmap)
            out
        } catch (e: Exception) {
            Log.w(TAG, "exif rotate failed", e)
            bitmap
        }
    }

    private fun decodeResourceForWallpaper(
        res: android.content.res.Resources,
        resId: Int,
        targetW: Int,
        targetH: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeResource(
            res,
            resId,
            decodeOptionsForWallpaper(bounds, targetW, targetH),
        )
    }

    private fun decodeOptionsForWallpaper(
        bounds: BitmapFactory.Options,
        targetW: Int,
        targetH: Int,
    ): BitmapFactory.Options {
        var sample = 1
        val maxDim = max(targetW, targetH)
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDim * 2) {
            sample *= 2
        }
        return BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    }

    /** 居中裁剪/放大到目标分辨率，避免 MIUI 拉伸过小 Bitmap 成黑屏。 */
    private fun prepareWallpaperBitmap(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (source.config != Bitmap.Config.ARGB_8888) {
            val converted = source.copy(Bitmap.Config.ARGB_8888, false)
            if (converted != null && converted !== source) {
                val result = prepareWallpaperBitmap(converted, targetW, targetH)
                recycleQuietly(converted)
                return result
            }
        }
        val scale = max(
            targetW.toFloat() / source.width.toFloat(),
            targetH.toFloat() / source.height.toFloat(),
        )
        val scaledW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val scaledH = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = if (scale == 1f && source.width == targetW && source.height == targetH) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        } else {
            Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        }
        if (scaled.width == targetW && scaled.height == targetH) {
            return scaled
        }
        val x = ((scaled.width - targetW) / 2f).coerceAtLeast(0f)
        val y = ((scaled.height - targetH) / 2f).coerceAtLeast(0f)
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(scaled, -x, -y, null)
        if (scaled !== source) {
            recycleQuietly(scaled)
        }
        return out
    }

    private fun recycleQuietly(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            try {
                bitmap.recycle()
            } catch (_: Exception) { }
        }
    }
}
