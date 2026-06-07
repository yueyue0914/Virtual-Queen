package com.dianziqueen.app

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object QueenPointsStore {

    const val ACTIVATION_BONUS_POINTS = 20
    const val DAILY_OPEN_BONUS_POINTS = 5
    /** 每日凭证：现场拍摄奖励 */
    const val DAILY_SELFIE_CAPTURE_POINTS = 5
    /** 每日凭证：从相册上传仅奖励 1 积分 */
    const val DAILY_SELFIE_UPLOAD_POINTS = 1
    /** 每次完成宣言验证奖励 */
    const val DECLARATION_PASS_POINTS = 2

    private val dayKeyFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun getPoints(context: Context): Int =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getInt(Prefs.QUEEN_POINTS, 0)

    /** 积分足够则扣除并返回 true，否则不扣。 */
    fun trySpend(context: Context, cost: Int): Boolean {
        if (cost <= 0) return false
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(Prefs.QUEEN_POINTS, 0)
        if (current < cost) return false
        prefs.edit().putInt(Prefs.QUEEN_POINTS, current - cost).apply()
        return true
    }

    fun addPoints(context: Context, amount: Int) {
        if (amount == 0) return
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(Prefs.QUEEN_POINTS, 0)
        prefs.edit().putInt(Prefs.QUEEN_POINTS, current + amount).apply()
    }

    /** 首次上交权限并激活后发放默认积分（仅一次）。 */
    fun grantActivationBonusIfNeeded(
        context: Context,
        amount: Int = ACTIVATION_BONUS_POINTS,
    ): Boolean {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.QUEEN_POINTS_ACTIVATION_BONUS, false)) return false
        prefs.edit()
            .putBoolean(Prefs.QUEEN_POINTS_ACTIVATION_BONUS, true)
            .putInt(Prefs.QUEEN_POINTS, amount)
            .apply()
        return true
    }

    /** 已激活用户：每个自然日首次进入 App 时追加积分。 */
    fun grantDailyOpenBonusIfNeeded(
        context: Context,
        amount: Int = DAILY_OPEN_BONUS_POINTS,
    ): Boolean {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return false
        val today = LocalDate.now().format(dayKeyFormatter)
        if (prefs.getString(Prefs.QUEEN_POINTS_DAILY_BONUS_DAY, null) == today) return false
        addPoints(context, amount)
        prefs.edit().putString(Prefs.QUEEN_POINTS_DAILY_BONUS_DAY, today).apply()
        return true
    }
}
