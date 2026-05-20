package com.dianziqueen.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
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
        if (!NotificationHelper.hasEarlyNotificationsReady(context)) {
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
        Settings.System.canWrite(context)

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 相册选取使用 [ActivityResultContracts.GetContent]，Android 13+ 仅需图片读取权；
     * 不再强制要求 READ_MEDIA_VIDEO，避免「只给了照片仍判未授权」。
     */
    fun hasStorageAccess(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 33 ->
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES,
                ) == PackageManager.PERMISSION_GRANTED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
            else -> true
        }
    }

    fun storagePermissionsToRequest(context: Context): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 33 -> {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_IMAGES,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    emptyArray()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    emptyArray()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val list = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                list.toTypedArray()
            }
            else -> emptyArray()
        }
    }

    /** 无障碍：AccessibilityManager + Settings.Secure 双通道，兼容各厂商格式。 */
    fun isAccessibilityEnabled(context: Context): Boolean =
        QueenAccessibilityHelper.isServiceEnabled(context)

    fun isAccessibilityRunning(context: Context): Boolean =
        QueenAccessibilityHelper.isServiceRunning(context)
}
