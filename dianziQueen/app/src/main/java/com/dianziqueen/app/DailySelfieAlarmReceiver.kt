package com.dianziqueen.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailySelfieAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!DailySelfieScheduler.isActivated(context)) return
        if (DailySelfieScheduler.shouldEnforce(context)) {
            DailySelfieEnforcement.bringDemandToFront(context)
        }
    }
}
