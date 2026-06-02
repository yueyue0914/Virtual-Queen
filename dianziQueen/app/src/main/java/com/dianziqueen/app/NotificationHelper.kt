package com.dianziqueen.app

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 检测通知是否可能被系统/用户「拦截」；无法绕过系统将渠道强制改为「重要」，只能引导去设置页。
 */
object NotificationHelper {

    fun isPostNotificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun areAppNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 上交/激活门槛：运行时通知权 + 总开关（不含渠道重要性，避免「已授权仍被拦」）。 */
    fun hasNotificationPermissionReady(context: Context): Boolean =
        isPostNotificationsGranted(context) && areAppNotificationsEnabled(context)

    /** 完整就绪：运行时通知权 + 总开关 + 渠道重要性（用于激活后横幅提示）。 */
    fun hasEarlyNotificationsReady(context: Context): Boolean =
        hasNotificationPermissionReady(context) &&
            isTeasingChannelImportanceAdequate(context)

    /**
     * 「Queen 提示」渠道重要性是否至少为 HIGH（便于悬浮横幅）。
     * 渠道尚未创建时视为通过，避免误报。
     */
    fun isTeasingChannelImportanceAdequate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return true
        val ch = nm.getNotificationChannel(DianziQueenApp.CHANNEL_TEASING) ?: return true
        return ch.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun openAppNotificationSettings(activity: Activity) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            }
            activity.startActivity(intent)
        } catch (_: Exception) { }
    }

    /** 直达「Queen 提示」渠道设置（可改重要性）；失败则打开应用通知总页。 */
    fun openTeasingChannelSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openAppNotificationSettings(activity)
            return
        }
        try {
            activity.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, DianziQueenApp.CHANNEL_TEASING)
                }
            )
        } catch (_: Exception) {
            openAppNotificationSettings(activity)
        }
    }
}
