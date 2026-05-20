package com.dianziqueen.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 国产手机（小米/红米/华为/荣耀等）权限高强度引导。
 */
object DomesticPermissionGuide {

    private const val PREF_GUIDE_LAST_MS = "queen_domestic_guide_last_ms"
    private const val PREF_GUIDE_PENDING = "queen_domestic_guide_pending"
    private const val GUIDE_COOLDOWN_MS = 6 * 60 * 60 * 1000L

    enum class DomesticBrand {
        XIAOMI,
        HUAWEI,
        OTHER,
    }

    fun detectBrand(): DomesticBrand {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                DomesticBrand.XIAOMI
            brand.contains("huawei") || brand.contains("honor") ||
                manufacturer.contains("huawei") ->
                DomesticBrand.HUAWEI
            else -> DomesticBrand.OTHER
        }
    }

    fun isDomesticPhone(): Boolean = detectBrand() != DomesticBrand.OTHER

    /** 激活后 / 接管结束：缺权限则强制弹窗。 */
    fun showStrongGuideIfNeeded(activity: AppCompatActivity) {
        if (FloatingWindowPermissionHelper.hasPermission(activity)) {
            clearPending(activity)
            return
        }
        showStrongGuide(activity)
    }

    /**
     * onResume：服务曾检测到权限丢失，或定期提醒（带冷却）。
     * @return 是否已展示引导
     */
    fun maybeShowOnResume(activity: AppCompatActivity): Boolean {
        if (FloatingWindowPermissionHelper.hasPermission(activity)) {
            clearPending(activity)
            return false
        }
        val prefs = activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_GUIDE_PENDING, false)) {
            prefs.edit().putBoolean(PREF_GUIDE_PENDING, false).apply()
            showStrongGuide(activity)
            return true
        }
        val now = System.currentTimeMillis()
        val last = prefs.getLong(PREF_GUIDE_LAST_MS, 0L)
        if (now - last < GUIDE_COOLDOWN_MS) return false
        prefs.edit().putLong(PREF_GUIDE_LAST_MS, now).apply()
        showStrongGuide(activity)
        return true
    }

    /** QueenService 发现悬浮窗权限不可用，下次进 App 再弹引导。 */
    fun markPendingFromBackground(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_GUIDE_PENDING, true)
            .apply()
    }

    fun showStrongGuide(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val message = when (detectBrand()) {
            DomesticBrand.XIAOMI -> activity.getString(R.string.domestic_guide_message_xiaomi)
            DomesticBrand.HUAWEI -> activity.getString(R.string.domestic_guide_message_huawei)
            DomesticBrand.OTHER -> activity.getString(R.string.domestic_guide_message_default)
        }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.domestic_guide_title)
            .setMessage(message)
            .setPositiveButton(R.string.domestic_guide_go_settings) { _, _ ->
                openPermissionSettings(activity)
            }
            .setNeutralButton(R.string.domestic_guide_app_details) { _, _ ->
                openAppDetails(activity)
            }
            .setNegativeButton(R.string.domestic_guide_later) { _, _ ->
                Toast.makeText(
                    activity,
                    R.string.domestic_guide_later_toast,
                    Toast.LENGTH_LONG,
                ).show()
            }
            .setCancelable(false)
            .show()
        activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_GUIDE_LAST_MS, System.currentTimeMillis())
            .apply()
    }

    fun openPermissionSettings(activity: AppCompatActivity) {
        try {
            val overlay = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}"),
            )
            activity.startActivity(overlay)
        } catch (_: Exception) {
            openAppDetails(activity)
        }
        when (detectBrand()) {
            DomesticBrand.XIAOMI -> FloatingWindowPermissionHelper.tryOpenXiaomiExtraSettings(activity)
            DomesticBrand.HUAWEI -> tryOpenHuaweiPermissionHub(activity)
            DomesticBrand.OTHER -> { }
        }
    }

    private fun openAppDetails(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun tryOpenHuaweiPermissionHub(context: Context): Boolean {
        val pkg = context.packageName
        val candidates = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.permissionmanager.ui.MainActivity",
                )
                putExtra("packageName", pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        for (intent in candidates) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) { }
        }
        return false
    }

    private fun clearPending(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_GUIDE_PENDING, false)
            .apply()
    }
}
