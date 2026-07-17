package com.dianziqueen.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlin.random.Random

/**
 * 将设备/蓝牙显示名改为「电子女王的第XXX号电子贱奴」。
 * 优先级：蓝牙 → System(device_name) → Global(DEVICE_NAME，需 WRITE_SECURE_SETTINGS)。
 */
object QueenDeviceNameHelper {

    private const val TAG = "DeviceName"
    private const val SLAVE_NUMBER_MIN = 100
    private const val SLAVE_NUMBER_MAX = 999
    private const val SYSTEM_DEVICE_NAME_KEY = "device_name"

    fun ensureSlaveNumber(context: Context): Int {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(Prefs.QUEEN_SLAVE_NUMBER)) {
            val n = Random.nextInt(SLAVE_NUMBER_MIN, SLAVE_NUMBER_MAX + 1)
            prefs.edit().putInt(Prefs.QUEEN_SLAVE_NUMBER, n).apply()
        }
        return prefs.getInt(Prefs.QUEEN_SLAVE_NUMBER, SLAVE_NUMBER_MIN)
    }

    fun queenDeviceName(context: Context): String {
        val n = ensureSlaveNumber(context)
        return "电子女王的第${n}号电子贱奴"
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteSecureSettingsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED

    /** 主入口：激活成功后调用。无权限时降级为本地记录，不崩溃。 */
    fun applyQueenDeviceName(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, false)) return true
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_RENAME_SKIPPED, false)) return false

        val name = queenDeviceName(context)
        var method: String? = null

        if (applyViaBluetooth(app, name)) {
            method = "bluetooth"
        } else if (applyViaSystemSetting(app, name)) {
            method = "system"
        } else if (applyViaGlobalDeviceName(app, name)) {
            method = "global"
        }

        return if (method != null) {
            saveSuccessRecord(app, method)
            QueenLogger.i(TAG, "设备名称标记成功 ($method): $name")
            true
        } else {
            QueenLogger.w(TAG, "所有设备名称修改方式均失败，仅做本地记录: $name")
            prefs.edit().putBoolean(Prefs.QUEEN_DEVICE_NAME_RENAME_SKIPPED, true).apply()
            showSkipToastIfUi(app, context)
            false
        }
    }

    /** 用户新授予蓝牙等权限后，允许再尝试写入系统设备名。 */
    fun clearRenameSkippedForRetry(context: Context) {
        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Prefs.QUEEN_DEVICE_NAME_RENAME_SKIPPED, false)
            .apply()
    }

    @SuppressLint("MissingPermission")
    private fun applyViaBluetooth(context: Context, name: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasBluetoothConnectPermission(context)
        ) {
            return false
        }
        val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)
            ?.adapter
            ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.setName(name)
        } catch (e: SecurityException) {
            QueenLogger.w(TAG, "蓝牙名称修改被拒绝: ${e.message}", e)
            false
        } catch (e: Exception) {
            QueenLogger.e(TAG, "蓝牙名称修改失败", e)
            false
        }
    }

    private fun applyViaSystemSetting(context: Context, name: String): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putString(
                context.contentResolver,
                SYSTEM_DEVICE_NAME_KEY,
                name,
            )
        } catch (e: SecurityException) {
            QueenLogger.w(TAG, "System Settings 修改被拒绝: ${e.message}", e)
            false
        } catch (e: Exception) {
            QueenLogger.e(TAG, "System Settings 修改失败", e)
            false
        }
    }

    private fun applyViaGlobalDeviceName(context: Context, name: String): Boolean {
        if (!hasWriteSecureSettingsPermission(context)) {
            QueenLogger.w(TAG, "无 WRITE_SECURE_SETTINGS 权限，跳过 Global 修改")
            return false
        }
        return try {
            Settings.Global.putString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME,
                name,
            )
        } catch (e: SecurityException) {
            QueenLogger.w(TAG, "Global 需要 WRITE_SECURE_SETTINGS 权限: ${e.message}", e)
            false
        } catch (e: Exception) {
            QueenLogger.e(TAG, "Global 修改失败", e)
            false
        }
    }

    private fun saveSuccessRecord(context: Context, method: String) {
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, true)
            .putString(Prefs.QUEEN_DEVICE_NAME_METHOD, method)
            .apply()
    }

    /** 仅在界面场景提示一次，避免每次 onResume 重复弹 Toast。 */
    private fun showSkipToastIfUi(app: Context, context: Context) {
        if (context !is Activity) return
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_MARK_TOAST_SHOWN, false)) return
        prefs.edit().putBoolean(Prefs.QUEEN_DEVICE_NAME_MARK_TOAST_SHOWN, true).apply()
        Handler(Looper.getMainLooper()).post {
            app.toastShort(R.string.queen_device_name_secure_skip_toast)
        }
    }
}
