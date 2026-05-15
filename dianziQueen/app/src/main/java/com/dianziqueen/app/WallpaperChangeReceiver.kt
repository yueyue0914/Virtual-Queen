package com.dianziqueen.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/**
 * 监听系统壁纸变化：若非本应用主动设置，则在随机 30～60 秒后强制恢复为电子 Queen 壁纸。
 * 由 [QueenService] 在激活后动态注册（前台服务存活期间持续有效）。
 */
class WallpaperChangeReceiver : BroadcastReceiver() {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 延迟区间：毫秒，含 30s，不含 61s → 实际 [30_000, 60_000] */
    private val revertDelayMsRange: LongRange = 30_000L..60_000L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_WALLPAPER_CHANGED) return
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (prefs.getBoolean(Prefs.WE_SET_WALLPAPER, false)) {
            prefs.edit().putBoolean(Prefs.WE_SET_WALLPAPER, false).apply()
            return
        }
        val appCtx = context.applicationContext
        val delay = Random.nextLong(revertDelayMsRange.first, revertDelayMsRange.last + 1)
        mainHandler.postDelayed({
            Thread {
                try {
                    QueenWallpaper.forceQueenWallpaper(appCtx)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }, delay)
    }
}
