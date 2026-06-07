package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * 检测到关机/电源菜单时拉起全屏羞辱界面。
 */
object ShutdownGuard {

    private const val COOLDOWN_MS = 3_500L
    private var lastTriggerAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    fun onShutdownAttemptDetected(context: Context, source: String = "power_menu") {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (!SettingsLockGuard.isStrongControlEnabled(app)) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < COOLDOWN_MS) return
        lastTriggerAt = now

        QueenDeviceAdminHelper.enforcePowerMenuBlock(app)

        handler.post {
            try {
                val backOk = QueenAccessibilityService.performBackGlobally()
                if (!backOk) {
                    // 无无障碍实例时仍继续弹羞辱页
                }
            } catch (_: Exception) { }
        }

        val intent = Intent(app, ShutdownHumiliationActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(ShutdownHumiliationActivity.EXTRA_SOURCE, source)
        }
        try {
            app.startActivity(intent)
        } catch (_: Exception) { }
    }
}
