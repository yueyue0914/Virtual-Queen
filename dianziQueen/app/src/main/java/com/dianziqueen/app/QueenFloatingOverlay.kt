package com.dianziqueen.app

import android.annotation.SuppressLint
import android.content.Context
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.random.Random

/**
 * 全局半透明悬浮女王（对应设计稿中的 FloatingQueenWindow）。
 * 可拖动、随机气泡台词、点击弹出 [QueenMenuDialog]、随机 Toast 羞辱；
 * 由 [QueenService] / [QueenFloatingWindow] 在激活且已授权悬浮窗时拉起。
 */
object QueenFloatingOverlay {

    /** 悬浮头像边长（dp），约为初版的 1/2；可在设置页调整。 */
    private fun floatAvatarDp(): Float {
        val ctx = appContext ?: return QueenFloatingSize.DEFAULT_AVATAR_DP
        return QueenFloatingSize.avatarDp(ctx)
    }

    private fun floatBubbleWidthDp(): Float = QueenFloatingSize.bubbleWidthDp()

    private fun floatMenuWidthDp(): Float = QueenFloatingSize.menuWidthDp()

    private const val FLOAT_GAP_DP = 3f
    private const val FLOAT_MIN_WINDOW_DP = 40f

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private val touchSlop: Int
        get() = (8 * (appContext?.resources?.displayMetrics?.density ?: 1f)).toInt()

    private var rootViewRef = WeakReference<View>(null)
    private var layoutParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var overlayVisible = false

    private var avatarViewRef = WeakReference<ImageView>(null)
    private var bubbleViewRef = WeakReference<TextView>(null)
    private var menuPanelRef = WeakReference<LinearLayout>(null)

    private val rootView: View? get() = rootViewRef.get()
    private val avatarView: ImageView? get() = avatarViewRef.get()
    private val bubbleView: TextView? get() = bubbleViewRef.get()
    private val menuPanel: LinearLayout? get() = menuPanelRef.get()

    private var currentMood = QueenFloatingMood.MOCKING
    private var bubbleBelowAvatar: Boolean? = null
    private var applyingBubblePlacement = false
    /** 未替换称谓的原文，便于改称谓后刷新当前气泡。 */
    private var lastTauntRaw: String? = null

    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var dragging = false
    private var pointerDownOnAvatar = false
    private var menuCompanionHidden = false

    private val prefs
        get() = appContext?.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    private val hideBubbleRunnable = Runnable { hideSpeechBubble() }
    private val randomTauntRunnable = object : Runnable {
        override fun run() {
            if (!overlayVisible || !isActivated()) return
            if (!QueenMenuDialog.isShowing()) {
                val (mood, line) = QueenFloatingPhraseLibrary.randomLine()
                showTaunt(mood, line)
            }
            scheduleRandomTaunt()
        }
    }
    private val randomInsultToastRunnable = object : Runnable {
        override fun run() {
            if (!overlayVisible || !isActivated()) return
            val ctx = appContext ?: return
            val msg = QueenInsultLibrary.getRandom()?.let { QueenHonorific.apply(ctx, it) }
            if (msg != null) {
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
            scheduleRandomInsultToast()
        }
    }

    fun isShowing(): Boolean = overlayVisible

    fun isAttached(): Boolean = rootView?.isAttachedToWindow == true

    fun applicationContextOrNull(): Context? = appContext

    fun isActivated(context: Context): Boolean =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.ACTIVATED, false)

    fun ensureShown(context: Context) {
        appContext = context.applicationContext
        if (!isActivated()) {
            hide()
            return
        }
        if (!PermissionChecker.hasOverlay(appContext!!)) {
            hide()
            return
        }
        if (overlayVisible && rootView?.isAttachedToWindow == true) {
            refreshAppearance()
            return
        }
        if (overlayVisible) {
            hide()
        }
        try {
            val app = appContext!!
            val wm = ContextCompat.getSystemService(app, WindowManager::class.java) ?: return
            val inflater = LayoutInflater.from(app)
            val inflateParent = FrameLayout(app)
            val root = inflater.inflate(R.layout.overlay_queen_floating, inflateParent, false)
            val avatar = root.findViewById<ImageView>(R.id.queenFloatAvatar)
            val bubble = root.findViewById<TextView>(R.id.queenSpeechBubble)
            val menu = root.findViewById<LinearLayout>(R.id.queenFloatMenu)

            avatarViewRef = WeakReference(avatar)
            bubbleViewRef = WeakReference(bubble)
            menuPanelRef = WeakReference(menu)

            bubble.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                QueenFloatingSize.bubbleTextSp(),
            )
            applyAvatarBase(avatar)
            applyAvatarSizeCaps(avatar)
            currentMood.applyAvatar(avatar, avatarStyle())
            menu.visibility = View.GONE
            setupAdvancedDragging(root, avatar, wm)
            refreshHonorificLabels()

            val (x, y) = loadSavedPosition()
            val params = createOverlayLayoutParams(x, y)

            wm.addView(root, params)
            rootViewRef = WeakReference(root)
            layoutParams = params
            windowManager = wm
            overlayVisible = true

            handler.postDelayed({
                if (overlayVisible) {
                    showTaunt(
                        QueenFloatingMood.COMMAND,
                        app.getString(R.string.queen_float_welcome),
                    )
                }
            }, 2_500L)
            scheduleRandomTaunt()
            scheduleRandomInsultToast()
            handler.post {
                applyBubblePlacement(forceRelayout = true)
            }
        } catch (e: Exception) {
            QueenLogger.e("FloatOverlay", "ensureShown failed", e)
            hide()
        }
    }

    /** 被 MIUI 等摘掉后由 [QueenFloatingWindow] 看门狗调用，尝试重新挂载。 */
    fun reattachIfNeeded() {
        if (!isActivated()) {
            hide()
            return
        }
        val app = appContext ?: return
        if (!PermissionChecker.hasOverlay(app)) return
        if (overlayVisible && rootView?.isAttachedToWindow == true) return
        overlayVisible = false
        ensureShown(app)
    }

    fun hide() {
        handler.removeCallbacks(hideBubbleRunnable)
        handler.removeCallbacks(randomTauntRunnable)
        handler.removeCallbacks(randomInsultToastRunnable)
        QueenMenuDialog.dismiss()
        menuCompanionHidden = false
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
            QueenLogger.w("FloatOverlay", "hide removeView failed", e)
        } finally {
            rootViewRef = WeakReference(null)
            layoutParams = null
            windowManager = null
            avatarViewRef = WeakReference(null)
            bubbleViewRef = WeakReference(null)
            menuPanelRef = WeakReference(null)
            bubbleBelowAvatar = null
            applyingBubblePlacement = false
            lastTauntRaw = null
            overlayVisible = false
        }
    }

    /** 菜单打开时隐藏头像悬浮层，避免多 Overlay 在部分 ROM 上抢 Z 序导致菜单无法点击。 */
    internal fun onMenuOpening() {
        if (!overlayVisible || menuCompanionHidden) return
        menuCompanionHidden = true
        rootView?.visibility = View.INVISIBLE
    }

    internal fun onMenuClosed() {
        if (!menuCompanionHidden) return
        menuCompanionHidden = false
        rootView?.visibility = View.VISIBLE
        refreshWindowLayout()
    }

    /** 用户更改称谓后，刷新当前气泡文案。 */
    fun refreshHonorificLabels() {
        val ctx = appContext ?: return
        if (!overlayVisible) return
        val raw = lastTauntRaw ?: return
        val bubble = bubbleView ?: return
        if (bubble.visibility == View.VISIBLE) {
            bubble.text = QueenHonorific.apply(ctx, raw)
        }
    }

    fun refreshAppearance() {
        refreshSize()
    }

    /** 设置页调整大小或切换样式后调用。 */
    fun refreshSize() {
        val avatar = avatarView ?: return
        if (appContext == null) return
        applyAvatarBase(avatar)
        currentMood.applyAvatar(avatar, avatarStyle())
        if (!overlayVisible) return
        applyAvatarSizeCaps(avatar)
        avatar.layoutParams = avatarLayoutParams()
        avatar.requestLayout()
        rootView?.requestLayout()
        refreshWindowLayout()
    }

    private fun createOverlayLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAdvancedDragging(root: View, avatar: ImageView, wm: WindowManager) {
        avatar.isClickable = false
        avatar.isFocusable = false
        avatar.setOnClickListener {
            appContext?.let { ctx ->
                QueenVibratorHelper.lightTap(ctx)
                QueenMenuDialog.show(ctx, ::showTaunt)
            }
        }
        root.isClickable = true
        root.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    pointerDownOnAvatar = isTouchOnAvatar(avatar, event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartRawX).toInt()
                    val dy = (event.rawY - touchStartRawY).toInt()
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        QueenMenuDialog.dismiss()
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
                        snapToNearestEdge(params)
                        savePosition(params.x, params.y)
                        handler.post {
                            applyBubblePlacement()
                            refreshWindowLayout()
                        }
                    } else if (pointerDownOnAvatar) {
                        avatar.performClick()
                    }
                    dragging = false
                    pointerDownOnAvatar = false
                    true
                }
                else -> false
            }
        }
    }

    private fun isTouchOnAvatar(avatar: ImageView, event: MotionEvent): Boolean {
        val loc = IntArray(2)
        avatar.getLocationOnScreen(loc)
        return event.rawX >= loc[0] && event.rawX <= loc[0] + avatar.width &&
            event.rawY >= loc[1] && event.rawY <= loc[1] + avatar.height
    }

    /** 松手后吸附到左右边缘，减少 MIUI 误触遮挡。 */
    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        val dm = appContext?.resources?.displayMetrics ?: return
        val (windowW, _) = currentWindowContentSize()
        val margin = (16 * dm.density).toInt()
        val centerX = params.x + windowW / 2
        params.x = if (centerX < dm.widthPixels / 2) {
            margin
        } else {
            (dm.widthPixels - windowW - margin).coerceAtLeast(margin)
        }
        clampToScreen(params)
    }

    fun showTaunt(mood: QueenFloatingMood, text: String) {
        if (!overlayVisible) return
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
    private fun applyBubblePlacement(forceRelayout: Boolean = false) {
        if (applyingBubblePlacement) return
        val root = rootView as? LinearLayout ?: return
        val bubble = bubbleView ?: return
        val avatar = avatarView ?: return
        val menu = menuPanel ?: return
        val below = shouldPlaceBubbleBelow()
        val gapPx = dpPx(FLOAT_GAP_DP)
        if (!forceRelayout &&
            bubbleBelowAvatar == below &&
            bubble.parent == root &&
            avatar.parent == root
        ) {
            updateFloatChildLayoutParams(below, gapPx)
            return
        }
        applyingBubblePlacement = true
        try {
            bubbleBelowAvatar = below

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
            applyAvatarSizeCaps(avatar)
        } finally {
            applyingBubblePlacement = false
        }
    }

    /** 仅更新尺寸（方向不变时），并解除 XML 里 36dp 的 max 限制。 */
    private fun updateFloatChildLayoutParams(below: Boolean, gapPx: Int) {
        val avatar = avatarView ?: return
        val bubble = bubbleView ?: return
        val menu = menuPanel ?: return
        applyAvatarSizeCaps(avatar)
        avatar.layoutParams = avatarLayoutParams()
        bubble.layoutParams = bubbleLayoutParams(below, gapPx)
        menu.layoutParams = menuLayoutParams(gapPx)
        avatar.requestLayout()
        bubble.requestLayout()
        menu.requestLayout()
        rootView?.requestLayout()
    }

    private fun applyAvatarSizeCaps(avatar: ImageView) {
        val side = dpPx(floatAvatarDp())
        avatar.maxWidth = side
        avatar.maxHeight = side
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
        val side = dpPx(floatAvatarDp())
        return LinearLayout.LayoutParams(side, side).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    private fun bubbleLayoutParams(below: Boolean, gapPx: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dpPx(floatBubbleWidthDp()), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = if (below) gapPx else 0
            bottomMargin = if (below) 0 else gapPx
        }

    private fun menuLayoutParams(gapPx: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dpPx(floatMenuWidthDp()), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
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
        if (QueenMenuDialog.isShowing()) {
            QueenMenuDialog.bringToFront()
            return
        }
        val root = rootView ?: return
        val wm = windowManager ?: return
        val params = layoutParams ?: return
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        root.requestLayout()
        root.post {
            if (!overlayVisible || rootView !== root) return@post
            clampToScreen(params)
            try {
                wm.updateViewLayout(root, params)
            } catch (_: Exception) { }
        }
    }

    private fun scheduleRandomTaunt() {
        handler.removeCallbacks(randomTauntRunnable)
        if (!overlayVisible) return
        val delay = Random.nextLong(RANDOM_TAUNT_MIN_MS, RANDOM_TAUNT_MAX_MS)
        handler.postDelayed(randomTauntRunnable, delay)
    }

    private fun scheduleRandomInsultToast() {
        handler.removeCallbacks(randomInsultToastRunnable)
        if (!overlayVisible) return
        val delay = Random.nextLong(RANDOM_INSULT_MIN_MS, RANDOM_INSULT_MAX_MS)
        handler.postDelayed(randomInsultToastRunnable, delay)
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
        val size = (floatAvatarDp() * dm.density).toInt()
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
        val avatar = (floatAvatarDp() * d).toInt()
        val gap = (FLOAT_GAP_DP * d).toInt()
        val bubbleVisible = bubbleView?.visibility == View.VISIBLE
        val bubbleW = if (bubbleVisible) (floatBubbleWidthDp() * d).toInt() else 0
        val bubbleH = if (bubbleVisible) (48f * d).toInt() else 0
        val w = maxOf(avatar, bubbleW) + (4f * d).toInt()
        val h = avatar + bubbleH + gap * 2 + (4f * d).toInt()
        return w.coerceAtLeast((FLOAT_MIN_WINDOW_DP * d).toInt()) to
            h.coerceAtLeast((FLOAT_MIN_WINDOW_DP * d).toInt())
    }

    private fun isActivated(): Boolean {
        val ctx = appContext ?: return false
        return isActivated(ctx)
    }

    private fun avatarStyle(): QueenFloatingAvatarStyle {
        val app = appContext ?: return QueenFloatingAvatarStyle.DEFAULT
        return QueenFloatingAvatarStyle.current(app)
    }

    private fun applyAvatarBase(avatar: ImageView) {
        avatarStyle().applyTo(avatar)
    }

    private const val RANDOM_TAUNT_MIN_MS = 45_000L
    private const val RANDOM_TAUNT_MAX_MS = 100_000L
    private const val RANDOM_INSULT_MIN_MS = 35_000L
    private const val RANDOM_INSULT_MAX_MS = 120_000L
}
