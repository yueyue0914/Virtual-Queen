package com.dianziqueen.app

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 悬浮窗权限检测：标准 [Settings.canDrawOverlays] + 小米/红米 AppOps 补充判断。
 */
object FloatingWindowPermissionHelper {

    private const val TAG = "FloatingWindowPerm"
    private const val PREF_OVERLAY_GUIDE_LAST_MS = "queen_overlay_guide_last_ms"
    private const val OVERLAY_GUIDE_COOLDOWN_MS = 6 * 60 * 60 * 1000L

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (Settings.canDrawOverlays(context)) return true
        if (!isXiaomiFamily()) return false
        return isXiaomiAppOpsGranted(context)
    }

    fun isXiaomiFamily(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            brand.contains("xiaomi") ||
            brand.contains("redmi") ||
            brand.contains("poco")
    }

    /** 小米等机型上 Settings 可能误报，用 AppOps 再确认一次。 */
    private fun isXiaomiAppOpsGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    context.applicationInfo.uid,
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    "android:system_alert_window",
                    context.applicationInfo.uid,
                    context.packageName,
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.w(TAG, "AppOps overlay check failed: ${e.message}")
            false
        }
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openOverlaySettings failed", e)
        }
    }

    /** 尝试打开小米权限中心 / 自启动（失败则忽略）。 */
    fun tryOpenXiaomiExtraSettings(context: Context): Boolean {
        val pkg = context.packageName
        val candidates = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                )
                putExtra("extra_pkgname", pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", pkg)
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

    fun showPermissionGuideDialog(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val message = if (isXiaomiFamily()) {
            activity.getString(R.string.overlay_guide_message_xiaomi)
        } else {
            activity.getString(R.string.overlay_guide_message_generic)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.overlay_guide_title)
            .setMessage(message)
            .setPositiveButton(R.string.overlay_guide_go_settings) { _, _ ->
                openOverlaySettings(activity)
            }
            .apply {
                if (isXiaomiFamily()) {
                    setNeutralButton(R.string.overlay_guide_xiaomi_extra) { _, _ ->
                        if (!tryOpenXiaomiExtraSettings(activity)) {
                            openOverlaySettings(activity)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 激活后或回到前台：缺悬浮窗时提示（小米必弹；其它机型带冷却）。
     */
    fun maybePromptIfNeeded(activity: AppCompatActivity, force: Boolean = false) {
        if (hasPermission(activity)) return
        val prefs = activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && !isXiaomiFamily()) {
            val last = prefs.getLong(PREF_OVERLAY_GUIDE_LAST_MS, 0L)
            if (now - last < OVERLAY_GUIDE_COOLDOWN_MS) return
        }
        prefs.edit().putLong(PREF_OVERLAY_GUIDE_LAST_MS, now).apply()
        showPermissionGuideDialog(activity)
    }

    fun markGuideShown(context: Context) {
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_OVERLAY_GUIDE_LAST_MS, System.currentTimeMillis())
            .apply()
    }
}
