package com.codeunlock.ui.screens.lock

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeunlock.ui.components.mascot.FocusBuddyMascot
import com.codeunlock.ui.components.mascot.FocusBuddyMood

@Immutable
data class LockScreenState(
    val appName: String = "Restricted App",
    val remainingUnlockMinutes: Int = 0,
    val rewardMinutesPerSolve: Int = 30,
    val emergencyUnlockEnabled: Boolean = true,
    val usedMinutesToday: Long = 0L,
    val dailyLimitMinutes: Int = 0,
    val statusMessage: String = "Solve a coding problem to unlock access.",
    val footerMessage: String = "Emergency unlock is available once per day if enabled.",
    val emergencyUnlockLabel: String = "Emergency Unlock",
    val primaryActionLabel: String = "Solve to Unlock",
    val secondaryActionLabel: String = "Return to Dashboard",
    // Enhanced Focus Mode & Limit Quota fields
    val isFocusBlocked: Boolean = false,
    val focusSubject: String = "",
    val focusTimeRemainingFormatted: String = "",
    val isLimitExceeded: Boolean = false
)

private val FOCUS_QUOTES = listOf(
    "Stay in Focus! 🎧 Deep work sprint in progress.",
    "Distracting apps are locked to protect your flow. Let's finish strong! 💪",
    "Don't let a quick scroll break your focus streak! 🔥",
    "Your future self will thank you for mastering this topic! ⚡",
    "Deep work equals clean code. Stay locked in! 🚀"
)

private val LIMIT_QUOTES = listOf(
    "Time's up! ⏳ You've reached your daily limit for today.",
    "Out of screen time! Solve a coding problem to unlock +30 bonus minutes. 💡",
    "Turn mindless scrolling into algorithm mastery! 🚀",
    "Discipline equals freedom. Step away or code to unlock! 🔥",
    "Time to recharge or sharpen your problem-solving skills! ✨"
)

private val DEFAULT_QUOTES = listOf(
    "Focus First! 🛡️ This app is protected by CodeUnlock.",
    "Solve a coding challenge to unlock guilt-free screen time! 🏆",
    "One problem solved is 30 minutes of freedom earned. 💻",
    "Code first, scroll later! You've got this. 🚀"
)

@Composable
fun LockRoute(
    appName: String,
    onSolveNow: () -> Unit,
    onBackToDashboard: () -> Unit,
    onEmergencyUnlock: (() -> Unit)? = null,
    onReturnToFocusSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolvedName = appName.ifBlank { "Restricted App" }

    val settings = com.codeunlock.data.settings.BlockingSettingsStore.read(context)
    val remainingMinutes = com.codeunlock.data.unlock.UnlockSessionStore.getRemainingUnlockMinutes(context, null)
    val canUseEmergency = com.codeunlock.data.unlock.UnlockSessionStore.canUseEmergencyUnlock(context)
    val hasActiveUnlock = remainingMinutes > 0

    val remainingSec by com.codeunlock.service.FocusTimerService.remainingSeconds.collectAsState()
    val focusTimeFormatted = remember(remainingSec) { com.codeunlock.service.FocusTimerService.formatTime(remainingSec) }
    val focusSubject by com.codeunlock.service.FocusTimerService.activeSubject.collectAsState()
    val isFocusRunning by com.codeunlock.service.FocusTimerService.isRunning.collectAsState()
    val isBreakTime by com.codeunlock.service.FocusTimerService.isBreakTime.collectAsState()
    val isFocusActive = isFocusRunning && !isBreakTime

    val statusMessage = if (isFocusActive) {
        "Focus session active on ${focusSubject.ifBlank { "Coding Sprint" }}.\nDistracting apps are locked to keep you focused!"
    } else if (hasActiveUnlock) {
        "Unlock time is active. Return to dashboard to continue."
    } else {
        "Solve a coding problem to unlock access."
    }

    val footerMessage = when {
        isFocusActive ->
            "Focus mode locked. Complete your coding sprint to unlock."
        !settings.emergencyUnlockEnabled ->
            "Emergency unlock is disabled in your current settings."
        canUseEmergency ->
            "Emergency unlock is available once today if you really need it."
        else ->
            "Emergency unlock has already been used today."
    }

    LockScreen(
        state = LockScreenState(
            appName = resolvedName,
            remainingUnlockMinutes = remainingMinutes,
            emergencyUnlockEnabled = settings.emergencyUnlockEnabled && !isFocusActive,
            statusMessage = statusMessage,
            footerMessage = footerMessage,
            isFocusBlocked = isFocusActive,
            focusSubject = focusSubject,
            focusTimeRemainingFormatted = focusTimeFormatted
        ),
        onSolveToUnlock = onSolveNow,
        onBackToDashboard = onBackToDashboard,
        onEmergencyUnlock = onEmergencyUnlock,
        onReturnToFocusSession = onReturnToFocusSession,
        modifier = modifier
    )
}

@Composable
fun LockScreen(
    state: LockScreenState,
    onSolveToUnlock: () -> Unit,
    onBackToDashboard: () -> Unit,
    onEmergencyUnlock: (() -> Unit)?,
    onReturnToFocusSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    var userQuoteIndex by remember { mutableIntStateOf(0) }
    var tapCount by remember { mutableIntStateOf(0) }

    val activeQuotes = when {
        state.isFocusBlocked -> FOCUS_QUOTES
        state.isLimitExceeded || (state.dailyLimitMinutes > 0 && state.usedMinutesToday >= state.dailyLimitMinutes) -> LIMIT_QUOTES
        else -> DEFAULT_QUOTES
    }

    val defaultSpeech = when {
        state.isFocusBlocked -> "Stay in Focus! 🎧 Deep work on ${state.focusSubject.ifBlank { "Coding Sprint" }} is active."
        state.isLimitExceeded || (state.dailyLimitMinutes > 0 && state.usedMinutesToday >= state.dailyLimitMinutes) -> "Time's up for ${state.appName}! ⏳ Daily limit reached."
        else -> "Focus First! 🛡️ Solve a challenge to unlock ${state.appName}."
    }

    val mascotSpeech = if (tapCount > 0) {
        activeQuotes[userQuoteIndex % activeQuotes.size]
    } else {
        defaultSpeech
    }

    val mood = when {
        state.isFocusBlocked -> FocusBuddyMood.FOCUS
        state.isLimitExceeded || (state.dailyLimitMinutes > 0 && state.usedMinutesToday >= state.dailyLimitMinutes) -> FocusBuddyMood.HAPPY
        else -> FocusBuddyMood.IDLE
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            colors.background,
                            colors.surface,
                            colors.background
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── 1. Top Mode Badge / Status Header ────────────────────────
                StatusBadgeHeader(
                    isFocusBlocked = state.isFocusBlocked,
                    isLimitExceeded = state.isLimitExceeded || (state.dailyLimitMinutes > 0 && state.usedMinutesToday >= state.dailyLimitMinutes),
                    focusSubject = state.focusSubject,
                    focusTimeRemaining = state.focusTimeRemainingFormatted
                )

                // ── 2. Mascot Speech Bubble ──────────────────────────────────
                MascotSpeechBubble(
                    speechText = mascotSpeech,
                    isFocus = state.isFocusBlocked,
                    isLimitExceeded = state.isLimitExceeded || (state.dailyLimitMinutes > 0 && state.usedMinutesToday >= state.dailyLimitMinutes),
                    onBubbleClick = {
                        userQuoteIndex++
                        tapCount++
                    }
                )

                // ── 3. Kodi Mascot ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            userQuoteIndex++
                            tapCount++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    FocusBuddyMascot(
                        size = 115.dp,
                        mood = mood,
                        showHeadphones = true,
                        onTap = {
                            userQuoteIndex++
                            tapCount++
                        }
                    )
                }

                Text(
                    text = "Tap Kodi to interact 🐾",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.55f)
                )

                // ── 4. Main App Locked Title ─────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${state.appName} is Locked",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onBackground,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = when {
                            state.isFocusBlocked -> "Distracting apps are locked to keep your momentum going."
                            state.isLimitExceeded -> "You have reached your allocated screen time for this app today."
                            else -> "Solve a coding problem or wait for your unlock window."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // ── 5. Status / Information Card ─────────────────────────────
                StatusCard(
                    isFocusBlocked = state.isFocusBlocked,
                    focusSubject = state.focusSubject,
                    focusTimeRemaining = state.focusTimeRemainingFormatted,
                    isLimitExceeded = state.isLimitExceeded || (state.dailyLimitMinutes > 0 && state.usedMinutesToday >= state.dailyLimitMinutes),
                    remainingUnlockMinutes = state.remainingUnlockMinutes,
                    rewardMinutesPerSolve = state.rewardMinutesPerSolve,
                    usedMinutesToday = state.usedMinutesToday,
                    dailyLimitMinutes = state.dailyLimitMinutes
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ── 6. Primary Action Buttons ────────────────────────────────
                if (state.isFocusBlocked) {
                    // Focus Mode: Primary action is to return to the focus session
                    Button(
                        onClick = onReturnToFocusSession ?: onBackToDashboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Return to Focus Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onSolveToUnlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Solve Challenge to Unlock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                    }

                    OutlinedButton(
                        onClick = onBackToDashboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "Back to Dashboard",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceVariant
                        )
                    }
                } else {
                    // Normal / Limit Exceeded Mode
                    Button(
                        onClick = onSolveToUnlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isLimitExceeded) "Solve to Unlock (+${state.rewardMinutesPerSolve}m)" else state.primaryActionLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onBackToDashboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = state.secondaryActionLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                    }

                    if (state.emergencyUnlockEnabled && onEmergencyUnlock != null) {
                        OutlinedButton(
                            onClick = onEmergencyUnlock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.tertiary
                            )
                        ) {
                            Text(
                                text = state.emergencyUnlockLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ── 7. Footer Context Note ───────────────────────────────────
                Text(
                    text = state.footerMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }
    }
}

// ── Status Badge Header ──────────────────────────────────────────────────────

@Composable
private fun StatusBadgeHeader(
    isFocusBlocked: Boolean,
    isLimitExceeded: Boolean,
    focusSubject: String,
    focusTimeRemaining: String
) {
    val colors = MaterialTheme.colorScheme

    val (chipBg, chipBorder, chipText, chipIcon) = when {
        isFocusBlocked -> Quad(
            colors.primaryContainer.copy(alpha = 0.45f),
            colors.primary.copy(alpha = 0.35f),
            colors.primary,
            Icons.Default.Headphones
        )
        isLimitExceeded -> Quad(
            colors.errorContainer.copy(alpha = 0.45f),
            colors.error.copy(alpha = 0.35f),
            colors.error,
            Icons.Default.HourglassTop
        )
        else -> Quad(
            colors.surfaceVariant.copy(alpha = 0.6f),
            colors.outlineVariant,
            colors.onSurfaceVariant,
            Icons.Outlined.Lock
        )
    }

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = chipBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, chipBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = chipIcon,
                contentDescription = null,
                tint = chipText,
                modifier = Modifier.size(15.dp)
            )

            val badgeTitle = when {
                isFocusBlocked -> if (focusTimeRemaining.isNotBlank()) "Focus Sprint: $focusTimeRemaining" else "Focus Mode Active"
                isLimitExceeded -> "Daily Limit Reached"
                else -> "App Protected"
            }

            Text(
                text = badgeTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = chipText
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ── Mascot Speech Bubble ─────────────────────────────────────────────────────

@Composable
private fun MascotSpeechBubble(
    speechText: String,
    isFocus: Boolean,
    isLimitExceeded: Boolean,
    onBubbleClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val bubbleBorder = when {
        isFocus -> colors.primary.copy(alpha = 0.35f)
        isLimitExceeded -> colors.error.copy(alpha = 0.35f)
        else -> colors.outlineVariant
    }

    val bubbleBg = colors.surface.copy(alpha = 0.95f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onBubbleClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bubbleBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, bubbleBorder),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                AnimatedContent(
                    targetState = speechText,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 60)) +
                            slideInVertically(animationSpec = tween(220)) { it / 3 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(180)) +
                                    slideOutVertically(animationSpec = tween(180)) { -it / 3 }
                            )
                    },
                    label = "SpeechBubbleText"
                ) { targetText ->
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Pointer triangle
        Box(
            modifier = Modifier
                .offset(y = (-4).dp)
                .size(10.dp)
                .background(bubbleBg)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

// ── Status Information Card ──────────────────────────────────────────────────

@Composable
private fun StatusCard(
    isFocusBlocked: Boolean,
    focusSubject: String,
    focusTimeRemaining: String,
    isLimitExceeded: Boolean,
    remainingUnlockMinutes: Int,
    rewardMinutesPerSolve: Int,
    usedMinutesToday: Long,
    dailyLimitMinutes: Int
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isFocusBlocked) {
                // Focus session details
                RowItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    },
                    title = "Active Subject",
                    body = focusSubject.ifBlank { "Deep Work Coding Sprint" }
                )

                if (focusTimeRemaining.isNotBlank()) {
                    RowItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = colors.tertiary
                            )
                        },
                        title = "Sprint Remaining",
                        body = "$focusTimeRemaining until sprint completes"
                    )
                }

                RowItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = colors.secondary
                        )
                    },
                    title = "Unlock Condition",
                    body = "Complete your sprint or solve a problem to earn access."
                )
            } else if (isLimitExceeded || dailyLimitMinutes > 0) {
                // Daily limit quota details with visual progress bar
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Screen Time",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "${formatMinutes(usedMinutesToday)} / ${formatMinutes(dailyLimitMinutes.toLong())}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLimitExceeded) colors.error else colors.primary
                        )
                    }

                    val progressFraction = if (dailyLimitMinutes > 0) {
                        (usedMinutesToday.toFloat() / dailyLimitMinutes.toFloat()).coerceIn(0f, 1f)
                    } else 1f

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isLimitExceeded) colors.error else colors.primary,
                        trackColor = colors.surfaceVariant
                    )
                }

                RowItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    },
                    title = "Next Reward",
                    body = "Solve 1 verified problem to unlock +$rewardMinutesPerSolve bonus minutes."
                )
            } else {
                // Default lock details
                RowItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.tertiary
                        )
                    },
                    title = "Current Status",
                    body = if (remainingUnlockMinutes > 0) {
                        "You have $remainingUnlockMinutes minutes remaining."
                    } else {
                        "Access locked. Solve a coding problem to unlock."
                    }
                )

                RowItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    },
                    title = "Next Reward",
                    body = "Solve 1 verified problem to unlock $rewardMinutesPerSolve minutes."
                )
            }
        }
    }
}

private fun formatMinutes(minutes: Long): String {
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

@Composable
private fun RowItem(
    icon: @Composable () -> Unit,
    title: String,
    body: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(10.dp)
        ) {
            icon()
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
