package com.codeunlock.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codeunlock.R
import com.codeunlock.ui.MainActivity

/**
 * NotificationHelper — PRD §17 — all 5 notification types.
 *
 * Previously completely absent. The manifest already has POST_NOTIFICATIONS
 * declared. These channels and builders are the missing implementation.
 *
 * PRD §17 notification types:
 *   1. Daily reminder if solve target not met by 8 PM
 *   2. Badge earned celebration
 *   3. Friend activity (optional)
 *   4. Ranking change (top 10 movement)
 *   5. Wellbeing weekly report
 *   + Unlock granted (PRD §6.3: "push notification 'Unlocked 30 min!'")
 */
object NotificationHelper {

    // Channels
    const val CHANNEL_SOLVE_REMINDER = "codeunlock_solve_reminder"
    const val CHANNEL_UNLOCK         = "codeunlock_unlock"
    const val CHANNEL_BADGE          = "codeunlock_badge"
    const val CHANNEL_SOCIAL         = "codeunlock_social"
    const val CHANNEL_RANKINGS       = "codeunlock_rankings"
    const val CHANNEL_WELLBEING      = "codeunlock_wellbeing"
    const val CHANNEL_FOCUS_TIMER    = "codeunlock_focus_timer"

    // Notification IDs
    private const val ID_SOLVE_REMINDER = 1001
    private const val ID_UNLOCK_GRANTED = 1002
    private const val ID_BADGE          = 1003
    private const val ID_RANKING        = 1004
    private const val ID_WELLBEING      = 1005
    const val ID_FOCUS_TIMER            = 1006
    private const val ID_FRIEND_BASE    = 2000  // + hash for per-friend IDs

    // ── Channel registration ─────────────────────────────────────
    // Call once from Application.onCreate().

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        listOf(
            NotificationChannel(CHANNEL_SOLVE_REMINDER, "Solve Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminds you to hit your daily coding target"
            },
            NotificationChannel(CHANNEL_UNLOCK, "Unlock Granted", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifies when you earn unlock time"
            },
            NotificationChannel(CHANNEL_BADGE, "Badges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Badge earned celebrations"
            },
            NotificationChannel(CHANNEL_SOCIAL, "Friends", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Friend activity updates"
            },
            NotificationChannel(CHANNEL_RANKINGS, "Rankings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ranking movement alerts"
            },
            NotificationChannel(CHANNEL_WELLBEING, "Wellbeing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Weekly screen time reports"
            },
            NotificationChannel(CHANNEL_FOCUS_TIMER, "Focus Timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown for focus and coding sessions"
            },
        ).forEach { nm.createNotificationChannel(it) }
    }

    // ── 1. Daily solve reminder (PRD §17) ────────────────────────
    // Trigger at 8 PM if daily target not met.

    fun showSolveReminder(context: Context, solved: Int, target: Int) {
        val remaining = target - solved
        show(
            context      = context,
            id           = ID_SOLVE_REMINDER,
            channelId    = CHANNEL_SOLVE_REMINDER,
            title        = "🎯 $remaining more problem${if (remaining != 1) "s" else ""} today",
            body         = "You've solved $solved/$target. Solve now to keep your apps unlocked.",
            route        = "problem/0",
        )
    }

    // ── 2. Unlock granted (PRD §6.3) ─────────────────────────────
    // "push notification 'Unlocked 30 min!'"

    fun showUnlockGranted(context: Context, minutes: Int, appName: String? = null) {
        val title = "🔓 Unlocked ${minutes} minutes!"
        val body  = if (appName != null) "$appName is now accessible for $minutes minutes." else "Your restricted apps are accessible for $minutes minutes."
        show(
            context   = context,
            id        = ID_UNLOCK_GRANTED,
            channelId = CHANNEL_UNLOCK,
            title     = title,
            body      = body,
            route     = "dashboard",
        )
    }

    // ── 3. Badge earned (PRD §17) ────────────────────────────────

    fun showBadgeEarned(context: Context, badgeName: String, badgeEmoji: String) {
        show(
            context   = context,
            id        = ID_BADGE,
            channelId = CHANNEL_BADGE,
            title     = "$badgeEmoji Badge Earned: $badgeName",
            body      = "Keep going — you're building real discipline.",
            route     = "badges",
        )
    }

    // ── 4. Ranking change (PRD §17) ──────────────────────────────
    // Shown when user enters / leaves top 10.

    fun showRankingChange(context: Context, newRank: Int, previousRank: Int) {
        val moved    = previousRank - newRank
        val direction = if (moved > 0) "up ↑" else "down ↓"
        show(
            context   = context,
            id        = ID_RANKING,
            channelId = CHANNEL_RANKINGS,
            title     = "🏆 You moved $direction ${ Math.abs(moved)} places!",
            body      = "You're now ranked #$newRank globally. Keep solving!",
            route     = "rankings",
        )
    }

    // ── 5. Wellbeing weekly report (PRD §17) ─────────────────────

    fun showWellbeingWeeklyReport(context: Context, avgScore: Double, screenTimeReductionPct: Int) {
        show(
            context   = context,
            id        = ID_WELLBEING,
            channelId = CHANNEL_WELLBEING,
            title     = "📊 Your weekly wellbeing report",
            body      = "Avg score: ${avgScore.toInt()}/100 · Screen time ↓$screenTimeReductionPct% vs last week",
            route     = "wellbeing",
        )
    }

    // ── 6. Friend activity (PRD §17 — optional) ──────────────────

    fun showFriendActivity(context: Context, friendName: String, activity: String) {
        show(
            context   = context,
            id        = ID_FRIEND_BASE + friendName.hashCode(),
            channelId = CHANNEL_SOCIAL,
            title     = "👥 $friendName $activity",
            body      = "Tap to compare stats.",
            route     = "social",
        )
    }

    // ── Internal builder ─────────────────────────────────────────

    private fun show(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        body: String,
        route: String,
    ) {
        val tapIntent = PendingIntent.getActivity(
            context,
            id,
            MainActivity.createIntent(context, route).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
