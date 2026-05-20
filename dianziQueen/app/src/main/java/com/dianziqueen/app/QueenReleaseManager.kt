package com.dianziqueen.app

import android.content.Context

/**
 * 消耗积分请求「暂时释放」：退出上交模式，清理系统日历/相册注入，保留 App 内加密相册。
 */
object QueenReleaseManager {

    const val RELEASE_COST_POINTS = 100

    sealed class ReleaseResult {
        data class Success(val cleanup: QueenInjectionRegistry.CleanupResult) : ReleaseResult()
        data object NotActivated : ReleaseResult()
        data object InsufficientPoints : ReleaseResult()
    }

    fun performRelease(context: Context): ReleaseResult {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) {
            return ReleaseResult.NotActivated
        }
        if (!QueenPointsStore.trySpend(app, RELEASE_COST_POINTS)) {
            return ReleaseResult.InsufficientPoints
        }

        val cleanup = QueenInjectionRegistry.deleteAllInjected(app)
        QueenWallpaper.applyTemporaryFreedomWallpaper(app)
        deactivateQueenMode(app)
        return ReleaseResult.Success(cleanup)
    }

    private fun deactivateQueenMode(context: Context) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(Prefs.ACTIVATED, false)
            .putBoolean(Prefs.CALENDAR_INJECTED, false)
            .putBoolean(Prefs.CALENDAR_SCHEDULE_ACTIVE, false)
            .putInt(Prefs.CALENDAR_INJECT_INDEX, 0)
            .putLong(Prefs.CALENDAR_LAST_INJECT_AT, 0L)
            .putInt(Prefs.CALENDAR_LAST_QUEEN_COUNT, 0)
            .putBoolean(Prefs.WE_SET_WALLPAPER, true)
            .apply()

        CalendarInjector.cancelInjectAlarms(context)
        CalendarInjector.unregisterDeletionWatch(context)
        DailySelfieScheduler.cancelAlarm(context)
        QueenAccessibilityHelper.cancelAccessibilityNotification(context)
        QueenBatteryHelper.cancelBatteryNotification(context)
        QueenFloatingOverlay.hide()
        QueenService.stop(context)
    }
}
