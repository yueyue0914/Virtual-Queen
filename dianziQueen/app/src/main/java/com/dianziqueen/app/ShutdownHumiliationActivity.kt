package com.dianziqueen.app

import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import kotlin.random.Random

/**
 * 企图关机时全屏羞辱 + 震动 + TTS 骂人。
 */
class ShutdownHumiliationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var insultIndex = 0
    private var vibrating = false

    private lateinit var tvThreat: TextView
    private lateinit var btnSurrender: Button

    private val threats by lazy { resources.getStringArray(R.array.shutdown_threats) }
    private val voiceInsults by lazy { resources.getStringArray(R.array.shutdown_voice_insults) }

    private val vibrateRunnable = object : Runnable {
        override fun run() {
            if (!vibrating) return
            pulseVibrate()
            handler.postDelayed(this, 900L)
        }
    }

    private val rotateThreatRunnable = object : Runnable {
        override fun run() {
            if (isFinishing) return
            tvThreat.text = threats[Random.nextInt(threats.size)]
            handler.postDelayed(this, 2800L)
        }
    }

    private val speakRunnable = object : Runnable {
        override fun run() {
            if (isFinishing || !ttsReady) return
            val line = voiceInsults[insultIndex % voiceInsults.size]
            insultIndex++
            tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "queen_insult_$insultIndex")
            handler.postDelayed(this, 3200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shutdown_humiliation)

        applyFullscreenLock()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { }
            },
        )

        tvThreat = findViewById(R.id.tvShutdownThreat)
        btnSurrender = findViewById(R.id.btnSurrender)

        tvThreat.text = threats.first()
        btnSurrender.setOnClickListener { finish() }

        tts = TextToSpeech(this, this)
        startAssault()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.CHINESE
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(0.92f)
        }
    }

    private fun applyFullscreenLock() {
        window.setFormat(PixelFormat.OPAQUE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun startAssault() {
        vibrating = true
        handler.post(vibrateRunnable)
        handler.post(rotateThreatRunnable)
        handler.postDelayed(speakRunnable, 400L)
    }

    private fun stopAssault() {
        vibrating = false
        handler.removeCallbacks(vibrateRunnable)
        handler.removeCallbacks(rotateThreatRunnable)
        handler.removeCallbacks(speakRunnable)
        vibrator()?.cancel()
    }

    private fun pulseVibrate() {
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        v.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 120, 60, 180, 60, 260),
                intArrayOf(0, 200, 0, 255, 0, 255),
                -1,
            ),
        )
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onDestroy() {
        stopAssault()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SOURCE = "source"
    }
}
