package com.dianziqueen.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class QueenSettingsActivity : AppCompatActivity() {

    private lateinit var galleryCountText: TextView
    private lateinit var calendarCountText: TextView
    private lateinit var releaseBodyText: TextView
    private lateinit var releaseButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queen_settings)

        galleryCountText = findViewById(R.id.settingsGalleryCountText)
        calendarCountText = findViewById(R.id.settingsCalendarCountText)
        releaseBodyText = findViewById(R.id.settingsReleaseBodyText)
        releaseButton = findViewById(R.id.settingsReleaseButton)

        releaseButton.setOnClickListener { confirmAndRelease() }
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        updateReleaseButtonLabel()
    }

    private fun refreshStats() {
        val gallery = QueenInjectionRegistry.getGalleryCount(this)
        val calendar = QueenInjectionRegistry.getCalendarCount(this)
        galleryCountText.text = getString(R.string.settings_gallery_count_fmt, gallery)
        calendarCountText.text = getString(R.string.settings_calendar_count_fmt, calendar)
    }

    private fun updateReleaseButtonLabel() {
        val cost = QueenReleaseManager.RELEASE_COST_POINTS
        val points = QueenPointsStore.getPoints(this)
        releaseBodyText.text = getString(R.string.settings_release_body, cost)
        releaseButton.text = getString(R.string.settings_release_btn_fmt, cost, points)
    }

    private fun confirmAndRelease() {
        val cost = QueenReleaseManager.RELEASE_COST_POINTS
        val points = QueenPointsStore.getPoints(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_release_confirm_title)
            .setMessage(getString(R.string.settings_release_confirm_msg, cost, points))
            .setPositiveButton(android.R.string.ok) { _, _ -> performRelease() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performRelease() {
        when (QueenReleaseManager.performRelease(this)) {
            is QueenReleaseManager.ReleaseResult.Success -> {
                Toast.makeText(this, R.string.settings_release_ok, Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            }
            QueenReleaseManager.ReleaseResult.NotActivated -> {
                Toast.makeText(this, R.string.settings_release_not_activated, Toast.LENGTH_SHORT).show()
                finish()
            }
            QueenReleaseManager.ReleaseResult.InsufficientPoints -> {
                Toast.makeText(
                    this,
                    getString(
                        R.string.settings_release_points_insufficient,
                        QueenReleaseManager.RELEASE_COST_POINTS,
                        QueenPointsStore.getPoints(this),
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                updateReleaseButtonLabel()
            }
        }
    }
}
