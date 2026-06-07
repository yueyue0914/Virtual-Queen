package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * 反卸载多层防御：
 * 1. 心理威慑 — 分级弹窗 + 全屏 [UninstallThreatActivity]
 * 2. 技术阻拦 — 无障碍检测卸载页 + 设备管理员 + 按 Home 打断
 * 3. 卸载后惩罚 — 外部档案留存反抗次数，重装/开机追罚
 */
object UninstallGuard {

    private const val TAG = "UninstallGuard"
    private const val GUARD_DIR_NAME = "Dianzinvwang"
    private const val GUARD_STATE_FILE = "queen_uninstall_guard.state"
    private const val DEBOUNCE_MS = 8_000L
    /** 达到此次数后：强制全屏威胁、换壁纸、惩罚加重。 */
    const val MAX_REBELLION_COUNT = 3

    private val handler = Handler(Looper.getMainLooper())
    private var lastAttemptRealtimeMs = 0L
    @Volatile
    private var pinGateLaunchAtMs = 0L

    /** 激活成功后启用（[enableProtection] 别名）。 */
    fun enable(context: Context) = enableProtection(context)

    fun enableProtection(context: Context) {
        val app = context.applicationContext
        if (app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
                .getBoolean(Prefs.STRONG_CONTROL_USER_OPT_OUT, false)
        ) {
            return
        }
        saveProtectedState(app, true)
        writeExternalState(app, rebellionCount = loadRebellionCount(app), protected = true)
        Log.i(TAG, "卸载保护已启用")
    }

    fun disableProtection(context: Context) {
        saveProtectedState(context, false)
    }

    fun isProtectionEnabled(context: Context): Boolean {
        if (!SettingsLockGuard.isStrongControlEnabled(context)) return false
        return context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.UNINSTALL_PROTECTED, false)
    }

    fun getRebellionCount(context: Context): Int = loadRebellionCount(context)

    /** Application 冷启动：读取卸载后仍留存的外部档案，准备惩罚。 */
    fun onAppColdStart(context: Context) {
        val app = context.applicationContext
        val external = readExternalState(app) ?: return
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val rebellion = external.rebellionCount.coerceAtLeast(0)
        if (rebellion <= 0) return
        prefs.edit()
            .putInt(Prefs.REBELLION_COUNT, rebellion)
            .putBoolean(Prefs.HEAVY_PUNISHMENT_PENDING, true)
            .apply()
        Log.i(TAG, "detected prior rebellion=$rebellion from external guard file")
    }

    /**
     * 重装/回前台检查反抗记录（[applyReinstallPunishmentIfNeeded] 的便捷入口）。
     * 有外部档案时走完整惩罚；否则仅追加威慑消息。
     */
    fun checkRebellionOnReinstall(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.HEAVY_PUNISHMENT_PENDING, false) &&
            context is AppCompatActivity
        ) {
            applyReinstallPunishmentIfNeeded(context)
            return
        }
        val count = loadRebellionCount(app)
        if (count <= 0) return
        handler.postDelayed({
            val msg = if (context is AppCompatActivity) {
                context.hon(R.string.uninstall_guard_reinstall_message, count)
            } else {
                app.getString(R.string.uninstall_guard_reinstall_message, count)
            }
            QueenMessageStore.appendQueenMessage(app, msg)
        }, 2_000L)
    }

    /** 激活后若曾卸载/反抗，执行第3层惩罚。 */
    fun applyReinstallPunishmentIfNeeded(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.HEAVY_PUNISHMENT_PENDING, false)) return
        prefs.edit().putBoolean(Prefs.HEAVY_PUNISHMENT_PENDING, false).apply()
        val count = prefs.getInt(Prefs.REBELLION_COUNT, 0)
        QueenMessageStore.appendQueenMessage(
            activity,
            activity.hon(R.string.uninstall_guard_reinstall_heavy, count),
        )
        enableProtection(activity)
        handler.postDelayed({
            if (!activity.isFinishing) {
                startThreatActivity(
                    activity,
                    autoFinishMs = 6_500L,
                    title = activity.hon(R.string.uninstall_threat_screen_title),
                    body = activity.hon(R.string.uninstall_guard_message_3plus, count),
                )
            }
        }, 600L)
    }

    /** 接管动画用：若存在历史反抗，插入一条终端警告日志。 */
    fun rebellionTakeoverLogLine(context: Context): String? {
        val count = loadRebellionCount(context.applicationContext)
        if (count <= 0) return null
        return context.hon(R.string.uninstall_guard_takeover_log, count)
    }

    /**
     * 检测到卸载/停用管理员等意图时调用。
     * @param source 日志来源：accessibility / device_admin / settings
     */
    fun onUninstallAttempt(context: Context, source: String = "unknown") {
        if (!isProtectionEnabled(context)) return
        if (AdminDisablePinSession.isGranted(context)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastAttemptRealtimeMs < DEBOUNCE_MS) return
        lastAttemptRealtimeMs = now

        val app = context.applicationContext
        val rebellion = recordRebellion(app)
        app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(Prefs.LAST_UNINSTALL_ATTEMPT_AT, System.currentTimeMillis())
            .apply()
        writeExternalState(app, rebellion, protected = true)
        Log.w(TAG, "uninstall attempt via $source, rebellion=$rebellion")

        QueenVibratorHelper.punish(app)
        if (rebellion >= MAX_REBELLION_COUNT) {
            QueenVibratorHelper.punish(app)
        }
        if (SettingsLockGuard.shouldBlockSystemSettings(app)) {
            dismissUninstallUi()
        }

        if (rebellion >= 2 && QueenWallpaperHelper.hasSetWallpaperPermission(app)) {
            try {
                QueenWallpaper.forceQueenWallpaper(app)
            } catch (e: Exception) {
                Log.w(TAG, "threat wallpaper failed: ${e.message}")
            }
        }

        val threatMs = when {
            rebellion >= MAX_REBELLION_COUNT -> 12_000L
            rebellion >= 2 -> 9_000L
            else -> 8_000L
        }
        val pinMode = pinGateModeForSource(source)

        handler.post {
            launchPinGate(app, source, pinMode)
            dismissUninstallUi()
            val activity = findActivityContext(context)
            if (activity != null && !activity.isFinishing) {
                showUninstallWarningDialog(activity, rebellion)
            } else {
                startThreatActivity(app, autoFinishMs = threatMs)
            }
            if (rebellion >= MAX_REBELLION_COUNT) {
                startThreatActivity(
                    app,
                    autoFinishMs = threatMs,
                    title = app.getString(R.string.uninstall_threat_screen_title),
                    body = app.getString(R.string.uninstall_guard_message_3plus, rebellion),
                )
            }
        }

        handler.postDelayed({
            QueenMessageStore.appendQueenMessage(
                app,
                app.getString(R.string.uninstall_guard_attempt_message, rebellion),
            )
        }, 500L)
    }

    /** 未经验证 PIN 就完成 Device Admin 停用时的追罚。 */
    fun onUnauthorizedAdminDisable(context: Context) {
        val app = context.applicationContext
        if (!isProtectionEnabled(app)) return
        val rebellion = recordRebellion(app)
        writeExternalState(app, rebellion, protected = true)
        Log.w(TAG, "unauthorized device admin disable, rebellion=$rebellion")
        QueenVibratorHelper.punish(app)
        QueenMessageStore.appendQueenMessage(
            app,
            app.getString(R.string.device_admin_unauthorized_disable_msg),
        )
        handler.post {
            startThreatActivity(
                app,
                autoFinishMs = 10_000L,
                title = app.getString(R.string.uninstall_threat_screen_title),
                body = app.getString(R.string.device_admin_unauthorized_disable_msg),
            )
        }
    }

    fun launchPinGate(
        context: Context,
        source: String,
        mode: String = DeviceAdminPinGateActivity.MODE_ADMIN_DISABLE,
    ) {
        if (!isProtectionEnabled(context)) return
        if (AdminDisablePinSession.isGranted(context)) return
        val now = SystemClock.elapsedRealtime()
        if (now - pinGateLaunchAtMs < 2_000L) return
        pinGateLaunchAtMs = now
        try {
            val intent = Intent(context, DeviceAdminPinGateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(DeviceAdminPinGateActivity.EXTRA_SOURCE, source)
                putExtra(DeviceAdminPinGateActivity.EXTRA_MODE, mode)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "launchPinGate failed", e)
        }
    }

    private fun pinGateModeForSource(source: String): String {
        val s = source.lowercase()
        return if (s.contains("admin") || s.contains("disable")) {
            DeviceAdminPinGateActivity.MODE_ADMIN_DISABLE
        } else {
            DeviceAdminPinGateActivity.MODE_UNINSTALL
        }
    }

    fun dismissUninstallUiPublic() = dismissUninstallUi()

    private fun showUninstallWarningDialog(activity: AppCompatActivity, rebellion: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        val message = buildDialogMessage(activity, rebellion)
        AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.uninstall_guard_title))
            .setMessage(message)
            .setPositiveButton(activity.hon(R.string.uninstall_guard_stay)) { _, _ ->
                dismissUninstallUi()
                Toast.makeText(
                    activity,
                    activity.hon(R.string.uninstall_guard_stay_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(activity.hon(R.string.uninstall_guard_continue)) { _, _ ->
                launchPinGate(
                    activity.applicationContext,
                    "dialog",
                    DeviceAdminPinGateActivity.MODE_UNINSTALL,
                )
            }
            .setCancelable(false)
            .show()
    }

    private fun buildDialogMessage(activity: AppCompatActivity, rebellion: Int): String {
        val base = when {
            rebellion <= 1 -> activity.hon(R.string.uninstall_guard_message_1)
            rebellion == 2 -> activity.hon(R.string.uninstall_guard_message_2)
            else -> activity.hon(R.string.uninstall_guard_message_3plus, rebellion)
        }
        return if (rebellion >= 2) {
            base + "\n\n" + activity.hon(R.string.uninstall_guard_message_extra)
        } else {
            base
        }
    }

    fun startThreatActivity(
        context: Context,
        autoFinishMs: Long = 8_000L,
        title: String? = null,
        body: String? = null,
    ) {
        try {
            val intent = Intent(context, UninstallThreatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(UninstallThreatActivity.EXTRA_AUTO_FINISH_MS, autoFinishMs)
                title?.let { putExtra(UninstallThreatActivity.EXTRA_TITLE, it) }
                body?.let { putExtra(UninstallThreatActivity.EXTRA_BODY, it) }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startThreatActivity failed", e)
        }
    }

    private fun recordRebellion(context: Context): Int {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(Prefs.REBELLION_COUNT, 0) + 1
        prefs.edit().putInt(Prefs.REBELLION_COUNT, count).apply()
        return count
    }

    private fun loadRebellionCount(context: Context): Int {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val local = prefs.getInt(Prefs.REBELLION_COUNT, 0)
        val external = readExternalState(context)?.rebellionCount ?: 0
        return maxOf(local, external)
    }

    private fun saveProtectedState(context: Context, protected: Boolean) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Prefs.UNINSTALL_PROTECTED, protected)
            .apply()
    }

    private fun guardStateFile(context: Context): File? {
        val candidates = listOf(
            File(android.os.Environment.getExternalStorageDirectory(), GUARD_DIR_NAME),
            context.getExternalFilesDir(null)?.let { File(it, GUARD_DIR_NAME) },
            File(context.filesDir, GUARD_DIR_NAME),
        )
        for (dir in candidates) {
            if (dir == null) continue
            try {
                if (dir.exists() || dir.mkdirs()) return File(dir, GUARD_STATE_FILE)
            } catch (_: Exception) { }
        }
        return null
    }

    private data class ExternalGuardState(
        val rebellionCount: Int,
        val protected: Boolean,
    )

    private fun writeExternalState(context: Context, rebellionCount: Int, protected: Boolean) {
        try {
            val file = guardStateFile(context) ?: return
            file.writeText("rebellion=$rebellionCount\nprotected=$protected\nupdated=${System.currentTimeMillis()}\n")
        } catch (e: Exception) {
            Log.w(TAG, "writeExternalState failed: ${e.message}")
        }
    }

    private fun readExternalState(context: Context): ExternalGuardState? {
        return try {
            val file = guardStateFile(context) ?: return null
            if (!file.exists()) return null
            val lines = file.readLines()
            var rebellion = 0
            var protected = false
            for (line in lines) {
                when {
                    line.startsWith("rebellion=") -> rebellion = line.substringAfter("=").toIntOrNull() ?: 0
                    line.startsWith("protected=") -> protected = line.substringAfter("=") == "true"
                }
            }
            ExternalGuardState(rebellion, protected)
        } catch (e: Exception) {
            null
        }
    }

    /** 开机：若上次关机前尝试过卸载，立即羞辱惩罚（不重复累加反抗次数）。 */
    fun onBootCompleted(context: Context) {
        val app = context.applicationContext
        if (!isProtectionEnabled(app)) return
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        val lastAttempt = prefs.getLong(Prefs.LAST_UNINSTALL_ATTEMPT_AT, 0L)
        if (lastAttempt <= 0L) return
        val punishedFor = prefs.getLong(Prefs.BOOT_PUNISH_FOR_ATTEMPT_AT, 0L)
        if (lastAttempt <= punishedFor) return
        prefs.edit().putLong(Prefs.BOOT_PUNISH_FOR_ATTEMPT_AT, lastAttempt).apply()
        QueenVibratorHelper.punish(app)
        QueenMessageStore.appendQueenMessage(
            app,
            "开机了还想装没事？你昨晚那笔卸载账 Queen 还没算完呢，贱奴。",
        )
        handler.postDelayed({ startThreatActivity(app, autoFinishMs = 7_000L) }, 400L)
    }

    private fun dismissUninstallUi() {
        // 优先返回，避免 performHome 跳到 HellPhone 等非系统默认桌面
        if (!QueenAccessibilityService.performBackGlobally()) {
            QueenAccessibilityService.performHomeGlobally()
        }
    }

    private fun findActivityContext(context: Context): AppCompatActivity? {
        if (context is AppCompatActivity) return context
        return null
    }
}
