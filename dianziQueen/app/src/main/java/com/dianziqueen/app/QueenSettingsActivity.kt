package com.dianziqueen.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class QueenSettingsActivity : AppCompatActivity() {

    private lateinit var galleryCountText: TextView
    private lateinit var calendarCountText: TextView
    private lateinit var releaseBodyText: TextView
    private lateinit var releaseButton: Button
    private lateinit var honorificCurrentText: TextView
    private lateinit var honorificChangeButton: Button
    private lateinit var strongControlStatusText: TextView
    private lateinit var strongControlDisableButton: Button
    private lateinit var declarationEnabledSwitch: SwitchCompat
    private lateinit var declarationModeRandom: RadioButton
    private lateinit var declarationModeFixed: RadioButton
    private lateinit var declarationRandomMinEdit: EditText
    private lateinit var declarationRandomMaxEdit: EditText
    private lateinit var declarationFixedEdit: EditText
    private lateinit var declarationPreviewText: TextView

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
        honorificCurrentText = findViewById(R.id.settingsHonorificCurrentText)
        honorificChangeButton = findViewById(R.id.settingsHonorificChangeButton)
        strongControlStatusText = findViewById(R.id.settingsStrongControlStatusText)
        strongControlDisableButton = findViewById(R.id.settingsStrongControlDisableButton)
        declarationEnabledSwitch = findViewById(R.id.settingsDeclarationEnabledSwitch)
        declarationModeRandom = findViewById(R.id.settingsDeclarationModeRandom)
        declarationModeFixed = findViewById(R.id.settingsDeclarationModeFixed)
        declarationRandomMinEdit = findViewById(R.id.settingsDeclarationRandomMinEdit)
        declarationRandomMaxEdit = findViewById(R.id.settingsDeclarationRandomMaxEdit)
        declarationFixedEdit = findViewById(R.id.settingsDeclarationFixedEdit)
        declarationPreviewText = findViewById(R.id.settingsDeclarationPreviewText)

        findViewById<Button>(R.id.settingsDeclarationSaveButton).setOnClickListener {
            saveDeclarationSettings()
        }

        strongControlDisableButton.setOnClickListener { confirmDisableStrongControl() }

        honorificChangeButton.setOnClickListener {
            QueenHonorific.showPicker(this) {
                refreshHonorificSection()
                refreshReleaseTexts()
                applyHonorificToStaticLabels()
                refreshFloatAvatarStyleLabels()
                QueenFloatingOverlay.refreshHonorificLabels()
            }
        }

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
        findViewById<Button>(R.id.settingsPermCheckButton).setOnClickListener {
            startActivity(PermissionCheckActivity.createIntent(this))
        }
        refreshStats()
        refreshFloatAvatarSelectionUi()
        refreshHonorificSection()
        refreshReleaseTexts()
        applyHonorificToStaticLabels()
        refreshFloatAvatarStyleLabels()
        refreshStrongControlSection()
        loadDeclarationSettingsUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        updateReleaseButtonLabel()
        refreshFloatAvatarSelectionUi()
        refreshHonorificSection()
        refreshReleaseTexts()
        applyHonorificToStaticLabels()
        refreshFloatAvatarStyleLabels()
        refreshStrongControlSection()
        refreshDeclarationStatusUi()
    }

    private fun loadDeclarationSettingsUi() {
        declarationEnabledSwitch.isChecked = DeclarationScheduler.isEnabled(this)
        when (DeclarationScheduler.currentMode(this)) {
            DeclarationScheduler.MODE_FIXED -> declarationModeFixed.isChecked = true
            else -> declarationModeRandom.isChecked = true
        }
        declarationRandomMinEdit.setText(
            DeclarationScheduler.randomMinMinutes(this).toString(),
        )
        declarationRandomMaxEdit.setText(
            DeclarationScheduler.randomMaxMinutes(this).toString(),
        )
        declarationFixedEdit.setText(
            DeclarationScheduler.fixedMinutes(this).toString(),
        )
        refreshDeclarationStatusUi()
    }

    private fun refreshDeclarationStatusUi() {
        declarationPreviewText.text = getString(
            R.string.settings_declaration_preview_random_fmt,
            DeclarationScheduler.previewDeclarationSample(this),
            DeclarationTemplateLibrary.templateCount(),
        )
    }

    private fun saveDeclarationSettings() {
        val min = declarationRandomMinEdit.text?.toString()?.toIntOrNull()
            ?: DeclarationScheduler.DEFAULT_RANDOM_MIN_MINUTES
        val max = declarationRandomMaxEdit.text?.toString()?.toIntOrNull()
            ?: DeclarationScheduler.DEFAULT_RANDOM_MAX_MINUTES
        val fixed = declarationFixedEdit.text?.toString()?.toIntOrNull()
            ?: DeclarationScheduler.DEFAULT_FIXED_MINUTES
        if (min < 1 || max < min) {
            Toast.makeText(this, R.string.settings_declaration_invalid_range, Toast.LENGTH_LONG).show()
            return
        }
        DeclarationScheduler.setEnabled(this, declarationEnabledSwitch.isChecked)
        val mode = if (declarationModeFixed.isChecked) {
            DeclarationScheduler.MODE_FIXED
        } else {
            DeclarationScheduler.MODE_RANDOM
        }
        DeclarationScheduler.setMode(this, mode)
        DeclarationScheduler.setRandomRange(this, min, max)
        DeclarationScheduler.setFixedMinutes(this, fixed.coerceAtLeast(1))
        Toast.makeText(this, R.string.settings_declaration_saved, Toast.LENGTH_SHORT).show()
        refreshDeclarationStatusUi()
    }

    private fun refreshStrongControlSection() {
        val activated = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getBoolean(Prefs.ACTIVATED, false)
        val enabled = SettingsLockGuard.isStrongControlEnabled(this)
        strongControlStatusText.text = if (enabled) {
            hon(R.string.settings_strong_control_status_on)
        } else {
            hon(R.string.settings_strong_control_status_off)
        }
        strongControlDisableButton.visibility =
            if (activated && enabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun confirmDisableStrongControl() {
        val input = EditText(this).apply {
            hint = getString(R.string.settings_strong_control_password_hint)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(hon(R.string.settings_strong_control_disable_title))
            .setMessage(hon(R.string.settings_strong_control_disable_message))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pwd = input.text?.toString().orEmpty()
                if (SettingsLockGuard.verifyDisablePassword(pwd)) {
                    SettingsLockGuard.disableStrongControl(this)
                    Toast.makeText(
                        this,
                        hon(R.string.settings_strong_control_disabled_toast),
                        Toast.LENGTH_LONG,
                    ).show()
                    refreshStrongControlSection()
                } else {
                    Toast.makeText(
                        this,
                        hon(R.string.settings_strong_control_password_wrong),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyHonorificToStaticLabels() {
        findViewById<TextView>(R.id.settingsPageTitle).text = hon(R.string.settings_title)
        findViewById<TextView>(R.id.settingsPageSubtitle).text = hon(R.string.settings_subtitle)
        findViewById<TextView>(R.id.settingsInjectionNote).text = hon(R.string.settings_injection_note)
        findViewById<TextView>(R.id.settingsFloatAvatarTitle)?.text =
            hon(R.string.settings_float_avatar_title)
    }

    private fun refreshHonorificSection() {
        honorificCurrentText.text = getString(
            R.string.honorific_settings_current_fmt,
            QueenHonorific.displayName(this),
        )
    }

    private fun refreshFloatAvatarStyleLabels() {
        findViewById<TextView>(R.id.settingsFloatStyleNvw1Label)?.text =
            hon(R.string.settings_float_avatar_nvw1_label)
        findViewById<TextView>(R.id.settingsFloatStyleNvw2Label)?.text =
            hon(R.string.settings_float_avatar_nvw2_label)
        findViewById<TextView>(R.id.settingsFloatStyleNvw3Label)?.text =
            hon(R.string.settings_float_avatar_nvw3_label)
    }

    private fun refreshReleaseTexts() {
        val cost = QueenReleaseManager.RELEASE_COST_POINTS
        releaseBodyText.text = hon(R.string.settings_release_body, cost)
    }

    private fun selectFloatAvatarStyle(style: QueenFloatingAvatarStyle) {
        if (QueenFloatingAvatarStyle.current(this) == style) return
        QueenFloatingAvatarStyle.set(this, style)
        if (getSharedPreferences(Prefs.NAME, MODE_PRIVATE).getBoolean(Prefs.ACTIVATED, false)) {
            QueenService.start(this)
        }
        refreshFloatAvatarSelectionUi()
        Toast.makeText(this, hon(R.string.settings_float_avatar_applied), Toast.LENGTH_SHORT).show()
        refreshFloatAvatarStyleLabels()
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
        releaseButton.text = hon(R.string.settings_release_btn_fmt, cost, points)
    }

    private fun confirmAndRelease() {
        val cost = QueenReleaseManager.RELEASE_COST_POINTS
        val points = QueenPointsStore.getPoints(this)
        AlertDialog.Builder(this)
            .setTitle(hon(R.string.settings_release_confirm_title))
            .setMessage(hon(R.string.settings_release_confirm_msg, cost, points))
            .setPositiveButton(android.R.string.ok) { _, _ -> performRelease() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performRelease() {
        when (QueenReleaseManager.performRelease(this)) {
            is QueenReleaseManager.ReleaseResult.Success -> {
                Toast.makeText(this, hon(R.string.settings_release_ok), Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            }
            QueenReleaseManager.ReleaseResult.NotActivated -> {
                Toast.makeText(this, hon(R.string.settings_release_not_activated), Toast.LENGTH_SHORT).show()
                finish()
            }
            QueenReleaseManager.ReleaseResult.InsufficientPoints -> {
                Toast.makeText(
                    this,
                    hon(
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
