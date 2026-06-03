package com.dianziqueen.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 二次拦截：系统设置（最强控制）→ [SettingsLockGuard]；
 * 电源/关机对话框 → [ShutdownGuard]；卸载/应用详情页 → [UninstallGuard]。
 */
class QueenAccessibilityService : AccessibilityService() {

    private var wasKeyguardLocked: Boolean = true

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> { /* continue */ }
            else -> return
        }

        maybeTriggerDeclarationChallenge(event)
        maybeReassertDeclarationBlock(event)

        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()
        val root = rootInActiveWindow ?: return
        try {
            if (SettingsLockGuard.shouldBlockSystemSettings(this)) {
                if (SettingsLockGuard.isBlockedExternalWindow(this, pkg)) {
                    val fromState = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    SettingsLockGuard.onSystemSettingsEntered(this, "window:$pkg", fromState)
                    return
                }
            }
            if (UninstallGuard.isProtectionEnabled(this) && isUninstallRelatedWindow(pkg, cls)) {
                if (containsUninstallUiForQueen(root)) {
                    UninstallGuard.onUninstallAttempt(this, "accessibility:$pkg")
                }
            }
            if (isPowerRelatedWindow(pkg, cls) && containsShutdownUi(root)) {
                ShutdownGuard.onShutdownAttemptDetected(this, "accessibility:$pkg")
            }
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {
        // 系统短暂中断后通常会再次 onServiceConnected；勿在此主动 stopSelf。
    }

    /**
     * 宣誓未完成时拦截返回 / 多任务 / Home（Home 在部分 ROM 上系统仍可能放行，配合 Lock Task）。
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!DeclarationEnforcement.shouldReassertBlocking(this)) return false
        if (DailySelfieEnforcement.isBlockingAppUsage(this)) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_HOME -> {
                onDeclarationEscapeKeyBlocked()
                true
            }
            else -> false
        }
    }

    private fun onDeclarationEscapeKeyBlocked() {
        QueenVibratorHelper.punish(this)
        if (!DeclarationEnforcement.challengeInForeground) {
            DeclarationEnforcement.bringToFront(this)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    private fun isUninstallRelatedWindow(pkg: String, cls: String): Boolean {
        if (pkg.isBlank()) return false
        val p = pkg.lowercase()
        if (!isUninstallRelatedPackage(p)) return false
        val c = cls.lowercase()
        return c.contains("uninstall") ||
            c.contains("installedappdetails") ||
            c.contains("appinfo") ||
            c.contains("applicationsettings") ||
            c.contains("deletedialog") ||
            p == "com.android.settings" ||
            p.startsWith("com.android.settings.")
    }

    /** 仅系统/厂商安装器与应用详情页；避免 HellPhone 等第三方 App 误触发卸载陷阱。 */
    private fun isUninstallRelatedPackage(p: String): Boolean {
        if (p.contains("packageinstaller")) return true
        if (p.contains("securitycenter") && p.contains("miui")) return true
        if (p == "com.android.settings" || p.startsWith("com.android.settings.")) return true
        if (p.contains("permissioncontroller")) return true
        if (p.contains("systemmanager")) return true
        if (p.contains("safecenter")) return true
        if (p.contains("permissionmanager")) return true
        if (p.contains("oplus.securitypermission")) return true
        if (p.contains("coloros") && p.contains("safe")) return true
        if (p.contains("oplus") && p.contains("safe")) return true
        if (p.contains("vivo") && p.contains("permission")) return true
        return false
    }

    private fun containsUninstallUiForQueen(node: AccessibilityNodeInfo): Boolean =
        scanUninstallNode(node, 0)

    private fun scanUninstallNode(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 12) return false
        val text = buildString {
            append(node.text?.toString().orEmpty())
            append(node.contentDescription?.toString().orEmpty())
        }
        if (matchesUninstallTextForQueen(text)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = scanUninstallNode(child, depth + 1)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun matchesUninstallTextForQueen(raw: String): Boolean {
        if (raw.isBlank()) return false
        val honorific = QueenHonorific.displayName(this)
        val mentionsQueen = raw.contains("电子Queen", ignoreCase = true) ||
            raw.contains("电子QUEEN", ignoreCase = true) ||
            raw.contains("电子女王", ignoreCase = true) ||
            raw.contains(honorific, ignoreCase = true) ||
            raw.contains(packageName, ignoreCase = true)
        if (!mentionsQueen) return false
        val t = raw.lowercase()
        return t.contains("卸载") ||
            t.contains("解除安装") ||
            t.contains("uninstall") ||
            t.contains("remove app") ||
            (t.contains("删除") && (t.contains("应用") || t.contains("app")))
    }

    private fun maybeTriggerDeclarationChallenge(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!DeclarationScheduler.isEnabled(this)) return
        if (DailySelfieEnforcement.isBlockingAppUsage(this)) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == packageName) return

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        wasKeyguardLocked = km?.isKeyguardLocked != false

        if (!DeclarationEnforcement.isScreenUsable(this)) return
        if (!DeclarationScheduler.isDue(this) && !DeclarationScheduler.isPending(this)) return
        DeclarationEnforcement.launchIfNeeded(this)
    }

    /** 未完成宣誓时：任何切到其他窗口立刻拉回宣誓页。 */
    private fun maybeReassertDeclarationBlock(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!DeclarationEnforcement.shouldReassertBlocking(this)) return
        if (DailySelfieEnforcement.isBlockingAppUsage(this)) return
        if (DeclarationEnforcement.challengeInForeground) return
        DeclarationEnforcement.bringToFront(this)
    }

    private fun isPowerRelatedWindow(pkg: String, cls: String): Boolean {
        if (pkg.contains("systemui", ignoreCase = true)) return true
        if (pkg == "android") return true
        val c = cls.lowercase()
        return c.contains("globalactions") ||
            c.contains("shutdown") ||
            c.contains("power") ||
            c.contains("actionsdialog") ||
            c.contains("reboot")
    }

    private fun containsShutdownUi(node: AccessibilityNodeInfo): Boolean =
        scanShutdownNode(node, 0)

    private fun scanShutdownNode(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 10) return false
        val text = buildString {
            append(node.text?.toString().orEmpty())
            append(node.contentDescription?.toString().orEmpty())
        }
        if (matchesShutdownText(text)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = scanShutdownNode(child, depth + 1)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun matchesShutdownText(raw: String): Boolean {
        if (raw.isBlank()) return false
        val t = raw.lowercase()
        return t.contains("关机") ||
            t.contains("重启") ||
            t.contains("重新启动") ||
            t.contains("power off") ||
            t.contains("shut down") ||
            t.contains("shutdown") ||
            t.contains("restart") ||
            t.contains("reboot")
    }

    companion object {
        @Volatile
        private var instance: QueenAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun performBackGlobally(): Boolean {
            val svc = instance ?: return false
            return svc.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun performHomeGlobally(): Boolean {
            val svc = instance ?: return false
            return svc.performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        wasKeyguardLocked = km?.isKeyguardLocked != false
        QueenDeviceAdminHelper.applyQueenPolicies(applicationContext)
        DeclarationScheduler.ensureScheduleInitialized(applicationContext)
        QueenKeepAlive.ensureRunning(applicationContext, notifyIfRestored = false)
        QueenKeepAlive.startRemoteDaemon(applicationContext)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
