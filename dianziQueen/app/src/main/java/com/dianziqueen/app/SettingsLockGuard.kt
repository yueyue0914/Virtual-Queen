package com.dianziqueen.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * 最强控制：权限已全部就绪且已上交后，拦截进入系统设置（含设备管理员/应用详情等）。
 * 若 [QueenPrivilegeAuditor] 检测到仍有缺失权限，则不拦截，便于用户去系统里补开。
 */
object SettingsLockGuard {

    private const val TAG = "SettingsLockGuard"
    /** 同一设置页内 CONTENT_CHANGED 洪泛去重（不影响用户再次点开设置）。 */
    private const val CONTENT_FLOOD_DEBOUNCE_MS = 500L
    /** Queen 消息写入间隔，避免连点刷屏。 */
    private const val MESSAGE_DEBOUNCE_MS = 8_000L
    const val DISABLE_PASSWORD = "DianZiQueen2026"

    private val handler = Handler(Looper.getMainLooper())
    private var lastContentBlockMs = 0L
    private var lastMessageMs = 0L
    private var pendingOverlayRunnable: Runnable? = null

    fun isStrongControlEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return false
        if (prefs.getBoolean(Prefs.STRONG_CONTROL_USER_OPT_OUT, false)) return false
        return prefs.getBoolean(Prefs.STRONG_CONTROL_ENABLED, true)
    }

    /** 激活完成时调用：仅当用户从未手动关闭时才默认开启拦截。 */
    fun ensureStrongControlOnActivation(context: Context) {
        if (isUserOptedOut(context)) return
        enableStrongControl(context)
        UninstallGuard.enableProtection(context)
    }

    fun enableStrongControl(context: Context) {
        if (isUserOptedOut(context)) return
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Prefs.STRONG_CONTROL_ENABLED, true)
            .apply()
    }

    fun disableStrongControl(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Prefs.STRONG_CONTROL_ENABLED, false)
            .putBoolean(Prefs.STRONG_CONTROL_USER_OPT_OUT, true)
            .apply()
        releaseAllInterceptBlocks(context)
    }

    /** 关闭最强控制时：卸载/关机/PIN 门/设置警告等全部解除。 */
    fun releaseAllInterceptBlocks(context: Context) {
        val app = context.applicationContext
        UninstallGuard.disableProtection(app)
        AdminDisablePinSession.revoke(app)
        pendingOverlayRunnable?.let { handler.removeCallbacks(it) }
        pendingOverlayRunnable = null
        SettingsBlockOverlay.hide()
        Log.i(TAG, "all intercept blocks released")
    }

    private fun isUserOptedOut(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.STRONG_CONTROL_USER_OPT_OUT, false)

    fun verifyDisablePassword(input: String): Boolean =
        input.trim() == DISABLE_PASSWORD

    fun shouldBlockSystemSettings(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return false
        if (prefs.getBoolean(Prefs.STRONG_CONTROL_USER_OPT_OUT, false)) return false
        if (!prefs.getBoolean(Prefs.STRONG_CONTROL_ENABLED, true)) return false
        if (!QueenPrivilegeAuditor.isAllCriticalOk(app)) return false
        return true
    }

    fun isBlockedExternalWindow(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val self = context.packageName
        if (packageName.equals(self, ignoreCase = true)) return false
        val p = packageName.lowercase()
        if (isAllowedEverydayApp(p)) return false
        return isBlockedSettingsPackage(p)
    }

    /** 日历、时钟、联系人等日常 App 不应被「禁止进设置」误伤。 */
    private fun isAllowedEverydayApp(p: String): Boolean {
        if (p.contains("calendar")) return true
        if (p.contains("deskclock") || p.endsWith(".clock")) return true
        if (p.contains(".contacts") && !p.contains("settings")) return true
        if (p.contains(".dialer") || p == "com.android.phone") return true
        if (p.contains(".mms") || p.contains(".messaging")) return true
        if (p.contains("com.android.calculator")) return true
        return false
    }

    /** 仅拦截系统/厂商设置与权限中心，不用宽泛的 contains("settings")。 */
    private fun isBlockedSettingsPackage(p: String): Boolean {
        if (p == "com.android.settings" || p.startsWith("com.android.settings.")) return true
        if (p == "com.android.permissioncontroller") return true
        if (p.contains("packageinstaller")) return true
        if (p == "com.xiaomi.misettings" || p.startsWith("com.miui.securitycenter")) return true
        if (p.contains("securitycenter") && p.contains("miui")) return true
        if (p.contains("systemmanager") && (p.contains("huawei") || p.contains("honor"))) return true
        if (p.contains("coloros") && (p.contains("safe") || p.contains("perm") || p.contains("secure"))) {
            return true
        }
        if (p.contains("oplus") && (p.contains("safe") || p.contains("perm"))) return true
        if (p.contains("vivo") && p.contains("permission")) return true
        if (p.endsWith(".settings") || p.endsWith(".misettings")) return true
        if (p.contains(".settings.") && !p.contains("calendar")) return true
        if (p.contains("safecenter")) return true
        if (p.contains("permissionmanager")) return true
        if (p.contains("systemmanager")) return true
        if (p.contains("huawei.systemmanager")) return true
        if (p.contains("miui") && (p.contains("security") || p.contains("perm"))) return true
        return false
    }

    /**
     * 拦截流程：返回 → 悬浮窗全屏警告。
     *
     * @param fromWindowStateChange 用户新开设置页（WINDOW_STATE_CHANGED）时必拦截；
     *        同页 CONTENT_CHANGED 仅短防抖，避免无障碍事件洪泛。
     */
    fun onSystemSettingsEntered(
        context: Context,
        source: String,
        fromWindowStateChange: Boolean,
    ) {
        if (!shouldBlockSystemSettings(context)) return

        val now = SystemClock.elapsedRealtime()
        if (!fromWindowStateChange) {
            if (now - lastContentBlockMs < CONTENT_FLOOD_DEBOUNCE_MS) return
        }
        lastContentBlockMs = now
        Log.w(TAG, "blocked system settings entry: $source (state=$fromWindowStateChange)")

        val app = context.applicationContext
        QueenVibratorHelper.punish(app)
        QueenAccessibilityService.performBackGlobally()

        val title = app.getString(R.string.settings_block_threat_title)
        val body = QueenHonorific.apply(app, app.getString(R.string.settings_block_threat_body))

        pendingOverlayRunnable?.let { handler.removeCallbacks(it) }
        val overlayTask = Runnable {
            if (SettingsBlockOverlay.isVisible()) {
                SettingsBlockOverlay.refreshAutoHide(10_000L)
            } else if (!SettingsBlockOverlay.show(app, title, body, autoFinishMs = 10_000L)) {
                Log.w(TAG, "SettingsBlockOverlay.show failed (no overlay permission?)")
            }
        }
        pendingOverlayRunnable = overlayTask
        handler.postDelayed(overlayTask, 150L)

        if (now - lastMessageMs >= MESSAGE_DEBOUNCE_MS) {
            lastMessageMs = now
            handler.postDelayed({
                QueenMessageStore.appendQueenMessage(
                    app,
                    "贱奴，又想进设置动 Queen 的权限？滚回桌面跪着。",
                )
            }, 600L)
        }
    }

    fun onDeviceAdminDisabled(context: Context) {
        if (!isStrongControlEnabled(context)) return
        UninstallGuard.onUninstallAttempt(context.applicationContext, "device_admin")
    }
}
