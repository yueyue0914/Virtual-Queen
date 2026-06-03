package com.dianziqueen.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * 设备管理员：启用策略、拦截停用请求。
 * 卸载/停用验证 PIN 固定为 [QueenDeviceAdminHelper.ADMIN_DISABLE_PIN]（20262026），App 不设置系统锁屏 PIN。
 */
class QueenDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        try {
            QueenDeviceAdminHelper.applyQueenPolicies(context)
            DeclarationHardBlockHelper.ensureLockTaskAllowlist(context)
        } catch (e: Exception) {
            Log.w(TAG, "applyQueenPolicies on enable failed", e)
        }
        Toast.makeText(context, R.string.device_admin_enabled_toast, Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        SettingsLockGuard.onDeviceAdminDisabled(context)
        Toast.makeText(context, R.string.device_admin_disabled_toast, Toast.LENGTH_LONG).show()
    }

    /** 用户尝试停用 Device Admin 时：心理威慑 + 触发 [UninstallGuard]。 */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        UninstallGuard.onUninstallAttempt(context.applicationContext, "device_admin_disable")
        return context.getString(R.string.device_admin_disable_requested_message)
    }

    companion object {
        private const val TAG = "QueenDeviceAdminRcvr"
    }
}
