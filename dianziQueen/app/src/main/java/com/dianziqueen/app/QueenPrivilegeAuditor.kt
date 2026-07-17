package com.dianziqueen.app

import android.content.Context

/**
 * 统一权限/特权检测：供主界面、服务与引导流程共用。
 * 单项检查委托 [PermissionChecker]，避免与 Helper 重复实现。
 */
object QueenPrivilegeAuditor {

    enum class Privilege {
        CALENDAR,
        WRITE_SETTINGS,
        DEVICE_ADMIN,
        ACCESSIBILITY,
        NOTIFICATION_LISTENER,
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
        if (!PermissionChecker.hasCalendar(context)) missing.add(Privilege.CALENDAR)
        if (!PermissionChecker.canWriteSettings(context)) missing.add(Privilege.WRITE_SETTINGS)
        if (!PermissionChecker.hasBatteryExempt(context)) missing.add(Privilege.BATTERY)
        if (!PermissionChecker.hasOverlay(context)) missing.add(Privilege.OVERLAY)
        if (!PermissionChecker.hasDeviceAdmin(context)) missing.add(Privilege.DEVICE_ADMIN)
        if (!PermissionChecker.hasAccessibility(context)) missing.add(Privilege.ACCESSIBILITY)
        if (!PermissionChecker.hasNotificationListener(context)) {
            missing.add(Privilege.NOTIFICATION_LISTENER)
        }
        if (!PermissionChecker.hasNotifications(context)) missing.add(Privilege.NOTIFICATIONS)
        if (!PermissionChecker.hasCamera(context)) missing.add(Privilege.CAMERA)
        if (!PermissionChecker.hasWallpaper(context)) missing.add(Privilege.WALLPAPER)
        if (!PermissionChecker.hasBluetoothConnect(context)) missing.add(Privilege.BLUETOOTH)
        if (!PermissionChecker.hasStorage(context)) missing.add(Privilege.STORAGE)
        return AuditResult(missing)
    }

    fun isAllCriticalOk(context: Context): Boolean = audit(context).allOk

    fun canWriteSystemSettings(context: Context): Boolean =
        PermissionChecker.canWriteSettings(context)

    fun canDrawOverlays(context: Context): Boolean =
        PermissionChecker.hasOverlay(context)

    fun hasCameraPermission(context: Context): Boolean =
        PermissionChecker.hasCamera(context)

    fun hasStorageAccess(context: Context): Boolean =
        PermissionChecker.hasStorage(context)

    fun storagePermissionsToRequest(context: Context): Array<String> =
        PermissionChecker.storagePermissionsToRequest(context)

    fun isAccessibilityEnabled(context: Context): Boolean =
        PermissionChecker.hasAccessibility(context)

    fun isAccessibilityRunning(context: Context): Boolean =
        QueenAccessibilityHelper.isServiceRunning(context)
}
