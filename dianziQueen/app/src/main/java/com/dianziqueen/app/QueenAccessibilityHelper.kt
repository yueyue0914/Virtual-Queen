package com.dianziqueen.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils

object QueenAccessibilityHelper {

    private const val NOTIFY_ID_ACCESSIBILITY = 2005

    fun serviceComponent(context: Context): ComponentName =
        ComponentName(context, QueenAccessibilityService::class.java)

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = serviceComponent(context).flattenToString()
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /** 系统已勾选且服务进程已连接（最佳运行态）。 */
    fun isServiceRunning(context: Context): Boolean =
        isServiceEnabled(context) && QueenAccessibilityService.isConnected()

    /**
     * 尽量直达本 App 的无障碍详情页（用户只需拨动开关）。
     * 普通应用无法代用户打开开关，只能跳转设置。
     */
    fun openQueenAccessibilitySettings(context: Context) {
        val component = serviceComponent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.startActivity(
                    Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("android.intent.extra.COMPONENT_NAME", component.flattenToString())
                    },
                )
                return
            } catch (_: Exception) { }
        }
        openAccessibilitySettings(context)
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    fun notifyAccessibilityDisconnected(context: Context) {
        if (!NotificationHelper.hasEarlyNotificationsReady(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_OPEN_ACCESSIBILITY, true)
        }
        val pi = android.app.PendingIntent.getActivity(
            context,
            0,
            open,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val n = androidx.core.app.NotificationCompat.Builder(
            context,
            DianziQueenApp.CHANNEL_TEASING,
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.accessibility_notify_title))
            .setContentText(context.getString(R.string.accessibility_notify_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context)
            .notify(NOTIFY_ID_ACCESSIBILITY, n)
    }

    fun cancelAccessibilityNotification(context: Context) {
        androidx.core.app.NotificationManagerCompat.from(context)
            .cancel(NOTIFY_ID_ACCESSIBILITY)
    }
}
