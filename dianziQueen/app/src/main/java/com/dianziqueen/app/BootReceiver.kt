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
        val i = Intent(context.applicationContext, QueenService::class.java)
        context.applicationContext.startForegroundService(i)
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
}
