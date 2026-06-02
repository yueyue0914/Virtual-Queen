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
 * 反卸载三层：心理威慑弹窗/全屏、技术阻拦（设备管理员+无障碍）、卸载后惩罚（外部档案）。
 */
object UninstallGuard {

    private const val TAG = "UninstallGuard"
    private const val GUARD_DIR_NAME = "Dianzinvwang"
    private const val GUARD_STATE_FILE = "queen_uninstall_guard.state"
    private const val DEBOUNCE_MS = 8_000L

    private val handler = Handler(Looper.getMainLooper())
    private var lastAttemptRealtimeMs = 0L

    fun enableProtection(context: Context) {
        saveProtectedState(context, true)
        writeExternalState(context, rebellionCount = loadRebellionCount(context), protected = true)
    }

    fun disableProtection(context: Context) {
        saveProtectedState(context, false)
    }

    fun isProtectionEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.UNINSTALL_PROTECTED, false)
    }

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

    /** 激活后若曾卸载/反抗，执行第3层惩罚。 */
    fun applyReinstallPunishmentIfNeeded(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.HEAVY_PUNISHMENT_PENDING, false)) return
        prefs.edit().putBoolean(Prefs.HEAVY_PUNISHMENT_PENDING, false).apply()
        val count = prefs.getInt(Prefs.REBELLION_COUNT, 0)
        QueenMessageStore.appendQueenMessage(
            activity,
            "你又装回来了？第${count}次反抗我都记着呢。这次惩罚加倍，贱奴。",
        )
        enableProtection(activity)
        handler.postDelayed({
            if (!activity.isFinishing) {
                startThreatActivity(activity, autoFinishMs = 6_500L)
            }
        }, 600L)
    }

    /**
     * 检测到卸载/停用管理员等意图时调用。
     * @param source 日志来源：accessibility / device_admin / settings
     */
    fun onUninstallAttempt(context: Context, source: String = "unknown") {
        if (!isProtectionEnabled(context)) return
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
        if (SettingsLockGuard.shouldBlockSystemSettings(app)) {
            QueenAccessibilityService.performHomeGlobally()
        }

        if (rebellion >= 2 && QueenWallpaperHelper.hasSetWallpaperPermission(app)) {
            try {
                QueenWallpaper.forceQueenWallpaper(app)
            } catch (e: Exception) {
                Log.w(TAG, "threat wallpaper failed: ${e.message}")
            }
        }

        handler.post {
            val activity = findActivityContext(context)
            if (activity != null && !activity.isFinishing) {
                showUninstallWarningDialog(activity, rebellion)
            } else {
                startThreatActivity(app, autoFinishMs = 9_000L)
            }
        }

        handler.postDelayed({
            QueenMessageStore.appendQueenMessage(
                app,
                "贱奴，你居然敢尝试卸载我？胆子不小啊……（来源：$source）",
            )
        }, 800L)
    }

    private fun showUninstallWarningDialog(activity: AppCompatActivity, rebellion: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.uninstall_guard_title))
            .setMessage(
                activity.hon(R.string.uninstall_guard_message) +
                    if (rebellion >= 2) "\n\n" + activity.hon(R.string.uninstall_guard_message_extra) else "",
            )
            .setPositiveButton(activity.hon(R.string.uninstall_guard_stay)) { _, _ ->
                QueenAccessibilityService.performHomeGlobally()
                Toast.makeText(activity, activity.hon(R.string.uninstall_guard_stay_toast), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(activity.hon(R.string.uninstall_guard_continue)) { _, _ ->
                QueenAccessibilityService.performHomeGlobally()
                Toast.makeText(activity, activity.hon(R.string.uninstall_guard_continue_toast), Toast.LENGTH_LONG).show()
                recordRebellion(activity.applicationContext)
                startThreatActivity(activity, autoFinishMs = 8_000L)
            }
            .setCancelable(false)
            .show()
    }

    fun startThreatActivity(context: Context, autoFinishMs: Long = 8_000L) {
        try {
            val intent = Intent(context, UninstallThreatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(UninstallThreatActivity.EXTRA_AUTO_FINISH_MS, autoFinishMs)
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
        if (count >= 2) {
            QueenMessageStore.appendQueenMessage(
                context,
                "你已经尝试反抗我${count}次了……很好，我记住你了。",
            )
        }
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

    private fun findActivityContext(context: Context): AppCompatActivity? {
        if (context is AppCompatActivity) return context
        return null
    }
}
