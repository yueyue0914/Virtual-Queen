package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 消息附带相册图：全屏马赛克；点击并确认后扣积分查看无码。
 */
class MessagePhotoFullscreenActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var hint: TextView

    private var messageId: String = ""
    private var photoId: String = ""
    private var revealed: Boolean = false
    private var showingClear: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_photo)

        messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty()
        photoId = intent.getStringExtra(EXTRA_PHOTO_ID).orEmpty()
        revealed = intent.getBooleanExtra(EXTRA_ALREADY_REVEALED, false)

        if (messageId.isEmpty() || photoId.isEmpty()) {
            finish()
            return
        }

        imageView = findViewById(R.id.messagePhotoImage)
        progress = findViewById(R.id.messagePhotoProgress)
        hint = findViewById(R.id.messagePhotoHint)

        if (revealed) {
            loadClear()
        } else {
            loadMosaic()
        }

        imageView.setOnClickListener { onImageTapped() }
        hint.setOnClickListener { onImageTapped() }
    }

    private fun onImageTapped() {
        if (showingClear) {
            finish()
            return
        }
        if (revealed) {
            loadClear()
            return
        }
        val cost = QueenMessageStore.PHOTO_VIEW_COST
        val points = QueenPointsStore.getPoints(this)
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.message_photo_reveal_confirm, cost, points))
            .setPositiveButton(android.R.string.ok) { _, _ -> tryReveal() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun tryReveal() {
        val cost = QueenMessageStore.PHOTO_VIEW_COST
        if (!QueenPointsStore.trySpend(this, cost)) {
            Toast.makeText(
                this,
                getString(R.string.album_points_insufficient, cost, QueenPointsStore.getPoints(this)),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        QueenMessageStore.markMessagePhotoRevealed(this, messageId)
        revealed = true
        setResult(RESULT_OK)
        loadClear()
    }

    private fun loadMosaic() {
        showingClear = false
        progress.visibility = View.VISIBLE
        imageView.setImageDrawable(null)
        hint.text = getString(
            R.string.message_photo_fullscreen_hint,
            QueenMessageStore.PHOTO_VIEW_COST,
        )
        QueenMessagePhotoLoader.loadMosaicForFullscreen(this, photoId) { bmp ->
            if (isFinishing) return@loadMosaicForFullscreen
            progress.visibility = View.GONE
            if (bmp == null) {
                Toast.makeText(this, R.string.message_photo_missing, Toast.LENGTH_SHORT).show()
                finish()
                return@loadMosaicForFullscreen
            }
            imageView.setImageBitmap(bmp)
        }
    }

    private fun loadClear() {
        showingClear = true
        progress.visibility = View.VISIBLE
        hint.text = getString(R.string.message_photo_revealed_hint)
        QueenMessagePhotoLoader.loadClearForFullscreen(this, photoId) { bmp ->
            if (isFinishing) return@loadClearForFullscreen
            progress.visibility = View.GONE
            if (bmp == null) {
                if (revealed) {
                    QueenPointsStore.addPoints(this, QueenMessageStore.PHOTO_VIEW_COST)
                }
                Toast.makeText(this, R.string.album_view_decode_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@loadClearForFullscreen
            }
            imageView.setImageBitmap(bmp)
        }
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PHOTO_ID = "photo_id"
        const val EXTRA_ALREADY_REVEALED = "already_revealed"

        fun intent(
            context: Context,
            messageId: String,
            photoId: String,
            alreadyRevealed: Boolean,
        ): Intent =
            Intent(context, MessagePhotoFullscreenActivity::class.java)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
                .putExtra(EXTRA_PHOTO_ID, photoId)
                .putExtra(EXTRA_ALREADY_REVEALED, alreadyRevealed)
    }
}
