package com.dianziqueen.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager

/**
 * 1×1 透明 Activity：App 进入后台时拉起，把进程优先级从 HIDDEN 提到 VISIBLE。
 * 不自动 finish；由 [DianziQueenApp] 在其它界面回到前台时 [dismiss]。
 */
class KeepAlivePixelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        window.setGravity(Gravity.START or Gravity.TOP)
        val params = window.attributes
        params.width = 1
        params.height = 1
        params.x = 0
        params.y = 0
        params.gravity = Gravity.START or Gravity.TOP
        window.attributes = params
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val SHOW_COOLDOWN_MS = 20_000L

        @Volatile
        private var instance: KeepAlivePixelActivity? = null

        @Volatile
        private var lastShowAt = 0L

        fun isShowing(): Boolean = instance != null

        fun dismiss() {
            instance?.finish()
        }

        fun showIfNeeded(context: Context) {
            val app = context.applicationContext
            if (!app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
                    .getBoolean(Prefs.ACTIVATED, false)
            ) {
                return
            }
            if (instance != null) return
            val now = System.currentTimeMillis()
            if (now - lastShowAt < SHOW_COOLDOWN_MS) return
            if (DeclarationInterceptor.shouldReassertBlocking(app)) return
            if (DailySelfieEnforcement.isBlockingAppUsage(app)) return
            if (DailySelfieEnforcement.externalFlowInProgress()) return
            lastShowAt = now
            val intent = Intent(app, KeepAlivePixelActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                app.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }
}
