package com.dianziqueen.app

import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
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
 * 全屏宣言验证：须输入正确宣言才能继续使用手机；Home/返回/切 App 会立即被拉回。
 */
class DeclarationChallengeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var passed = false
    private var closing = false
    private lateinit var requiredText: String
    private lateinit var submitButton: Button
    private lateinit var declarationInput: EditText

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            punishEscapeAttempt()
        }
    }

    private val relaunchRunnable = Runnable {
        if (isFinishingOrClosed()) return@Runnable
        if (!DeclarationEnforcement.shouldReassertBlocking(applicationContext)) return@Runnable
        DeclarationEnforcement.bringToFront(applicationContext)
    }

    private val finishRunnable = Runnable {
        closeActivitySafely()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeclarationScheduler.isEnabled(this) &&
            !DeclarationScheduler.isPending(this)
        ) {
            finish()
            return
        }
        requiredText = intent.getStringExtra(DeclarationScheduler.EXTRA_REQUIRED_DECLARATION)
            ?: DeclarationScheduler.currentDeclarationText(this)
        setContentView(R.layout.activity_declaration_challenge)
        applyBlockingWindow()

        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        bindUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isFinishingOrClosed()) {
            closeActivitySafely()
            return
        }
        setIntent(intent)
        intent.getStringExtra(DeclarationScheduler.EXTRA_REQUIRED_DECLARATION)?.let {
            requiredText = it
            findViewById<TextView>(R.id.tvDeclaration)?.text =
                getString(R.string.declaration_challenge_prompt_fmt, requiredText)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishingOrClosed()) {
            closeActivitySafely()
            return
        }
        DeclarationEnforcement.challengeInForeground = true
        DeclarationHardBlockHelper.enter(this)
        if (!DeclarationScheduler.shouldBlockUsage(this) &&
            !DeclarationScheduler.isDue(this)
        ) {
            DeclarationEnforcement.challengeInForeground = false
            DeclarationHardBlockHelper.exit(this)
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        reassertBlockingImmediate()
    }

    override fun onPause() {
        super.onPause()
        DeclarationEnforcement.challengeInForeground = false
        reassertBlockingImmediate()
    }

    override fun onStop() {
        super.onStop()
        DeclarationEnforcement.challengeInForeground = false
        reassertBlockingImmediate()
    }

    override fun onDestroy() {
        DeclarationEnforcement.challengeInForeground = false
        handler.removeCallbacksAndMessages(null)
        if (!isFinishingOrClosed()) {
            DeclarationHardBlockHelper.exit(this)
        }
        super.onDestroy()
    }

    private fun bindUi() {
        findViewById<TextView>(R.id.declarationTitle).text =
            getString(R.string.declaration_challenge_title)
        findViewById<TextView>(R.id.tvDeclaration).text =
            getString(R.string.declaration_challenge_prompt_fmt, requiredText)
        declarationInput = findViewById(R.id.etDeclaration)
        submitButton = findViewById(R.id.btnSubmit)
        submitButton.setOnClickListener {
            if (isFinishingOrClosed()) return@setOnClickListener
            val typed = declarationInput.text?.toString()?.trim().orEmpty()
            if (typed.equals(requiredText, ignoreCase = true)) {
                onDeclarationSuccess()
            } else {
                Toast.makeText(this, R.string.declaration_challenge_wrong, Toast.LENGTH_LONG).show()
                declarationInput.text?.clear()
                vibratePunish()
            }
        }
    }

    private fun onDeclarationSuccess() {
        if (isFinishingOrClosed()) return
        closing = true
        passed = true
        backPressedCallback.isEnabled = false
        submitButton.isEnabled = false
        declarationInput.isEnabled = false
        handler.removeCallbacksAndMessages(null)
        DeclarationScheduler.markChallengePassed(this)
        DeclarationHardBlockHelper.exit(this)
        Toast.makeText(this, R.string.declaration_challenge_ok, Toast.LENGTH_SHORT).show()
        handler.postDelayed(finishRunnable, FINISH_DELAY_MS)
    }

    /** 在主线程延迟关闭，避免部分机型 finish 与 onPause/拉回竞态。 */
    private fun closeActivitySafely() {
        if (isFinishing) return
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask()
                } else {
                    @Suppress("DEPRECATION")
                    finish()
                }
            } catch (_: Exception) {
                finish()
            }
        }
    }

    private fun isFinishingOrClosed(): Boolean = passed || closing || isFinishing

    private fun reassertBlockingImmediate() {
        if (isFinishingOrClosed()) return
        if (!DeclarationEnforcement.shouldReassertBlocking(applicationContext)) return
        handler.removeCallbacks(relaunchRunnable)
        handler.post {
            if (!isFinishingOrClosed() &&
                DeclarationEnforcement.shouldReassertBlocking(applicationContext)
            ) {
                DeclarationEnforcement.bringToFront(applicationContext)
            }
        }
        handler.postDelayed(relaunchRunnable, DeclarationEnforcement.REASSERT_DELAY_MS)
    }

    private fun punishEscapeAttempt() {
        if (isFinishingOrClosed()) return
        vibratePunish()
        Toast.makeText(this, R.string.declaration_challenge_escape_denied, Toast.LENGTH_SHORT).show()
        reassertBlockingImmediate()
    }

    private fun applyBlockingWindow() {
        window.setFormat(PixelFormat.OPAQUE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun vibratePunish() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = getSystemService(VibratorManager::class.java) ?: return
            mgr.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 180, 80, 220), -1),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 180, 80, 220), -1)
        }
    }

    companion object {
        private const val FINISH_DELAY_MS = 300L
    }
}
