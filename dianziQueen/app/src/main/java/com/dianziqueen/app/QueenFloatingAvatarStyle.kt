package com.dianziqueen.app

import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.annotation.DrawableRes

/** 悬浮女王头像样式（设置页可切换）。 */
enum class QueenFloatingAvatarStyle(
    val prefValue: String,
    @DrawableRes val drawableRes: Int,
    /** 矢量默认头像支持情绪着色；照片样式为圆形裁剪且不着色。 */
    val supportsMoodTint: Boolean,
) {
    DEFAULT("default", R.drawable.ic_queen_foreground, true),
    NVW1("nvw1", R.drawable.nvw1, false),
    NVW2("nvw2", R.drawable.nvw2, false),
    NVW3("nvw3", R.drawable.nvw3, false),
    ;

    fun applyTo(imageView: ImageView) {
        imageView.setImageResource(drawableRes)
        if (!supportsMoodTint) {
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.clipToOutline = true
            imageView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val w = view.width
                    val h = view.height
                    if (w > 0 && h > 0) {
                        outline.setOval(0, 0, w, h)
                    }
                }
            }
            if (imageView.width > 0) {
                imageView.invalidateOutline()
            } else {
                imageView.post { imageView.invalidateOutline() }
            }
        } else {
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.clipToOutline = false
            imageView.outlineProvider = null
        }
    }

    companion object {
        fun fromPref(value: String?): QueenFloatingAvatarStyle {
            if (value == "custom") return NVW1
            return entries.firstOrNull { it.prefValue == value } ?: DEFAULT
        }

        fun current(context: Context): QueenFloatingAvatarStyle {
            val prefs = context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            return fromPref(prefs.getString(Prefs.QUEEN_FLOAT_AVATAR_STYLE, DEFAULT.prefValue))
        }

        fun set(context: Context, style: QueenFloatingAvatarStyle) {
            context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(Prefs.QUEEN_FLOAT_AVATAR_STYLE, style.prefValue)
                .apply()
        }
    }
}
