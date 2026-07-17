package com.dianziqueen.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object QueenVibratorHelper {

    fun punish(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 280, 90, 320, 90, 520, 120, 680),
                intArrayOf(0, 180, 0, 200, 0, 255, 0, 255),
                -1,
            ),
        )
    }

    fun lightTap(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(60, 80))
    }

    private fun vibrator(context: Context): Vibrator? {
        val app = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
