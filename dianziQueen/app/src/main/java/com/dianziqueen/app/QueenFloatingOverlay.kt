package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.random.Random

/**
 * 全局半透明悬浮女王（对应设计稿中的 FloatingQueenWindow）。
 * 可拖动、随机气泡台词、点击菜单；由 [QueenService] 在激活且已授权悬浮窗时拉起。
 */
object QueenFloatingOverlay {

    /** 悬浮头像边长（dp），约为初版的 1/2。 */
    private const val FLOAT_AVATAR_DP = 36f
    private const val FLOAT_BUBBLE_WIDTH_DP = 110f
    private const val FLOAT_MENU_WIDTH_DP = 100f
    private const val FLOAT_GAP_DP = 3f
    private const val FLOAT_MIN_WINDOW_DP = 40f

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private val touchSlop: Int
        get() = (8 * (appContext?.resources?.displayMetrics?.density ?: 1f)).toInt()

    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false

    private var avatarView: ImageView? = null
    private var bubbleView: TextView? = null
    private var menuPanel: LinearLayout? = null

    private var currentMood = QueenFloatingMood.MOCKING
    private var menuVisible = false
    private var bubbleBelowAvatar: Boolean? = null
    private var applyingBubblePlacement = false
    /** 未替换称谓的原文，便于改称谓后刷新当前气泡。 */
    private var lastTauntRaw: String? = null

    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var dragging = false

    private val prefs
        get() = appContext?.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    private val hideBubbleRunnable = Runnable { hideSpeechBubble() }
    private val randomTauntRunnable = object : Runnable {
        override fun run() {
            if (!isShowing || !isActivated()) return
            if (!menuVisible) {
                val (mood, line) = QueenFloatingPhraseLibrary.randomLine()
                showTaunt(mood, line)
            }
            scheduleRandomTaunt()
        }
    }

    fun ensureShown(context: Context) {
        appContext = context.applicationContext
        if (!isActivated()) {
            hide()
            return
        }
        if (!FloatingWindowPermissionHelper.hasPermission(appContext!!)) {
            hide()
            return
        }
        if (isShowing && rootView?.isAttachedToWindow == true) {
            refreshAppearance()
            return
        }
        if (isShowing) {
            hide()
        }
        try {
            val app = appContext!!
            val wm = ContextCompat.getSystemService(app, WindowManager::class.java) ?: return
            val inflater = LayoutInflater.from(app)
            val root = inflater.inflate(R.layout.overlay_queen_floating, null)
            val avatar = root.findViewById<ImageView>(R.id.queenFloatAvatar)
            val bubble = root.findViewById<TextView>(R.id.queenSpeechBubble)
            val menu = root.findViewById<LinearLayout>(R.id.queenFloatMenu)

            avatarView = avatar
            bubbleView = bubble
            menuPanel = menu

            applyAvatarBase(avatar)
            currentMood.applyAvatar(avatar, avatarStyle())
            setupAvatarTouch(avatar, wm, root)
            setupMenuActions(root)
            refreshHonorificLabels()

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val (x, y) = loadSavedPosition()
                this.x = x
                this.y = y
            }

            wm.addView(root, params)
            rootView = root
            layoutParams = params
            windowManager = wm
            isShowing = true

            handler.postDelayed({
                if (isShowing) {
                    showTaunt(
                        QueenFloatingMood.COMMAND,
                        app.getString(R.string.queen_float_welcome),
                    )
                }
            }, 2_500L)
            scheduleRandomTaunt()
            handler.post {
                if (!shouldPlaceBubbleBelow()) {
                    applyBubblePlacement()
                } else {
                    bubbleBelowAvatar = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            hide()
        }
    }

    fun hide() {
        handler.removeCallbacks(hideBubbleRunnable)
        handler.removeCallbacks(randomTauntRunnable)
        menuVisible = false
        try {
            val wm = windowManager
                ?: appContext?.let {
                    ContextCompat.getSystemService(it, WindowManager::class.java)
                }
            rootView?.let { v ->
                try {
                    wm?.removeView(v)
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            rootView = null
            layoutParams = null
            windowManager = null
            avatarView = null
            bubbleView = null
            menuPanel = null
            bubbleBelowAvatar = null
            applyingBubblePlacement = false
            lastTauntRaw = null
            isShowing = false
        }
    }

    /** 用户更改称谓后，刷新当前气泡与菜单文案。 */
    fun refreshHonorificLabels() {
        val ctx = appContext ?: return
        if (!isShowing) return
        val root = rootView ?: return
        root.findViewById<TextView>(R.id.queenMenuConfess)?.text =
            ctx.hon(R.string.queen_float_menu_confess)
        root.findViewById<TextView>(R.id.queenMenuDaily)?.text =
            ctx.hon(R.string.queen_float_menu_daily)
        root.findViewById<TextView>(R.id.queenMenuPunish)?.text =
            ctx.hon(R.string.queen_float_menu_punish)
        root.findViewById<TextView>(R.id.queenMenuSelfie)?.text =
            ctx.hon(R.string.queen_float_menu_selfie)
        root.findViewById<TextView>(R.id.queenMenuMessages)?.text =
            ctx.hon(R.string.queen_float_menu_messages)
        root.findViewById<TextView>(R.id.queenMenuDismiss)?.text =
            ctx.hon(R.string.queen_float_menu_dismiss)
        val raw = lastTauntRaw ?: return
        val bubble = bubbleView ?: return
        if (bubble.visibility == View.VISIBLE) {
            bubble.text = QueenHonorific.apply(ctx, raw)
        }
    }

    fun refreshAppearance() {
        val avatar = avatarView ?: return
        applyAvatarBase(avatar)
        currentMood.applyAvatar(avatar, avatarStyle())
    }

    private fun setupAvatarTouch(avatar: ImageView, wm: WindowManager, root: View) {
        avatar.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartRawX).toInt()
                    val dy = (event.rawY - touchStartRawY).toInt()
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        if (menuVisible) {
                            setMenuVisible(false)
                        }
                        dragging = true
                    }
                    if (dragging) {
                        params.x = dragStartX + dx
                        params.y = dragStartY + dy
                        params.width = WindowManager.LayoutParams.WRAP_CONTENT
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        clampToScreen(params)
                        try {
                            wm.updateViewLayout(root, params)
                        } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        savePosition(params.x, params.y)
                        handler.post {
                            applyBubblePlacement()
                            refreshWindowLayout()
                        }
                    } else {
                        toggleMenu()
                        appContext?.let { QueenVibratorHelper.lightTap(it) }
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun setupMenuActions(root: View) {
        val app = appContext ?: return
        root.findViewById<TextView>(R.id.queenMenuConfess).setOnClickListener {
            onConfess(app)
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuDaily).setOnClickListener {
            onDailyTask(app)
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuPunish).setOnClickListener {
            onPunishment(app)
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuSelfie).setOnClickListener {
            onSelfieUpload(app)
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuMessages).setOnClickListener {
            openMainTab(app, messages = true)
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuDismiss).setOnClickListener {
            setMenuVisible(false)
        }
    }

    private fun toggleMenu() {
        setMenuVisible(!menuVisible)
    }

    private fun setMenuVisible(visible: Boolean) {
        menuVisible = visible
        menuPanel?.visibility = if (visible) View.VISIBLE else View.GONE
        val params = layoutParams ?: return
        val wm = windowManager ?: return
        val root = rootView ?: return
        if (visible) {
            handler.removeCallbacks(hideBubbleRunnable)
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            wm.updateViewLayout(root, params)
        } catch (_: Exception) { }
        refreshWindowLayout()
    }

    private fun onConfess(app: Context) {
        val insult = QueenInsultLibrary.getRandom()?.let { QueenHonorific.apply(app, it) }.orEmpty()
        if (insult.isNotEmpty()) {
            QueenMessageStore.appendQueenMessage(app, "【认罪】$insult")
        }
        showTaunt(QueenFloatingMood.COLD_SMILE, QueenFloatingPhraseLibrary.confessionReply())
    }

    private fun onDailyTask(app: Context) {
        if (DailySelfieScheduler.shouldEnforce(app)) {
            DailySelfieEnforcement.launch(app)
            showTaunt(QueenFloatingMood.COMMAND, app.getString(R.string.queen_float_daily_enforced))
        } else {
            showTaunt(
                QueenFloatingMood.COLD_SMILE,
                app.getString(R.string.queen_float_daily_done),
            )
        }
    }

    private fun onPunishment(app: Context) {
        QueenVibratorHelper.punish(app)
        showTaunt(QueenFloatingMood.ANGRY, QueenFloatingPhraseLibrary.punishmentTaunt())
    }

    private fun onSelfieUpload(app: Context) {
        if (DailySelfieScheduler.shouldEnforce(app)) {
            DailySelfieEnforcement.bringDemandToFront(app)
        } else {
            openMainTab(app, album = true)
            Toast.makeText(app, R.string.queen_float_selfie_to_album, Toast.LENGTH_SHORT).show()
        }
        showTaunt(QueenFloatingMood.EXCITED, app.getString(R.string.queen_float_selfie_go))
    }

    private fun openMainTab(app: Context, messages: Boolean = false, album: Boolean = false) {
        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (messages) putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true)
            if (album) putExtra(MainActivity.EXTRA_OPEN_ALBUM, true)
        }
        app.startActivity(intent)
    }

    fun showTaunt(mood: QueenFloatingMood, text: String) {
        if (!isShowing) return
        val ctx = appContext ?: return
        currentMood = mood
        avatarView?.let { mood.applyAvatar(it, avatarStyle()) }
        val bubble = bubbleView ?: return
        handler.removeCallbacks(hideBubbleRunnable)
        lastTauntRaw = text
        bubble.text = QueenHonorific.apply(ctx, text)
        applyBubblePlacement()
        bubble.visibility = View.VISIBLE
        refreshWindowLayout()
        val duration = Random.nextLong(4_500L, 7_000L)
        handler.postDelayed(hideBubbleRunnable, duration)
    }

    private fun hideSpeechBubble() {
        bubbleView?.visibility = View.GONE
        refreshWindowLayout()
    }

    /**
     * 靠近屏幕顶部时气泡放在头像下方，避免气泡在上方把头像整体挤下去。
     * 靠近底部时气泡放在头像上方。
     */
    private fun applyBubblePlacement() {
        if (applyingBubblePlacement) return
        val root = rootView as? LinearLayout ?: return
        val bubble = bubbleView ?: return
        val avatar = avatarView ?: return
        val menu = menuPanel ?: return
        val below = shouldPlaceBubbleBelow()
        if (bubbleBelowAvatar == below && bubble.parent == root && avatar.parent == root) {
            return
        }
        applyingBubblePlacement = true
        try {
            bubbleBelowAvatar = below

            val gapPx = dpPx(FLOAT_GAP_DP)
            val avatarLp = avatarLayoutParams()
            val bubbleLp = bubbleLayoutParams(below, gapPx)
            val menuLp = menuLayoutParams(gapPx)

            detachFromParent(avatar)
            detachFromParent(bubble)
            detachFromParent(menu)

            if (below) {
                root.addView(avatar, avatarLp)
                root.addView(bubble, bubbleLp)
            } else {
                root.addView(bubble, bubbleLp)
                root.addView(avatar, avatarLp)
            }
            root.addView(menu, menuLp)
        } finally {
            applyingBubblePlacement = false
        }
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun dpPx(dp: Float): Int {
        val density = appContext?.resources?.displayMetrics?.density ?: 1f
        return (dp * density).toInt().coerceAtLeast(1)
    }

    /** 必须用固定 dp，不可用 WRAP_CONTENT，否则 nvw 大图会按原图像素撑满全屏。 */
    private fun avatarLayoutParams(): LinearLayout.LayoutParams {
        val side = dpPx(FLOAT_AVATAR_DP)
        return LinearLayout.LayoutParams(side, side).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    private fun bubbleLayoutParams(below: Boolean, gapPx: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dpPx(FLOAT_BUBBLE_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = if (below) gapPx else 0
            bottomMargin = if (below) 0 else gapPx
        }

    private fun menuLayoutParams(gapPx: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dpPx(FLOAT_MENU_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = gapPx
        }

    private fun shouldPlaceBubbleBelow(): Boolean {
        val params = layoutParams ?: return true
        val dm = appContext?.resources?.displayMetrics ?: return true
        val topZone = (dm.heightPixels * 0.32f).toInt()
        return params.y < topZone
    }

    /**
     * 悬浮窗必须保持 WRAP_CONTENT，勿写入固定像素宽高，否则系统会把根布局撑满导致「突然变大」。
     */
    private fun refreshWindowLayout() {
        val root = rootView ?: return
        val wm = windowManager ?: return
        val params = layoutParams ?: return
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        root.requestLayout()
        root.post {
            if (!isShowing || rootView !== root) return@post
            clampToScreen(params)
            try {
                wm.updateViewLayout(root, params)
            } catch (_: Exception) { }
        }
    }

    private fun scheduleRandomTaunt() {
        handler.removeCallbacks(randomTauntRunnable)
        if (!isShowing) return
        val delay = Random.nextLong(RANDOM_TAUNT_MIN_MS, RANDOM_TAUNT_MAX_MS)
        handler.postDelayed(randomTauntRunnable, delay)
    }

    private fun loadSavedPosition(): Pair<Int, Int> {
        val p = prefs
        val app = appContext
        if (p != null && app != null &&
            p.contains(Prefs.QUEEN_FLOAT_X) && p.contains(Prefs.QUEEN_FLOAT_Y)
        ) {
            return p.getInt(Prefs.QUEEN_FLOAT_X, 0) to p.getInt(Prefs.QUEEN_FLOAT_Y, 0)
        }
        val dm = app?.resources?.displayMetrics ?: return 0 to 0
        val size = (FLOAT_AVATAR_DP * dm.density).toInt()
        val margin = (16 * dm.density).toInt()
        val x = dm.widthPixels - size - margin
        val y = dm.heightPixels - size - margin * 3
        return x.coerceAtLeast(margin) to y.coerceAtLeast(margin)
    }

    private fun savePosition(x: Int, y: Int) {
        prefs?.edit()
            ?.putInt(Prefs.QUEEN_FLOAT_X, x)
            ?.putInt(Prefs.QUEEN_FLOAT_Y, y)
            ?.apply()
    }

    private fun clampToScreen(params: WindowManager.LayoutParams) {
        val dm = appContext?.resources?.displayMetrics ?: return
        val (windowW, windowH) = currentWindowContentSize()
        val margin = (8 * dm.density).toInt()
        params.x = params.x.coerceIn(
            margin,
            (dm.widthPixels - windowW - margin).coerceAtLeast(margin),
        )
        params.y = params.y.coerceIn(
            margin,
            (dm.heightPixels - windowH - margin).coerceAtLeast(margin),
        )
    }

    /** 已布局时用实测尺寸，否则用 dp 估算，避免拖动时按错误宽高钳位。 */
    private fun currentWindowContentSize(): Pair<Int, Int> {
        val dm = appContext?.resources?.displayMetrics
        val root = rootView
        if (root != null && root.width > 0 && root.height > 0) {
            return root.width to root.height
        }
        val d = dm?.density ?: 1f
        val avatar = (FLOAT_AVATAR_DP * d).toInt()
        val gap = (FLOAT_GAP_DP * d).toInt()
        val bubbleVisible = bubbleView?.visibility == View.VISIBLE
        val menuVisible = menuPanel?.visibility == View.VISIBLE
        val bubbleW = if (bubbleVisible) (110f * d).toInt() else 0
        val bubbleH = if (bubbleVisible) (48f * d).toInt() else 0
        val menuW = if (menuVisible) (100f * d).toInt() else 0
        val menuH = if (menuVisible) (120f * d).toInt() else 0
        val w = maxOf(avatar, bubbleW, menuW) + (4f * d).toInt()
        val h = avatar + bubbleH + menuH + gap * 2 + (4f * d).toInt()
        return w.coerceAtLeast((FLOAT_MIN_WINDOW_DP * d).toInt()) to
            h.coerceAtLeast((FLOAT_MIN_WINDOW_DP * d).toInt())
    }

    private fun isActivated(): Boolean =
        prefs?.getBoolean(Prefs.ACTIVATED, false) == true

    private fun avatarStyle(): QueenFloatingAvatarStyle {
        val app = appContext ?: return QueenFloatingAvatarStyle.DEFAULT
        return QueenFloatingAvatarStyle.current(app)
    }

    private fun applyAvatarBase(avatar: ImageView) {
        avatarStyle().applyTo(avatar)
    }

    private const val RANDOM_TAUNT_MIN_MS = 45_000L
    private const val RANDOM_TAUNT_MAX_MS = 100_000L
}
