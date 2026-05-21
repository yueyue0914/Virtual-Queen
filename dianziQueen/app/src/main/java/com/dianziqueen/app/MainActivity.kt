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
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_ACCESSIBILITY = "open_accessibility"
        const val EXTRA_OPEN_BATTERY_SETTINGS = "open_battery_settings"
        const val EXTRA_OPEN_MESSAGES = "open_messages"
        const val EXTRA_OPEN_ALBUM = "open_album"
    }

    /** 激活口令，支持中文。 */
    private val correctPassword = "我是被控制的贱奴"
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
    private lateinit var btnHonorificSettings: TextView
    private lateinit var activatedBottomNav: BottomNavigationView
    private lateinit var tabPanelHome: View
    private lateinit var tabPanelAlbum: View
    private lateinit var tabPanelMessages: View
    private lateinit var tabPanelProfile: View
    private lateinit var bottomDock: LinearLayout
    private lateinit var profileSlaveValue: TextView
    private lateinit var profileDeviceNameValue: TextView
    private lateinit var profileModelValue: TextView
    private lateinit var profileStatusValue: TextView
    private lateinit var profileRenameValue: TextView
    private lateinit var profilePointsText: TextView
    private lateinit var profileSubtitleText: TextView
    private lateinit var albumTabController: AlbumTabController
    private lateinit var messagesTabController: MessagesTabController

    private enum class ActivatedTab {
        HOME, ALBUM, MESSAGES, PROFILE
    }

    private var currentActivatedTab = ActivatedTab.HOME

    private val messageUnreadListener = QueenMessageHub.Listener { _ ->
        if (currentActivatedTab == ActivatedTab.MESSAGES) {
            QueenMessageStore.markAllRead(this)
        }
        refreshMessagesUnreadBadge()
    }

    /** 代码里同步底部选中项时，避免再次触发 [activatedBottomNav] 监听造成递归崩溃。 */
    private var suppressBottomNavCallback = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        runtimePermissionDialogOpen = false
        calendarPermissionRequestInFlight = false
        postNotificationsRequestInFlight = false
        cameraPermissionRequestInFlight = false
        updatePrivilegeUi()
        if (grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        ) {
            tryAutoInjectCalendar()
        }
        if (grants[Manifest.permission.CAMERA] == true) {
            cameraAutoPromptAttempted = false
        }
        if (grants[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            prefs.getBoolean(Prefs.ACTIVATED, false)
        ) {
            QueenDeviceNameHelper.clearRenameSkippedForRetry(this)
            tryApplyQueenDeviceName()
        }
        handler.postDelayed({ continuePrivilegeAuditAfterRuntimeDialog() }, 320L)
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
        schedulePrivilegeAuditOnAppOpen()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        refreshHonorificUi()
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        showPasswordGate()
        refreshProfilePanel()
        updatePrivilegeUi()
        syncChromePalette(restartTitleGlow = true)
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
    private var batteryOptimizationPromptInFlight = false
    private var cameraPermissionRequestInFlight = false

    /** 系统运行时权限弹窗是否正在显示（onResume 不得重置并重复 launch）。 */
    private var runtimePermissionDialogOpen = false

    /** 本会话是否已自动弹过相机授权（单次引导流程内防连弹；每次 onResume 会重置）。 */
    private var cameraAutoPromptAttempted = false

    private var privilegeAuditPass = 0

    /** 自动跳转系统设置页后，短时间内不再自动拉起（避免从设置返回又被踢回去）。 */
    private var lastAutoPrivilegeGuideAtMs = 0L

    private val autoPrivilegeGuideCooldownMs = 15_000L

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
        btnHonorificSettings = findViewById(R.id.btnHonorificSettings)
        btnHonorificSettings.setOnClickListener {
            QueenHonorific.showPicker(this) { refreshHonorificUi() }
        }
        refreshHonorificUi()
        activatedBottomNav = findViewById(R.id.activatedBottomNav)
        tabPanelHome = findViewById(R.id.tabPanelHome)
        tabPanelAlbum = findViewById(R.id.tabPanelAlbum)
        tabPanelMessages = findViewById(R.id.tabPanelMessages)
        tabPanelProfile = findViewById(R.id.tabPanelProfile)
        bottomDock = findViewById(R.id.bottomDock)
        profileSlaveValue = findViewById(R.id.profileSlaveValue)
        profileDeviceNameValue = findViewById(R.id.profileDeviceNameValue)
        profileModelValue = findViewById(R.id.profileModelValue)
        profileStatusValue = findViewById(R.id.profileStatusValue)
        profileRenameValue = findViewById(R.id.profileRenameValue)
        profilePointsText = findViewById(R.id.profilePointsText)
        profileSubtitleText = findViewById(R.id.profileSubtitleText)

        findViewById<TextView>(R.id.profileSlaveLabel).text =
            getString(R.string.profile_label_slave)
        findViewById<TextView>(R.id.profileDeviceNameLabel).text =
            getString(R.string.profile_label_device_name)
        findViewById<TextView>(R.id.profileModelLabel).text =
            getString(R.string.profile_label_model)
        findViewById<TextView>(R.id.profileStatusLabel).text =
            getString(R.string.profile_label_status)
        findViewById<TextView>(R.id.profileRenameLabel).text =
            getString(R.string.profile_label_rename)

        albumTabController = AlbumTabController(this, tabPanelAlbum) {
            refreshProfilePanel()
        }
        messagesTabController = MessagesTabController(
            this,
            tabPanelMessages,
            onPointsChanged = { refreshProfilePanel() },
            onUnreadChanged = { refreshMessagesUnreadBadge() },
        )
        QueenMessageHub.addListener(messageUnreadListener)

        activatedBottomNav.setOnItemSelectedListener { item ->
            if (suppressBottomNavCallback) return@setOnItemSelectedListener true
            val tab = when (item.itemId) {
                R.id.nav_home -> ActivatedTab.HOME
                R.id.nav_album -> ActivatedTab.ALBUM
                R.id.nav_messages -> ActivatedTab.MESSAGES
                R.id.nav_profile -> ActivatedTab.PROFILE
                else -> return@setOnItemSelectedListener false
            }
            showActivatedTab(tab)
            true
        }

        submitButton.setOnClickListener {
            albumTabController.onSubmitClicked()
            checkPassword()
        }
        payButton.setOnClickListener { openSupportUrl() }
        fixPermissionsButton.setOnClickListener { openNextMissingPrivilege() }
        findViewById<Button>(R.id.profileOverlayCheckButton).setOnClickListener {
            openOverlayPrivilegeSelfCheck()
        }
        findViewById<Button>(R.id.profileSettingsButton).setOnClickListener {
            settingsLauncher.launch(Intent(this, QueenSettingsActivity::class.java))
        }

        if (prefs.getBoolean(Prefs.ACTIVATED, false)) {
            QueenPointsStore.grantActivationBonusIfNeeded(this)
            DailySelfieScheduler.ensureTodaySchedule(this)
            if (passDailySelfieGate()) {
                showActivatedState()
            }
            ensureCalendarInjected()
        } else {
            showPasswordGate()
        }
        handlePrivilegeIntentExtras(intent)
        handleFloatingDeepLinkExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrivilegeIntentExtras(intent)
        handleFloatingDeepLinkExtras(intent)
    }

    private fun handleFloatingDeepLinkExtras(intent: Intent?) {
        if (intent == null || !prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (intent.getBooleanExtra(EXTRA_OPEN_MESSAGES, false)) {
            intent.removeExtra(EXTRA_OPEN_MESSAGES)
            if (::tabPanelMessages.isInitialized) {
                showActivatedTab(ActivatedTab.MESSAGES)
            }
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_ALBUM, false)) {
            intent.removeExtra(EXTRA_OPEN_ALBUM)
            if (::tabPanelAlbum.isInitialized) {
                showActivatedTab(ActivatedTab.ALBUM)
            }
        }
    }

    private fun handlePrivilegeIntentExtras(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_ACCESSIBILITY, false)) {
            intent.removeExtra(EXTRA_OPEN_ACCESSIBILITY)
            QueenAccessibilityHelper.openQueenAccessibilitySettings(this)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_BATTERY_SETTINGS, false)) {
            intent.removeExtra(EXTRA_OPEN_BATTERY_SETTINGS)
            QueenBatteryHelper.openBatteryExemptionSettings(this)
            Toast.makeText(this, R.string.toast_battery_guide, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        glowAnimator?.cancel()
        if (::albumTabController.isInitialized) {
            albumTabController.shutdown()
        }
        if (::messagesTabController.isInitialized) {
            messagesTabController.shutdown()
        }
        QueenMessageHub.removeListener(messageUnreadListener)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        schedulePrivilegeAuditOnAppOpen()
    }

    override fun onResume() {
        super.onResume()
        // 仅重置「运行时权限弹窗」相关标志；勿重置无障碍/悬浮窗/电池等「已跳转设置」标志，
        // 否则 onResume 触发的延迟复检会立刻再次 startActivity 打开设置页。
        cameraPermissionRequestInFlight = false
        calendarPermissionRequestInFlight = false
        cameraAutoPromptAttempted = false
        if (runtimePermissionDialogOpen) {
            updatePrivilegeUi()
            return
        }
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
            UninstallGuard.applyReinstallPunishmentIfNeeded(this)
            if (!passDailySelfieGate()) return
            if (activatedPanel.visibility != View.VISIBLE) {
                showActivatedState()
            }
            if (QueenPointsStore.grantDailyOpenBonusIfNeeded(this)) {
                Toast.makeText(
                    this,
                    getString(
                        R.string.points_daily_bonus_toast,
                        QueenPointsStore.DAILY_OPEN_BONUS_POINTS,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                refreshProfilePanel()
            }
            ensureCalendarInjected()
            QueenDeviceAdminHelper.applyQueenPolicies(this)
            tryApplyQueenDeviceName()
            if (currentActivatedTab == ActivatedTab.PROFILE) {
                refreshProfilePanel()
            }
            refreshMessagesUnreadBadge()
            schedulePrivilegeAuditOnAppOpen()
            if (!FloatingWindowPermissionHelper.hasPermission(this)) {
                DomesticRomGuide.maybeShowOnResume(this)
            }
        } else {
            schedulePrivilegeAuditOnAppOpen()
        }
    }

    /** 每次打开 App：先刷新 UI；延迟复检仅更新横幅，自动跳设置须过冷却期。 */
    private fun schedulePrivilegeAuditOnAppOpen() {
        privilegeAuditPass = 0
        updatePrivilegeUi()
        if (runtimePermissionDialogOpen) return
        handler.postDelayed({ runPrivilegeAuditPass(1, autoGuide = false) }, 400L)
        handler.postDelayed({ runPrivilegeAuditPass(2, autoGuide = true) }, 1_600L)
    }

    private fun canAutoGuidePrivileges(): Boolean =
        System.currentTimeMillis() - lastAutoPrivilegeGuideAtMs >= autoPrivilegeGuideCooldownMs

    private fun markAutoPrivilegeGuideLaunched() {
        lastAutoPrivilegeGuideAtMs = System.currentTimeMillis()
    }

    private fun runPrivilegeAuditPass(pass: Int, autoGuide: Boolean) {
        if (runtimePermissionDialogOpen) return
        privilegeAuditPass = pass
        updatePrivilegeUi()
        if (QueenPrivilegeAuditor.isAllCriticalOk(this)) {
            writeSettingsPromptInFlight = false
            deviceAdminRequestInFlight = false
            accessibilityPromptInFlight = false
            batteryOptimizationPromptInFlight = false
            if (pass == 2 && prefs.getBoolean(Prefs.ACTIVATED, false)) {
                tryApplyQueenDeviceName()
                ensureCalendarInjected()
            }
            return
        }
        if (!autoGuide || !canAutoGuidePrivileges()) return
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) {
            auditPrivilegesAfterActivation()
        } else {
            maybeRequestEarlyPrivileges()
        }
    }

    private fun continuePrivilegeAuditAfterRuntimeDialog() {
        if (runtimePermissionDialogOpen) return
        updatePrivilegeUi()
        if (QueenPrivilegeAuditor.isAllCriticalOk(this)) return
        runPrivilegeAuditPass(2, autoGuide = true)
    }

    private fun launchRuntimePermissions(permissions: Array<String>) {
        if (runtimePermissionDialogOpen) return
        runtimePermissionDialogOpen = true
        permissionLauncher.launch(permissions)
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
        val activated = prefs.getBoolean(Prefs.ACTIVATED, false)
        codeRain.setActivatedMode(activated)
        codeRain.reloadPhrases(activated)
    }

    /** 称谓变更后刷新标题、字幕墙、各 Tab 文案等。 */
    private fun refreshHonorificUi() {
        btnHonorificSettings.text = QueenHonorific.settingsButtonLabel(this)
        titleText.text = hon(R.string.lock_title)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) {
            if (teasingLine1.visibility == View.VISIBLE) {
                stopTeaserRotation()
                beginTypewriterForRandomPair()
            }
        } else {
            statusText.text = hon(R.string.status_activated)
            systemLockedText.text = buildString {
                append(hon(R.string.lock_system_locked))
                append('\n')
                append(hon(R.string.lock_system_online))
            }
        }
        refreshCodeRainPhrases()
        updatePrivilegeUi()
        if (::messagesTabController.isInitialized) {
            messagesTabController.refreshHonorificLabels()
        }
        if (::albumTabController.isInitialized) {
            albumTabController.refreshHonorificLabels()
        }
        if (currentActivatedTab == ActivatedTab.PROFILE) {
            refreshProfilePanel()
        }
        QueenFloatingOverlay.refreshHonorificLabels()
    }

    /**
     * 今日强制自拍未交：隐藏主界面并拉起上缴页；返回 false 表示主界面不可使用。
     */
    private fun passDailySelfieGate(): Boolean {
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return true
        DailySelfieScheduler.ensureTodaySchedule(this)
        if (!DailySelfieScheduler.shouldEnforce(this)) return true
        hideMainContentForSelfieGate()
        DailySelfieEnforcement.launch(this)
        return false
    }

    private fun hideMainContentForSelfieGate() {
        passwordContainer.visibility = View.GONE
        activatedPanel.visibility = View.GONE
        bottomDock.visibility = View.GONE
        setActivatedBottomNavVisible(false)
        permissionBanner.visibility = View.GONE
        teasingLine1.visibility = View.GONE
        teasingLine2.visibility = View.GONE
    }

    private fun showPasswordGate() {
        setActivatedBottomNavVisible(false)
        passwordContainer.visibility = View.VISIBLE
        bottomDock.visibility = View.VISIBLE
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
        bottomDock.visibility = View.GONE
        activatedPanel.visibility = View.VISIBLE
        setActivatedBottomNavVisible(true)
        showActivatedTab(ActivatedTab.HOME)
        systemLockedText.text = buildString {
            append(getString(R.string.lock_system_locked))
            append('\n')
            append(getString(R.string.lock_system_online))
        }
        teasingLine1.visibility = View.GONE
        teasingLine2.visibility = View.GONE
        statusText.text = hon(R.string.status_activated)
        refreshCodeRainPhrases()
        syncChromePalette(restartTitleGlow = true)
        refreshMessagesUnreadBadge()
    }

    private fun refreshMessagesUnreadBadge() {
        if (!::activatedBottomNav.isInitialized) return
        if (!prefs.getBoolean(Prefs.ACTIVATED, false) ||
            activatedBottomNav.visibility != View.VISIBLE
        ) {
            activatedBottomNav.removeBadge(R.id.nav_messages)
            return
        }
        val unread = QueenMessageStore.getUnreadCount(this)
        if (unread <= 0) {
            activatedBottomNav.removeBadge(R.id.nav_messages)
            return
        }
        val badge = activatedBottomNav.getOrCreateBadge(R.id.nav_messages)
        badge.isVisible = true
        badge.number = unread.coerceAtMost(99)
        badge.backgroundColor = ContextCompat.getColor(this, R.color.crimson_glow)
        badge.badgeTextColor = ContextCompat.getColor(this, R.color.pure_black)
    }

    private fun setActivatedBottomNavVisible(visible: Boolean) {
        activatedBottomNav.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showActivatedTab(tab: ActivatedTab) {
        currentActivatedTab = tab
        tabPanelHome.visibility = if (tab == ActivatedTab.HOME) View.VISIBLE else View.GONE
        tabPanelAlbum.visibility = if (tab == ActivatedTab.ALBUM) View.VISIBLE else View.GONE
        tabPanelMessages.visibility = if (tab == ActivatedTab.MESSAGES) View.VISIBLE else View.GONE
        tabPanelProfile.visibility = if (tab == ActivatedTab.PROFILE) View.VISIBLE else View.GONE
        val navItemId = when (tab) {
            ActivatedTab.HOME -> R.id.nav_home
            ActivatedTab.ALBUM -> R.id.nav_album
            ActivatedTab.MESSAGES -> R.id.nav_messages
            ActivatedTab.PROFILE -> R.id.nav_profile
        }
        if (activatedBottomNav.selectedItemId != navItemId) {
            suppressBottomNavCallback = true
            try {
                activatedBottomNav.selectedItemId = navItemId
            } finally {
                suppressBottomNavCallback = false
            }
        }
        when (tab) {
            ActivatedTab.ALBUM -> albumTabController.onTabShown()
            ActivatedTab.MESSAGES -> messagesTabController.onTabShown()
            ActivatedTab.PROFILE -> refreshProfilePanel()
            ActivatedTab.HOME -> { }
        }
    }

    private fun refreshProfilePanel() {
        profileSubtitleText.text = hon(R.string.profile_subtitle)
        val points = prefs.getInt(Prefs.QUEEN_POINTS, 0)
        profilePointsText.text = getString(R.string.profile_points_fmt, points)
        val slaveNo = QueenDeviceNameHelper.ensureSlaveNumber(this)
        profileSlaveValue.text = getString(R.string.profile_slave_fmt, slaveNo)
        profileDeviceNameValue.text = QueenDeviceNameHelper.queenDeviceName(this)
        profileModelValue.text = Build.MODEL
        profileStatusValue.text = getString(R.string.profile_status_locked)
        val renameApplied = prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, false)
        profileRenameValue.text = if (renameApplied) {
            val method = prefs.getString(Prefs.QUEEN_DEVICE_NAME_METHOD, "").orEmpty()
            getString(R.string.profile_rename_applied, method.ifBlank { "ok" })
        } else {
            getString(R.string.profile_rename_pending)
        }
    }

    /** 未激活：进入时按顺序引导补齐权限。 */
    private fun maybeRequestEarlyPrivileges() {
        if (prefs.getBoolean(Prefs.ACTIVATED, false)) return
        requestNextMissingPrivilege(force = false)
    }

    /** 已激活：每次回到前台复检并引导补齐缺失权限。 */
    private fun auditPrivilegesAfterActivation() {
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (runtimePermissionDialogOpen) return
        updatePrivilegeUi()
        if (QueenPrivilegeAuditor.isAllCriticalOk(this)) return
        requestNextMissingPrivilege(force = false)
    }

    /**
     * 按固定顺序检查并引导下一项缺失权限（未激活/已激活共用）。
     * 日历 → 系统设置 → 设备管理员 → 无障碍 → 通知 → 相机 → 电池 → 蓝牙 → 存储 → 悬浮窗
     *
     * @param force true 时忽略冷却（用户点击「去开启下一项」）
     */
    private fun requestNextMissingPrivilege(force: Boolean = false) {
        if (runtimePermissionDialogOpen) return
        if (!force && !canAutoGuidePrivileges()) return
        val audit = QueenPrivilegeAuditor.audit(this)
        for (privilege in audit.missing) {
            when (privilege) {
                QueenPrivilegeAuditor.Privilege.CALENDAR -> {
                    requestCalendarPermission()
                    return
                }
                QueenPrivilegeAuditor.Privilege.WRITE_SETTINGS -> {
                    requestWriteSettings()
                    return
                }
                QueenPrivilegeAuditor.Privilege.DEVICE_ADMIN -> {
                    requestDeviceAdmin()
                    return
                }
                QueenPrivilegeAuditor.Privilege.ACCESSIBILITY -> {
                    requestAccessibility()
                    return
                }
                QueenPrivilegeAuditor.Privilege.NOTIFICATIONS -> {
                    requestNotifications()
                    return
                }
                QueenPrivilegeAuditor.Privilege.CAMERA -> {
                    requestCameraPermission()
                    return
                }
                QueenPrivilegeAuditor.Privilege.WALLPAPER -> {
                    Toast.makeText(this, R.string.perm_wallpaper, Toast.LENGTH_LONG).show()
                    return
                }
                QueenPrivilegeAuditor.Privilege.BATTERY -> {
                    requestBatteryOptimization()
                    return
                }
                QueenPrivilegeAuditor.Privilege.BLUETOOTH -> {
                    requestBluetoothConnect()
                    return
                }
                QueenPrivilegeAuditor.Privilege.STORAGE -> {
                    val storage = QueenPrivilegeAuditor.storagePermissionsToRequest(this)
                    if (storage.isNotEmpty()) {
                        launchRuntimePermissions(storage)
                    }
                    return
                }
                QueenPrivilegeAuditor.Privilege.OVERLAY -> {
                    requestOverlayPermission()
                    return
                }
            }
        }
    }

    private fun requestCameraPermission() {
        if (QueenPrivilegeAuditor.hasCameraPermission(this)) return
        if (runtimePermissionDialogOpen || cameraPermissionRequestInFlight) return
        if (cameraAutoPromptAttempted) return
        cameraAutoPromptAttempted = true
        cameraPermissionRequestInFlight = true
        launchRuntimePermissions(arrayOf(Manifest.permission.CAMERA))
    }

    private fun requestBatteryOptimization() {
        if (QueenBatteryHelper.isExemptFromBatteryOptimizations(this)) return
        if (batteryOptimizationPromptInFlight) return
        batteryOptimizationPromptInFlight = true
        markAutoPrivilegeGuideLaunched()
        QueenBatteryHelper.openBatteryExemptionSettings(this)
        Toast.makeText(this, R.string.toast_battery_guide, Toast.LENGTH_LONG).show()
    }

    private fun requestBluetoothConnect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (QueenDeviceNameHelper.hasBluetoothConnectPermission(this)) return
        launchRuntimePermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
    }

    private fun requestCalendarPermission() {
        if (CalendarInjector.hasCalendarPermission(this)) return
        if (calendarPermissionRequestInFlight || runtimePermissionDialogOpen) return
        calendarPermissionRequestInFlight = true
        launchRuntimePermissions(calendarPermissions())
    }

    private fun requestWriteSettings() {
        if (QueenPrivilegeAuditor.canWriteSystemSettings(this)) return
        if (writeSettingsPromptInFlight) return
        writeSettingsPromptInFlight = true
        markAutoPrivilegeGuideLaunched()
        openManageWriteSettingsScreen()
    }

    private fun requestOverlayPermission() {
        if (QueenPrivilegeAuditor.canDrawOverlays(this)) return
        markAutoPrivilegeGuideLaunched()
        DomesticRomGuide.showGuideIfNeeded(this)
    }

    /** 我的页：Queen 权限自检页。 */
    private fun openOverlayPrivilegeSelfCheck() {
        startActivity(PermissionCheckActivity.createIntent(this))
    }

    private fun canWriteSystemSettings(): Boolean =
        QueenPrivilegeAuditor.canWriteSystemSettings(this)

    private fun tryApplyQueenDeviceName() {
        if (runtimePermissionDialogOpen) return
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_APPLIED, false)) return
        if (prefs.getBoolean(Prefs.QUEEN_DEVICE_NAME_RENAME_SKIPPED, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !QueenDeviceNameHelper.hasBluetoothConnectPermission(this)
        ) {
            launchRuntimePermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return
        }
        QueenDeviceNameHelper.applyQueenDeviceName(this)
    }

    private fun requestDeviceAdmin() {
        if (QueenDeviceAdminHelper.isAdminActive(this)) return
        if (deviceAdminRequestInFlight) return
        deviceAdminRequestInFlight = true
        try {
            deviceAdminLauncher.launch(QueenDeviceAdminHelper.createAddAdminIntent(this))
        } catch (_: Exception) {
            deviceAdminRequestInFlight = false
        }
    }

    private fun requestAccessibility() {
        if (QueenAccessibilityHelper.isServiceEnabled(this)) {
            accessibilityPromptInFlight = false
            return
        }
        if (accessibilityPromptInFlight) return
        accessibilityPromptInFlight = true
        markAutoPrivilegeGuideLaunched()
        QueenAccessibilityHelper.openQueenAccessibilitySettings(this)
    }

    private fun requestNotifications() {
        if (NotificationHelper.hasEarlyNotificationsReady(this)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.isPostNotificationsGranted(this)
        ) {
            if (postNotificationsRequestInFlight) return
            postNotificationsRequestInFlight = true
            launchRuntimePermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            return
        }
        if (!NotificationHelper.areAppNotificationsEnabled(this)) {
            markAutoPrivilegeGuideLaunched()
            NotificationHelper.openAppNotificationSettings(this)
            return
        }
        if (!NotificationHelper.isTeasingChannelImportanceAdequate(this)) {
            markAutoPrivilegeGuideLaunched()
            NotificationHelper.openTeasingChannelSettings(this)
        }
    }

    /** 激活后静默烙印日历（权限由统一复检流程申请，此处不再重复 launch）。 */
    private fun ensureCalendarInjected() {
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return
        if (!CalendarInjector.hasCalendarPermission(this)) return
        CalendarInjector.ensureGradualInjection(this)
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
        teaserLine1Full = QueenHonorific.apply(this, first)
        teaserLine2Full = QueenHonorific.apply(this, second)
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
        if (!QueenPrivilegeAuditor.isAllCriticalOk(this)) {
            Toast.makeText(this, R.string.status_need_permissions, Toast.LENGTH_SHORT).show()
            schedulePrivilegeAuditOnAppOpen()
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
        QueenPointsStore.grantActivationBonusIfNeeded(this)
        DailySelfieScheduler.scheduleActivationDaySelfie(this)
        QueenMessageStore.ensureSessionOpened(this)
        showActivatedState()
        refreshProfilePanel()
        QueenService.start(this)
        QueenDeviceAdminHelper.applyQueenPolicies(this)
        ensureCalendarInjected()
        tryApplyQueenDeviceName()
        updatePrivilegeUi()
        DomesticRomGuide.showGuideIfNeeded(this)
        UninstallGuard.enableProtection(this)
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

        val ok = QueenPrivilegeAuditor.isAllCriticalOk(this)
        if (!activated) {
            submitButton.isEnabled = ok
            submitButton.alpha = if (ok) 1f else 0.45f
        } else {
            submitButton.isEnabled = true
            submitButton.alpha = 1f
        }

        if (activated) {
            statusText.text = if (ok) {
                hon(R.string.status_activated)
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
            out.add(hon(R.string.perm_channel_not_high))
        }
    }

    private fun buildMissingPrivilegeLines(): List<String> {
        val lines = mutableListOf<String>()
        if (!CalendarInjector.hasCalendarPermission(this)) {
            lines.add(hon(R.string.perm_calendar))
        }
        if (!canWriteSystemSettings()) {
            lines.add(hon(R.string.perm_write_settings))
        }
        if (!QueenDeviceAdminHelper.isAdminActive(this)) {
            lines.add(getString(R.string.perm_device_admin))
        }
        if (!QueenAccessibilityHelper.isServiceEnabled(this)) {
            lines.add(getString(R.string.perm_accessibility))
        } else if (!QueenAccessibilityHelper.isServiceRunning(this)) {
            lines.add(getString(R.string.perm_accessibility_not_running))
        }
        if (!NotificationHelper.hasEarlyNotificationsReady(this)) {
            appendNotificationMissingLines(lines)
        }
        if (!QueenPrivilegeAuditor.hasCameraPermission(this)) {
            lines.add(getString(R.string.perm_camera))
        }
        if (!QueenWallpaperHelper.hasSetWallpaperPermission(this)) {
            lines.add(hon(R.string.perm_wallpaper))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !QueenDeviceNameHelper.hasBluetoothConnectPermission(this)
        ) {
            lines.add(hon(R.string.perm_bluetooth_connect))
        }
        if (!QueenPrivilegeAuditor.hasStorageAccess(this)) {
            lines.add(getString(R.string.perm_storage))
        }
        if (!QueenPrivilegeAuditor.canDrawOverlays(this)) {
            lines.add(hon(R.string.perm_overlay))
        }
        if (!QueenBatteryHelper.isExemptFromBatteryOptimizations(this)) {
            lines.add(getString(R.string.perm_battery))
        }
        return lines
    }

    /** 用户点击「修复权限」：与启动复检共用同一套顺序。 */
    private fun openNextMissingPrivilege() {
        updatePrivilegeUi()
        if (QueenPrivilegeAuditor.isAllCriticalOk(this)) {
            Toast.makeText(this, R.string.toast_all_privileges_ready, Toast.LENGTH_SHORT).show()
            return
        }
        cameraAutoPromptAttempted = false
        lastAutoPrivilegeGuideAtMs = 0L
        requestNextMissingPrivilege(force = true)
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
