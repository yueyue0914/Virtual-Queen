package com.dianziqueen.app

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知监听：仅作系统级保活复活点，不处理通知业务。
 * 系统连接 NLS 或分发任意通知时，借机拉回 [QueenService]。
 */
class QueenNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        QueenNotificationListenerHelper.onListenerConnected(this)
        QueenKeepAlive.onNotificationServiceConnected(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == packageName) return
        if (DeclarationInterceptor.shouldReassertBlocking(this)) {
            if (!DeclarationEnforcement.challengeInForeground) {
                DeclarationEnforcement.bringToFront(this)
            }
            return
        }
        if (!QueenKeepAlive.shouldEnsureRunning(this)) return
        QueenKeepAlive.ensureRunningOnNotificationEvent(this)
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
        try {
            requestRebind(ComponentName(this, QueenNotificationListener::class.java))
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: QueenNotificationListener? = null

        fun isConnected(): Boolean = instance != null
    }
}
