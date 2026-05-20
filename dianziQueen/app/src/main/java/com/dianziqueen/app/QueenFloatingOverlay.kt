package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.random.Random

/**
 * 全局半透明悬浮女王：可拖动、随机台词、点击弹出快捷菜单。
 */
class QueenFloatingOverlay(context: Context) {

    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    private val touchSlop = (8 * app.resources.displayMetrics.density).toInt()

    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false

    private var avatarView: ImageView? = null
    private var bubbleView: TextView? = null
    private var menuPanel: LinearLayout? = null

    private var currentMood = QueenFloatingMood.MOCKING
    private var menuVisible = false

    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var dragging = false

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

    fun ensureShown() {
        if (!isActivated()) {
            hide()
            return
        }
        if (!Settings.canDrawOverlays(app)) {
            hide()
            return
        }
        if (isShowing) return
        try {
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

            val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SECURE,
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
                ?: ContextCompat.getSystemService(app, WindowManager::class.java)
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
            isShowing = false
        }
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
                    } else {
                        toggleMenu()
                        QueenVibratorHelper.lightTap(app)
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun setupMenuActions(root: View) {
        root.findViewById<TextView>(R.id.queenMenuConfess).setOnClickListener {
            onConfess()
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuDaily).setOnClickListener {
            onDailyTask()
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuPunish).setOnClickListener {
            onPunishment()
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuSelfie).setOnClickListener {
            onSelfieUpload()
            setMenuVisible(false)
        }
        root.findViewById<TextView>(R.id.queenMenuMessages).setOnClickListener {
            openMainTab(messages = true)
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
    }

    private fun onConfess() {
        val insult = QueenInsultLibrary.getRandom().orEmpty()
        if (insult.isNotEmpty()) {
            QueenMessageStore.appendQueenMessage(app, "【认罪】$insult")
        }
        showTaunt(QueenFloatingMood.COLD_SMILE, QueenFloatingPhraseLibrary.confessionReply())
    }

    private fun onDailyTask() {
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

    private fun onPunishment() {
        QueenVibratorHelper.punish(app)
        showTaunt(QueenFloatingMood.ANGRY, QueenFloatingPhraseLibrary.punishmentTaunt())
    }

    private fun onSelfieUpload() {
        if (DailySelfieScheduler.shouldEnforce(app)) {
            DailySelfieEnforcement.bringDemandToFront(app)
        } else {
            openMainTab(album = true)
            Toast.makeText(app, R.string.queen_float_selfie_to_album, Toast.LENGTH_SHORT).show()
        }
        showTaunt(QueenFloatingMood.EXCITED, app.getString(R.string.queen_float_selfie_go))
    }

    private fun openMainTab(messages: Boolean = false, album: Boolean = false) {
        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (messages) putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true)
            if (album) putExtra(MainActivity.EXTRA_OPEN_ALBUM, true)
        }
        app.startActivity(intent)
    }

    /** 设置页切换头像后刷新当前悬浮窗（若正在显示）。 */
    fun refreshAppearance() {
        val avatar = avatarView ?: return
        applyAvatarBase(avatar)
        currentMood.applyAvatar(avatar, avatarStyle())
    }

    fun showTaunt(mood: QueenFloatingMood, text: String) {
        if (!isShowing) return
        currentMood = mood
        avatarView?.let { mood.applyAvatar(it, avatarStyle()) }
        val bubble = bubbleView ?: return
        handler.removeCallbacks(hideBubbleRunnable)
        bubble.text = text
        bubble.visibility = View.VISIBLE
        val duration = Random.nextLong(4_500L, 7_000L)
        handler.postDelayed(hideBubbleRunnable, duration)
    }

    private fun hideSpeechBubble() {
        bubbleView?.visibility = View.GONE
    }

    private fun scheduleRandomTaunt() {
        handler.removeCallbacks(randomTauntRunnable)
        if (!isShowing) return
        val delay = Random.nextLong(RANDOM_TAUNT_MIN_MS, RANDOM_TAUNT_MAX_MS)
        handler.postDelayed(randomTauntRunnable, delay)
    }

    private fun loadSavedPosition(): Pair<Int, Int> {
        if (prefs.contains(Prefs.QUEEN_FLOAT_X) && prefs.contains(Prefs.QUEEN_FLOAT_Y)) {
            return prefs.getInt(Prefs.QUEEN_FLOAT_X, 0) to prefs.getInt(Prefs.QUEEN_FLOAT_Y, 0)
        }
        val dm = app.resources.displayMetrics
        val size = (72 * dm.density).toInt()
        val margin = (16 * dm.density).toInt()
        val x = dm.widthPixels - size - margin
        val y = dm.heightPixels - size - margin * 3
        return x.coerceAtLeast(margin) to y.coerceAtLeast(margin)
    }

    private fun savePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(Prefs.QUEEN_FLOAT_X, x)
            .putInt(Prefs.QUEEN_FLOAT_Y, y)
            .apply()
    }

    private fun clampToScreen(params: WindowManager.LayoutParams) {
        val dm = app.resources.displayMetrics
        val root = rootView ?: return
        val maxW = root.width.coerceAtLeast((80 * dm.density).toInt())
        val maxH = root.height.coerceAtLeast((80 * dm.density).toInt())
        val margin = (8 * dm.density).toInt()
        params.x = params.x.coerceIn(margin, (dm.widthPixels - maxW - margin).coerceAtLeast(margin))
        params.y = params.y.coerceIn(margin, (dm.heightPixels - maxH - margin).coerceAtLeast(margin))
    }

    private fun isActivated(): Boolean =
        prefs.getBoolean(Prefs.ACTIVATED, false)

    private fun avatarStyle(): QueenFloatingAvatarStyle =
        QueenFloatingAvatarStyle.current(app)

    private fun applyAvatarBase(avatar: ImageView) {
        avatarStyle().applyTo(avatar)
    }

    companion object {
        private const val RANDOM_TAUNT_MIN_MS = 45_000L
        private const val RANDOM_TAUNT_MAX_MS = 100_000L
    }
}
