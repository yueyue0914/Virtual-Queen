package com.dianziqueen.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        val app = context.applicationContext
        UninstallGuard.onBootCompleted(app)
        DailySelfieScheduler.ensureTodaySchedule(app)
        DeclarationScheduler.ensureScheduleInitialized(app)
        val i = Intent(app, QueenService::class.java)
        app.startForegroundService(i)
        QueenKeepAlive.scheduleWatchdog(app)
    }
}

object Prefs {
    const val NAME = "queen_prefs"
    const val ACTIVATED = "activated"
    const val WE_SET_WALLPAPER = "we_set_wallpaper"
    const val CALENDAR_INJECTED = "calendar_injected"
    const val CALENDAR_SCHEDULE_ACTIVE = "calendar_schedule_active"
    const val CALENDAR_INJECT_INDEX = "calendar_inject_index"
    const val CALENDAR_LAST_INJECT_AT = "calendar_last_inject_at"
    const val CALENDAR_LAST_QUEEN_COUNT = "calendar_last_queen_count"
    /** 接管时分配的 100–999 编号，用于设备名与入侵日志一致 */
    const val QUEEN_SLAVE_NUMBER = "queen_slave_number"
    const val QUEEN_DEVICE_NAME_APPLIED = "queen_device_name_applied"
    const val QUEEN_DEVICE_NAME_METHOD = "queen_device_name_method"
    /** 更名渠道均失败且已提示用户，避免 onResume 反复弹 Toast。 */
    const val QUEEN_DEVICE_NAME_RENAME_SKIPPED = "queen_device_name_rename_skipped"
    /** 「已标记此设备」Toast 是否已展示过。 */
    const val QUEEN_DEVICE_NAME_MARK_TOAST_SHOWN = "queen_device_name_mark_toast_shown"
    const val QUEEN_POINTS = "queen_points"
    /** 首次激活后是否已发放默认积分 */
    const val QUEEN_POINTS_ACTIVATION_BONUS = "queen_points_activation_bonus"
    /** 每日签到积分已发放到的日期（yyyy-MM-dd，本地时区） */
    const val QUEEN_POINTS_DAILY_BONUS_DAY = "queen_points_daily_bonus_day"
    const val QUEEN_DAILY_SELFIE_SCHEDULE_DAY = "queen_daily_selfie_schedule_day"
    const val QUEEN_DAILY_SELFIE_TRIGGER_AT = "queen_daily_selfie_trigger_at"
    const val QUEEN_DAILY_SELFIE_SUBMITTED_DAY = "queen_daily_selfie_submitted_day"
    /** 消息分页上次已读时间戳（毫秒）；晚于此时间的消息计为未读。 */
    const val QUEEN_MESSAGES_LAST_READ_TS = "queen_messages_last_read_ts"
    /** 悬浮女王上次位置（屏幕坐标，像素）。 */
    const val QUEEN_FLOAT_X = "queen_float_x"
    const val QUEEN_FLOAT_Y = "queen_float_y"
    /** 悬浮头像样式：default | custom */
    const val QUEEN_FLOAT_AVATAR_STYLE = "queen_float_avatar_style"
    /** 悬浮头像边长（dp），默认 36。 */
    const val QUEEN_FLOAT_AVATAR_SIZE_DP = "queen_float_avatar_size_dp"
    /** 下次轮换壁纸是否优先 App 相册（与 raw 交替；无相册图时不使用）。 */
    const val QUEEN_WALLPAPER_NEXT_VAULT = "queen_wallpaper_next_vault"
    /** 反卸载保护是否已启用（激活后默认 true）。 */
    const val UNINSTALL_PROTECTED = "uninstall_protected"
    /** 卸载/停用管理员等反抗次数。 */
    const val REBELLION_COUNT = "rebellion_count"
    /** 重装后待执行的重度惩罚（由外部档案同步）。 */
    const val HEAVY_PUNISHMENT_PENDING = "heavy_punishment_pending"
    /** 最近一次卸载尝试时间戳（毫秒）。 */
    const val LAST_UNINSTALL_ATTEMPT_AT = "last_uninstall_attempt_at"
    /** 已为哪次尝试时间戳执行过开机惩罚（避免重复）。 */
    const val BOOT_PUNISH_FOR_ATTEMPT_AT = "boot_punish_for_attempt_at"
    /** Queen PIN 验证通过后的授权截止时间（毫秒）。 */
    const val ADMIN_DISABLE_PIN_GRANTED_UNTIL = "admin_disable_pin_granted_until"
    /** 最强控制：拦截系统设置（默认开启，可在 App 设置页用密码关闭）。 */
    const val STRONG_CONTROL_ENABLED = "strong_control_enabled"
    /** 用户曾用密码手动关闭最强控制；激活流程不得再自动打开。 */
    const val STRONG_CONTROL_USER_OPT_OUT = "strong_control_user_opt_out"
    /** [QueenService] 最近一次心跳（毫秒）。 */
    const val QUEEN_SERVICE_HEARTBEAT_AT = "queen_service_heartbeat_at"
    /** 连续被杀次数（阶梯复活延迟用）。 */
    const val KEEPALIVE_DEATH_STREAK = "keepalive_death_streak"
    /** 最近一次进程/服务死亡时间（毫秒）。 */
    const val KEEPALIVE_LAST_DEATH_AT = "keepalive_last_death_at"
    /** [QueenNotificationListener] 最近一次连接时间（毫秒）。 */
    const val QUEEN_NLS_CONNECTED_AT = "queen_nls_connected_at"
    /** NLS 断开触发的契约宣誓冷却锚点（毫秒）。 */
    const val NLS_LAST_BREACH_AT = "nls_last_breach_at"
    /** 服务启动后是否已尝试弹出系统「忽略电池优化」（仅一次）。 */
    const val BATTERY_EXEMPTION_SERVICE_PROMPTED = "battery_exemption_service_prompted"
    /** 宣言验证：是否启用（激活后默认 true）。 */
    const val DECLARATION_ENABLED = "declaration_enabled"
    /** 宣言验证间隔模式：random | fixed */
    const val DECLARATION_MODE = "declaration_mode"
    const val DECLARATION_RANDOM_MIN_MINUTES = "declaration_random_min_minutes"
    const val DECLARATION_RANDOM_MAX_MINUTES = "declaration_random_max_minutes"
    const val DECLARATION_FIXED_MINUTES = "declaration_fixed_minutes"
    /** 下次宣言验证触发时间（毫秒）。 */
    const val DECLARATION_NEXT_AT = "declaration_next_at"
    /** 上次通过宣言验证的时间（毫秒）。 */
    const val DECLARATION_LAST_PASSED_AT = "declaration_last_passed_at"
    /** 当前是否有未完成的宣言验证（须输入正确才能用手机）。 */
    const val DECLARATION_CHALLENGE_PENDING = "declaration_challenge_pending"
    /** 当前待输入的宣言（触发验证时已随机抽取并固定）。 */
    const val DECLARATION_CURRENT_TEXT = "declaration_current_text"
    const val QUEEN_HONORIFIC_PRESET = "queen_honorific_preset"
}
