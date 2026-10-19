package com.codeunlock.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.codeunlock.data.settings.StrictModeStore

/**
 * CodeUnlockDeviceAdminReceiver — provides anti-uninstall protection inspired by Regain.
 *
 * When activated as Device Administrator:
 * - The Android system prevents uninstalling CodeUnlock directly from launcher or Play Store
 * - Attempting to deactivate admin in System Settings triggers `onDisableRequested` warning
 *   if Strict Mode is active or a coding focus session is in progress
 */
class CodeUnlockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Administrator protection enabled")
        StrictModeStore.setPreventUninstallEnabled(context, true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Administrator protection disabled")
        StrictModeStore.setPreventUninstallEnabled(context, false)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        if (StrictModeStore.isProtectionActive(context)) {
            Log.w(TAG, "Device admin deactivation requested while Strict Mode is ACTIVE")
            return "⚠️ CodeUnlock Strict Mode is active! You cannot disable device protection while a focus session is in progress or daily challenge is locked. Solve your problem in CodeUnlock to unlock."
        }
        return null
    }

    companion object {
        private const val TAG = "CodeUnlock.DeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, CodeUnlockDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            return dpm.isAdminActive(getComponentName(context))
        }

        fun createEnableAdminIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "CodeUnlock uses Device Administrator permissions to prevent bypassing screen time restrictions and accidental uninstalls during active coding focus sessions."
                )
            }
        }
    }
}
