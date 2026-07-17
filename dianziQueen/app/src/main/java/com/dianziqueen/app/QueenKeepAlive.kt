package com.dianziqueen.app



import android.app.AlarmManager

import android.app.PendingIntent

import android.content.Context

import android.content.Intent

import android.os.Build
import androidx.core.app.NotificationCompat

import androidx.core.app.NotificationManagerCompat



/**

 * 激活后尽量扛住国产机「清理内存 / 一键加速」（单进程）：

 * - 前台服务 + START_STICKY（见 [QueenService]）

 * - 精确 AlarmManager 看门狗 + 阶梯式延迟复活

 * - 辅助功能连接时借壳拉起（见 [QueenAccessibilityService]）

 * - 通知监听 NLS 事件级复活（见 [QueenNotificationListener]）

 * - 后台 1 像素 Activity 抬优先级（见 [KeepAlivePixelActivity]）

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

    /** 阶梯复活：避开系统清理窗口的连续扫描。 */

    private val RESTART_DELAYS_MS = longArrayOf(2_000L, 10_000L, 30_000L)

    private const val DEATH_STREAK_RESET_MS = 5 * 60_000L

    /** 超过此时间无心跳视为服务已死。 */

    private const val HEARTBEAT_STALE_MS = 4 * 60_000L

    /** NLS 收到通知时拉起主服务的最小间隔，避免通知风暴。 */

    private const val NLS_ENSURE_MIN_INTERVAL_MS = 30_000L



    @Volatile

    private var lastNlsEnsureAt = 0L



    fun shouldEnsureRunning(context: Context): Boolean = isActivated(context)



    fun isNlsHealthy(context: Context): Boolean =

        QueenNotificationListenerHelper.isServiceEnabled(context)



    fun onNotificationServiceConnected(context: Context) {

        val app = context.applicationContext

        ensureRunning(app, notifyIfRestored = false)

        scheduleWatchdog(app)

    }



    /** 由 [QueenNotificationListener.onNotificationPosted] 调用（已节流）。 */

    fun ensureRunningOnNotificationEvent(context: Context) {

        if (!shouldEnsureRunning(context)) return

        val now = System.currentTimeMillis()

        synchronized(this) {

            if (now - lastNlsEnsureAt < NLS_ENSURE_MIN_INTERVAL_MS) return

            lastNlsEnsureAt = now

        }

        ensureRunning(context.applicationContext, notifyIfRestored = false)

    }



    fun onActivated(context: Context) {

        val app = context.applicationContext

        scheduleWatchdog(app)

        ensureRunning(app, notifyIfRestored = false)

    }



    fun onServiceStarted(context: Context) {

        heartbeat(context)

        resetDeathStreak(context)

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

            KeepAlivePixelActivity.dismiss()

            return

        }

        if (!QueenService.isAlive()) {

            QueenLogger.w(TAG, "QueenService not alive, restarting foreground service")

            try {

                QueenService.start(app)

            } catch (e: Exception) {

                QueenLogger.e(TAG, "start QueenService failed", e)

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



    fun recordDeath(context: Context, reason: String) {

        val prefs = context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

        val now = System.currentTimeMillis()

        val lastDeath = prefs.getLong(Prefs.KEEPALIVE_LAST_DEATH_AT, 0L)

        val streak = if (now - lastDeath > DEATH_STREAK_RESET_MS) {

            1

        } else {

            prefs.getInt(Prefs.KEEPALIVE_DEATH_STREAK, 0) + 1

        }

        prefs.edit()

            .putInt(Prefs.KEEPALIVE_DEATH_STREAK, streak)

            .putLong(Prefs.KEEPALIVE_LAST_DEATH_AT, now)

            .apply()

        QueenLogger.w(TAG, "recordDeath streak=$streak reason=$reason")

    }



    fun resetDeathStreak(context: Context) {

        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

            .edit()

            .putInt(Prefs.KEEPALIVE_DEATH_STREAK, 0)

            .apply()

    }



    fun requestDelayedRestart(context: Context, reason: String) {

        val app = context.applicationContext

        if (!isActivated(app)) return

        recordDeath(app, reason)

        QueenLogger.w(TAG, "requestDelayedRestart: $reason")

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

        val delay = computeRestartDelay(app)

        scheduleWakeAlarm(am, System.currentTimeMillis() + delay, pi)

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

        scheduleWakeAlarm(am, System.currentTimeMillis() + WATCHDOG_INTERVAL_MS, pi)

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



    private fun computeRestartDelay(context: Context): Long {

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

        val lastDeath = prefs.getLong(Prefs.KEEPALIVE_LAST_DEATH_AT, 0L)

        if (System.currentTimeMillis() - lastDeath > DEATH_STREAK_RESET_MS) {

            resetDeathStreak(context)

        }

        val streak = prefs.getInt(Prefs.KEEPALIVE_DEATH_STREAK, 0).coerceAtLeast(1)

        val index = (streak - 1).coerceAtMost(RESTART_DELAYS_MS.lastIndex)

        return RESTART_DELAYS_MS[index]

    }



    /** Android 14+ Doze 下尽量准时唤醒；无精确闹钟权限时降级。 */

    private fun scheduleWakeAlarm(

        am: AlarmManager,

        triggerAt: Long,

        pi: PendingIntent,

    ) {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&

                !am.canScheduleExactAlarms()

            ) {

                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)

            } else {

                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)

            }

        } catch (e: SecurityException) {

            QueenLogger.w(TAG, "exact alarm denied, fallback: ${e.message}")

            try {

                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)

            } catch (e2: Exception) {

                QueenLogger.w(TAG, "fallback alarm failed: ${e2.message}")

            }

        } catch (e: Exception) {

            QueenLogger.w(TAG, "scheduleWakeAlarm failed: ${e.message}")

        }

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

        val n = NotificationCompat.Builder(context, DianziQueenApp.CHANNEL_DAEMON)

            .setSmallIcon(android.R.drawable.ic_popup_reminder)

            .setContentTitle(context.getString(R.string.daemon_notification_title))

            .setContentText(context.getString(R.string.daemon_notification_text))

            .setContentIntent(pi)

            .setAutoCancel(true)

            .setPriority(NotificationCompat.PRIORITY_HIGH)

            .build()

        NotificationHelper.notify(context, NOTIFY_ID_RESTORED, n)

    }



    private fun isActivated(context: Context): Boolean =

        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

            .getBoolean(Prefs.ACTIVATED, false)

}


