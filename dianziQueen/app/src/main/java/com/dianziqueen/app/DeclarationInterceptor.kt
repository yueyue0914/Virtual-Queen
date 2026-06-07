package com.dianziqueen.app

import android.content.Context
import android.os.SystemClock

/**
 * 宣言拦截物理开关：仅在有未完成宣誓时允许全局「拉回」；
 * 通关后立即关闭并进入豁免期，避免异步任务与 finish 竞态。
 */
object DeclarationInterceptor {

    private const val SUCCESS_EXEMPT_MS = 5_000L

    /** 为 true 时，Service / 无障碍 / NLS 等才允许执行宣誓页拉回。 */
    @Volatile
    private var isInterceptActive = false

    @Volatile
    private var exemptionEndTime = 0L

    fun startChallenge() {
        isInterceptActive = true
        exemptionEndTime = 0L
        DeclarationHardBlockHelper.resetChallengeSession()
    }

    fun finishChallengeSuccess() {
        isInterceptActive = false
        exemptionEndTime = SystemClock.elapsedRealtime() + SUCCESS_EXEMPT_MS
    }

    fun reset() {
        isInterceptActive = false
        exemptionEndTime = 0L
    }

    fun isActive(): Boolean = isInterceptActive

    /** 拦截已关闭（含通关后豁免期）：DPM 约束与全局拉回均应停止。 */
    fun isChallengePassed(): Boolean = !isInterceptActive

    /** 当前是否应执行「逃课拉回」类拦截。 */
    fun shouldReassertBlocking(context: Context): Boolean {
        if (!isInterceptActive) return false
        if (SystemClock.elapsedRealtime() < exemptionEndTime) return false
        if (!DeclarationScheduler.isActivated(context)) return false
        return DeclarationScheduler.shouldBlockUsage(context)
    }
}
