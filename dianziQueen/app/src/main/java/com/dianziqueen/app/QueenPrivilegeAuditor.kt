package com.dianziqueen.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 统一权限/特权检测：供主界面、服务与引导流程共用，减少「系统已开但 App 仍判未授权」的误报。
 */
object QueenPrivilegeAuditor {

    enum class Privilege {
        CALENDAR,
        WRITE_SETTINGS,
        DEVICE_ADMIN,
        ACCESSIBILITY,
        NOTIFICATIONS,
        CAMERA,
        WALLPAPER,
        BATTERY,
        BLUETOOTH,
        STORAGE,
        OVERLAY,
    }

    data class AuditResult(
        val missing: List<Privilege>,
    ) {
        val allOk: Boolean get() = missing.isEmpty()
    }

    fun audit(context: Context): AuditResult {
        val missing = mutableListOf<Privilege>()
        if (!CalendarInjector.hasCalendarPermission(context)) {
            missing.add(Privilege.CALENDAR)
        }
        if (!canWriteSystemSettings(context)) {
            missing.add(Privilege.WRITE_SETTINGS)
        }
        if (!QueenDeviceAdminHelper.isAdminActive(context)) {
            missing.add(Privilege.DEVICE_ADMIN)
        }
        if (!QueenAccessibilityHelper.isServiceEnabled(context)) {
            missing.add(Privilege.ACCESSIBILITY)
        }
        if (!NotificationHelper.hasNotificationPermissionReady(context)) {
            missing.add(Privilege.NOTIFICATIONS)
        }
        if (!hasCameraPermission(context)) {
            missing.add(Privilege.CAMERA)
        }
        if (!QueenWallpaperHelper.hasSetWallpaperPermission(context)) {
            missing.add(Privilege.WALLPAPER)
        }
        if (!QueenBatteryHelper.isExemptFromBatteryOptimizations(context)) {
            missing.add(Privilege.BATTERY)
        }
        if (!QueenDeviceNameHelper.hasBluetoothConnectPermission(context)) {
            missing.add(Privilege.BLUETOOTH)
        }
        if (!hasStorageAccess(context)) {
            missing.add(Privilege.STORAGE)
        }
        if (!canDrawOverlays(context)) {
            missing.add(Privilege.OVERLAY)
        }
        return AuditResult(missing)
    }

    fun isAllCriticalOk(context: Context): Boolean = audit(context).allOk

    fun canWriteSystemSettings(context: Context): Boolean =
        RomPermissionProbe.isWriteSettingsGranted(context)

    fun canDrawOverlays(context: Context): Boolean =
        FloatingWindowPermissionHelper.hasPermission(context)

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 相册导入后删除原图等能力需要存储读取权。
     * 兼容：Android 14 部分照片授权、Android 13 从旧版升级保留的 READ_EXTERNAL_STORAGE。
     */
    fun hasStorageAccess(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 34 ->
                isPermissionGranted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    isPermissionGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 ->
                isPermissionGranted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    isPermissionGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else -> true
        }
    }

    fun storagePermissionsToRequest(context: Context): Array<String> {
        if (hasStorageAccess(context)) return emptyArray()
        return when {
            Build.VERSION.SDK_INT >= 33 ->
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val list = mutableListOf<String>()
                if (!isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (!isPermissionGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                list.toTypedArray()
            }
            else -> emptyArray()
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /** 无障碍：AccessibilityManager + Settings.Secure 双通道，兼容各厂商格式。 */
    fun isAccessibilityEnabled(context: Context): Boolean =
        QueenAccessibilityHelper.isServiceEnabled(context)

    fun isAccessibilityRunning(context: Context): Boolean =
        QueenAccessibilityHelper.isServiceRunning(context)
}
