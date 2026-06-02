package com.dianziqueen.app

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

/**
 * 全局悬浮女王完善版入口：与 [QueenFloatingOverlay] 共用实现。
 * - 可拖动头像 + 气泡台词
 * - 点击弹出 [QueenMenuDialog]
 * - 随机 Toast 羞辱（[QueenInsultLibrary]）
 */
object QueenFloatingWindow {

    fun show(context: Context) {
        if (!FloatingWindowPermissionHelper.hasPermission(context)) {
            promptPermissionIfPossible(context)
            return
        }
        QueenFloatingOverlay.ensureShown(context)
    }

    /** 与 [QueenService] 等后台场景配合：有权限则展示，无权限则收起并标记待引导。 */
    fun ensureShown(context: Context) {
        if (!FloatingWindowPermissionHelper.hasPermission(context)) {
            hide()
            if (QueenFloatingOverlay.isActivated(context)) {
                DomesticRomGuide.markPendingFromBackground(context)
            }
            return
        }
        QueenFloatingOverlay.ensureShown(context)
        if (DomesticRomGuide.isDomesticRom() &&
            !QueenBatteryHelper.isExemptFromBatteryOptimizations(context) &&
            QueenFloatingOverlay.isActivated(context)
        ) {
            DomesticRomGuide.markPendingFromBackground(context)
        }
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
