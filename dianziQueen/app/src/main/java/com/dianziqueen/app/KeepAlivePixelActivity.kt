package com.dianziqueen.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager

/**
 * 1×1 透明 Activity：App 整体进入后台时短暂拉起，将进程优先级从 HIDDEN 提升到 VISIBLE。
 * 用户不可见、不可触摸；回到任意正常界面时自动 finish。
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
        @Volatile
        private var instance: KeepAlivePixelActivity? = null

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
            if (DeclarationEnforcement.shouldReassertBlocking(app)) return
            if (DailySelfieEnforcement.isBlockingAppUsage(app)) return
            val intent = Intent(app, KeepAlivePixelActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                app.startActivity(intent)
            } catch (_: Exception) { }
        }
    }
}
