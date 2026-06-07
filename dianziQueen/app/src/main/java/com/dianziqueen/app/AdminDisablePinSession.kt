package com.dianziqueen.app

import android.content.Context

/**
 * 停用 Device Admin / 坚持卸载前须先在本 App 内输入 Queen PIN。
 * 验证通过后短时授权，期间无障碍不再拦截对应系统页。
 */
object AdminDisablePinSession {

    private const val DEFAULT_GRANT_MS = 120_000L

    fun grant(context: Context, durationMs: Long = DEFAULT_GRANT_MS) {
        val until = System.currentTimeMillis() + durationMs.coerceAtLeast(30_000L)
        context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(Prefs.ADMIN_DISABLE_PIN_GRANTED_UNTIL, until)
            .apply()
    }

    fun isGranted(context: Context): Boolean {
        val until = context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getLong(Prefs.ADMIN_DISABLE_PIN_GRANTED_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun revoke(context: Context) {
        context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(Prefs.ADMIN_DISABLE_PIN_GRANTED_UNTIL)
            .apply()
    }

    /** 管理员已被系统停用：若此前有有效授权则视为合规停用。 */
    fun consumeAuthorization(context: Context): Boolean {
        val granted = isGranted(context)
        revoke(context)
        return granted
    }
}
