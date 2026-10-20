package com.codeunlock.service

import org.junit.Assert.*
import org.junit.Test

class AppBlockerPolicyTest {

    private val distractingApps = setOf(
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.twitter.android",
        "com.instagram.android"
    )

    private val safeApps = setOf(
        "com.codeunlock",
        "com.android.settings",
        "com.google.android.dialer",
        "com.android.systemui"
    )

    private fun isAppBlocked(
        packageName: String,
        isFocusActive: Boolean,
        activeUnlockMinutes: Int,
        restrictedApps: Set<String>
    ): Boolean {
        // Safe / Essential apps are never blocked
        if (safeApps.contains(packageName)) return false

        // 1. Focus Mode Strict Lockdown: distracting apps are locked even if general unlock minutes exist
        if (isFocusActive) {
            if (packageName in distractingApps || packageName in restrictedApps) {
                return true
            }
        }

        // 2. Normal Mode: if user holds verified unlock minutes, grant access
        if (activeUnlockMinutes > 0) {
            return false
        }

        // 3. Normal Mode: if app is in restricted list and no unlock minutes exist, block it
        return packageName in restrictedApps
    }

    @Test
    fun testFocusModeStrictLockdownOverridesActiveUnlock() {
        // Even with 30 minutes of general unlock, YouTube MUST be blocked during active focus sprint
        val blocked = isAppBlocked(
            packageName = "com.google.android.youtube",
            isFocusActive = true,
            activeUnlockMinutes = 30,
            restrictedApps = distractingApps
        )
        assertTrue("YouTube must be blocked during active focus session", blocked)
    }

    @Test
    fun testNormalModeAllowsDistractingAppWithActiveUnlock() {
        // Outside focus session, if user earned 30 minutes, YouTube is allowed
        val blocked = isAppBlocked(
            packageName = "com.google.android.youtube",
            isFocusActive = false,
            activeUnlockMinutes = 30,
            restrictedApps = distractingApps
        )
        assertFalse("YouTube must be allowed when active unlock session exists and focus mode is off", blocked)
    }

    @Test
    fun testNormalModeBlocksDistractingAppWithoutUnlock() {
        // Outside focus session, without active unlock, YouTube is blocked
        val blocked = isAppBlocked(
            packageName = "com.google.android.youtube",
            isFocusActive = false,
            activeUnlockMinutes = 0,
            restrictedApps = distractingApps
        )
        assertTrue("YouTube must be blocked when no active unlock minutes exist", blocked)
    }

    @Test
    fun testEssentialAppsNeverBlocked() {
        // CodeUnlock and settings must never be blocked under any circumstances
        assertFalse(isAppBlocked("com.codeunlock", isFocusActive = true, activeUnlockMinutes = 0, restrictedApps = distractingApps))
        assertFalse(isAppBlocked("com.android.settings", isFocusActive = true, activeUnlockMinutes = 0, restrictedApps = distractingApps))
        assertFalse(isAppBlocked("com.android.systemui", isFocusActive = true, activeUnlockMinutes = 0, restrictedApps = distractingApps))
        assertFalse(isAppBlocked("com.google.android.dialer", isFocusActive = true, activeUnlockMinutes = 0, restrictedApps = distractingApps))
    }
}
