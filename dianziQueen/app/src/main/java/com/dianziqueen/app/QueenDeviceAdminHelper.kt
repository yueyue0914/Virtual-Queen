package com.dianziqueen.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object QueenDeviceAdminHelper {

    private const val TAG = "QueenDeviceAdminHelper"

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

    /**
     * 在已激活且管理员启用时尝试刷新策略。
     *
     * 普通「设备管理员」≠ Device Owner：`addUserRestriction` / `setStatusBarDisabled` 等
     * 在多数机型上会直接失败；部分 ROM 对非法 restriction 键还会在系统侧 NPE。
     * 关机拦截主要依赖 [QueenAccessibilityService]，此处仅 best-effort，且绝不向外抛异常。
     */
    fun applyQueenPolicies(context: Context) {
        if (!isAdminActive(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = adminComponent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                dpm.setStatusBarDisabled(admin, true)
            }.onFailure { e ->
                Log.d(TAG, "setStatusBarDisabled not available: ${e.message}")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                dpm.setKeyguardDisabled(admin, false)
            }.onFailure { e ->
                Log.d(TAG, "setKeyguardDisabled not available: ${e.message}")
            }
        }
    }

    /** 尽力刷新策略（关机菜单仍主要靠无障碍拦截）。 */
    fun enforcePowerMenuBlock(context: Context) {
        applyQueenPolicies(context)
    }
}
