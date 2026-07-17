package com.dianziqueen.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * 激活后尽量扛住国产机「清理内存 / 一键加速」：
 * - 前台服务 + START_STICKY（见 [QueenService]）
 * - 独立进程守护（见 [QueenRemoteService]）
 * - 低频精确闹钟看门狗（不用 AlarmClock，避免系统闹钟风暴卡顿）
 * - 辅助功能 / 通知监听借壳拉起
 * - 短暂 1 像素 Activity 抬优先级（见 [KeepAlivePixelActivity]）
 *
 * 无法绕过用户「强制停止」或部分 ROM 硬杀，需配合自启动/电池无限制/多任务锁定。
 */
object QueenKeepAlive {

    private const val TAG = "QueenKeepAlive"
    private const val ALARM_WATCHDOG = 91_001
    private const val ALARM_RESTART = 91_002
    private const val NOTIFY_ID_RESTORED = 2010

    /** 常规看门狗：过密会拖慢整机。 */
    private const val WATCHDOG_INTERVAL_MS = 2 * 60_000L

    /** 清后台后少量复活闹钟（勿用 AlarmClock 连环排程）。 */
    private val RESTART_BURST_DELAYS_MS = longArrayOf(3_000L, 15_000L)

    /** 阶梯复活：避开系统清理窗口的连续扫描。 */
    private val RESTART_DELAYS_MS = longArrayOf(5_000L, 20_000L, 45_000L)

    private const val DEATH_STREAK_RESET_MS = 5 * 60_000L

    /** 超过此时间无心跳视为服务已死（跨进程读 SharedPreferences）。 */
    private const val HEARTBEAT_STALE_MS = 3 * 60_000L

    /** NLS 收到通知时拉起主服务的最小间隔，避免通知风暴。 */
    private const val NLS_ENSURE_MIN_INTERVAL_MS = 60_000L

    private const val WATCHDOG_RESCHEDULE_MIN_MS = 30_000L

    @Volatile
    private var lastNlsEnsureAt = 0L

    @Volatile
    private var lastWatchdogScheduledAt = 0L

    @Volatile
    private var lastEnsureFloatAt = 0L

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
        QueenRemoteService.start(context)
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
            QueenRemoteService.stop(app)
            return
        }
        val needStart = !QueenService.isAlive()
        if (needStart) {
            QueenLogger.w(TAG, "QueenService not alive, restarting foreground service")
            try {
                QueenService.start(app)
            } catch (e: Exception) {
                QueenLogger.e(TAG, "start QueenService failed", e)
            }
            if (notifyIfRestored) {
                notifyServiceRestored(app)
            }
            QueenRemoteService.start(app)
        }
        maybeEnsureFloating(app)
        scheduleWatchdog(app)
    }

    private fun maybeEnsureFloating(app: Context) {
        val now = System.currentTimeMillis()
        if (QueenFloatingWindow.isShowing() &&
            QueenFloatingOverlay.isAttached() &&
            now - lastEnsureFloatAt < 60_000L
        ) {
            return
        }
        lastEnsureFloatAt = now
        QueenFloatingWindow.ensureShown(app)
    }

    fun onWatchdogAlarm(context: Context) {
        ensureRunning(context.applicationContext, notifyIfRestored = false)
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
        } catch (_: Exception) {
        }
        try {
            QueenRemoteService.start(app)
        } catch (_: Exception) {
        }
        scheduleRestartBurst(app)
        scheduleWatchdog(app)
    }

    /** 清后台后少量精确闹钟拉起；不用 AlarmClock，避免状态栏闹钟风暴导致整机卡顿。 */
    private fun scheduleRestartBurst(app: Context) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val streakDelay = computeRestartDelay(app)
        val delays = (RESTART_BURST_DELAYS_MS.toList() + streakDelay).distinct().sorted()
        delays.forEachIndexed { index, delay ->
            val intent = Intent(app, KeepAliveReceiver::class.java).apply {
                action = KeepAliveReceiver.ACTION_RESTART
            }
            val pi = PendingIntent.getBroadcast(
                app,
                ALARM_RESTART + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            scheduleWakeAlarm(am, now + delay, pi)
        }
    }

    fun scheduleWatchdog(context: Context) {
        val app = context.applicationContext
        if (!isActivated(app)) {
            cancelWatchdog(app)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastWatchdogScheduledAt < WATCHDOG_RESCHEDULE_MIN_MS) return
        lastWatchdogScheduledAt = now
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
        scheduleWakeAlarm(am, now + WATCHDOG_INTERVAL_MS, pi)
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
        am.cancel(watchdog)
        for (i in 0..7) {
            val restart = PendingIntent.getBroadcast(
                app,
                ALARM_RESTART + i,
                Intent(app, KeepAliveReceiver::class.java).apply {
                    action = KeepAliveReceiver.ACTION_RESTART
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.cancel(restart)
        }
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
