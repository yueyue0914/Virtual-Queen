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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 280, 90, 320, 90, 520, 120, 680),
                    intArrayOf(0, 180, 0, 200, 0, 255, 0, 255),
                    -1,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 280, 90, 320, 90, 520, 120, 680), -1)
        }
    }

    fun lightTap(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(60, 80))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(60)
        }
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
