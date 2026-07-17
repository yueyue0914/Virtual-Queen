package com.dianziqueen.app

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 华米 OV 等国产 ROM 的 Queen 级权限强引导（悬浮窗 / 自启动 / 后台 / 电池）。
 *
 * 比单纯弹「去开悬浮窗」更完整：按 ROM 展示分步说明、检测当前缺失项、
 * 一键跳转厂商权限中心，并支持分项设置（自启动 / 电池等）。
 *
 * 相关配合类：
 * - [RomPermissionUtils] — 各品牌设置页精准跳转
 * - [RomPermissionProbe] — AppOps 检测 + 自检页手动确认
 * - [FloatingWindowPermissionHelper] — 悬浮窗检测
 * - [QueenBatteryHelper] — 电池优化豁免
 * - [PermissionCheckActivity] — 完整权限自检页
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

    /**
     * 是否需要弹出国产 ROM 引导：缺悬浮窗，或国产机缺电池豁免（后台易被杀）。
     */
    fun needsGuide(context: Context): Boolean {
        if (!FloatingWindowPermissionHelper.hasPermission(context)) return true
        if (!isDomesticRom()) return false
        return !QueenBatteryHelper.isExemptFromBatteryOptimizations(context)
    }

    /** 激活后 / 接管动画结束：缺关键 ROM 权限则弹窗。 */
    fun showGuideIfNeeded(activity: AppCompatActivity) {
        if (!needsGuide(activity)) {
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
        if (!needsGuide(activity)) {
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
            .setMessage(buildFullMessage(activity, messageRes))
            .setPositiveButton(primaryActionLabel(activity)) { _, _ ->
                openPrimarySettings(activity)
            }
            .setNeutralButton(R.string.domestic_rom_guide_pick) { _, _ ->
                showQuickJumpMenu(activity)
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

    /** 优先打开当前最缺的一项；悬浮窗仍缺时走综合引导（悬浮窗 + 厂商中心）。 */
    fun openPrimarySettings(activity: AppCompatActivity) {
        when {
            !FloatingWindowPermissionHelper.hasPermission(activity) ->
                RomPermissionUtils.openQueenPermissionHub(activity)
            isDomesticRom() &&
                !QueenBatteryHelper.isExemptFromBatteryOptimizations(activity) ->
                QueenBatteryHelper.openBatteryExemptionSettings(activity)
            isDomesticRom() -> {
                if (!RomPermissionUtils.openRomExtraPermissionHub(activity)) {
                    RomPermissionUtils.openAutoStartSettings(activity)
                }
            }
            else -> RomPermissionUtils.openOverlaySettings(activity)
        }
    }

    fun openSettings(activity: AppCompatActivity) {
        RomPermissionUtils.openQueenPermissionHub(activity)
    }

    private fun showQuickJumpMenu(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val labels = activity.resources.getStringArray(R.array.domestic_rom_guide_pick_items)
        AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.domestic_rom_guide_pick_title))
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> RomPermissionUtils.openOverlaySettings(activity)
                    1 -> RomPermissionUtils.openAutoStartSettings(activity)
                    2 -> QueenBatteryHelper.openBatteryExemptionSettings(activity)
                    3 -> {
                        if (!RomPermissionUtils.openRomExtraPermissionHub(activity)) {
                            RomPermissionUtils.openAutoStartSettings(activity)
                        }
                    }
                    4 -> RomPermissionUtils.openAppDetails(activity)
                    else -> RomPermissionUtils.openAppDetails(activity)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun primaryActionLabel(activity: AppCompatActivity): Int = when {
        !FloatingWindowPermissionHelper.hasPermission(activity) ->
            R.string.domestic_rom_guide_go
        isDomesticRom() &&
            !QueenBatteryHelper.isExemptFromBatteryOptimizations(activity) ->
            if (detectRom() == RomVendor.XIAOMI) {
                R.string.domestic_rom_guide_go_xiaomi_power
            } else {
                R.string.domestic_rom_guide_go_battery
            }
        else -> R.string.domestic_rom_guide_go_rom
    }

    private fun buildFullMessage(activity: AppCompatActivity, baseMessageRes: Int): String {
        val base = activity.hon(baseMessageRes)
        val statusLines = buildStatusLines(activity)
        if (statusLines.isEmpty()) return base
        return buildString {
            append(base)
            append("\n\n")
            append(activity.getString(R.string.domestic_rom_guide_status_header))
            append('\n')
            statusLines.forEach { append("• ").append(it).append('\n') }
        }.trimEnd()
    }

    private fun buildStatusLines(context: Context): List<String> {
        val lines = mutableListOf<String>()
        if (!FloatingWindowPermissionHelper.hasPermission(context)) {
            lines.add(context.getString(R.string.domestic_rom_status_overlay))
        }
        if (isDomesticRom()) {
            if (!QueenBatteryHelper.isExemptFromBatteryOptimizations(context)) {
                lines.add(
                    if (detectRom() == RomVendor.XIAOMI) {
                        context.getString(R.string.domestic_rom_status_xiaomi_power)
                    } else {
                        context.getString(R.string.domestic_rom_status_battery)
                    },
                )
            }
            if (!RomPermissionProbe.isWriteSettingsAutoDetected(context)) {
                lines.add(context.getString(R.string.domestic_rom_status_write_settings))
            }
            if (!isConfirmed(context, "autostart")) {
                lines.add(
                    if (detectRom() == RomVendor.XIAOMI) {
                        context.getString(R.string.domestic_rom_status_xiaomi_autostart)
                    } else {
                        context.getString(R.string.domestic_rom_status_autostart_hint)
                    },
                )
            }
            if (!isConfirmed(context, "lock_app")) {
                lines.add(context.getString(R.string.perm_check_item_lock_app))
            }
            if (!isConfirmed(context, "rom_extra")) {
                lines.add(context.getString(R.string.perm_check_item_rom_extra))
            }
        }
        return lines
    }

    private fun isConfirmed(context: Context, id: String): Boolean {
        val key = RomPermissionProbe.confirmKeyForPermissionId(id) ?: return false
        return RomPermissionProbe.isUserConfirmed(context, key)
    }

    private fun clearPending(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_GUIDE_PENDING, false)
            .apply()
    }
}
