package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dianziqueen.app.databinding.ActivityPermissionCheckBinding

/**
 * Queen 权限自检：逐项展示关键权限状态，并一键跳转厂商设置页。
 */
class PermissionCheckActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionCheckBinding
    private var lastChecks: List<PermissionStatus> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.permCheckTitle.text = hon(R.string.perm_check_title)
        binding.permCheckSubtitle.text = hon(R.string.perm_check_subtitle)

        binding.btnCheckAgain.setOnClickListener { checkAllPermissions() }
        binding.btnOpenAutoStart.setOnClickListener { RomPermissionUtils.openAutoStartSettings(this) }
        binding.btnOpenOverlay.setOnClickListener { RomPermissionUtils.openOverlaySettings(this) }
        binding.btnOpenSettings.setOnClickListener { DomesticRomGuide.showGuide(this) }

        checkAllPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkAllPermissions()
    }

    private fun checkAllPermissions() {
        val checks = buildPermissionChecks(this)
        lastChecks = checks
        renderPermissionList(checks)
        updateSummary(checks)
    }

    private fun buildPermissionChecks(context: Context): List<PermissionStatus> {
        val manualNote = getString(R.string.perm_check_manual_note)
        val autostartNote = if (RomPermissionUtils.isXiaomi()) {
            getString(R.string.perm_check_xiaomi_autostart_note)
        } else {
            manualNote
        }
        val activated = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.ACTIVATED, false)

        val list = mutableListOf(
            PermissionStatus(
                id = "overlay",
                name = getString(R.string.perm_check_item_overlay),
                isGranted = FloatingWindowPermissionHelper.hasPermission(context),
                critical = true,
                note = romProbeNote(context, "overlay"),
                fixAction = PermissionStatus.FixAction.OVERLAY,
            ),
            PermissionStatus(
                id = "accessibility",
                name = getString(R.string.perm_check_item_accessibility),
                isGranted = QueenAccessibilityHelper.isServiceEnabled(context),
                critical = true,
                note = if (QueenAccessibilityHelper.isServiceEnabled(context) &&
                    !QueenAccessibilityHelper.isServiceRunning(context)
                ) {
                    getString(R.string.perm_check_accessibility_not_running)
                } else {
                    ""
                },
                fixAction = PermissionStatus.FixAction.ACCESSIBILITY,
            ),
            PermissionStatus(
                id = "notifications",
                name = getString(R.string.perm_check_item_notifications),
                isGranted = NotificationHelper.hasNotificationPermissionReady(context),
                critical = true,
                note = if (NotificationHelper.hasNotificationPermissionReady(context) &&
                    !NotificationHelper.isTeasingChannelImportanceAdequate(context)
                ) {
                    hon(R.string.perm_channel_not_high)
                } else {
                    ""
                },
                fixAction = PermissionStatus.FixAction.NOTIFICATIONS,
            ),
            PermissionStatus(
                id = "notification_listener",
                name = getString(R.string.perm_check_item_notification_listener),
                isGranted = QueenNotificationListenerHelper.isServiceEnabled(context),
                critical = true,
                note = if (QueenNotificationListenerHelper.isServiceEnabled(context) &&
                    !QueenNotificationListenerHelper.isServiceRunning(context)
                ) {
                    getString(R.string.perm_check_notification_listener_not_running)
                } else {
                    ""
                },
                fixAction = PermissionStatus.FixAction.NOTIFICATION_LISTENER,
            ),
            PermissionStatus(
                id = "device_admin",
                name = getString(R.string.perm_check_item_device_admin),
                isGranted = QueenDeviceAdminHelper.isAdminActive(context),
                critical = true,
                fixAction = PermissionStatus.FixAction.DEVICE_ADMIN,
            ),
            PermissionStatus(
                id = "write_settings",
                name = getString(R.string.perm_check_item_write_settings),
                isGranted = QueenPrivilegeAuditor.canWriteSystemSettings(context),
                critical = true,
                note = romProbeNote(context, "write_settings"),
                fixAction = PermissionStatus.FixAction.WRITE_SETTINGS,
            ),
            PermissionStatus(
                id = "battery",
                name = getString(R.string.perm_check_item_battery),
                isGranted = QueenBatteryHelper.isExemptFromBatteryOptimizations(context),
                critical = true,
                note = buildBatteryNote(context),
                fixAction = PermissionStatus.FixAction.BATTERY,
            ),
        )

        if (activated) {
            list.add(
                PermissionStatus(
                    id = "keepalive",
                    name = getString(R.string.perm_check_item_keepalive),
                    isGranted = QueenKeepAlive.isServiceHealthy(context),
                    critical = true,
                    note = if (!QueenKeepAlive.isServiceHealthy(context)) {
                        getString(R.string.perm_check_keepalive_note)
                    } else {
                        ""
                    },
                    fixAction = PermissionStatus.FixAction.ROM_GUIDE,
                ),
            )
        }

        list.add(
            PermissionStatus(
                id = "autostart",
                name = getString(R.string.perm_check_item_autostart),
                isGranted = true,
                manualOnly = true,
                note = autostartNote,
                fixAction = PermissionStatus.FixAction.AUTO_START,
            ),
        )

        if (RomPermissionUtils.isDomesticRom()) {
            list.add(
                PermissionStatus(
                    id = "lock_app",
                    name = getString(R.string.perm_check_item_lock_app),
                    isGranted = true,
                    manualOnly = true,
                    note = manualNote,
                    fixAction = PermissionStatus.FixAction.ROM_GUIDE,
                ),
            )
        }

        if (activated) {
            list.add(
                PermissionStatus(
                    id = "camera",
                    name = getString(R.string.perm_check_item_camera),
                    isGranted = QueenPrivilegeAuditor.hasCameraPermission(context),
                    fixAction = PermissionStatus.FixAction.CAMERA,
                ),
            )
            list.add(
                PermissionStatus(
                    id = "calendar",
                    name = getString(R.string.perm_check_item_calendar),
                    isGranted = CalendarInjector.hasCalendarPermission(context),
                    fixAction = PermissionStatus.FixAction.CALENDAR,
                ),
            )
        }

        if (RomPermissionUtils.isDomesticRom()) {
            list.add(
                PermissionStatus(
                    id = "rom_extra",
                    name = getString(R.string.perm_check_item_rom_extra),
                    isGranted = true,
                    manualOnly = true,
                    note = getString(R.string.perm_check_rom_extra_note),
                    fixAction = PermissionStatus.FixAction.ROM_GUIDE,
                ),
            )
        }

        return list
    }

    private fun romProbeNote(context: Context, id: String): String {
        val key = RomPermissionProbe.confirmKeyForPermissionId(id) ?: return ""
        return when {
            RomPermissionProbe.isUserConfirmed(context, key) ->
                getString(R.string.perm_check_rom_confirmed_note)
            RomPermissionProbe.needsManualConfirmHint(context, id) ->
                getString(R.string.perm_check_rom_long_press_confirm)
            else -> ""
        }
    }

    private fun buildBatteryNote(context: Context): String {
        val parts = mutableListOf<String>()
        val probe = romProbeNote(context, "battery")
        if (probe.isNotBlank()) parts.add(probe)
        if (RomPermissionUtils.isXiaomi() &&
            !QueenBatteryHelper.isExemptFromBatteryOptimizations(context)
        ) {
            parts.add(getString(R.string.perm_check_xiaomi_battery_note))
        }
        return parts.joinToString("\n")
    }

    private fun renderPermissionList(checks: List<PermissionStatus>) {
        binding.permissionListContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (item in checks) {
            val row = inflater.inflate(R.layout.item_permission_check_row, binding.permissionListContainer, false)
            val nameView = row.findViewById<TextView>(R.id.permRowName)
            val statusView = row.findViewById<TextView>(R.id.permRowStatus)
            val noteView = row.findViewById<TextView>(R.id.permRowNote)

            nameView.text = item.name
            when {
                item.manualOnly -> {
                    statusView.text = getString(R.string.perm_check_status_manual)
                    statusView.setTextColor(ContextCompat.getColor(this, R.color.text_red_dim))
                }
                item.isGranted -> {
                    statusView.text = getString(R.string.perm_check_status_ok)
                    statusView.setTextColor(ContextCompat.getColor(this, R.color.matrix_green_glow))
                }
                else -> {
                    statusView.text = getString(R.string.perm_check_status_missing)
                    statusView.setTextColor(ContextCompat.getColor(this, R.color.crimson_glow))
                }
            }

            if (item.note.isNotBlank()) {
                noteView.visibility = View.VISIBLE
                noteView.text = item.note
            } else {
                noteView.visibility = View.GONE
            }

            if (!item.manualOnly && !item.isGranted && item.fixAction != PermissionStatus.FixAction.NONE) {
                row.setOnClickListener { openFix(item.fixAction) }
            } else if (item.manualOnly && item.fixAction != PermissionStatus.FixAction.NONE) {
                row.setOnClickListener { openFix(item.fixAction) }
            }

            if (RomPermissionProbe.needsManualConfirmHint(this, item.id)) {
                row.setOnLongClickListener {
                    offerManualConfirm(item.id)
                    true
                }
            }

            binding.permissionListContainer.addView(row)
        }
    }

    private fun offerManualConfirm(permissionId: String) {
        val key = RomPermissionProbe.confirmKeyForPermissionId(permissionId) ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.perm_check_manual_confirm_title)
            .setMessage(R.string.perm_check_manual_confirm_message)
            .setPositiveButton(R.string.perm_check_manual_confirm_yes) { _, _ ->
                RomPermissionProbe.setUserConfirmed(this, key, true)
                checkAllPermissions()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateSummary(checks: List<PermissionStatus>) {
        val criticalOk = checks.filter { it.critical }.all { it.displayOk }
        val anyAutoMissing = checks.any { !it.manualOnly && !it.isGranted }

        if (criticalOk && !anyAutoMissing) {
            binding.tvStatus.text = hon(R.string.perm_check_summary_ok)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.matrix_green_glow))
        } else if (criticalOk) {
            binding.tvStatus.text = hon(R.string.perm_check_summary_partial)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_red_primary))
        } else {
            binding.tvStatus.text = hon(R.string.perm_check_summary_missing)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.crimson_glow))
        }
    }

    private fun openFix(action: PermissionStatus.FixAction) {
        when (action) {
            PermissionStatus.FixAction.OVERLAY ->
                RomPermissionUtils.openOverlaySettings(this)
            PermissionStatus.FixAction.ACCESSIBILITY ->
                QueenAccessibilityHelper.openQueenAccessibilitySettings(this)
            PermissionStatus.FixAction.NOTIFICATIONS ->
                NotificationHelper.openAppNotificationSettings(this)
            PermissionStatus.FixAction.NOTIFICATION_LISTENER ->
                QueenNotificationListenerHelper.openNotificationListenerSettings(this)
            PermissionStatus.FixAction.DEVICE_ADMIN ->
                try {
                    startActivity(QueenDeviceAdminHelper.createAddAdminIntent(this))
                } catch (_: Exception) {
                    RomPermissionUtils.openAppDetails(this)
                }
            PermissionStatus.FixAction.WRITE_SETTINGS ->
                try {
                    startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        },
                    )
                } catch (_: Exception) {
                    RomPermissionUtils.openAppDetails(this)
                }
            PermissionStatus.FixAction.BATTERY ->
                QueenBatteryHelper.openBatteryExemptionSettings(this)
            PermissionStatus.FixAction.AUTO_START ->
                RomPermissionUtils.openAutoStartSettings(this)
            PermissionStatus.FixAction.ROM_GUIDE ->
                DomesticRomGuide.showGuide(this)
            PermissionStatus.FixAction.APP_DETAILS ->
                RomPermissionUtils.openAppDetails(this)
            PermissionStatus.FixAction.CAMERA ->
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_CAMERA)
            PermissionStatus.FixAction.CALENDAR ->
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_CALENDAR,
                        android.Manifest.permission.WRITE_CALENDAR,
                    ),
                    REQ_CALENDAR,
                )
            PermissionStatus.FixAction.NONE -> { }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA || requestCode == REQ_CALENDAR) {
            checkAllPermissions()
        }
    }

    companion object {
        private const val REQ_CAMERA = 4101
        private const val REQ_CALENDAR = 4102

        fun createIntent(context: Context): Intent =
            Intent(context, PermissionCheckActivity::class.java)
    }
}
