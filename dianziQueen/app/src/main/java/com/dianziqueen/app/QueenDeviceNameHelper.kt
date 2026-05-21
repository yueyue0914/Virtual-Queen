package com.dianziqueen.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.random.Random

/**
 * 将设备/蓝牙显示名改为「电子女王的第XXX号电子贱奴」。
 * 优先级：蓝牙 → System(device_name) → Global(DEVICE_NAME，需 WRITE_SECURE_SETTINGS)。
 */
object QueenDeviceNameHelper {

    private const val TAG = "QueenDeviceName"
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

    /** @return 是否至少一种渠道写入成功 */
    fun applyQueenDeviceName(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, false)) {
            return true
        }
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_RENAME_SKIPPED, false)) {
            return false
        }

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
            Log.i(TAG, "设备名称修改成功 ($method): $name")
            true
        } else {
            Log.w(
                TAG,
                "设备名写入未成功（蓝牙/修改系统设置/Global；Global 需 WRITE_SECURE_SETTINGS）: $name",
            )
            prefs.edit().putBoolean(Prefs.QUEEN_DEVICE_NAME_RENAME_SKIPPED, true).apply()
            showMarkToastOnceIfUi(context)
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
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.setName(name)
        } catch (e: SecurityException) {
            Log.e(TAG, "蓝牙 setName 被拒绝: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "蓝牙 setName 失败", e)
            false
        }
    }

    private fun applyViaSystemSetting(context: Context, name: String): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            val ok = Settings.System.putString(
                context.contentResolver,
                SYSTEM_DEVICE_NAME_KEY,
                name,
            )
            if (!ok) Log.w(TAG, "System.putString(device_name) 返回 false")
            ok
        } catch (e: SecurityException) {
            Log.e(TAG, "System 设备名修改失败: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "System 设备名未知错误", e)
            false
        }
    }

    private fun applyViaGlobalDeviceName(context: Context, name: String): Boolean {
        if (!hasWriteSecureSettingsPermission(context)) {
            Log.w(TAG, "无 WRITE_SECURE_SETTINGS 权限，跳过 Global 设备名")
            return false
        }
        return try {
            val ok = Settings.Global.putString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME,
                name,
            )
            if (!ok) Log.w(TAG, "Global.putString(DEVICE_NAME) 返回 false")
            ok
        } catch (e: SecurityException) {
            Log.e(TAG, "Global 设备名修改失败: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Global 设备名未知错误", e)
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

    /** 仅在界面场景提示一次，避免每次 onResume 重复弹「已标记此设备」。 */
    private fun showMarkToastOnceIfUi(context: Context) {
        if (context !is Activity) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_MARK_TOAST_SHOWN, false)) return
        prefs.edit().putBoolean(Prefs.QUEEN_DEVICE_NAME_MARK_TOAST_SHOWN, true).apply()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                app,
                R.string.queen_device_name_secure_skip_toast,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
