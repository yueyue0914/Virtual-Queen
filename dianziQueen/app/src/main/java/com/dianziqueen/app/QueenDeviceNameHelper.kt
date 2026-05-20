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
 *
 * [Settings.Global.DEVICE_NAME] 需 [Manifest.permission.WRITE_SECURE_SETTINGS]（普通应用默认无），
 * 无权限时跳过 Global 写入，避免 SecurityException 崩溃；优先蓝牙名与 System 表项。
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

    /**
     * @return 是否至少一种渠道写入成功
     */
    fun applyQueenDeviceName(context: Context): Boolean {
        return try {
            applyQueenDeviceNameInternal(context)
        } catch (e: SecurityException) {
            Log.e(TAG, "修改失败: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "未知错误", e)
            false
        }
    }

    private fun applyQueenDeviceNameInternal(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, false)) {
            return true
        }

        val name = queenDeviceName(context)
        val showUiHint = context is Activity

        val viaBluetooth = applyViaBluetooth(app, name)
        val viaSystem = if (Settings.System.canWrite(app)) {
            applyViaSystemSetting(app, name)
        } else {
            false
        }
        val viaGlobal = applyViaGlobalDeviceName(
            context = app,
            name = name,
            showToastOnSkip = showUiHint && !viaBluetooth && !viaSystem,
        )

        val ok = viaBluetooth || viaSystem || viaGlobal
        val method = when {
            viaBluetooth -> "bluetooth"
            viaSystem -> "system"
            viaGlobal -> "global"
            else -> null
        }

        if (ok && method != null) {
            prefs.edit()
                .putBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, true)
                .putString(Prefs.QUEEN_DEVICE_NAME_METHOD, method)
                .apply()
            Log.i(TAG, "设备名称修改成功 ($method): $name")
        } else {
            Log.w(
                TAG,
                "设备名写入未成功（可授予蓝牙/修改系统设置；Global 需 WRITE_SECURE_SETTINGS）: $name",
            )
        }
        return ok
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

    private fun applyViaGlobalDeviceName(
        context: Context,
        name: String,
        showToastOnSkip: Boolean = false,
    ): Boolean {
        if (!hasWriteSecureSettingsPermission(context)) {
            Log.w(TAG, "无 WRITE_SECURE_SETTINGS 权限，跳过设备名称修改")
            if (showToastOnSkip) {
                showSecureSettingsSkipToast(context)
            }
            return false
        }
        return try {
            val ok = Settings.Global.putString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME,
                name,
            )
            if (ok) {
                Log.i(TAG, "Global 设备名修改成功: $name")
            } else {
                Log.w(TAG, "Global.putString(DEVICE_NAME) 返回 false")
            }
            ok
        } catch (e: SecurityException) {
            Log.e(TAG, "修改失败: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Global 设备名未知错误", e)
            false
        }
    }

    private fun showSecureSettingsSkipToast(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                R.string.queen_device_name_secure_skip_toast,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
