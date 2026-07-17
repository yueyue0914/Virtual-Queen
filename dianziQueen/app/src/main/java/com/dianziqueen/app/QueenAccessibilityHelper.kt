package com.dianziqueen.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

object QueenAccessibilityHelper {

    private const val NOTIFY_ID_ACCESSIBILITY = 2005

    fun serviceComponent(context: Context): ComponentName =
        ComponentName(context, QueenAccessibilityService::class.java)

    fun isServiceEnabled(context: Context): Boolean {
        if (isServiceEnabledViaAccessibilityManager(context)) return true
        if (isServiceEnabledViaSecureSettings(context)) return true
        return isServiceEnabledViaGlobalSwitch(context)
    }

    private fun isServiceEnabledViaAccessibilityManager(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = serviceComponent(context)
        val expectedClass = QueenAccessibilityService::class.java.name
        val shortClass = QueenAccessibilityService::class.java.simpleName
        val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (info in list) {
            val si = info.resolveInfo?.serviceInfo ?: continue
            if (si.packageName != context.packageName) continue
            val name = si.name.orEmpty()
            if (name == expectedClass ||
                name.endsWith(shortClass) ||
                expected.className == name ||
                expected.flattenToString().equals("${context.packageName}/$name", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun isServiceEnabledViaSecureSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = serviceComponent(context).flattenToString()
        val expectedShort = "${context.packageName}/${QueenAccessibilityService::class.java.simpleName}"
        val pkg = context.packageName
        val serviceToken = QueenAccessibilityService::class.java.simpleName
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val entry = splitter.next()
            if (entry.equals(expected, ignoreCase = true) ||
                entry.equals(expectedShort, ignoreCase = true) ||
                (entry.contains(pkg, ignoreCase = true) &&
                    entry.contains(serviceToken, ignoreCase = true))
            ) {
                return true
            }
        }
        return false
    }

    /** 华米 OV 等：总开关已开且 enabled_services 含本包名，但 Manager 列表未及时刷新。 */
    private fun isServiceEnabledViaGlobalSwitch(context: Context): Boolean {
        if (!RomPermissionUtils.isDomesticRom()) return false
        val enabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) == 1
        } catch (_: Exception) {
            return false
        }
        if (!enabled) return false
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        if (services.isBlank()) return false
        val pkg = context.packageName
        val token = QueenAccessibilityService::class.java.simpleName
        return services.contains(pkg, ignoreCase = true) &&
            services.contains(token, ignoreCase = true)
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
        try {
            context.startActivity(
                Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("android.intent.extra.COMPONENT_NAME", component.flattenToString())
                },
            )
            return
        } catch (_: Exception) { }
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
        NotificationHelper.notify(context, NOTIFY_ID_ACCESSIBILITY, n)
    }

    fun cancelAccessibilityNotification(context: Context) {
        NotificationHelper.cancel(context, NOTIFY_ID_ACCESSIBILITY)
    }
}
