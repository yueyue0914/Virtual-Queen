package com.dianziqueen.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 二次拦截：监测系统电源/关机对话框，触发 [ShutdownGuard]。
 */
class QueenAccessibilityService : AccessibilityService() {

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

        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()
        if (!isPowerRelatedWindow(pkg, cls)) return

        val root = rootInActiveWindow ?: return
        val hit = try {
            containsShutdownUi(root)
        } finally {
            root.recycle()
        }
        if (!hit) return

        ShutdownGuard.onShutdownAttemptDetected(this, "accessibility:$pkg")
    }

    override fun onInterrupt() { }

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

        fun performBackGlobally(): Boolean {
            val svc = instance ?: return false
            return svc.performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        QueenDeviceAdminHelper.applyQueenPolicies(applicationContext)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
