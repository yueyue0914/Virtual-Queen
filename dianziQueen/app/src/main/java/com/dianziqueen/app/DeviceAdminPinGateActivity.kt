package com.dianziqueen.app

import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 全屏 PIN 门：系统停用 Device Admin / 卸载流程无法弹 App 内 Dialog 时由此 Activity 承接。
 */
class DeviceAdminPinGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_admin_pin_gate)
        applyFullscreenLock()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { }
            },
        )

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ADMIN_DISABLE
        findViewById<TextView>(R.id.tvPinGateTitle).text = hon(R.string.device_admin_pin_title)
        findViewById<TextView>(R.id.tvPinGateMessage).text = when (mode) {
            MODE_UNINSTALL -> hon(R.string.device_admin_pin_message_uninstall)
            else -> hon(R.string.device_admin_pin_message_admin)
        }

        val input = findViewById<EditText>(R.id.etPinGate)
        findViewById<Button>(R.id.btnPinGateSubmit).setOnClickListener {
            verifyPin(input.text?.toString().orEmpty(), mode)
        }
        findViewById<Button>(R.id.btnPinGateCancel).apply {
            text = hon(R.string.uninstall_guard_stay)
            setOnClickListener { surrender() }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verifyPin(input.text?.toString().orEmpty(), mode)
                true
            } else {
                false
            }
        }
    }

    private fun verifyPin(pin: String, mode: String) {
        if (QueenDeviceAdminHelper.verifyDisablePin(pin)) {
            AdminDisablePinSession.grant(applicationContext)
            if (mode == MODE_UNINSTALL) {
                applicationContext.getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(Prefs.UNINSTALL_PROTECTED, false)
                    .apply()
            }
            Toast.makeText(
                this,
                if (mode == MODE_UNINSTALL) {
                    getString(R.string.device_admin_pin_correct_toast)
                } else {
                    getString(R.string.device_admin_pin_admin_granted_toast)
                },
                Toast.LENGTH_LONG,
            ).show()
            finish()
            return
        }
        QueenDeviceAdminHelper.onPinVerificationFailed(this)
        Toast.makeText(this, R.string.device_admin_pin_wrong_toast, Toast.LENGTH_LONG).show()
        QueenVibratorHelper.punish(this)
    }

    private fun surrender() {
        UninstallGuard.dismissUninstallUiPublic()
        finish()
    }

    private fun applyFullscreenLock() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.attributes.layoutInDisplayCutoutMode =
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.setFormat(PixelFormat.OPAQUE)
    }

    companion object {
        const val EXTRA_MODE = "pin_gate_mode"
        const val EXTRA_SOURCE = "pin_gate_source"
        const val MODE_ADMIN_DISABLE = "admin_disable"
        const val MODE_UNINSTALL = "uninstall"
    }
}
