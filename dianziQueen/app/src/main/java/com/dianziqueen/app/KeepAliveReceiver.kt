package com.dianziqueen.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * 看门狗 / 复活入口：Alarm、亮屏解锁、充电等事件拉回 [QueenService]。
 *
 * 注意：小米「清理」若等价强制停止，系统会取消全部闹钟且不向本包派发广播，
 * 只能等用户打开 App、开机，或无障碍/通知使用权被系统重新拉起。
 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext
        when (action) {
            ACTION_WATCHDOG -> {
                QueenLogger.i(TAG, "watchdog alarm")
                QueenKeepAlive.onWatchdogAlarm(app)
            }
            ACTION_RESTART -> {
                QueenLogger.i(TAG, "restart alarm")
                QueenKeepAlive.ensureRunning(app, notifyIfRestored = true)
            }
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                if (!QueenKeepAlive.shouldEnsureRunning(app)) return
                if (!shouldHandleSystemEvent()) return
                QueenLogger.i(TAG, "system event revive: $action")
                QueenKeepAlive.ensureRunning(app, notifyIfRestored = true)
            }
        }
    }

    companion object {
        private const val TAG = "KeepAliveReceiver"
        const val ACTION_WATCHDOG = "com.dianziqueen.app.action.KEEPALIVE_WATCHDOG"
        const val ACTION_RESTART = "com.dianziqueen.app.action.KEEPALIVE_RESTART"

        private const val SYSTEM_EVENT_MIN_INTERVAL_MS = 60_000L

        @Volatile
        private var lastSystemEventAt = 0L

        private fun shouldHandleSystemEvent(): Boolean {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSystemEventAt < SYSTEM_EVENT_MIN_INTERVAL_MS) return false
            lastSystemEventAt = now
            return true
        }
    }
}
