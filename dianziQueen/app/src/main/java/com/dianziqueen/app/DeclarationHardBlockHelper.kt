package com.dianziqueen.app

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log

/** 宣誓页硬锁：Lock Task（屏幕固定）+ 设备管理员白名单。 */
object DeclarationHardBlockHelper {

    private const val TAG = "DeclarationHardBlock"

    /** 尽力将本 App 加入 Lock Task 白名单（需设备管理员；Device Owner 时最稳）。 */
    fun ensureLockTaskAllowlist(context: Context) {
        if (!QueenDeviceAdminHelper.isAdminActive(context)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = QueenDeviceAdminHelper.adminComponent(context)
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        }.onFailure { e ->
            Log.d(TAG, "setLockTaskPackages unavailable: ${e.message}")
        }
    }

    /** 进入 Lock Task：屏蔽 Home/多任务（成功时系统级有效）。 */
    fun enter(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        ensureLockTaskAllowlist(activity)
        if (isLockTaskLocked(activity)) return
        runCatching {
            activity.startLockTask()
            Log.i(TAG, "startLockTask entered")
        }.onFailure { e ->
            Log.d(TAG, "startLockTask failed: ${e.message}")
        }
    }

    fun exit(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (!isLockTaskLocked(activity)) return
        runCatching {
            activity.stopLockTask()
            Log.i(TAG, "stopLockTask")
        }.onFailure { e ->
            Log.d(TAG, "stopLockTask failed: ${e.message}")
        }
    }

    private fun isLockTaskLocked(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
    }
}
