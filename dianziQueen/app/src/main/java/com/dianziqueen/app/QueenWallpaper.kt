package com.dianziqueen.app

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import kotlin.random.Random

object QueenWallpaper {

    /** 与原版一致：raw 命名为 wallpaper_1、wallpaper_2 …（文件名如 wallpaper_3.png） */
    private const val RAW_WALLPAPER_PREFIX = "wallpaper_"
    private const val RAW_WALLPAPER_INDEX_MAX = 99

    fun collectRawWallpaperIds(context: Context): List<Int> {
        val res = context.resources
        val pkg = context.packageName
        return (1..RAW_WALLPAPER_INDEX_MAX).mapNotNull { i ->
            val id = res.getIdentifier("$RAW_WALLPAPER_PREFIX$i", "raw", pkg)
            if (id != 0) id else null
        }
    }

    /**
     * 从 [res/raw] 中随机选一张设为系统壁纸（优先 setStream，失败则 decode + setBitmap）。
     * @return 是否成功使用 raw 资源；若 raw 列表为空则为 false，可再调用 [randomWallpaper]+[applyWallpaper] 作回退。
     */
    fun applyRandomWallpaperFromRaw(context: Context): Boolean {
        val ids = collectRawWallpaperIds(context)
        if (ids.isEmpty()) return false
        val resId = ids.random()
        val wm = WallpaperManager.getInstance(context)
        val res = context.resources

        try {
            res.openRawResource(resId).use { input ->
                if (trySetWallpaperStream(wm, input)) return true
            }
        } catch (_: Exception) { }

        var bmp: Bitmap? = null
        return try {
            bmp = BitmapFactory.decodeResource(res, resId) ?: return false
            applyWallpaper(context, bmp)
            true
        } catch (_: Exception) {
            false
        } finally {
            bmp?.recycle()
        }
    }

    private fun trySetWallpaperStream(wm: WallpaperManager, input: java.io.InputStream): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setStream(input, null, true, WallpaperManager.FLAG_SYSTEM)
            } else {
                @Suppress("DEPRECATION")
                wm.setStream(input)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 无 raw 素材时的程序生成壁纸（渐变 + 文案） */
    fun randomWallpaper(
        width: Int = 1080,
        height: Int = 1920,
        seed: Long = Random.nextLong()
    ): Bitmap {
        val rnd = Random(seed)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c0 = randomNeon(rnd)
        val c1 = randomNeon(rnd)
        val c2 = randomNeon(rnd)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(c0, c1, c2),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = width * 0.08f
            textAlign = Paint.Align.CENTER
            setShadowLayer(12f, 0f, 0f, 0xFF000000.toInt())
        }
        canvas.drawText("电子QUEEN", width / 2f, height * 0.45f, tp)
        tp.textSize = width * 0.035f
        tp.color = 0xCCFFFFFF.toInt()
        canvas.drawText("DIANZI · SYNC", width / 2f, height * 0.52f, tp)
        return bmp
    }

    private fun randomNeon(rnd: Random): Int {
        val h = rnd.nextFloat() * 360f
        val s = 0.65f + rnd.nextFloat() * 0.35f
        val v = 0.35f + rnd.nextFloat() * 0.45f
        return android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    }

    fun applyWallpaper(context: Context, bitmap: Bitmap) {
        val wm = WallpaperManager.getInstance(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            } else {
                @Suppress("DEPRECATION")
                wm.setBitmap(bitmap)
            }
        } catch (_: Exception) {
            try {
                wm.setBitmap(bitmap)
            } catch (_: Exception) { }
        }
    }

    /** 标记为本应用设置壁纸后，随机 raw 或程序生成并应用（供定时任务与壁纸监听共用）。 */
    fun forceQueenWallpaper(context: Context) {
        try {
            val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Prefs.WE_SET_WALLPAPER, true).apply()
            if (!applyRandomWallpaperFromRaw(context)) {
                val bmp = randomWallpaper()
                applyWallpaper(context, bmp)
                bmp.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
