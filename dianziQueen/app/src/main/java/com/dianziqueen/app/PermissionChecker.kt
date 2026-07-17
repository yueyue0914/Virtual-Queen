package com.dianziqueen.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 常用权限的薄封装，避免各处重复写 [ContextCompat.checkSelfPermission] /
 * Helper 调用组合。复杂审计仍走 [QueenPrivilegeAuditor]。
 */
object PermissionChecker {

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun hasOverlay(context: Context): Boolean =
        FloatingWindowPermissionHelper.hasPermission(context)

    fun hasCamera(context: Context): Boolean =
        isGranted(context, Manifest.permission.CAMERA)

    fun hasDeviceAdmin(context: Context): Boolean =
        QueenDeviceAdminHelper.isAdminActive(context)

    fun hasBatteryExempt(context: Context): Boolean =
        QueenBatteryHelper.isExemptFromBatteryOptimizations(context)

    fun hasAccessibility(context: Context): Boolean =
        QueenAccessibilityHelper.isServiceEnabled(context)

    fun hasNotificationListener(context: Context): Boolean =
        QueenNotificationListenerHelper.isServiceEnabled(context)

    fun hasNotifications(context: Context): Boolean =
        NotificationHelper.hasNotificationPermissionReady(context)

    fun canWriteSettings(context: Context): Boolean =
        RomPermissionProbe.isWriteSettingsGranted(context)

    fun hasWallpaper(context: Context): Boolean =
        QueenWallpaperHelper.hasSetWallpaperPermission(context)

    fun hasBluetoothConnect(context: Context): Boolean =
        QueenDeviceNameHelper.hasBluetoothConnectPermission(context)

    fun hasCalendar(context: Context): Boolean =
        CalendarInjector.hasCalendarPermission(context)

    /** 相册导入/删原图等所需的读存储权限（含部分照片授权）。 */
    fun hasStorage(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 34 ->
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 ->
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            else ->
                isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    isGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun storagePermissionsToRequest(context: Context): Array<String> {
        if (hasStorage(context)) return emptyArray()
        return when {
            Build.VERSION.SDK_INT >= 34 ->
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
            Build.VERSION.SDK_INT >= 33 ->
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> {
                val list = mutableListOf<String>()
                if (!isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (!isGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                list.toTypedArray()
            }
        }
    }
}
