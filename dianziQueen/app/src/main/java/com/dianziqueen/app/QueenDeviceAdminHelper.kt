package com.dianziqueen.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object QueenDeviceAdminHelper {

    private const val TAG = "DeviceAdmin"

    /** 停用管理员 / 坚持卸载须输入的 Queen PIN（固定，App 不代为设置系统锁屏）。 */
    const val ADMIN_DISABLE_PIN = "20262026"

    /**
     * 仅 Device Owner / Profile Owner 才能调用 setStatusBarDisabled 等高级 API。
     * 普通 Device Admin 激活后仍会探测失败；结果进程内永久缓存，避免日志刷屏。
     */
    @Volatile
    private var ownerCapabilityProbed = false

    @Volatile
    private var hasDpmOwnerCapability = false

    private const val POLICY_APPLY_COOLDOWN_MS = 30_000L

    @Volatile
    private var lastPolicyApplyAt = 0L

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, QueenDeviceAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(context))
    }

    fun createAddAdminIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_explanation),
            )
        }

    fun verifyDisablePin(pin: String): Boolean = pin == ADMIN_DISABLE_PIN

    fun onPinVerificationFailed(context: Context) {
        val app = context.applicationContext
        QueenVibratorHelper.punish(app)
        QueenMessageStore.appendQueenMessage(
            app,
            app.getString(R.string.device_admin_pin_failed_msg),
        )
    }

    /** 是否具备 Device/Profile Owner 级 DPM 能力（探测一次后缓存）。 */
    fun hasOwnerDpmCapability(context: Context): Boolean {
        if (ownerCapabilityProbed) return hasDpmOwnerCapability
        synchronized(this) {
            if (ownerCapabilityProbed) return hasDpmOwnerCapability
            val dpm = context.applicationContext
                .getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val pkg = context.applicationContext.packageName
            hasDpmOwnerCapability = dpm != null &&
                (dpm.isDeviceOwnerApp(pkg) || dpm.isProfileOwnerApp(pkg))
            ownerCapabilityProbed = true
            if (!hasDpmOwnerCapability) {
                QueenLogger.i(TAG, "非 Device/Profile Owner，跳过 setStatusBarDisabled 等高级 DPM API")
            }
        }
        return hasDpmOwnerCapability
    }

    /**
     * 状态栏/锁屏等约束（仅 Device Owner 类角色有效）。
     * @param disable true=宣誓拦截期间尝试禁用；false=通关后解除。
     */
    fun setConstraintsDisabled(context: Context, disable: Boolean) {
        if (!hasOwnerDpmCapability(context)) return
        if (DeclarationInterceptor.isChallengePassed() && disable) return
        if (!isAdminActive(context)) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        val admin = adminComponent(context)

        try {
            dpm.setStatusBarDisabled(admin, disable)
            dpm.setKeyguardDisabled(admin, false)
        } catch (e: SecurityException) {
            hasDpmOwnerCapability = false
            ownerCapabilityProbed = true
            QueenLogger.w(TAG, "DPM Owner API 不可用，已永久跳过: ${e.message}", e)
        } catch (e: Exception) {
            QueenLogger.d(TAG, "setConstraintsDisabled failed: ${e.message}")
        }
    }

    /**
     * 在已激活且管理员启用时尝试刷新策略（best-effort，带冷却）。
     * 关机拦截主要依赖 [QueenAccessibilityService]。
     */
    fun applyQueenPolicies(context: Context) {
        if (!isAdminActive(context)) return
        if (!hasOwnerDpmCapability(context)) return
        if (DeclarationInterceptor.isChallengePassed()) return
        if (!DeclarationInterceptor.isActive()) return
        val now = System.currentTimeMillis()
        if (now - lastPolicyApplyAt < POLICY_APPLY_COOLDOWN_MS) return
        lastPolicyApplyAt = now
        setConstraintsDisabled(context, disable = true)
    }

    fun enforcePowerMenuBlock(context: Context) {
        applyQueenPolicies(context)
    }
}
