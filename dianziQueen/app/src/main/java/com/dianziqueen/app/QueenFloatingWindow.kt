package com.dianziqueen.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * 全局悬浮女王完善版入口（实现见 [QueenFloatingOverlay]）。
 *
 * 能力：
 * - 可拖动 + 边缘吸附
 * - 点击弹出 [QueenMenuDialog]
 * - 气泡随机说话 + Toast 羞辱（约 35s～2.5min）
 * - 前台服务看门狗：MIUI/HyperOS 摘掉悬浮窗后自动重挂
 * - 无悬浮窗权限时强制走 [DomesticRomGuide]
 */
object QueenFloatingWindow {

    private val handler = Handler(Looper.getMainLooper())
    /** 小米/红米杀悬浮窗较快，健康检查不宜过长。 */
    private const val HEALTH_CHECK_MS = 60_000L
    private var healthWatchRunning = false

    private val healthRunnable = object : Runnable {
        override fun run() {
            if (!healthWatchRunning) return
            val ctx = QueenFloatingOverlay.applicationContextOrNull()
            if (ctx != null && QueenFloatingOverlay.isActivated(ctx)) {
                if (!PermissionChecker.hasOverlay(ctx)) {
                    hide()
                    DomesticRomGuide.markPendingFromBackground(ctx)
                } else {
                    QueenFloatingOverlay.reattachIfNeeded()
                }
            }
            handler.postDelayed(this, HEALTH_CHECK_MS)
        }
    }

    fun show(context: Context) {
        if (QueenFloatingOverlay.isShowing() && QueenFloatingOverlay.isAttached()) {
            startHealthWatch()
            return
        }
        if (!PermissionChecker.hasOverlay(context)) {
            promptPermissionIfPossible(context)
            return
        }
        QueenFloatingOverlay.ensureShown(context)
        startHealthWatch()
    }

    /**
     * 与 [QueenService] / 看门狗配合：有权限则展示并保活，无权限则收起并标记待引导。
     */
    fun ensureShown(context: Context) {
        if (!PermissionChecker.hasOverlay(context)) {
            hide()
            if (QueenFloatingOverlay.isActivated(context)) {
                DomesticRomGuide.markPendingFromBackground(context)
            }
            return
        }
        // 只走一条路径，避免 reattach + ensureShown 竞态叠两个窗
        QueenFloatingOverlay.ensureShown(context)
        startHealthWatch()
        if (DomesticRomGuide.isDomesticRom() &&
            !PermissionChecker.hasBatteryExempt(context) &&
            QueenFloatingOverlay.isActivated(context)
        ) {
            DomesticRomGuide.markPendingFromBackground(context)
        }
    }

    /** 服务存活期间定期检测悬浮窗是否被系统摘掉。 */
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
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            DomesticRomGuide.showGuide(activity)
        } else if (QueenFloatingOverlay.isActivated(context)) {
            DomesticRomGuide.markPendingFromBackground(context)
        }
    }

    fun hide() {
        QueenMenuDialog.dismiss()
        QueenFloatingOverlay.hide()
    }

    fun isShowing(): Boolean = QueenFloatingOverlay.isShowing()
}
