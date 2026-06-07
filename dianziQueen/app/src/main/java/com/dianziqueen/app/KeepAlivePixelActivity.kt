package com.dianziqueen.app



import android.app.Activity

import android.content.Context

import android.content.Intent

import android.os.Bundle

import android.os.Handler

import android.os.Looper

import android.view.Gravity

import android.view.WindowManager



/**

 * 1×1 透明 Activity：App 整体进入后台时短暂拉起，将进程优先级从 HIDDEN 提升到 VISIBLE。

 * 用户不可见、不可触摸；[onResume] 后立即 finish，避免无焦点窗口导致 ANR。

 */

class KeepAlivePixelActivity : Activity() {



    private val handler = Handler(Looper.getMainLooper())



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



    override fun onResume() {

        super.onResume()

        handler.removeCallbacks(finishRunnable)

        handler.post(finishRunnable)

    }



    override fun onDestroy() {

        handler.removeCallbacks(finishRunnable)

        if (instance === this) instance = null

        super.onDestroy()

    }



    private val finishRunnable = Runnable {

        if (isFinishing) return@Runnable

        finish()

        @Suppress("DEPRECATION")

        overridePendingTransition(0, 0)

    }



    companion object {

        private const val SHOW_COOLDOWN_MS = 60_000L



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

            } catch (_: Exception) { }

        }

    }

}


