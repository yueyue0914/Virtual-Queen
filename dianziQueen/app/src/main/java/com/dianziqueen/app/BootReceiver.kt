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
}
