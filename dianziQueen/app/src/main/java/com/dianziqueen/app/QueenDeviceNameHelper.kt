package com.dianziqueen.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.random.Random

/**
 * 将设备/蓝牙显示名改为「电子女王的第XXX号电子贱奴」。
 *
 * 说明：[Settings.Global.DEVICE_NAME] 在多数机型上需要 WRITE_SECURE_SETTINGS（普通应用不可用），
 * 故优先用 [BluetoothAdapter.setName]（关于本机/蓝牙名常与此同步），再尝试 System 键与 Global。
 */
object QueenDeviceNameHelper {

    private const val TAG = "QueenDeviceNameHelper"
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

    /**
     * @return 是否至少一种渠道写入成功
     */
    fun applyQueenDeviceName(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, false)) {
            return true
        }

        val name = queenDeviceName(context)

        val viaBluetooth = applyViaBluetooth(app, name)
        val viaSystem = if (Settings.System.canWrite(app)) applyViaSystemSetting(app, name) else false
        val viaGlobal = if (Settings.System.canWrite(app)) applyViaGlobalDeviceName(app, name) else false

        val ok = viaBluetooth || viaSystem || viaGlobal
        val method = when {
            viaBluetooth -> "bluetooth"
            viaSystem -> "system"
            viaGlobal -> "global"
            else -> null
        }

        if (ok && method != null) {
            app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, true)
                .putString(Prefs.QUEEN_DEVICE_NAME_METHOD, method)
                .apply()
            Log.i(TAG, "设备名已写入 ($method): $name")
        } else {
            Log.w(
                TAG,
                "设备名写入失败（请授予附近设备/蓝牙权限；Global 需系统签名）: $name",
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
            Log.w(TAG, "bluetooth setName denied", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "bluetooth setName failed", e)
            false
        }
    }

    private fun applyViaSystemSetting(context: Context, name: String): Boolean {
        return try {
            Settings.System.putString(
                context.contentResolver,
                SYSTEM_DEVICE_NAME_KEY,
                name,
            )
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun applyViaGlobalDeviceName(context: Context, name: String): Boolean {
        return try {
            Settings.Global.putString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME,
                name,
            )
        } catch (e: SecurityException) {
            // 普通应用常见：需要 WRITE_SECURE_SETTINGS
            false
        } catch (e: Exception) {
            false
        }
    }
}
