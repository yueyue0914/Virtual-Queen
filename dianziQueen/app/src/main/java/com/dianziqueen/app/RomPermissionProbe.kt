package com.dianziqueen.app

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 华米 OV 等国产 ROM 权限探测：标准 API 误报时用 AppOps 补充，
 * 仍无法识别时允许用户在自检页手动确认（仅国产 ROM）。
 */
object RomPermissionProbe {

    private const val TAG = "RomPermissionProbe"
    private const val OP_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background"
    private const val OP_RUN_IN_BACKGROUND = "android:run_in_background"

    const val CONFIRM_OVERLAY = "rom_confirm_overlay"
    const val CONFIRM_WRITE_SETTINGS = "rom_confirm_write_settings"
    const val CONFIRM_BATTERY = "rom_confirm_battery"

    fun isUserConfirmed(context: Context, key: String): Boolean =
        context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(key, false)

    fun setUserConfirmed(context: Context, key: String, confirmed: Boolean) {
        context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, confirmed)
            .apply()
    }

    /** 悬浮窗：canDrawOverlays + AppOps（全品牌，不仅小米）。 */
    fun isOverlayGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (Settings.canDrawOverlays(context)) return true
        if (isAppOpGranted(context, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW)) return true
        if (RomPermissionUtils.isDomesticRom() && isUserConfirmed(context, CONFIRM_OVERLAY)) {
            return true
        }
        return false
    }

    /** 仅自动检测通道（不含用户手动确认），供 UI 提示用。 */
    fun isOverlayAutoDetected(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (Settings.canDrawOverlays(context)) return true
        return isAppOpGranted(context, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW)
    }

    /** 修改系统设置：canWrite + AppOps WRITE_SETTINGS。 */
    fun isWriteSettingsGranted(context: Context): Boolean {
        if (Settings.System.canWrite(context)) return true
        if (isAppOpGranted(context, AppOpsManager.OPSTR_WRITE_SETTINGS)) return true
        if (RomPermissionUtils.isDomesticRom() &&
            isUserConfirmed(context, CONFIRM_WRITE_SETTINGS)
        ) {
            return true
        }
        return false
    }

    fun isWriteSettingsAutoDetected(context: Context): Boolean {
        if (Settings.System.canWrite(context)) return true
        return isAppOpGranted(context, AppOpsManager.OPSTR_WRITE_SETTINGS)
    }

    /** 电池优化豁免 + 国产 ROM 后台运行 AppOps。 */
    fun isBatteryExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return true
        if (RomPermissionUtils.isDomesticRom()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                isAppOpGranted(context, OP_RUN_ANY_IN_BACKGROUND)
            ) {
                return true
            }
            if (isAppOpGranted(context, OP_RUN_IN_BACKGROUND)) {
                return true
            }
            if (isUserConfirmed(context, CONFIRM_BATTERY)) return true
        }
        return false
    }

    fun isBatteryAutoDetected(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return true
        if (!RomPermissionUtils.isDomesticRom()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            isAppOpGranted(context, OP_RUN_ANY_IN_BACKGROUND)
        ) {
            return true
        }
        return isAppOpGranted(context, OP_RUN_IN_BACKGROUND)
    }

    fun confirmKeyForPermissionId(id: String): String? = when (id) {
        "overlay" -> CONFIRM_OVERLAY
        "write_settings" -> CONFIRM_WRITE_SETTINGS
        "battery" -> CONFIRM_BATTERY
        else -> null
    }

    fun needsManualConfirmHint(context: Context, id: String): Boolean {
        if (!RomPermissionUtils.isDomesticRom()) return false
        val key = confirmKeyForPermissionId(id) ?: return false
        if (isUserConfirmed(context, key)) return false
        return when (id) {
            "overlay" -> !isOverlayAutoDetected(context)
            "write_settings" -> !isWriteSettingsAutoDetected(context)
            "battery" -> !isBatteryAutoDetected(context)
            else -> false
        }
    }

    private fun isAppOpGranted(context: Context, op: String): Boolean {
        val mode = queryAppOpMode(context, op) ?: return false
        return mode == AppOpsManager.MODE_ALLOWED ||
            (RomPermissionUtils.isDomesticRom() && mode == AppOpsManager.MODE_DEFAULT)
    }

    private fun queryAppOpMode(context: Context, op: String): Int? {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    op,
                    context.applicationInfo.uid,
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    op,
                    context.applicationInfo.uid,
                    context.packageName,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AppOps query failed op=$op: ${e.message}")
            null
        }
    }
}
