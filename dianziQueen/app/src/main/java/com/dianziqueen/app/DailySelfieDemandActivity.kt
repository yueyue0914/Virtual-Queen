package com.dianziqueen.app

import android.Manifest
import android.content.ActivityNotFoundException
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
    private val photoDeleteHelper = PhotoDeleteHelper.Helper(this)

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
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        DailySelfieEnforcement.cameraCaptureInProgress = false
        val file = pendingCaptureFile
        val uri = pendingCaptureUri
        pendingCaptureFile = null
        pendingCaptureUri = null
        QueenCameraCapture.handleCaptureResult(
            this,
            result.resultCode,
            result.data,
            file,
            uri,
            onSuccess = { bytes -> importBytesAndFinish(bytes, SubmissionSource.CAMERA) },
            onFailure = {
                Toast.makeText(this, R.string.daily_selfie_capture_failed, Toast.LENGTH_SHORT).show()
                scheduleRelaunch(150L)
            },
        )
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        DailySelfieEnforcement.galleryPickInProgress = false
        if (uri == null) {
            scheduleRelaunch(150L)
            return@registerForActivityResult
        }
        PhotoDeleteHelper.takePersistableAccess(this, uri)
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
        importBytesAndFinish(bytes, SubmissionSource.GALLERY, gallerySourceUri = uri)
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
        val shouldRelaunch = !uploadCompleted &&
            !DailySelfieEnforcement.externalFlowInProgress() &&
            DailySelfieScheduler.shouldEnforce(this)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
        if (shouldRelaunch) {
            Handler(Looper.getMainLooper()).postDelayed({
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
        setShowWhenLocked(true)
        setTurnScreenOn(true)
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
        val intent = QueenCameraCapture.launchableCaptureIntent(this, uri)
        if (intent == null) {
            QueenCameraCapture.cleanupCapture(this, file, uri)
            Toast.makeText(this, R.string.daily_selfie_no_camera, Toast.LENGTH_LONG).show()
            return
        }
        pendingCaptureFile = file
        pendingCaptureUri = uri
        DailySelfieEnforcement.cameraCaptureInProgress = true
        handler.removeCallbacks(relaunchRunnable)
        try {
            takePictureLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            DailySelfieEnforcement.cameraCaptureInProgress = false
            pendingCaptureFile = null
            pendingCaptureUri = null
            QueenCameraCapture.cleanupCapture(this, file, uri)
            Toast.makeText(this, R.string.daily_selfie_no_camera, Toast.LENGTH_LONG).show()
            scheduleRelaunch(150L)
        }
    }

    private fun launchGalleryPick() {
        DailySelfieEnforcement.galleryPickInProgress = true
        handler.removeCallbacks(relaunchRunnable)
        pickImageLauncher.launch(arrayOf("image/*"))
    }

    private fun importBytesAndFinish(
        bytes: ByteArray,
        source: SubmissionSource,
        gallerySourceUri: Uri? = null,
    ) {
        QueenAlbumVault.ensureMasterKey(this)
        val id = QueenAlbumVault.importPlainBytes(this, bytes)
        if (id == null) {
            showStatusMessage(getString(R.string.daily_selfie_import_failed), finishAfterMs = 1_500L)
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
        if (source == SubmissionSource.GALLERY && gallerySourceUri != null) {
            photoDeleteHelper.deleteAfterVaultImport(gallerySourceUri) { deleted ->
                runOnUiThread {
                    val msg = if (deleted) {
                        getString(R.string.daily_selfie_upload_ok, points)
                    } else {
                        getString(R.string.daily_selfie_upload_delete_failed)
                    }
                    showStatusMessage(msg, finishAfterMs = 1_800L)
                }
            }
            return
        }
        val msg = when (source) {
            SubmissionSource.CAMERA -> getString(R.string.daily_selfie_capture_ok, points)
            SubmissionSource.GALLERY -> getString(R.string.daily_selfie_upload_ok, points)
        }
        showStatusMessage(msg, finishAfterMs = 1_800L)
    }

    /** 应用内状态提示，避免 MIUI 等对 Toast.show 打印 callstack。 */
    private fun showStatusMessage(message: String, finishAfterMs: Long = 0L) {
        findViewById<android.widget.TextView>(R.id.dailySelfieMessage)?.text = message
        findViewById<Button>(R.id.dailySelfieCaptureButton)?.isEnabled = false
        findViewById<Button>(R.id.dailySelfieUploadButton)?.isEnabled = false
        if (finishAfterMs <= 0L) return
        handler.postDelayed({
            if (!isFinishing) finish()
        }, finishAfterMs)
    }
}
