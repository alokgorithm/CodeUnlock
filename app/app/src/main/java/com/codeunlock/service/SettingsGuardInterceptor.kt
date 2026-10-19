package com.codeunlock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.codeunlock.data.settings.StrictModeStore
import com.codeunlock.ui.lock.LockActivity

/**
 * SettingsGuardInterceptor — prevents bypassing restrictions via phone Settings.
 *
 * When Strict Mode is active:
 * - Blocks navigating into CodeUnlock App Info to "Force Stop" or "Clear Data"
 * - Blocks disabling Accessibility Service in System Settings
 * - Blocks uninstallation dialogs
 *
 * Automatically triggers GLOBAL_ACTION_HOME and routes to LockActivity with a strict mode notice.
 */
class SettingsGuardInterceptor(private val service: AccessibilityService) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastInterceptionTimestamp = 0L

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val context = service.applicationContext
        if (!StrictModeStore.isSettingsGuardEnabled(context)) return
        if (!StrictModeStore.isProtectionActive(context)) return

        val packageName = event.packageName?.toString() ?: return
        if (!SETTINGS_PACKAGES.contains(packageName) && !packageName.contains("settings", ignoreCase = true)) {
            return
        }

        val rootNode = service.rootInActiveWindow ?: return
        try {
            if (isTamperingWithCodeUnlock(rootNode)) {
                val now = System.currentTimeMillis()
                if (now - lastInterceptionTimestamp > INTERCEPTION_COOLDOWN_MS) {
                    lastInterceptionTimestamp = now
                    interceptTamperingAttempt()
                }
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun isTamperingWithCodeUnlock(node: AccessibilityNodeInfo): Boolean {
        var hasCodeUnlock = false
        var hasTamperKeyword = false

        fun scan(current: AccessibilityNodeInfo) {
            val text = current.text?.toString()?.lowercase() ?: ""
            val desc = current.contentDescription?.toString()?.lowercase() ?: ""

            if (text.contains("codeunlock") || desc.contains("codeunlock")) {
                hasCodeUnlock = true
            }

            for (keyword in TAMPER_KEYWORDS) {
                if (text.contains(keyword) || desc.contains(keyword)) {
                    hasTamperKeyword = true
                }
            }

            if (hasCodeUnlock && (hasTamperKeyword || text.contains("app info") || desc.contains("app info"))) {
                return
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child ->
                    scan(child)
                    child.recycle()
                }
            }
        }

        scan(node)
        return hasCodeUnlock
    }

    private fun interceptTamperingAttempt() {
        Log.w(TAG, "Settings Guard intercepted tampering attempt on CodeUnlock")

        // 1. Send device to home screen immediately
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        // 2. Display anti-tamper toast notification
        mainHandler.post {
            Toast.makeText(
                service.applicationContext,
                "🛡️ CodeUnlock Strict Mode: Settings are protected until you solve your coding challenge!",
                Toast.LENGTH_LONG
            ).show()
        }

        // 3. Launch LockActivity explaining the protection
        val intent = LockActivity.createIntent(
            context = service.applicationContext,
            appName = "System Settings (Protected)",
            appPackage = "com.android.settings"
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        service.startActivity(intent)
    }

    companion object {
        private const val TAG = "CodeUnlock.SettingsGuard"
        private const val INTERCEPTION_COOLDOWN_MS = 2_500L

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.samsung.android.lool",
            "com.huawei.systemmanager"
        )

        private val TAMPER_KEYWORDS = listOf(
            "force stop",
            "uninstall",
            "disable",
            "clear storage",
            "clear data",
            "accessibility",
            "device admin"
        )
    }
}
