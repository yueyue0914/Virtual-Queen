package com.dianziqueen.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 定时闹钟：每隔数小时烙印下一批日历羞辱日程。 */
class CalendarInjectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        CalendarInjector.injectScheduledBatchIfDue(app)
        CalendarInjector.scheduleNextInjectAlarm(app)
    }
}
