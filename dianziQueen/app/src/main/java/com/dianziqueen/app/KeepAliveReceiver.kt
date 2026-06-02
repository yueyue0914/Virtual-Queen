package com.dianziqueen.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager 看门狗：内存清理后拉回 [QueenService]。 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            ACTION_WATCHDOG -> QueenKeepAlive.onWatchdogAlarm(app)
            ACTION_RESTART -> QueenKeepAlive.ensureRunning(app, notifyIfRestored = true)
        }
    }

    companion object {
        const val ACTION_WATCHDOG = "com.dianziqueen.app.action.KEEPALIVE_WATCHDOG"
        const val ACTION_RESTART = "com.dianziqueen.app.action.KEEPALIVE_RESTART"
    }
}
