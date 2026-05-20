package com.dianziqueen.app

import android.content.Context
import android.content.Intent

object DailySelfieEnforcement {

    @Volatile
    var demandActivityVisible: Boolean = false

    /** 正在系统相机界面拍照，勿因 onStop 误判为逃跑而打断。 */
    @Volatile
    var cameraCaptureInProgress: Boolean = false

    /** 正在系统相册选取图片，勿因 onStop 误判为逃跑而打断。 */
    @Volatile
    var galleryPickInProgress: Boolean = false

    fun externalFlowInProgress(): Boolean =
        cameraCaptureInProgress || galleryPickInProgress

    fun launch(context: Context) {
        if (!DailySelfieScheduler.shouldEnforce(context)) return
        val app = context.applicationContext
        val intent = demandIntent(app)
        app.startActivity(intent)
    }

    /** 用户按 Home/切换应用后，将强制窗口重新置顶。 */
    fun bringDemandToFront(context: Context) {
        if (!DailySelfieScheduler.shouldEnforce(context)) return
        if (externalFlowInProgress()) return
        val app = context.applicationContext
        app.startActivity(demandIntent(app))
    }

    fun isBlockingAppUsage(context: Context): Boolean =
        DailySelfieScheduler.isActivated(context) &&
            DailySelfieScheduler.shouldEnforce(context)

    private fun demandIntent(app: Context): Intent =
        Intent(app, DailySelfieDemandActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
}
