package com.dianziqueen.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * 全局悬浮女王入口（实现见 [QueenFloatingOverlay]）。
 * - 可拖动头像 + 气泡台词 + 边缘吸附
 * - 点击弹出 [QueenMenuDialog]
 * - 随机 Toast 羞辱；结合 [QueenService] 前台服务与看门狗保活
 */
object QueenFloatingWindow {

    private val handler = Handler(Looper.getMainLooper())
    private const val HEALTH_CHECK_MS = 60_000L
    private var healthWatchRunning = false

    private val healthRunnable = object : Runnable {
        override fun run() {
            if (!healthWatchRunning) return
            val ctx = QueenFloatingOverlay.applicationContextOrNull()
            if (ctx != null && QueenFloatingOverlay.isActivated(ctx)) {
                QueenFloatingOverlay.reattachIfNeeded()
                if (DomesticRomGuide.isDomesticRom() &&
                    !PermissionChecker.hasBatteryExempt(ctx)
                ) {
                    DomesticRomGuide.markPendingFromBackground(ctx)
                }
            }
            handler.postDelayed(this, HEALTH_CHECK_MS)
        }
    }

    fun show(context: Context) {
        if (QueenFloatingOverlay.isShowing() && QueenFloatingOverlay.isAttached()) return
        if (!PermissionChecker.hasOverlay(context)) {
            promptPermissionIfPossible(context)
            return
        }
        QueenFloatingOverlay.ensureShown(context)
    }

    /** 与 [QueenService] 等后台场景配合：有权限则展示，无权限则收起并标记待引导。 */
    fun ensureShown(context: Context) {
        if (QueenFloatingOverlay.isShowing() && QueenFloatingOverlay.isAttached()) {
            return
        }
        if (!PermissionChecker.hasOverlay(context)) {
            hide()
            if (QueenFloatingOverlay.isActivated(context)) {
                DomesticRomGuide.markPendingFromBackground(context)
            }
            return
        }
        if (!QueenFloatingOverlay.isAttached()) {
            QueenFloatingOverlay.reattachIfNeeded()
        }
        QueenFloatingOverlay.ensureShown(context)
        if (DomesticRomGuide.isDomesticRom() &&
            !PermissionChecker.hasBatteryExempt(context) &&
            QueenFloatingOverlay.isActivated(context)
        ) {
            DomesticRomGuide.markPendingFromBackground(context)
        }
    }

    /** 服务存活期间定期检测悬浮窗是否被 MIUI 等系统摘掉。 */
    fun startHealthWatch() {
        if (healthWatchRunning) return
        healthWatchRunning = true
        handler.removeCallbacks(healthRunnable)
        handler.postDelayed(healthRunnable, HEALTH_CHECK_MS)
    }

    fun stopHealthWatch() {
        healthWatchRunning = false
        handler.removeCallbacks(healthRunnable)
    }

    private fun promptPermissionIfPossible(context: Context) {
        val activity = context as? AppCompatActivity
        if (activity != null && !activity.isFinishing) {
            DomesticRomGuide.showGuideIfNeeded(activity)
        }
    }

    fun hide() {
        QueenMenuDialog.dismiss()
        QueenFloatingOverlay.hide()
    }

    fun isShowing(): Boolean = QueenFloatingOverlay.isShowing()
}
