package com.dianziqueen.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * 悬浮头像点击后的全屏菜单（Overlay 对话框，无需 Activity 前台）。
 */
object QueenMenuDialog {

    private var dialogRoot: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun isShowing(): Boolean = dialogRoot?.isAttachedToWindow == true

    fun show(context: Context, showTaunt: (QueenFloatingMood, String) -> Unit) {
        if (isShowing()) return
        if (!FloatingWindowPermissionHelper.hasPermission(context)) return
        val app = context.applicationContext
        try {
            val wm = ContextCompat.getSystemService(app, WindowManager::class.java) ?: return
            val root = LayoutInflater.from(app).inflate(R.layout.dialog_queen_float_menu, null)
            bindLabels(app, root)
            bindActions(app, root, showTaunt)

            root.findViewById<View>(R.id.queenMenuDialogScrim).setOnClickListener { dismiss() }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            wm.addView(root, params)
            dialogRoot = root
            windowManager = wm
            layoutParams = params
        } catch (e: Exception) {
            e.printStackTrace()
            dismiss()
        }
    }

    fun dismiss() {
        try {
            val wm = windowManager
            val view = dialogRoot
            if (wm != null && view != null && view.isAttachedToWindow) {
                wm.removeView(view)
            }
        } catch (_: Exception) { }
        dialogRoot = null
        windowManager = null
        layoutParams = null
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
