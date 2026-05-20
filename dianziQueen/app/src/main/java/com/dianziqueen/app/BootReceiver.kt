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
        DailySelfieScheduler.ensureTodaySchedule(app)
        val i = Intent(app, QueenService::class.java)
        app.startForegroundService(i)
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
}
