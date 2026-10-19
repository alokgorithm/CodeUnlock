package com.codeunlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.codeunlock.service.ServiceAliveStore
import com.codeunlock.service.DailyProgressSyncWorker
import com.codeunlock.service.WellbeingSyncWorker
import dagger.hilt.android.AndroidEntryPoint

/**
 * BootReceiver — PRD §14 device integrity.
 *
 * Manifest had RECEIVE_BOOT_COMPLETED permission declared but no receiver
 * to use it. After a reboot:
 *   1. WorkManager re-schedules the 15-min wellbeing sync job.
 *   2. ServiceAliveStore is marked dead (Accessibility Service isn't
 *      auto-restarted — user must re-enable it; dashboard will show warning).
 *
 * Must be registered in AndroidManifest.xml (see fixes/AndroidManifest.xml).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        Log.i(TAG, "Device booted — re-scheduling workers")

        // 1. Mark accessibility service as dead until it reconnects
        ServiceAliveStore.markDead(context)

        // 2. Prune any expired unlock sessions so reboots cannot exploit stale grants
        com.codeunlock.data.unlock.UnlockSessionStore.pruneExpired(context)

        // 3. Check Strict Mode status on boot
        val strictActive = com.codeunlock.data.settings.StrictModeStore.isProtectionActive(context)
        if (strictActive) {
            Log.w(TAG, "Device booted with active Strict Mode protection — enforcing immediate lockdown")
        }

        // 4. Reschedule wellbeing sync (WorkManager persists across reboots
        //    on API 23+ but we call this defensively)
        WellbeingSyncWorker.schedule(context)
        DailyProgressSyncWorker.schedule(context)

        Log.i(TAG, "Boot setup complete (strictModeActive=$strictActive)")
    }

    companion object {
        private const val TAG = "CodeUnlock.BootReceiver"
    }
}
