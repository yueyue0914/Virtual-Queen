package com.dianziqueen.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

/**
 * 每日强制上缴自拍凭证：不可关闭；离屏（如按 Home）立即重新置顶。
 * 现场拍摄奖励较多积分；从相册上传仅奖励 1 积分。
 */
class DailySelfieDemandActivity : AppCompatActivity() {

    private enum class SubmissionSource {
        CAMERA,
        GALLERY,
    }

    private val handler = Handler(Looper.getMainLooper())
    private var uploadCompleted = false
    private var pendingCaptureFile: File? = null
    private var pendingCaptureUri: Uri? = null

    private val relaunchRunnable = Runnable {
        if (uploadCompleted || DailySelfieEnforcement.externalFlowInProgress()) return@Runnable
        if (DailySelfieScheduler.shouldEnforce(this)) {
            DailySelfieEnforcement.bringDemandToFront(this)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else {
            Toast.makeText(this, R.string.daily_selfie_camera_denied, Toast.LENGTH_LONG).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        DailySelfieEnforcement.cameraCaptureInProgress = false
        val file = pendingCaptureFile
        val uri = pendingCaptureUri
        pendingCaptureFile = null
        pendingCaptureUri = null
        if (!success || file == null || !file.exists() || file.length() <= 0L) {
            Toast.makeText(this, R.string.daily_selfie_capture_failed, Toast.LENGTH_SHORT).show()
            scheduleRelaunch(150L)
            return@registerForActivityResult
        }
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
        try {
            file.delete()
        } catch (_: Exception) { }
        if (uri != null) {
            try {
                contentResolver.delete(uri, null, null)
            } catch (_: Exception) { }
        }
        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(this, R.string.daily_selfie_capture_failed, Toast.LENGTH_SHORT).show()
            scheduleRelaunch(150L)
            return@registerForActivityResult
        }
        importBytesAndFinish(bytes, SubmissionSource.CAMERA)
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        DailySelfieEnforcement.galleryPickInProgress = false
        if (uri == null) {
            scheduleRelaunch(150L)
            return@registerForActivityResult
        }
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(this, R.string.daily_selfie_import_failed, Toast.LENGTH_SHORT).show()
            scheduleRelaunch(150L)
            return@registerForActivityResult
        }
        importBytesAndFinish(bytes, SubmissionSource.GALLERY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DailySelfieScheduler.shouldEnforce(this)) {
            finish()
            return
        }
        DailySelfieEnforcement.demandActivityVisible = true
        setContentView(R.layout.activity_daily_selfie_demand)
        applyBlockingWindow()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { }
            },
        )

        findViewById<Button>(R.id.dailySelfieCaptureButton).apply {
            text = getString(
                R.string.daily_selfie_capture_btn,
                QueenPointsStore.DAILY_SELFIE_CAPTURE_POINTS,
            )
            setOnClickListener { requestCameraAndCapture() }
        }
        findViewById<Button>(R.id.dailySelfieUploadButton).apply {
            text = getString(
                R.string.daily_selfie_upload_btn,
                QueenPointsStore.DAILY_SELFIE_UPLOAD_POINTS,
            )
            setOnClickListener { launchGalleryPick() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!uploadCompleted && !DailySelfieScheduler.shouldEnforce(this)) {
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        scheduleRelaunchIfEscaped()
    }

    override fun onPause() {
        super.onPause()
        scheduleRelaunchIfEscaped()
    }

    override fun onStop() {
        super.onStop()
        scheduleRelaunchIfEscaped()
    }

    override fun onDestroy() {
        DailySelfieEnforcement.demandActivityVisible = false
        handler.removeCallbacks(relaunchRunnable)
        super.onDestroy()
        if (!uploadCompleted && !DailySelfieEnforcement.externalFlowInProgress() &&
            DailySelfieScheduler.shouldEnforce(this)
        ) {
            handler.postDelayed({
                DailySelfieEnforcement.bringDemandToFront(applicationContext)
            }, 200L)
        }
    }

    private fun scheduleRelaunchIfEscaped() {
        if (uploadCompleted || DailySelfieEnforcement.externalFlowInProgress()) return
        if (!DailySelfieScheduler.shouldEnforce(this)) return
        scheduleRelaunch(280L)
    }

    private fun scheduleRelaunch(delayMs: Long) {
        handler.removeCallbacks(relaunchRunnable)
        handler.postDelayed(relaunchRunnable, delayMs)
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

    private fun requestCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        val (file, uri) = QueenCameraCapture.createOutputFile(this, "queen_daily_selfie")
        if (!QueenCameraCapture.canCapture(this, uri)) {
            Toast.makeText(this, R.string.daily_selfie_no_camera, Toast.LENGTH_LONG).show()
            return
        }
        pendingCaptureFile = file
        pendingCaptureUri = uri
        DailySelfieEnforcement.cameraCaptureInProgress = true
        handler.removeCallbacks(relaunchRunnable)
        takePictureLauncher.launch(uri)
    }

    private fun launchGalleryPick() {
        DailySelfieEnforcement.galleryPickInProgress = true
        handler.removeCallbacks(relaunchRunnable)
        pickImageLauncher.launch("image/*")
    }

    private fun importBytesAndFinish(bytes: ByteArray, source: SubmissionSource) {
        QueenAlbumVault.ensureMasterKey(this)
        val id = QueenAlbumVault.importPlainBytes(this, bytes)
        if (id == null) {
            Toast.makeText(this, R.string.daily_selfie_import_failed, Toast.LENGTH_SHORT).show()
            scheduleRelaunch(150L)
            return
        }
        val points = when (source) {
            SubmissionSource.CAMERA -> QueenPointsStore.DAILY_SELFIE_CAPTURE_POINTS
            SubmissionSource.GALLERY -> QueenPointsStore.DAILY_SELFIE_UPLOAD_POINTS
        }
        QueenPointsStore.addPoints(this, points)
        uploadCompleted = true
        handler.removeCallbacks(relaunchRunnable)
        DailySelfieScheduler.markSubmittedToday(this)
        val toastRes = when (source) {
            SubmissionSource.CAMERA -> R.string.daily_selfie_capture_ok
            SubmissionSource.GALLERY -> R.string.daily_selfie_upload_ok
        }
        Toast.makeText(this, getString(toastRes, points), Toast.LENGTH_LONG).show()
        finish()
    }
}
