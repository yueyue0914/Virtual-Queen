package com.dianziqueen.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 激活后尽量扛住国产机「清理内存 / 一键加速」：
 * - 前台服务 + START_STICKY（见 [QueenService]）
 * - AlarmManager 看门狗定期拉起
 * - 服务 onDestroy 延迟复活
 *
 * 无法绕过用户「强制停止」或部分 ROM 硬杀，需配合自启动/电池无限制/多任务锁定。
 */
object QueenKeepAlive {

    private const val TAG = "QueenKeepAlive"
    private const val ALARM_WATCHDOG = 91_001
    private const val ALARM_RESTART = 91_002
    private const val NOTIFY_ID_RESTORED = 2010
    /** 看门狗间隔：国产机清后台后尽量在数分钟内拉回。 */
    private const val WATCHDOG_INTERVAL_MS = 2 * 60_000L
    private const val RESTART_DELAY_MS = 1_500L
    /** 超过此时间无心跳视为服务已死。 */
    private const val HEARTBEAT_STALE_MS = 4 * 60_000L

    fun onActivated(context: Context) {
        val app = context.applicationContext
        scheduleWatchdog(app)
        ensureRunning(app, notifyIfRestored = false)
    }

    fun onServiceStarted(context: Context) {
        heartbeat(context)
        scheduleWatchdog(context.applicationContext)
    }

    fun heartbeat(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(Prefs.QUEEN_SERVICE_HEARTBEAT_AT, System.currentTimeMillis())
            .apply()
    }

    fun isServiceHealthy(context: Context): Boolean {
        if (QueenService.isAlive()) return true
        val last = context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getLong(Prefs.QUEEN_SERVICE_HEARTBEAT_AT, 0L)
        if (last <= 0L) return false
        return System.currentTimeMillis() - last < HEARTBEAT_STALE_MS
    }

    fun ensureRunning(context: Context, notifyIfRestored: Boolean = true) {
        val app = context.applicationContext
        if (!isActivated(app)) {
            cancelWatchdog(app)
            return
        }
        if (!QueenService.isAlive()) {
            Log.w(TAG, "QueenService not alive, restarting foreground service")
            try {
                QueenService.start(app)
            } catch (e: Exception) {
                Log.e(TAG, "start QueenService failed", e)
            }
            if (notifyIfRestored) {
                notifyServiceRestored(app)
            }
        }
        QueenFloatingWindow.ensureShown(app)
        scheduleWatchdog(app)
    }

    fun onWatchdogAlarm(context: Context) {
        ensureRunning(context.applicationContext, notifyIfRestored = true)
    }

    fun requestDelayedRestart(context: Context, reason: String) {
        val app = context.applicationContext
        if (!isActivated(app)) return
        Log.w(TAG, "requestDelayedRestart: $reason")
        try {
            QueenService.start(app)
        } catch (_: Exception) { }
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(app, KeepAliveReceiver::class.java).apply {
            action = KeepAliveReceiver.ACTION_RESTART
        }
        val pi = PendingIntent.getBroadcast(
            app,
            ALARM_RESTART,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = System.currentTimeMillis() + RESTART_DELAY_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "restart alarm failed: ${e.message}")
        }
        scheduleWatchdog(app)
    }

    fun scheduleWatchdog(context: Context) {
        val app = context.applicationContext
        if (!isActivated(app)) {
            cancelWatchdog(app)
            return
        }
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(app, KeepAliveReceiver::class.java).apply {
            action = KeepAliveReceiver.ACTION_WATCHDOG
        }
        val pi = PendingIntent.getBroadcast(
            app,
            ALARM_WATCHDOG,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "scheduleWatchdog failed: ${e.message}")
        }
    }

    fun cancelWatchdog(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val watchdog = PendingIntent.getBroadcast(
            app,
            ALARM_WATCHDOG,
            Intent(app, KeepAliveReceiver::class.java).apply {
                action = KeepAliveReceiver.ACTION_WATCHDOG
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val restart = PendingIntent.getBroadcast(
            app,
            ALARM_RESTART,
            Intent(app, KeepAliveReceiver::class.java).apply {
                action = KeepAliveReceiver.ACTION_RESTART
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(watchdog)
        am.cancel(restart)
    }

    private fun notifyServiceRestored(context: Context) {
        if (!NotificationHelper.hasEarlyNotificationsReady(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, DianziQueenApp.CHANNEL_TEASING)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.keep_alive_notify_title))
            .setContentText(context.getString(R.string.keep_alive_notify_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_ID_RESTORED, n)
    }

    private fun isActivated(context: Context): Boolean =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.ACTIVATED, false)
}
