package com.dianziqueen.app

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 悬浮窗权限：标准 [Settings.canDrawOverlays] + 小米/红米 AppOps 补充 + 引导弹窗。
 */
object FloatingWindowPermissionHelper {

    private const val TAG = "FloatingWindowPerm"
    private const val PREF_OVERLAY_GUIDE_LAST_MS = "queen_overlay_guide_last_ms"
    private const val OVERLAY_GUIDE_COOLDOWN_MS = 6 * 60 * 60 * 1000L

    /** 是否有悬浮窗权限（适配小米/红米）。 */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (Settings.canDrawOverlays(context)) return true
        if (!isXiaomiFamily()) return false
        return isXiaomiOverlayGranted(context)
    }

    fun isXiaomiFamily(): Boolean = isXiaomiDevice()

    private fun isXiaomiDevice(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("xiaomi") ||
            brand.contains("redmi") ||
            brand.contains("poco") ||
            manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi")
    }

    /** 小米/红米：Settings 误报时用 AppOps 再确认。 */
    private fun isXiaomiOverlayGranted(context: Context): Boolean {
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

    fun requestPermission(activity: AppCompatActivity) {
        if (hasPermission(activity)) return
        openOverlaySettings(activity)
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                if (context !is AppCompatActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openOverlaySettings failed", e)
        }
    }

    /** 尝试打开小米权限中心 / 自启动。 */
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

    /** 小米/红米高强度引导（推荐）。 */
    fun showXiaomiGuideDialog(activity: AppCompatActivity, onConfirmed: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.overlay_xiaomi_guide_title))
            .setMessage(activity.hon(R.string.overlay_xiaomi_guide_message))
            .setPositiveButton(R.string.overlay_xiaomi_guide_go) { _, _ ->
                requestPermission(activity)
                if (!tryOpenXiaomiExtraSettings(activity)) {
                    openOverlaySettings(activity)
                }
                onConfirmed?.invoke()
            }
            .setNeutralButton(R.string.overlay_guide_xiaomi_extra) { _, _ ->
                tryOpenXiaomiExtraSettings(activity)
            }
            .setNegativeButton(R.string.overlay_xiaomi_guide_later) { _, _ ->
                Toast.makeText(activity, activity.hon(R.string.overlay_xiaomi_guide_later_toast), Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    /** 通用检查 + 引导（自检按钮、权限流程可调用）。 */
    fun checkAndRequest(activity: AppCompatActivity) {
        if (hasPermission(activity)) {
            Toast.makeText(activity, activity.hon(R.string.overlay_already_granted), Toast.LENGTH_SHORT).show()
            QueenService.start(activity)
            return
        }
        if (isXiaomiFamily()) {
            showXiaomiGuideDialog(activity) {
                QueenService.start(activity)
            }
        } else {
            showGenericGuideDialog(activity)
        }
    }

    fun showPermissionGuideDialog(activity: AppCompatActivity) {
        if (isXiaomiFamily()) {
            showXiaomiGuideDialog(activity)
        } else {
            showGenericGuideDialog(activity)
        }
    }

    private fun showGenericGuideDialog(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.overlay_guide_title))
            .setMessage(activity.hon(R.string.overlay_guide_message_generic))
            .setPositiveButton(R.string.overlay_guide_go_settings) { _, _ ->
                requestPermission(activity)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 激活后 / 回前台：缺权限时提示。
     * 小米：更强弹窗；其它品牌：带冷却的普通引导。
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
        if (isXiaomiFamily()) {
            showXiaomiGuideDialog(activity) {
                QueenService.start(activity)
            }
        } else {
            showGenericGuideDialog(activity)
        }
    }
}
