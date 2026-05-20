package com.dianziqueen.app

import android.graphics.PorterDuff
import android.widget.ImageView

/** 悬浮女王当前表情/语气，用于头像着色与台词库。 */
enum class QueenFloatingMood(
    val tintArgb: Int,
    val avatarAlpha: Float,
) {
    ANGRY(0xFFFF1744.toInt(), 0.88f),
    COLD_SMILE(0xFF00E5FF.toInt(), 0.78f),
    EXCITED(0xFFE040FB.toInt(), 0.92f),
    MOCKING(0xFFFF5252.toInt(), 0.80f),
    COMMAND(0xFFB71C1C.toInt(), 0.90f),
    ;

    fun applyAvatar(imageView: ImageView, style: QueenFloatingAvatarStyle = QueenFloatingAvatarStyle.DEFAULT) {
        imageView.alpha = if (style.supportsMoodTint) avatarAlpha else (avatarAlpha * 0.96f).coerceIn(0.82f, 1f)
        if (style.supportsMoodTint) {
            imageView.setColorFilter(tintArgb, PorterDuff.Mode.SRC_ATOP)
        } else {
            imageView.clearColorFilter()
        }
    }

    companion object {
        fun random(): QueenFloatingMood = entries.random()
    }
}
