package com.dianziqueen.app

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * 独立进程 `:keepalive` 守护：主进程被清后台杀掉时，本进程仍可能存活并拉回 [QueenService]。
 * 与主进程互 bind；任一方断开即尝试重启对方。
 *
 * 无法对抗完整「强制停止」；需配合自启动 / 省电无限制 / 多任务锁定。
 */
class QueenRemoteService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val localBinder = Binder()
    private var boundToMain = false
    private var foregroundStarted = false

    private val mainConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            boundToMain = true
            QueenKeepAlive.resetDeathStreak(this@QueenRemoteService)
            QueenLogger.d(TAG, "bound to QueenService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundToMain = false
            QueenLogger.w(TAG, "QueenService process died, pulling back")
            if (!isActivated()) return
            pullMainService()
            handler.postDelayed({ bindMain() }, 600L)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) {
                stopSelf()
                return
            }
            if (!QueenKeepAlive.isServiceHealthy(this@QueenRemoteService)) {
                pullMainService()
            }
            if (!boundToMain) {
                bindMain()
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureForeground()
        bindMain()
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, 3_000L)
        QueenLogger.i(TAG, "daemon onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        if (isActivated()) {
            if (!QueenKeepAlive.isServiceHealthy(this)) {
                pullMainService()
            }
            bindMain()
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isActivated()) return
        pullMainService()
        QueenKeepAlive.requestDelayedRestart(this, "remote:onTaskRemoved")
        scheduleSelfRestart()
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        try {
            unbindService(mainConnection)
        } catch (_: Exception) {
        }
        boundToMain = false
        foregroundStarted = false
        if (isActivated()) {
            QueenKeepAlive.requestDelayedRestart(this, "remote:onDestroy")
            scheduleSelfRestart()
        }
        super.onDestroy()
    }

    private fun pullMainService() {
        try {
            QueenService.start(this)
        } catch (e: Exception) {
            QueenLogger.e(TAG, "start QueenService failed", e)
        }
    }

    private fun bindMain() {
        if (!isActivated()) return
        try {
            val ok = bindService(
                Intent(this, QueenService::class.java),
                mainConnection,
                Context.BIND_AUTO_CREATE,
            )
            if (!ok) {
                pullMainService()
            }
        } catch (e: Exception) {
            QueenLogger.w(TAG, "bindMain failed: ${e.message}")
            pullMainService()
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        foregroundStarted = true
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, DianziQueenApp.CHANNEL_DAEMON)
            .setContentTitle(getString(R.string.daemon_notification_title))
            .setContentText(getString(R.string.daemon_notification_text))
            .setSmallIcon(R.drawable.ic_queen_crown)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        try {
            startForeground(FG_ID, notification)
        } catch (e: Exception) {
            QueenLogger.e(TAG, "startForeground failed", e)
            foregroundStarted = false
        }
    }

    private fun scheduleSelfRestart() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(this, QueenRemoteService::class.java)
        val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                SELF_RESTART_REQ,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                this,
                SELF_RESTART_REQ,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val triggerAt = System.currentTimeMillis() + 2_500L
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: Exception) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (_: Exception) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 2_500L, pi)
            }
        }
    }

    private fun isActivated(): Boolean =
        getSharedPreferences(Prefs.NAME, MODE_PRIVATE).getBoolean(Prefs.ACTIVATED, false)

    companion object {
        private const val TAG = "QueenRemote"
        private const val FG_ID = 9101
        private const val SELF_RESTART_REQ = 91_101
        private const val POLL_MS = 60_000L
        private const val START_MIN_INTERVAL_MS = 20_000L

        @Volatile
        private var lastStartAt = 0L

        fun start(context: Context) {
            val app = context.applicationContext
            if (!app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
                    .getBoolean(Prefs.ACTIVATED, false)
            ) {
                return
            }
            val now = System.currentTimeMillis()
            if (now - lastStartAt < START_MIN_INTERVAL_MS) return
            lastStartAt = now
            val i = Intent(app, QueenRemoteService::class.java)
            try {
                app.startForegroundService(i)
            } catch (e: Exception) {
                QueenLogger.e(TAG, "start failed", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, QueenRemoteService::class.java),
                )
            } catch (_: Exception) {
            }
        }
    }
}
