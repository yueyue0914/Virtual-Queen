package com.dianziqueen.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference

/**
 * 华米 OV 等国产 ROM 的 Queen 级权限强引导。
 *
 * 分步顺序（必须严格遵守）：
 * 1. 悬浮窗（最重要）
 * 2. 自启动 + 后台运行
 * 3. 无障碍服务
 *
 * 与 [MainActivity] 特权审计的关系：
 * 上述三项由本类独占引导；审计在 [shouldDeferPrivilegeAudit] 为 true 时不得自动跳转。
 */
object DomesticRomGuide {

    private const val PREF_GUIDE_LAST_MS = "queen_domestic_rom_guide_last_ms"
    private const val PREF_GUIDE_PENDING = "queen_domestic_rom_guide_pending"
    private const val PREF_STEP_PENDING = "queen_domestic_rom_guide_step_pending"
    /** 常规冷却（已有权限但仍缺自启动等手动项时）。 */
    private const val GUIDE_COOLDOWN_MS = 6 * 60 * 60 * 1000L
    /** 缺悬浮窗时更频繁提醒（小米极易误关）。 */
    private const val OVERLAY_MISSING_COOLDOWN_MS = 20 * 60 * 1000L
    /** 用户从设置页返回后，再弹下一步的延迟。 */
    private const val NEXT_STEP_DELAY_MS = 1_500L

    private val handler = Handler(Looper.getMainLooper())
    private var activeDialog: AlertDialog? = null
    private var pendingActivityRef = WeakReference<AppCompatActivity>(null)
    private var nextStepRunnable: Runnable? = null

    private val dialogShowing: Boolean
        get() = activeDialog?.isShowing == true

    enum class RomVendor {
        XIAOMI,
        HUAWEI,
        OPPO,
        VIVO,
        OTHER,
    }

    private enum class GuideStep(val index: Int) {
        OVERLAY(1),
        AUTOSTART_BACKGROUND(2),
        ACCESSIBILITY(3),
    }

    fun detectRom(): RomVendor = when {
        RomPermissionUtils.isXiaomi() -> RomVendor.XIAOMI
        RomPermissionUtils.isHuawei() -> RomVendor.HUAWEI
        RomPermissionUtils.isOppo() -> RomVendor.OPPO
        RomPermissionUtils.isVivo() -> RomVendor.VIVO
        else -> RomVendor.OTHER
    }

    fun isDomesticRom(): Boolean = RomPermissionUtils.isDomesticRom()

    /**
     * 是否需要引导：缺悬浮窗 / 缺后台相关 / 缺无障碍。
     */
    fun needsGuide(context: Context): Boolean {
        if (!PermissionChecker.hasOverlay(context)) return true
        if (needsAutostartBackgroundStep(context)) return true
        return !PermissionChecker.hasAccessibility(context)
    }

    /**
     * 特权审计应让路：分步会话进行中，或核心三步（悬浮窗/自启动后台/无障碍）仍缺。
     * 避免与日历→电池→悬浮窗那一套叠弹、抢跳转。
     */
    fun shouldDeferPrivilegeAudit(context: Context): Boolean {
        if (dialogShowing) return true
        if (stepPending(context) > 0) return true
        if (!PermissionChecker.hasOverlay(context)) return true
        if (needsAutostartBackgroundStep(context)) return true
        if (!PermissionChecker.hasAccessibility(context)) return true
        return false
    }

    /** Activity 销毁时清理，防止 dialogShowing 假死。 */
    fun onHostDestroyed(activity: AppCompatActivity) {
        if (pendingActivityRef.get() === activity) {
            dismissActiveDialog()
            cancelScheduledNextStep()
            pendingActivityRef = WeakReference(null)
        }
    }

    /** 激活后 / 接管动画结束：缺关键权限则弹分步引导。 */
    fun showGuideIfNeeded(activity: AppCompatActivity) {
        if (!needsGuide(activity)) {
            clearPending(activity)
            clearStepPending(activity)
            return
        }
        showGuide(activity)
    }

    /**
     * 专用于悬浮窗丢失：有 Activity 时弹引导，但遵守缺悬浮窗冷却，避免 onResume 死循环。
     */
    fun promptOverlayIfMissing(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (PermissionChecker.hasOverlay(activity)) return
        val prefs = activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(PREF_GUIDE_LAST_MS, 0L)
        if (System.currentTimeMillis() - last < OVERLAY_MISSING_COOLDOWN_MS) return
        showGuide(activity)
    }

    /**
     * [MainActivity.onResume]：后台曾标记权限丢失，或定期提醒（带冷却）。
     * 若有未完成的分步，优先续接。
     */
    fun maybeShowOnResume(activity: AppCompatActivity): Boolean {
        if (!needsGuide(activity)) {
            clearPending(activity)
            clearStepPending(activity)
            return false
        }
        val prefs = activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val pendingStep = prefs.getInt(PREF_STEP_PENDING, 0)
        if (pendingStep > 0) {
            prefs.edit().putBoolean(PREF_GUIDE_PENDING, false).apply()
            showStepByStepGuide(activity, fromStep = pendingStep)
            return true
        }
        if (prefs.getBoolean(PREF_GUIDE_PENDING, false)) {
            prefs.edit().putBoolean(PREF_GUIDE_PENDING, false).apply()
            showGuide(activity)
            return true
        }
        val now = System.currentTimeMillis()
        val last = prefs.getLong(PREF_GUIDE_LAST_MS, 0L)
        val cooldown = if (!PermissionChecker.hasOverlay(activity)) {
            OVERLAY_MISSING_COOLDOWN_MS
        } else {
            GUIDE_COOLDOWN_MS
        }
        if (now - last < cooldown) return false
        prefs.edit().putLong(PREF_GUIDE_LAST_MS, now).apply()
        showGuide(activity)
        return true
    }

    /** [QueenService] / 悬浮窗看门狗发现权限丢失，下次进 App 再弹。 */
    fun markPendingFromBackground(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_GUIDE_PENDING, true)
            .apply()
    }

    /**
     * 入口：按固定顺序分步引导（悬浮窗 → 自启动/后台 → 无障碍）。
     * 已满足的步骤自动跳过。
     */
    fun showGuide(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_GUIDE_LAST_MS, System.currentTimeMillis())
            .apply()
        showStepByStepGuide(activity, fromStep = 1)
    }

    fun showStepByStepGuide(activity: AppCompatActivity, fromStep: Int = 1) {
        if (activity.isFinishing || activity.isDestroyed) return
        // 残留假死状态：dialog 已不在显示则复位
        if (activeDialog != null && activeDialog?.isShowing != true) {
            activeDialog = null
        }
        if (dialogShowing) return
        cancelScheduledNextStep()
        pendingActivityRef = WeakReference(activity)

        val step = firstNeededStep(activity, fromStep) ?: run {
            clearStepPending(activity)
            activity.toastLong(R.string.domestic_rom_step_done)
            return
        }
        when (step) {
            GuideStep.OVERLAY -> showStep1Overlay(activity)
            GuideStep.AUTOSTART_BACKGROUND -> showStep2AutostartBackground(activity)
            GuideStep.ACCESSIBILITY -> showStep3Accessibility(activity)
        }
    }

    /** 优先打开当前最缺的一项（兼容旧调用 / 分项菜单）。 */
    fun openPrimarySettings(activity: AppCompatActivity) {
        when {
            !PermissionChecker.hasOverlay(activity) ->
                FloatingWindowPermissionHelper.requestPermission(activity)
            needsAutostartBackgroundStep(activity) ->
                openAutostartAndBackgroundSettings(activity)
            !PermissionChecker.hasAccessibility(activity) ->
                QueenAccessibilityHelper.openQueenAccessibilitySettings(activity)
            else -> RomPermissionUtils.openOverlaySettings(activity)
        }
    }

    fun openSettings(activity: AppCompatActivity) {
        RomPermissionUtils.openQueenPermissionHub(activity)
    }

    // region Step dialogs

    private fun showStep1Overlay(activity: AppCompatActivity) {
        setStepPending(activity, GuideStep.OVERLAY.index)
        val builder = AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.domestic_rom_step1_title))
            .setMessage(activity.hon(R.string.domestic_rom_step1_message))
            .setPositiveButton(R.string.domestic_rom_step1_go) { _, _ ->
                FloatingWindowPermissionHelper.requestPermission(activity)
                scheduleNextStep(activity, afterStep = GuideStep.OVERLAY.index)
            }
            .setNegativeButton(R.string.domestic_rom_guide_later) { _, _ ->
                clearStepPending(activity)
                markGuideSnoozed(activity)
                activity.toastLong(R.string.domestic_rom_guide_later_toast)
            }
            .setCancelable(false)

        // 仅当悬浮窗已授予时才允许「进入下一步」，禁止跳过
        if (PermissionChecker.hasOverlay(activity)) {
            builder.setNeutralButton(R.string.domestic_rom_step_next) { _, _ ->
                dismissActiveDialog()
                showStepByStepGuide(activity, fromStep = GuideStep.OVERLAY.index + 1)
            }
        }

        showManagedDialog(builder)
    }

    private fun showStep2AutostartBackground(activity: AppCompatActivity) {
        setStepPending(activity, GuideStep.AUTOSTART_BACKGROUND.index)
        val messageRes = when (detectRom()) {
            RomVendor.XIAOMI -> R.string.domestic_rom_step2_message_xiaomi
            RomVendor.HUAWEI -> R.string.domestic_rom_step2_message_huawei
            RomVendor.OPPO -> R.string.domestic_rom_step2_message_oppo
            RomVendor.VIVO -> R.string.domestic_rom_step2_message_vivo
            RomVendor.OTHER -> R.string.domestic_rom_step2_message_default
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.domestic_rom_step2_title))
            .setMessage(activity.hon(messageRes))
            .setPositiveButton(R.string.domestic_rom_step2_go) { _, _ ->
                openAutostartAndBackgroundSettings(activity)
                scheduleNextStep(activity, afterStep = GuideStep.AUTOSTART_BACKGROUND.index)
            }
            .setNeutralButton(R.string.domestic_rom_step2_confirm) { _, _ ->
                acknowledgeStep2(activity)
                dismissActiveDialog()
                showStepByStepGuide(activity, fromStep = GuideStep.AUTOSTART_BACKGROUND.index + 1)
            }
            .setNegativeButton(R.string.domestic_rom_guide_later) { _, _ ->
                clearStepPending(activity)
                markGuideSnoozed(activity)
                activity.toastLong(R.string.domestic_rom_guide_later_toast)
            }
            .setCancelable(false)

        showManagedDialog(builder)
    }

    private fun showStep3Accessibility(activity: AppCompatActivity) {
        setStepPending(activity, GuideStep.ACCESSIBILITY.index)
        val builder = AlertDialog.Builder(activity)
            .setTitle(activity.hon(R.string.domestic_rom_step3_title))
            .setMessage(activity.hon(R.string.domestic_rom_step3_message))
            .setPositiveButton(R.string.domestic_rom_step3_go) { _, _ ->
                QueenAccessibilityHelper.openQueenAccessibilitySettings(activity)
                handler.postDelayed({
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        clearStepPending(activity)
                        if (!PermissionChecker.hasAccessibility(activity)) {
                            setStepPending(activity, GuideStep.ACCESSIBILITY.index)
                        }
                    }
                }, NEXT_STEP_DELAY_MS)
            }
            .setNegativeButton(R.string.domestic_rom_guide_later) { _, _ ->
                clearStepPending(activity)
                markGuideSnoozed(activity)
                activity.toastLong(R.string.domestic_rom_guide_later_toast)
            }
            .setCancelable(false)

        if (PermissionChecker.hasAccessibility(activity)) {
            builder.setNeutralButton(R.string.domestic_rom_step_next) { _, _ ->
                clearStepPending(activity)
                dismissActiveDialog()
                activity.toastLong(R.string.domestic_rom_step_done)
            }
        }

        showManagedDialog(builder)
    }

    // endregion

    private fun showManagedDialog(builder: AlertDialog.Builder) {
        dismissActiveDialog()
        val dialog = builder.create()
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog === dialog) {
                activeDialog = null
            }
        }
        dialog.show()
    }

    private fun dismissActiveDialog() {
        val dialog = activeDialog
        activeDialog = null
        if (dialog?.isShowing == true) {
            runCatching { dialog.dismiss() }
        }
    }

    private fun scheduleNextStep(activity: AppCompatActivity, afterStep: Int) {
        cancelScheduledNextStep()
        // 保持在当前步：未完成则重弹当前步；已完成则 firstNeededStep 自动进下一步
        setStepPending(activity, afterStep)
        val runnable = Runnable {
            nextStepRunnable = null
            val act = pendingActivityRef.get() ?: activity
            if (act.isFinishing || act.isDestroyed) return@Runnable
            if (act.hasWindowFocus()) {
                showStepByStepGuide(act, fromStep = afterStep)
            }
        }
        nextStepRunnable = runnable
        handler.postDelayed(runnable, NEXT_STEP_DELAY_MS)
    }

    private fun cancelScheduledNextStep() {
        nextStepRunnable?.let { handler.removeCallbacks(it) }
        nextStepRunnable = null
    }

    private fun firstNeededStep(context: Context, fromStep: Int): GuideStep? {
        val steps = GuideStep.entries.filter { it.index >= fromStep }
        for (step in steps) {
            when (step) {
                GuideStep.OVERLAY ->
                    if (!PermissionChecker.hasOverlay(context)) return step
                GuideStep.AUTOSTART_BACKGROUND ->
                    if (needsAutostartBackgroundStep(context)) return step
                GuideStep.ACCESSIBILITY ->
                    if (!PermissionChecker.hasAccessibility(context)) return step
            }
        }
        return null
    }

    /**
     * 电池未豁免且用户未在引导/自检中确认 → 需要；
     * 国产机自启动未确认 → 需要。
     * 「我已开启」会写入确认，从而可进入第 3 步。
     */
    private fun needsAutostartBackgroundStep(context: Context): Boolean {
        val batteryOk = PermissionChecker.hasBatteryExempt(context) ||
            isConfirmed(context, "battery")
        if (!batteryOk) return true
        if (!isDomesticRom()) return false
        return !isConfirmed(context, "autostart")
    }

    private fun acknowledgeStep2(context: Context) {
        RomPermissionProbe.setUserConfirmed(context, RomPermissionProbe.CONFIRM_AUTOSTART, true)
        RomPermissionProbe.setUserConfirmed(context, RomPermissionProbe.CONFIRM_BATTERY, true)
    }

    /** 只打开一个设置页，避免 800ms 连开两页互相覆盖。 */
    private fun openAutostartAndBackgroundSettings(activity: AppCompatActivity) {
        when {
            isDomesticRom() && !isConfirmed(activity, "autostart") -> {
                if (!RomPermissionUtils.openRomExtraPermissionHub(activity)) {
                    RomPermissionUtils.openAutoStartSettings(activity)
                }
            }
            !PermissionChecker.hasBatteryExempt(activity) ->
                QueenBatteryHelper.openBatteryExemptionSettings(activity)
            else -> {
                if (!RomPermissionUtils.openRomExtraPermissionHub(activity)) {
                    RomPermissionUtils.openAutoStartSettings(activity)
                }
            }
        }
    }

    private fun stepPending(context: Context): Int =
        context.applicationContext
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getInt(PREF_STEP_PENDING, 0)

    private fun setStepPending(context: Context, step: Int) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_STEP_PENDING, step)
            .apply()
    }

    private fun clearStepPending(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_STEP_PENDING)
            .apply()
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

    /** 「稍后」：刷新冷却起点，避免立刻又被 onResume 弹回。 */
    private fun markGuideSnoozed(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_GUIDE_LAST_MS, System.currentTimeMillis())
            .putBoolean(PREF_GUIDE_PENDING, false)
            .apply()
    }
}
