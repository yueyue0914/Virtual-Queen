package com.dianziqueen.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * 宣言验证：须输入正确宣言才能继续使用；Home/返回/切 App 会立即被拉回。
 * 拦截开关由 [DeclarationInterceptor] 统一管理，通过后立即关闭。
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
        if (!DeclarationInterceptor.shouldReassertBlocking(applicationContext)) return@Runnable
        DeclarationEnforcement.bringToFront(applicationContext)
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

        DeclarationInterceptor.startChallenge()

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
        if (!DeclarationScheduler.shouldBlockUsage(this) &&
            !DeclarationScheduler.isDue(this)
        ) {
            DeclarationEnforcement.challengeInForeground = false
            DeclarationInterceptor.reset()
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (DeclarationInterceptor.shouldReassertBlocking(this)) {
            reassertBlockingImmediate()
        }
    }

    override fun onPause() {
        super.onPause()
        // 不在 onPause 拉回：输入法/通知栏等会误触发，且会把 challengeInForeground  prematurely 置 false
    }

    override fun onStop() {
        super.onStop()
        DeclarationEnforcement.challengeInForeground = false
        if (DeclarationInterceptor.shouldReassertBlocking(this)) {
            reassertBlockingImmediate()
        } else {
            handler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        DeclarationEnforcement.challengeInForeground = false
        handler.removeCallbacksAndMessages(null)
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

        DeclarationInterceptor.finishChallengeSuccess()
        passed = true
        closing = true
        handler.removeCallbacksAndMessages(null)

        backPressedCallback.isEnabled = false
        submitButton.isEnabled = false
        declarationInput.isEnabled = false

        DeclarationScheduler.markChallengePassed(this)
        Toast.makeText(
            this,
            getString(
                R.string.declaration_challenge_ok_fmt,
                QueenPointsStore.DECLARATION_PASS_POINTS,
            ),
            Toast.LENGTH_SHORT,
        ).show()

        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            try {
                finishAndRemoveTask()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            } catch (_: Exception) {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun isFinishingOrClosed(): Boolean = passed || closing || isFinishing

    private fun closeActivitySafely() {
        if (isFinishing) return
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            try {
                finishAndRemoveTask()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            } catch (_: Exception) {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun reassertBlockingImmediate() {
        if (isFinishingOrClosed()) return
        if (!DeclarationInterceptor.shouldReassertBlocking(this)) return
        handler.removeCallbacks(relaunchRunnable)
        handler.postDelayed({
            if (!isFinishingOrClosed() &&
                DeclarationInterceptor.shouldReassertBlocking(applicationContext)
            ) {
                DeclarationEnforcement.bringToFront(applicationContext)
            }
        }, DeclarationEnforcement.REASSERT_DELAY_MS)
    }

    private fun punishEscapeAttempt() {
        if (isFinishingOrClosed()) return
        if (!DeclarationInterceptor.shouldReassertBlocking(this)) return
        vibratePunish()
        Toast.makeText(this, R.string.declaration_challenge_escape_denied, Toast.LENGTH_SHORT).show()
        reassertBlockingImmediate()
    }

    private fun vibratePunish() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = getSystemService(VibratorManager::class.java) ?: return
            mgr.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 180, 80, 220), -1),
        )
    }
}
