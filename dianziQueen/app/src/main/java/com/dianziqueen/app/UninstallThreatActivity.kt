package com.dianziqueen.app

import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.random.Random

/**
 * 尝试卸载时全屏羞辱 + 震动。
 */
class UninstallThreatActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var vibrating = true

    private val threats: Array<String>
        get() = resources.getStringArray(R.array.uninstall_threat_lines)
            .map { QueenHonorific.apply(this, it) }
            .toTypedArray()

    private val vibrateRunnable = object : Runnable {
        override fun run() {
            if (!vibrating) return
            QueenVibratorHelper.lightTap(applicationContext)
            handler.postDelayed(this, 750L)
        }
    }

    private val rotateRunnable = object : Runnable {
        override fun run() {
            if (isFinishing) return
            findViewById<TextView>(R.id.tvUninstallThreatBody)?.text =
                threats[Random.nextInt(threats.size)]
            handler.postDelayed(this, 2_400L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uninstall_threat)
        applyFullscreenLock()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { }
            },
        )

        findViewById<TextView>(R.id.tvUninstallThreatTitle).text =
            intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
                ?: hon(R.string.uninstall_threat_screen_title)
        findViewById<TextView>(R.id.tvUninstallThreatBody).text =
            intent.getStringExtra(EXTRA_BODY)?.takeIf { it.isNotBlank() }
                ?: hon(R.string.uninstall_threat_screen_body)
        findViewById<Button>(R.id.btnUninstallSurrender).text = hon(R.string.uninstall_threat_surrender)
        findViewById<Button>(R.id.btnUninstallSurrender).setOnClickListener {
            vibrating = false
            Toast.makeText(this, hon(R.string.uninstall_guard_continue_toast), Toast.LENGTH_LONG).show()
            finish()
        }

        handler.post(vibrateRunnable)
        handler.post(rotateRunnable)

        val autoFinish = intent.getLongExtra(EXTRA_AUTO_FINISH_MS, 9_000L).coerceAtLeast(3_000L)
        handler.postDelayed({
            if (!isFinishing) {
                Toast.makeText(this, hon(R.string.uninstall_guard_continue_toast), Toast.LENGTH_LONG).show()
                finish()
            }
        }, autoFinish)
    }

    override fun onDestroy() {
        vibrating = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun applyFullscreenLock() {
        window.setFormat(PixelFormat.OPAQUE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val EXTRA_AUTO_FINISH_MS = "auto_finish_ms"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
    }
}
