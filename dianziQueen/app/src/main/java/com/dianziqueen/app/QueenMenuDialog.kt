package com.dianziqueen.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/**
 * 悬浮头像点击后的全屏菜单（Overlay 对话框，无需 Activity 前台）。
 */
object QueenMenuDialog {

    private const val TAG = "MenuDialog"

    private var dialogRootRef = WeakReference<View>(null)
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val dialogRoot: View? get() = dialogRootRef.get()

    fun isShowing(): Boolean = dialogRoot?.isAttachedToWindow == true

    fun show(context: Context, showTaunt: (QueenFloatingMood, String) -> Unit) {
        if (isShowing()) {
            bringToFront()
            return
        }
        if (!PermissionChecker.hasOverlay(context)) return
        val app = context.applicationContext
        val ok = runCatchingQueen(TAG, "show menu") {
            val wm = ContextCompat.getSystemService(app, WindowManager::class.java) ?: return@runCatchingQueen
            val inflateParent = FrameLayout(app)
            val root = LayoutInflater.from(app)
                .inflate(R.layout.dialog_queen_float_menu, inflateParent, false)
            bindLabels(app, root)
            bindActions(app, root, showTaunt)
            setupTouchAndKeys(root)

            val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            // 真正模态：不加 NOT_FOCUSABLE / NOT_TOUCH_MODAL / NO_LIMITS，
            // 否则国产 ROM 上按钮点不中、点空白关不掉。DIM_BEHIND 让背景变暗。
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT,
            )
            params.dimAmount = 0.55f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            QueenFloatingOverlay.onMenuOpening()
            wm.addView(root, params)
            dialogRootRef = WeakReference(root)
            windowManager = wm
            layoutParams = params
            root.post {
                root.isFocusable = true
                root.isFocusableInTouchMode = true
                root.requestFocus()
                bringToFront()
            }
        }
        if (!ok) dismiss()
    }

    fun dismiss() {
        runCatchingQueen(TAG, "dismiss menu") {
            val wm = windowManager
            val view = dialogRoot
            if (wm != null && view != null && view.isAttachedToWindow) {
                wm.removeView(view)
            }
        }
        dialogRootRef = WeakReference(null)
        windowManager = null
        layoutParams = null
        QueenFloatingOverlay.onMenuClosed()
    }

    /** 部分 ROM 会在其它悬浮窗 updateViewLayout 后把菜单压到下层，需重新置顶。 */
    fun bringToFront() {
        val wm = windowManager ?: return
        val view = dialogRoot ?: return
        val params = layoutParams ?: return
        if (!view.isAttachedToWindow) return
        runCatchingQueen(TAG, "bringToFront") {
            wm.updateViewLayout(view, params)
        }
    }

    private fun setupTouchAndKeys(root: View) {
        val scrim = root.findViewById<View>(R.id.queenMenuDialogScrim)
        val panel = root.findViewById<View>(R.id.queenMenuDialogPanel)

        scrim.isClickable = true
        scrim.isFocusable = true
        // 变暗由 FLAG_DIM_BEHIND 负责，scrim 透明但仍接管点击以便点空白关闭。
        scrim.setBackgroundColor(0x00000000)
        scrim.setOnClickListener { dismiss() }
        // 部分机型 OnClickListener 不可靠，用 Touch 兜底。
        scrim.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP && !isTouchInsideView(panel, event)) {
                dismiss()
                true
            } else {
                v.onTouchEvent(event)
            }
        }

        panel.setOnClickListener { /* 吃掉点击，避免误触 scrim */ }

        root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss()
                true
            } else {
                false
            }
        }
    }

    private fun isTouchInsideView(view: View, event: MotionEvent): Boolean {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = event.rawX
        val y = event.rawY
        return x >= loc[0] && x <= loc[0] + view.width &&
            y >= loc[1] && y <= loc[1] + view.height
    }

    private fun bindLabels(app: Context, root: View) {
        root.findViewById<TextView>(R.id.queenMenuDialogTitle).text =
            app.hon(R.string.queen_float_menu_title)
        root.findViewById<TextView>(R.id.queenMenuConfess).text =
            app.hon(R.string.queen_float_menu_confess)
        root.findViewById<TextView>(R.id.queenMenuDaily).text =
            app.hon(R.string.queen_float_menu_daily)
        root.findViewById<TextView>(R.id.queenMenuPunish).text =
            app.hon(R.string.queen_float_menu_punish)
        root.findViewById<TextView>(R.id.queenMenuSelfie).text =
            app.hon(R.string.queen_float_menu_selfie)
        root.findViewById<TextView>(R.id.queenMenuMessages).text =
            app.hon(R.string.queen_float_menu_messages)
        root.findViewById<TextView>(R.id.queenMenuDismiss).text =
            app.hon(R.string.queen_float_menu_dismiss)
    }

    private fun bindActions(
        app: Context,
        root: View,
        showTaunt: (QueenFloatingMood, String) -> Unit,
    ) {
        root.findViewById<TextView>(R.id.queenMenuConfess).setOnClickListener {
            QueenFloatingActions.onConfess(app, showTaunt)
            dismiss()
        }
        root.findViewById<TextView>(R.id.queenMenuDaily).setOnClickListener {
            QueenFloatingActions.onDailyTask(app, showTaunt)
            dismiss()
        }
        root.findViewById<TextView>(R.id.queenMenuPunish).setOnClickListener {
            QueenFloatingActions.onPunishment(app, showTaunt)
            dismiss()
        }
        root.findViewById<TextView>(R.id.queenMenuSelfie).setOnClickListener {
            QueenFloatingActions.onSelfieUpload(app, showTaunt)
            dismiss()
        }
        root.findViewById<TextView>(R.id.queenMenuMessages).setOnClickListener {
            QueenFloatingActions.onMessages(app)
            dismiss()
        }
        root.findViewById<TextView>(R.id.queenMenuDismiss).setOnClickListener { dismiss() }
    }
}
