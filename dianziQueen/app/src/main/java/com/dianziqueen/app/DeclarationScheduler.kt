package com.dianziqueen.app

import android.content.Context
import kotlin.random.Random

/**
 * 随机/固定间隔的宣言验证调度：到达时间后须输入正确宣言才能继续使用手机。
 */
object DeclarationScheduler {

    const val MODE_RANDOM = "random"
    const val MODE_FIXED = "fixed"

    const val DEFAULT_RANDOM_MIN_MINUTES = 1
    const val DEFAULT_RANDOM_MAX_MINUTES = 5
    const val DEFAULT_FIXED_MINUTES = 3

    const val EXTRA_REQUIRED_DECLARATION = "required_declaration"

    /** 内存态优先，避免 [apply] 未完成时又被 [DeclarationEnforcement] 拉回宣誓页。 */
    @Volatile
    private var pendingInMemory: Boolean? = null

    fun isActivated(context: Context): Boolean =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.ACTIVATED, false)

    fun isEnabled(context: Context): Boolean {
        if (!isActivated(context)) return false
        return prefs(context).getBoolean(Prefs.DECLARATION_ENABLED, true)
    }

    fun isDue(context: Context): Boolean {
        if (!isEnabled(context)) return false
        val nextAt = prefs(context).getLong(Prefs.DECLARATION_NEXT_AT, 0L)
        return nextAt > 0L && System.currentTimeMillis() >= nextAt
    }

    fun isPending(context: Context): Boolean {
        pendingInMemory?.let { return it }
        return prefs(context).getBoolean(Prefs.DECLARATION_CHALLENGE_PENDING, false)
    }

    fun shouldBlockUsage(context: Context): Boolean =
        isEnabled(context) && isPending(context)

    fun currentDeclarationText(context: Context): String {
        val pending = prefs(context).getString(Prefs.DECLARATION_CURRENT_TEXT, null)
        if (!pending.isNullOrBlank()) return pending
        return DeclarationTemplateLibrary.pickRandom(context)
    }

    /** @deprecated 使用 [currentDeclarationText] */
    fun requiredDeclarationText(context: Context): String = currentDeclarationText(context)

    fun previewDeclarationSample(context: Context): String =
        DeclarationTemplateLibrary.pickPreviewSample(context)

    fun ensureScheduleInitialized(context: Context) {
        if (!isActivated(context)) return
        val p = prefs(context)
        if (p.getLong(Prefs.DECLARATION_NEXT_AT, 0L) <= 0L) {
            scheduleNext(context)
        }
    }

    fun scheduleNext(context: Context, fromMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(Prefs.DECLARATION_NEXT_AT, fromMillis + computeNextIntervalMs(context))
            .apply()
    }

    fun markChallengeShown(context: Context) {
        val text = DeclarationTemplateLibrary.pickRandom(context)
        pendingInMemory = true
        prefs(context).edit()
            .putBoolean(Prefs.DECLARATION_CHALLENGE_PENDING, true)
            .putString(Prefs.DECLARATION_CURRENT_TEXT, text)
            .apply()
    }

    fun markChallengePassed(context: Context) {
        DeclarationEnforcement.notifyChallengePassed()
        pendingInMemory = false
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(Prefs.DECLARATION_LAST_PASSED_AT, now)
            .putBoolean(Prefs.DECLARATION_CHALLENGE_PENDING, false)
            .remove(Prefs.DECLARATION_CURRENT_TEXT)
            .commit()
        scheduleNext(context, now)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val editor = prefs(context).edit().putBoolean(Prefs.DECLARATION_ENABLED, enabled)
        if (enabled && isActivated(context)) {
            editor.putLong(Prefs.DECLARATION_NEXT_AT, 0L)
        } else {
            editor.putBoolean(Prefs.DECLARATION_CHALLENGE_PENDING, false)
            editor.remove(Prefs.DECLARATION_CURRENT_TEXT)
        }
        editor.apply()
        if (enabled && isActivated(context)) {
            scheduleNext(context)
        }
    }

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString(Prefs.DECLARATION_MODE, mode).apply()
        rescheduleFromNow(context)
    }

    fun setRandomRange(context: Context, minMinutes: Int, maxMinutes: Int) {
        val min = minMinutes.coerceAtLeast(1)
        val max = maxMinutes.coerceAtLeast(min)
        prefs(context).edit()
            .putInt(Prefs.DECLARATION_RANDOM_MIN_MINUTES, min)
            .putInt(Prefs.DECLARATION_RANDOM_MAX_MINUTES, max)
            .apply()
        if (currentMode(context) == MODE_RANDOM) {
            rescheduleFromNow(context)
        }
    }

    fun setFixedMinutes(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(Prefs.DECLARATION_FIXED_MINUTES, minutes.coerceAtLeast(1))
            .apply()
        if (currentMode(context) == MODE_FIXED) {
            rescheduleFromNow(context)
        }
    }

    fun currentMode(context: Context): String =
        prefs(context).getString(Prefs.DECLARATION_MODE, MODE_RANDOM) ?: MODE_RANDOM

    fun randomMinMinutes(context: Context): Int =
        prefs(context).getInt(Prefs.DECLARATION_RANDOM_MIN_MINUTES, DEFAULT_RANDOM_MIN_MINUTES)

    fun randomMaxMinutes(context: Context): Int =
        prefs(context).getInt(Prefs.DECLARATION_RANDOM_MAX_MINUTES, DEFAULT_RANDOM_MAX_MINUTES)

    fun fixedMinutes(context: Context): Int =
        prefs(context).getInt(Prefs.DECLARATION_FIXED_MINUTES, DEFAULT_FIXED_MINUTES)

    fun nextChallengeAt(context: Context): Long =
        prefs(context).getLong(Prefs.DECLARATION_NEXT_AT, 0L)

    fun clearOnRelease(context: Context) {
        pendingInMemory = false
        prefs(context).edit()
            .remove(Prefs.DECLARATION_NEXT_AT)
            .remove(Prefs.DECLARATION_LAST_PASSED_AT)
            .putBoolean(Prefs.DECLARATION_CHALLENGE_PENDING, false)
            .remove(Prefs.DECLARATION_CURRENT_TEXT)
            .apply()
    }

    private fun rescheduleFromNow(context: Context) {
        if (!isEnabled(context)) return
        scheduleNext(context)
    }

    private fun computeNextIntervalMs(context: Context): Long {
        return if (currentMode(context) == MODE_FIXED) {
            fixedMinutes(context).coerceAtLeast(1) * 60_000L
        } else {
            val min = randomMinMinutes(context).coerceAtLeast(1)
            val max = randomMaxMinutes(context).coerceAtLeast(min)
            Random.nextLong(min * 60_000L, (max + 1) * 60_000L)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
}
