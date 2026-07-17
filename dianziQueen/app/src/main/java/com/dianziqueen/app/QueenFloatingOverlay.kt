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
import androidx.core.content.ContextCompat
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

    /**
     * 展示期间必须强引用：若只用 WeakReference，GC 后 WindowManager 仍挂着旧 View，
     * 再 ensureShown 会叠第二个悬浮窗，且孤儿窗抢走触摸导致拖不动/点不了。
     *
     * [hostView] 挂到 WindowManager；[rootView] 是其中的内容（头像+气泡）。
     * 拖动只改 LayoutParams.x/y，绝不把窗口临时挪到 (0,0)，否则松手时系统会做从左上角飞入的动画。
     */
    private var hostView: FrameLayout? = null
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var overlayVisible = false
    private var ensureInFlight = false

    private var avatarView: ImageView? = null
    private var bubbleView: TextView? = null
    private var menuPanel: LinearLayout? = null

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
    private var pointerDownOnFloat = false
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
                ctx.toastLong(msg)
            }
            scheduleRandomInsultToast()
        }
    }

    fun isShowing(): Boolean = overlayVisible

    fun isAttached(): Boolean = hostView?.isAttachedToWindow == true

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
        if (overlayVisible && hostView?.isAttachedToWindow == true) {
            if (menuCompanionHidden && !QueenMenuDialog.isShowing()) {
                // 菜单残留导致头像不可见：恢复可点可拖
                menuCompanionHidden = false
                hostView?.visibility = View.VISIBLE
            }
            refreshAppearance()
            return
        }
        if (ensureInFlight) return
        ensureInFlight = true
        try {
            // 先卸掉可能残留的旧 View，避免叠两个
            hide()
            val app = appContext!!
            if (!isActivated(app) || !PermissionChecker.hasOverlay(app)) return
            val wm = ContextCompat.getSystemService(app, WindowManager::class.java) ?: return
            val inflater = LayoutInflater.from(app)
            val host = FrameLayout(app).apply {
                // 透明全屏接拖时不挡视线
                setBackgroundColor(0x00000000)
            }
            val root = inflater.inflate(R.layout.overlay_queen_floating, host, false)
            val avatar = root.findViewById<ImageView>(R.id.queenFloatAvatar)
            val bubble = root.findViewById<TextView>(R.id.queenSpeechBubble)
            val menu = root.findViewById<LinearLayout>(R.id.queenFloatMenu)

            host.addView(
                root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            hostView = host
            rootView = root
            avatarView = avatar
            bubbleView = bubble
            menuPanel = menu

            bubble.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                QueenFloatingSize.bubbleTextSp(),
            )
            applyAvatarBase(avatar)
            applyAvatarSizeCaps(avatar)
            currentMood.applyAvatar(avatar, avatarStyle())
            menu.visibility = View.GONE
            setupAdvancedDragging(host, root, avatar, wm)
            refreshHonorificLabels()

            val (x, y) = loadSavedPosition()
            val params = createOverlayLayoutParams(x, y)

            wm.addView(host, params)
            layoutParams = params
            windowManager = wm
            overlayVisible = true
            menuCompanionHidden = false

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
        } finally {
            ensureInFlight = false
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
        if (overlayVisible && hostView?.isAttachedToWindow == true) {
            if (menuCompanionHidden && !QueenMenuDialog.isShowing()) {
                menuCompanionHidden = false
                hostView?.visibility = View.VISIBLE
            }
            return
        }
        ensureShown(app)
    }

    fun hide() {
        handler.removeCallbacks(hideBubbleRunnable)
        handler.removeCallbacks(randomTauntRunnable)
        handler.removeCallbacks(randomInsultToastRunnable)
        QueenMenuDialog.dismiss()
        menuCompanionHidden = false
        val toRemove = hostView
        try {
            val wm = windowManager
                ?: appContext?.let {
                    ContextCompat.getSystemService(it, WindowManager::class.java)
                }
            if (toRemove != null) {
                try {
                    wm?.removeView(toRemove)
                } catch (_: Exception) {
                    try {
                        wm?.removeViewImmediate(toRemove)
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            QueenLogger.w("FloatOverlay", "hide removeView failed", e)
        } finally {
            hostView = null
            rootView = null
            layoutParams = null
            windowManager = null
            avatarView = null
            bubbleView = null
            menuPanel = null
            bubbleBelowAvatar = null
            applyingBubblePlacement = false
            lastTauntRaw = null
            overlayVisible = false
            dragging = false
            pointerDownOnFloat = false
        }
    }

    /** 菜单打开时隐藏头像悬浮层，避免多 Overlay 在部分 ROM 上抢 Z 序导致菜单无法点击。 */
    internal fun onMenuOpening() {
        if (!overlayVisible || menuCompanionHidden) return
        menuCompanionHidden = true
        hostView?.visibility = View.INVISIBLE
    }

    internal fun onMenuClosed() {
        if (!menuCompanionHidden) return
        menuCompanionHidden = false
        hostView?.visibility = View.VISIBLE
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
            idleOverlayFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            // 禁止系统对悬浮窗做位移动画，否则松手会从 (0,0)「飞」到目标点
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun idleOverlayFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    /** 拖动中去掉 NOT_TOUCH_MODAL，并允许越界，便于手指移出小窗后仍能收到 MOVE。 */
    private fun dragOverlayFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAdvancedDragging(
        host: FrameLayout,
        content: View,
        avatar: ImageView,
        wm: WindowManager,
    ) {
        // 半透明 / 圆形裁剪头像在 NOT_TOUCH_MODAL 下，透明像素点击会穿透到下层，
        // 表现为「只能点到不透明的说话气泡」。给头像垫一层极淡不透明底。
        ensureOpaqueTouchTarget(avatar)
        bubbleView?.let { ensureOpaqueTouchTarget(it) }
        ensureOpaqueTouchTarget(content)

        avatar.isClickable = true
        avatar.isFocusable = false
        avatar.isFocusableInTouchMode = false
        bubbleView?.isClickable = true
        menuPanel?.isClickable = false
        content.isClickable = true
        host.isClickable = false
        host.isMotionEventSplittingEnabled = false

        val dragListener = View.OnTouchListener { _, event ->
            val params = layoutParams ?: return@OnTouchListener false
            val manager = windowManager ?: wm
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (menuCompanionHidden) return@OnTouchListener false
                    dragging = false
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    pointerDownOnFloat = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!pointerDownOnFloat) return@OnTouchListener false
                    val dx = (event.rawX - touchStartRawX).toInt()
                    val dy = (event.rawY - touchStartRawY).toInt()
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        QueenMenuDialog.dismiss()
                        dragging = true
                        params.flags = dragOverlayFlags()
                        params.windowAnimations = 0
                    }
                    if (dragging) {
                        params.x = dragStartX + dx
                        params.y = dragStartY + dy
                        params.width = WindowManager.LayoutParams.WRAP_CONTENT
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        clampToScreen(params)
                        try {
                            manager.updateViewLayout(host, params)
                        } catch (e: Exception) {
                            QueenLogger.w("FloatOverlay", "drag update failed", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragging
                    if (wasDragging) {
                        snapToNearestEdge(params)
                        params.flags = idleOverlayFlags()
                        params.windowAnimations = 0
                        params.width = WindowManager.LayoutParams.WRAP_CONTENT
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        savePosition(params.x, params.y)
                        try {
                            manager.updateViewLayout(host, params)
                        } catch (e: Exception) {
                            QueenLogger.w("FloatOverlay", "drag end failed", e)
                        }
                        handler.post {
                            if (overlayVisible) {
                                applyBubblePlacement()
                                refreshWindowLayout()
                            }
                        }
                    } else if (pointerDownOnFloat && event.actionMasked == MotionEvent.ACTION_UP) {
                        appContext?.let { ctx ->
                            QueenVibratorHelper.lightTap(ctx)
                            QueenMenuDialog.show(ctx, ::showTaunt)
                        }
                    }
                    dragging = false
                    pointerDownOnFloat = false
                    true
                }
                else -> false
            }
        }
        avatar.setOnTouchListener(dragListener)
        bubbleView?.setOnTouchListener(dragListener)
        content.setOnTouchListener(dragListener)
    }

    /** 极淡不透明底，避免 TRANSLUCENT + NOT_TOUCH_MODAL 时透明像素点不中。 */
    private fun ensureOpaqueTouchTarget(view: View) {
        if (view.background == null) {
            view.setBackgroundColor(0x01000000)
        }
    }

    /** 松手后吸附到左右边缘，减少 MIUI 误触遮挡。 */
    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        val dm = appContext?.resources?.displayMetrics ?: return
        val (windowW, _) = dragClampSize()
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
        if (dragging) return
        if (QueenMenuDialog.isShowing()) {
            QueenMenuDialog.bringToFront()
            return
        }
        val host = hostView ?: return
        val wm = windowManager ?: return
        val params = layoutParams ?: return
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.flags = idleOverlayFlags()
        params.windowAnimations = 0
        rootView?.requestLayout()
        host.post {
            if (!overlayVisible || hostView !== host || dragging) return@post
            clampToScreen(params)
            try {
                wm.updateViewLayout(host, params)
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
        val (windowW, windowH) = dragClampSize()
        val margin = (8 * dm.density).toInt()
        val maxX = (dm.widthPixels - windowW - margin).coerceAtLeast(margin)
        val maxY = (dm.heightPixels - windowH - margin).coerceAtLeast(margin)
        params.x = params.x.coerceIn(margin, maxX)
        params.y = params.y.coerceIn(margin, maxY)
    }

    /**
     * 拖动钳位用尺寸：优先实测，但若异常接近全屏（会把 x/y 锁死在边缘），改用头像估算值。
     */
    private fun dragClampSize(): Pair<Int, Int> {
        val dm = appContext?.resources?.displayMetrics
        val density = dm?.density ?: 1f
        val avatar = (floatAvatarDp() * density).toInt().coerceAtLeast(1)
        val estimated = avatar + (8f * density).toInt()
        val root = rootView
        if (root != null && root.width > 0 && root.height > 0 && dm != null) {
            val tooWide = root.width > dm.widthPixels * 0.45f
            val tooTall = root.height > dm.heightPixels * 0.45f
            if (!tooWide && !tooTall) {
                return root.width to root.height
            }
        }
        return estimated to estimated
    }

    /** 已布局时用实测尺寸，否则用 dp 估算，避免拖动时按错误宽高钳位。 */
    private fun currentWindowContentSize(): Pair<Int, Int> = dragClampSize()

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
    /** Toast 羞辱：35 秒～2.5 分钟随机。 */
    private const val RANDOM_INSULT_MIN_MS = 35_000L
    private const val RANDOM_INSULT_MAX_MS = 150_000L
}
