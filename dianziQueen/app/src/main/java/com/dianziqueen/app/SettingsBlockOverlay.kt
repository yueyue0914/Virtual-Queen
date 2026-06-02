package com.dianziqueen.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.random.Random

/**
 * 系统设置被拦截后，用悬浮窗全屏警告（无需 Activity 到前台，可盖在桌面上）。
 */
object SettingsBlockOverlay {

    private const val TAG = "SettingsBlockOverlay"

    private val handler = Handler(Looper.getMainLooper())
    private var rootView: View? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false
    private var vibrating = false

    private val threats: Array<String>
        get() = rootView?.context?.resources
            ?.getStringArray(R.array.uninstall_threat_lines)
            ?.map { QueenHonorific.apply(rootView!!.context, it) }
            ?.toTypedArray()
            ?: emptyArray()

    private val vibrateRunnable = object : Runnable {
        override fun run() {
            if (!vibrating) return
            rootView?.context?.let { QueenVibratorHelper.lightTap(it) }
            handler.postDelayed(this, 750L)
        }
    }

    private val rotateRunnable = object : Runnable {
        override fun run() {
            if (!isShowing) return
            val bodyView = rootView?.findViewById<TextView>(R.id.tvUninstallThreatBody) ?: return
            val lines = threats
            if (lines.isNotEmpty()) {
                bodyView.text = lines[Random.nextInt(lines.size)]
            }
            handler.postDelayed(this, 2_400L)
        }
    }

    private var autoHideRunnable: Runnable? = null

    /** @return 是否成功显示 */
    fun show(
        context: Context,
        title: String,
        body: String,
        autoFinishMs: Long = 10_000L,
    ): Boolean {
        val app = context.applicationContext
        if (!FloatingWindowPermissionHelper.hasPermission(app)) return false
        hide()
        return try {
            val wm = ContextCompat.getSystemService(app, WindowManager::class.java) ?: return false
            val root = LayoutInflater.from(app).inflate(R.layout.activity_uninstall_threat, null)
            root.findViewById<TextView>(R.id.tvUninstallThreatTitle).text = title
            root.findViewById<TextView>(R.id.tvUninstallThreatBody).text = body
            root.findViewById<Button>(R.id.btnUninstallSurrender).apply {
                text = app.hon(R.string.uninstall_threat_surrender)
                setOnClickListener { hide() }
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            )
            wm.addView(root, params)
            rootView = root
            windowManager = wm
            isShowing = true
            vibrating = true
            handler.post(vibrateRunnable)
            handler.post(rotateRunnable)
            autoHideRunnable = Runnable { hide() }.also {
                handler.postDelayed(it, autoFinishMs.coerceAtLeast(3_000L))
            }
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "show failed", e)
            hide()
            false
        }
    }

    fun hide() {
        vibrating = false
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = null
        handler.removeCallbacks(vibrateRunnable)
        handler.removeCallbacks(rotateRunnable)
        try {
            val wm = windowManager
            val view = rootView
            if (wm != null && view != null && view.isAttachedToWindow) {
                wm.removeView(view)
            }
        } catch (_: Exception) { }
        rootView = null
        windowManager = null
        isShowing = false
    }

    /** 警告已显示时用户再次闯入设置，延长展示时间。 */
    fun refreshAutoHide(autoFinishMs: Long = 10_000L) {
        if (!isShowing) return
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = Runnable { hide() }.also {
            handler.postDelayed(it, autoFinishMs.coerceAtLeast(3_000L))
        }
    }

    fun isVisible(): Boolean = isShowing
}
