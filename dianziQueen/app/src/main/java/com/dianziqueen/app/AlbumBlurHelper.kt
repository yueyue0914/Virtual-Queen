package com.dianziqueen.app

import android.graphics.Bitmap
import kotlin.math.max

/**
 * 相册缩略图高斯模糊（多档缩小再放大，近似高斯；性能适合网格列表）。
 */
object AlbumBlurHelper {

    fun blurForAlbumThumbnail(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 2 || h < 2) return source

        var current = source
        var owned = false

        if (!current.isMutable || current.config != Bitmap.Config.ARGB_8888) {
            current = source.copy(Bitmap.Config.ARGB_8888, false)
            owned = true
        }

        val pass1W = max(1, w / 10)
        val pass1H = max(1, h / 10)
        var small = Bitmap.createScaledBitmap(current, pass1W, pass1H, true)
        if (owned) {
            current.recycle()
        }

        val pass2W = max(1, pass1W / 2)
        val pass2H = max(1, pass1H / 2)
        val tiny = Bitmap.createScaledBitmap(small, pass2W, pass2H, true)
        small.recycle()

        small = Bitmap.createScaledBitmap(tiny, pass1W, pass1H, true)
        tiny.recycle()

        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        return blurred
    }
}
