package com.dianziqueen.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * 设备管理员组件：启用后由 [QueenDeviceAdminHelper] 尝试施加关机限制等策略。
 */
class QueenDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        try {
            QueenDeviceAdminHelper.applyQueenPolicies(context)
        } catch (e: Exception) {
            Log.w(TAG, "applyQueenPolicies on enable failed", e)
        }
        Toast.makeText(context, R.string.device_admin_enabled_toast, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "QueenDeviceAdminRcvr"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, R.string.device_admin_disabled_toast, Toast.LENGTH_LONG).show()
    }
}
