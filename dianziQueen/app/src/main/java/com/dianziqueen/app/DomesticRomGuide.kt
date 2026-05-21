package com.dianziqueen.app

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 华米 OV 等国产 ROM 的 Queen 级权限强引导（悬浮窗 / 自启动 / 后台 / 电池）。
 */
object DomesticRomGuide {

    private const val PREF_GUIDE_LAST_MS = "queen_domestic_rom_guide_last_ms"
    private const val PREF_GUIDE_PENDING = "queen_domestic_rom_guide_pending"
    private const val GUIDE_COOLDOWN_MS = 6 * 60 * 60 * 1000L

    enum class RomVendor {
        XIAOMI,
        HUAWEI,
        OPPO,
        VIVO,
        OTHER,
    }

    fun detectRom(): RomVendor {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                RomVendor.XIAOMI
            brand.contains("huawei") || brand.contains("honor") ||
                manufacturer.contains("huawei") ->
                RomVendor.HUAWEI
            brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") ||
                manufacturer.contains("oppo") ->
                RomVendor.OPPO
            brand.contains("vivo") || brand.contains("iqoo") ||
                manufacturer.contains("vivo") ->
                RomVendor.VIVO
            else -> RomVendor.OTHER
        }
    }

    fun isDomesticRom(): Boolean = detectRom() != RomVendor.OTHER

    /** 激活后 / 接管动画结束：缺悬浮窗则弹窗。 */
    fun showGuideIfNeeded(activity: AppCompatActivity) {
        if (FloatingWindowPermissionHelper.hasPermission(activity)) {
            clearPending(activity)
            return
        }
        showGuide(activity)
    }

    /**
     * [MainActivity.onResume]：后台曾标记权限丢失，或定期提醒（带冷却）。
     * @return 是否已展示引导
     */
    fun maybeShowOnResume(activity: AppCompatActivity): Boolean {
        if (FloatingWindowPermissionHelper.hasPermission(activity)) {
            clearPending(activity)
            return false
        }
        val prefs = activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_GUIDE_PENDING, false)) {
            prefs.edit().putBoolean(PREF_GUIDE_PENDING, false).apply()
            showGuide(activity)
            return true
        }
        val now = System.currentTimeMillis()
        val last = prefs.getLong(PREF_GUIDE_LAST_MS, 0L)
        if (now - last < GUIDE_COOLDOWN_MS) return false
        prefs.edit().putLong(PREF_GUIDE_LAST_MS, now).apply()
        showGuide(activity)
        return true
    }

    /** [QueenService] 发现悬浮窗不可用，下次进 App 再弹。 */
    fun markPendingFromBackground(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_GUIDE_PENDING, true)
            .apply()
    }

    fun showGuide(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val messageRes = when (detectRom()) {
            RomVendor.XIAOMI -> R.string.domestic_rom_guide_xiaomi
            RomVendor.HUAWEI -> R.string.domestic_rom_guide_huawei
            RomVendor.OPPO -> R.string.domestic_rom_guide_oppo
            RomVendor.VIVO -> R.string.domestic_rom_guide_vivo
            RomVendor.OTHER -> R.string.domestic_rom_guide_default
        }
        AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.domestic_rom_guide_title))
            .setMessage(activity.hon(messageRes))
            .setPositiveButton(R.string.domestic_rom_guide_go) { _, _ ->
                openSettings(activity)
            }
            .setNeutralButton(R.string.domestic_rom_guide_app_details) { _, _ ->
                openAppDetails(activity)
            }
            .setNegativeButton(R.string.domestic_rom_guide_later) { _, _ ->
                Toast.makeText(
                    activity,
                    R.string.domestic_rom_guide_later_toast,
                    Toast.LENGTH_LONG,
                ).show()
            }
            .setCancelable(false)
            .show()
        activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_GUIDE_LAST_MS, System.currentTimeMillis())
            .apply()
    }

    fun openSettings(activity: AppCompatActivity) {
        RomPermissionUtils.openQueenPermissionHub(activity)
    }

    private fun openAppDetails(context: Context) {
        RomPermissionUtils.openAppDetails(context)
    }

    private fun clearPending(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_GUIDE_PENDING, false)
            .apply()
    }
}
