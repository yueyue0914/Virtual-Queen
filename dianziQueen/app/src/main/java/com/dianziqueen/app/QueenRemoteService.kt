package com.dianziqueen.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 独立进程 [:keepalive] 极简守护服务：与主进程 [QueenService] 互相 bind，
 * 一方被杀时 [ServiceConnection.onServiceDisconnected] 触发另一方拉回。
 */
class QueenRemoteService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var mainConnection: ServiceConnection? = null

    override fun onCreate() {
        super.onCreate()
        startDaemonForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isActivated()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startDaemonForeground()
        bindMainQueenService()
        QueenKeepAlive.scheduleWatchdog(applicationContext)
        try {
            QueenService.start(this)
        } catch (_: Exception) { }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unbindMainQueenService()
        if (isActivated()) {
            QueenKeepAlive.requestDelayedRestart(applicationContext, "remoteDestroy")
            QueenKeepAlive.startRemoteDaemon(applicationContext)
        }
        super.onDestroy()
    }

    private fun startDaemonForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val tap = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, DianziQueenApp.CHANNEL_DAEMON)
            .setContentTitle(getString(R.string.daemon_notification_title))
            .setContentText(getString(R.string.daemon_notification_text))
            .setSmallIcon(R.drawable.ic_queen_crown)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(DAEMON_NOTIFICATION_ID, notification)
        }
    }

    private fun bindMainQueenService() {
        if (mainConnection != null) return
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                QueenKeepAlive.resetDeathStreak(applicationContext)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mainConnection = null
                if (!isActivated()) return
                Log.w(TAG, "main QueenService disconnected from :keepalive")
                QueenKeepAlive.recordDeath(applicationContext, "remote_link_lost")
                QueenKeepAlive.ensureRunning(applicationContext, notifyIfRestored = true)
                handler.postDelayed({ bindMainQueenService() }, 500L)
            }
        }
        mainConnection = conn
        try {
            bindService(
                Intent(this, QueenService::class.java),
                conn,
                Context.BIND_AUTO_CREATE,
            )
        } catch (e: Exception) {
            Log.w(TAG, "bind QueenService failed: ${e.message}")
            mainConnection = null
        }
    }

    private fun unbindMainQueenService() {
        val conn = mainConnection ?: return
        mainConnection = null
        try {
            unbindService(conn)
        } catch (_: Exception) { }
    }

    private fun isActivated(): Boolean =
        getSharedPreferences(Prefs.NAME, MODE_PRIVATE).getBoolean(Prefs.ACTIVATED, false)

    companion object {
        private const val TAG = "QueenRemoteService"
        private const val DAEMON_NOTIFICATION_ID = 9998
    }
}
