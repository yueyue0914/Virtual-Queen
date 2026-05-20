package com.dianziqueen.app

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class QueenSettingsActivity : AppCompatActivity() {

    private lateinit var galleryCountText: TextView
    private lateinit var calendarCountText: TextView
    private lateinit var releaseBodyText: TextView
    private lateinit var releaseButton: Button

    private lateinit var floatStyleOptions: List<FloatStyleOption>

    private data class FloatStyleOption(
        val container: LinearLayout,
        val style: QueenFloatingAvatarStyle,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queen_settings)

        galleryCountText = findViewById(R.id.settingsGalleryCountText)
        calendarCountText = findViewById(R.id.settingsCalendarCountText)
        releaseBodyText = findViewById(R.id.settingsReleaseBodyText)
        releaseButton = findViewById(R.id.settingsReleaseButton)

        floatStyleOptions = listOf(
            FloatStyleOption(findViewById(R.id.settingsFloatStyleDefault), QueenFloatingAvatarStyle.DEFAULT),
            FloatStyleOption(findViewById(R.id.settingsFloatStyleNvw1), QueenFloatingAvatarStyle.NVW1),
            FloatStyleOption(findViewById(R.id.settingsFloatStyleNvw2), QueenFloatingAvatarStyle.NVW2),
            FloatStyleOption(findViewById(R.id.settingsFloatStyleNvw3), QueenFloatingAvatarStyle.NVW3),
        )
        for (option in floatStyleOptions) {
            option.container.setOnClickListener { selectFloatAvatarStyle(option.style) }
        }

        QueenFloatingAvatarStyle.NVW1.applyTo(findViewById(R.id.settingsFloatPreviewNvw1))
        QueenFloatingAvatarStyle.NVW2.applyTo(findViewById<ImageView>(R.id.settingsFloatPreviewNvw2))
        QueenFloatingAvatarStyle.NVW3.applyTo(findViewById(R.id.settingsFloatPreviewNvw3))

        releaseButton.setOnClickListener { confirmAndRelease() }
        refreshStats()
        refreshFloatAvatarSelectionUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        updateReleaseButtonLabel()
        refreshFloatAvatarSelectionUi()
    }

    private fun selectFloatAvatarStyle(style: QueenFloatingAvatarStyle) {
        if (QueenFloatingAvatarStyle.current(this) == style) return
        QueenFloatingAvatarStyle.set(this, style)
        if (getSharedPreferences(Prefs.NAME, MODE_PRIVATE).getBoolean(Prefs.ACTIVATED, false)) {
            QueenService.start(this)
        }
        refreshFloatAvatarSelectionUi()
        Toast.makeText(this, R.string.settings_float_avatar_applied, Toast.LENGTH_SHORT).show()
    }

    private fun refreshFloatAvatarSelectionUi() {
        val current = QueenFloatingAvatarStyle.current(this)
        for (option in floatStyleOptions) {
            option.container.setBackgroundResource(
                if (current == option.style) {
                    R.drawable.bg_queen_float_style_option_selected
                } else {
                    R.drawable.bg_queen_float_style_option
                },
            )
        }
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
