package com.dianziqueen.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 通知监听服务（NLS）权限检测与引导。
 * NLS 作为 [QueenKeepAlive] 的第四维复活点，不在此处理业务逻辑。
 */
object QueenNotificationListenerHelper {

    private const val NOTIFY_ID_NLS = 2007

    fun serviceComponent(context: Context): ComponentName =
        ComponentName(context, QueenNotificationListener::class.java)

    fun isServiceEnabled(context: Context): Boolean {
        val expected = serviceComponent(context).flattenToString()
        val expectedShort =
            "${context.packageName}/${QueenNotificationListener::class.java.simpleName}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        if (enabled.isBlank()) return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val entry = splitter.next()
            if (entry.equals(expected, ignoreCase = true) ||
                entry.equals(expectedShort, ignoreCase = true) ||
                (entry.contains(context.packageName, ignoreCase = true) &&
                    entry.contains(QueenNotificationListener::class.java.simpleName, ignoreCase = true))
            ) {
                return true
            }
        }
        return false
    }

    fun isServiceRunning(context: Context): Boolean =
        isServiceEnabled(context) && QueenNotificationListener.isConnected()

    fun openNotificationListenerSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {
            RomPermissionUtils.openAppDetails(context)
        }
    }

    fun breachDeclarationText(context: Context): String =
        context.getString(R.string.nls_breach_declaration)

    fun notifyDisconnected(context: Context) {
        if (!NotificationHelper.hasEarlyNotificationsReady(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATION_LISTENER, true)
        }
        val pi = android.app.PendingIntent.getActivity(
            context,
            0,
            open,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, DianziQueenApp.CHANNEL_TEASING)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.nls_notify_title))
            .setContentText(context.getString(R.string.nls_notify_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_ID_NLS, n)
    }

    fun cancelDisconnectedNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID_NLS)
    }

    fun onListenerConnected(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(Prefs.QUEEN_NLS_CONNECTED_AT, System.currentTimeMillis())
            .apply()
        cancelDisconnectedNotification(context)
    }
}
