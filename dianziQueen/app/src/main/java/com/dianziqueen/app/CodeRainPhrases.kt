package com.dianziqueen.app

import android.content.Context

/**
 * 主界面瀑布字文案：激活前 / 激活后两套，跟随系统语言（values / values-en / values-ja）。
 */
internal object CodeRainPhrases {

    fun lines(context: Context, activated: Boolean): List<String> {
        val arrayId = if (activated) {
            R.array.code_rain_after
        } else {
            R.array.code_rain_before
        }
        return context.resources.getStringArray(arrayId)
            .map { QueenHonorific.apply(context, it) }
    }
}
