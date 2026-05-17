package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 检测是否已关闭电池优化（允许后台高耗电）；未豁免时引导用户授权。
 */
object QueenBatteryHelper {

    private const val NOTIFY_ID_BATTERY = 2006

    fun isExemptFromBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 优先弹出系统「允许忽略电池优化」；失败则打开电池优化列表或应用详情页。
     */
    fun openBatteryExemptionSettings(context: Context) {
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                return
            } catch (_: Exception) { }
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                return
            } catch (_: Exception) { }
        }
        openApplicationDetails(context)
    }

    fun openApplicationDetails(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
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

    fun notifyBatteryOptimizationRequired(context: Context) {
        if (!NotificationHelper.hasEarlyNotificationsReady(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_OPEN_BATTERY_SETTINGS, true)
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
            .setContentTitle(context.getString(R.string.battery_notify_title))
            .setContentText(context.getString(R.string.battery_notify_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.battery_notify_big_text)),
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_ID_BATTERY, n)
    }

    fun cancelBatteryNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID_BATTERY)
    }
}
