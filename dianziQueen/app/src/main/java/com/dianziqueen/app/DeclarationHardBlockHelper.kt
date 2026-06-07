package com.dianziqueen.app

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log

/** 宣誓页硬锁：Lock Task（屏幕固定）；非 Device Owner 时 best-effort，失败一次后永久闭嘴。 */
object DeclarationHardBlockHelper {

    private const val TAG = "DeclarationHardBlock"

    @Volatile
    private var lockTaskSupported = true

    /** 本轮宣誓是否已尝试过 Lock Task（每轮 [DeclarationInterceptor.startChallenge] 重置一次）。 */
    @Volatile
    private var hardLockAttemptedThisChallenge = false

    fun resetChallengeSession() {
        hardLockAttemptedThisChallenge = false
    }

    fun enter(activity: Activity) {
        startHardLock(activity)
    }

    /**
     * 仅在 [DeclarationInterceptor.isActive] 且本轮尚未尝试时调用一次。
     * Device Owner 才调 [DevicePolicyManager.setLockTaskPackages]；否则仅 [Activity.startLockTask]。
     */
    fun startHardLock(activity: Activity) {
        if (!DeclarationInterceptor.isActive()) return
        if (!lockTaskSupported) return
        if (hardLockAttemptedThisChallenge) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        hardLockAttemptedThisChallenge = true

        if (isLockTaskLocked(activity)) return

        maybeSetLockTaskAllowlist(activity)

        try {
            activity.startLockTask()
            Log.i(TAG, "startLockTask entered")
        } catch (e: SecurityException) {
            lockTaskSupported = false
            Log.e(TAG, "LockTask permission denied, circuit open", e)
        } catch (e: Exception) {
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

    private fun maybeSetLockTaskAllowlist(activity: Activity) {
        if (!QueenDeviceAdminHelper.hasOwnerDpmCapability(activity)) return
        if (!QueenDeviceAdminHelper.isAdminActive(activity)) return
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        runCatching {
            dpm.setLockTaskPackages(
                QueenDeviceAdminHelper.adminComponent(activity),
                arrayOf(activity.packageName),
            )
        }.onFailure { e ->
            lockTaskSupported = false
            Log.d(TAG, "setLockTaskPackages unavailable: ${e.message}")
        }
    }

    private fun isLockTaskLocked(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
    }
}
