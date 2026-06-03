package com.dianziqueen.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager

object DeclarationEnforcement {

    /** 逃离宣誓页后再次拉回的间隔（毫秒）。 */
    const val REASSERT_DELAY_MS = 500L

    /** 宣誓页是否在前台（仅此时不重复 startActivity）。 */
    @Volatile
    var challengeInForeground: Boolean = false

    /** 已通过宣誓、正在关闭页面：禁止再拉回。 */
    @Volatile
    private var passCompleted: Boolean = false

    fun notifyChallengePassed() {
        passCompleted = true
        challengeInForeground = false
    }

    fun notifyChallengeShown() {
        passCompleted = false
    }

    fun shouldReassertBlocking(context: Context): Boolean {
        if (passCompleted) return false
        return DeclarationScheduler.shouldBlockUsage(context)
    }

    fun launchIfNeeded(context: Context) {
        if (!DeclarationScheduler.isEnabled(context)) return
        if (DailySelfieEnforcement.isBlockingAppUsage(context)) return
        if (challengeInForeground) return
        if (!DeclarationScheduler.isDue(context) && !DeclarationScheduler.isPending(context)) return
        if (!isScreenUsable(context)) return
        notifyChallengeShown()
        DeclarationScheduler.markChallengeShown(context)
        context.applicationContext.startActivity(challengeIntent(context))
    }

    /** 未完成宣誓时强制拉回全屏页（Home/返回/切 App 后调用）。 */
    fun bringToFront(context: Context) {
        if (!shouldReassertBlocking(context)) return
        if (DailySelfieEnforcement.externalFlowInProgress()) return
        if (challengeInForeground) return
        context.applicationContext.startActivity(challengeIntent(context))
    }

    fun isScreenUsable(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        if (!pm.isInteractive) return false
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return true
        return !km.isKeyguardLocked
    }

    private fun challengeIntent(context: Context): Intent =
        Intent(context.applicationContext, DeclarationChallengeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra(
                DeclarationScheduler.EXTRA_REQUIRED_DECLARATION,
                DeclarationScheduler.currentDeclarationText(context),
            )
        }
}
