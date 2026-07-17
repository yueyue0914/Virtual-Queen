package com.dianziqueen.app

/**
 * 权限自检单项结果。
 */
data class PermissionStatus(
    val id: String,
    val name: String,
    val isGranted: Boolean,
    val critical: Boolean = false,
    /** 系统无法自动检测，需用户手动在设置中确认。 */
    val manualOnly: Boolean = false,
    val note: String = "",
    val fixAction: FixAction = FixAction.NONE,
) {
    enum class FixAction {
        NONE,
        OVERLAY,
        ACCESSIBILITY,
        NOTIFICATIONS,
        DEVICE_ADMIN,
        WRITE_SETTINGS,
        NOTIFICATION_LISTENER,
        BATTERY,
        AUTO_START,
        ROM_GUIDE,
        APP_DETAILS,
        CAMERA,
        CALENDAR,
    }

    val displayOk: Boolean = isGranted
}
