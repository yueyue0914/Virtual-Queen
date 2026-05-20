package com.dianziqueen.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object QueenMessagePhotoLoader {

    private const val LIST_MAX_SIDE = 640
    private const val FULLSCREEN_MAX_SIDE = 2048

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mosaicCache = android.util.LruCache<String, Bitmap>(20)
    private val clearCache = android.util.LruCache<String, Bitmap>(12)

    fun loadMosaicForList(
        context: Context,
        photoId: String,
        onReady: (Bitmap?) -> Unit,
    ) {
        loadMosaic(context, photoId, LIST_MAX_SIDE, mosaicCache, onReady)
    }

    fun loadMosaicForFullscreen(
        context: Context,
        photoId: String,
        onReady: (Bitmap?) -> Unit,
    ) {
        loadMosaic(context, photoId, FULLSCREEN_MAX_SIDE, mosaicCache, onReady)
    }

    fun loadClearForFullscreen(
        context: Context,
        photoId: String,
        onReady: (Bitmap?) -> Unit,
    ) {
        val key = "clear:$photoId:$FULLSCREEN_MAX_SIDE"
        clearCache.get(key)?.let {
            deliver(onReady, it)
            return
        }
        executor.execute {
            val bmp = decodeClear(context, photoId, FULLSCREEN_MAX_SIDE)
            if (bmp != null) clearCache.put(key, bmp)
            deliver(onReady, bmp)
        }
    }

    fun evict(photoId: String) {
        mosaicCache.remove(photoId)
        mosaicCache.remove("fs:$photoId")
        clearCache.remove("clear:$photoId:$FULLSCREEN_MAX_SIDE")
    }

    private fun loadMosaic(
        context: Context,
        photoId: String,
        maxSide: Int,
        cache: android.util.LruCache<String, Bitmap>,
        onReady: (Bitmap?) -> Unit,
    ) {
        val cacheKey = if (maxSide >= FULLSCREEN_MAX_SIDE) "fs:$photoId" else photoId
        cache.get(cacheKey)?.let {
            deliver(onReady, it)
            return
        }
        executor.execute {
            val plain = QueenAlbumVault.decryptToBytes(context, photoId) ?: run {
                deliver(onReady, null)
                return@execute
            }
            val decoded = AlbumPhotoAdapter.decodeThumbnail(plain, maxSide) ?: run {
                deliver(onReady, null)
                return@execute
            }
            val blurred = AlbumBlurHelper.blurForFullscreen(decoded)
            if (blurred !== decoded) decoded.recycle()
            cache.put(cacheKey, blurred)
            deliver(onReady, blurred)
        }
    }

    private fun decodeClear(context: Context, photoId: String, maxSide: Int): Bitmap? {
        val plain = QueenAlbumVault.decryptToBytes(context, photoId) ?: return null
        return AlbumPhotoAdapter.decodeThumbnail(plain, maxSide)
    }

    /** 所有 UI 回调必须在主线程执行。 */
    private fun deliver(onReady: (Bitmap?) -> Unit, result: Bitmap?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onReady(result)
        } else {
            mainHandler.post { onReady(result) }
        }
    }
}
