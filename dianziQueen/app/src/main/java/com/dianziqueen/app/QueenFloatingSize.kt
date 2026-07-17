package com.dianziqueen.app

import android.content.Context

/** 悬浮女王窗口尺寸（设置页可调，默认 36dp 头像边长）。 */
object QueenFloatingSize {

    const val DEFAULT_AVATAR_DP = 36f
    const val MIN_AVATAR_DP = 24f
    const val MAX_AVATAR_DP = 72f

    private const val BUBBLE_WIDTH_DP = 110f
    private const val MENU_WIDTH_DP = 100f
    private const val BUBBLE_TEXT_SP = 11f
    private const val GAP_DP = 3f
    private const val MIN_WINDOW_DP = 40f

    fun avatarDp(context: Context): Float {
        val prefs = context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(Prefs.QUEEN_FLOAT_AVATAR_SIZE_DP, DEFAULT_AVATAR_DP.toInt())
        return stored.toFloat().coerceIn(MIN_AVATAR_DP, MAX_AVATAR_DP)
    }

    fun setAvatarDp(context: Context, dp: Float) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                Prefs.QUEEN_FLOAT_AVATAR_SIZE_DP,
                dp.toInt().coerceIn(MIN_AVATAR_DP.toInt(), MAX_AVATAR_DP.toInt()),
            )
            .apply()
    }

    fun seekBarProgress(context: Context): Int =
        (avatarDp(context) - MIN_AVATAR_DP).toInt().coerceAtLeast(0)

    fun dpFromSeekBarProgress(progress: Int): Float =
        (MIN_AVATAR_DP + progress).coerceIn(MIN_AVATAR_DP, MAX_AVATAR_DP)

    /** 气泡宽度固定，不随头像大小设置变化。 */
    fun bubbleWidthDp(): Float = BUBBLE_WIDTH_DP

    fun menuWidthDp(): Float = MENU_WIDTH_DP

    /** 气泡字号固定，不随头像大小设置变化。 */
    fun bubbleTextSp(): Float = BUBBLE_TEXT_SP

    fun gapDp(context: Context): Float = GAP_DP

    fun minWindowDp(context: Context): Float = MIN_WINDOW_DP
}
