package com.dianziqueen.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

class FakeCameraIndicator(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var dotView: GreenDotView? = null
    private var isShowing = false

    val cameraTitles = listOf(
        "摄像头指示（演示）",
        "电子Queen · 视觉提示",
        "前台传感器样式提示",
        "仅 UI 模拟 · 非真实录制"
    )

    val cameraMessages = listOf(
        "这是一条应用内生成的提示，不会上传画面。",
        "绿色圆点为悬浮窗绘制，可随时在设置中关闭悬浮权限。",
        "用于提醒：已激活 Queen 模式。",
        "若感到打扰，请卸载应用或关闭激活状态。"
    )

    fun showFakeDot(durationMs: Long = 3000L) {
        if (isShowing) return
        try {
            val wm = ContextCompat.getSystemService(context, WindowManager::class.java) ?: return
            val dot = GreenDotView(context)
            val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = 48
            }
            wm.addView(dot, params)
            dotView = dot
            isShowing = true
            handler.postDelayed({ hideDot() }, durationMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideDot() {
        try {
            val wm = ContextCompat.getSystemService(context, WindowManager::class.java) ?: return
            dotView?.let { v ->
                try {
                    wm.removeView(v)
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            dotView = null
            isShowing = false
        }
    }

    class GreenDotView(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00FF41.toInt() }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(28, 28)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val r = width / 2f
            canvas.drawCircle(r, r, r * 0.85f, paint)
        }
    }
}
