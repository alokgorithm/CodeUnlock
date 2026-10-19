package com.codeunlock.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    val secondaryActionLabel: String = "Return to Dashboard"
)

@Composable
fun LockRoute(
    appName: String,
    onSolveNow: () -> Unit,
    onBackToDashboard: () -> Unit,
    onEmergencyUnlock: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolvedName = appName.ifBlank { "Restricted App" }

    val settings = com.codeunlock.data.settings.BlockingSettingsStore.read(context)
    val remainingMinutes = com.codeunlock.data.unlock.UnlockSessionStore.getRemainingUnlockMinutes(context, null)
    val canUseEmergency = com.codeunlock.data.unlock.UnlockSessionStore.canUseEmergencyUnlock(context)
    val hasActiveUnlock = remainingMinutes > 0

    val statusMessage = if (hasActiveUnlock) {
        "Unlock time is active. Return to dashboard to continue."
    } else {
        "Solve a coding problem to unlock access."
    }

    val footerMessage = when {
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
            emergencyUnlockEnabled = settings.emergencyUnlockEnabled,
            statusMessage = statusMessage,
            footerMessage = footerMessage
        ),
        onSolveToUnlock = onSolveNow,
        onBackToDashboard = onBackToDashboard,
        onEmergencyUnlock = onEmergencyUnlock,
        modifier = modifier
    )
}

@Composable
fun LockScreen(
    state: LockScreenState,
    onSolveToUnlock: () -> Unit,
    onBackToDashboard: () -> Unit,
    onEmergencyUnlock: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

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
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusIcon()

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "${state.appName} is Locked",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                StatusCard(
                    remainingUnlockMinutes = state.remainingUnlockMinutes,
                    rewardMinutesPerSolve = state.rewardMinutesPerSolve,
                    usedMinutesToday = state.usedMinutesToday,
                    dailyLimitMinutes = state.dailyLimitMinutes
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onSolveToUnlock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Text(
                        text = state.primaryActionLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onBackToDashboard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = state.secondaryActionLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                if (state.emergencyUnlockEnabled && onEmergencyUnlock != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onEmergencyUnlock,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.tertiary
                        )
                    ) {
                        Text(
                            text = state.emergencyUnlockLabel,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = state.footerMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StatusIcon() {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.errorContainer)
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = "Locked",
            tint = colors.error,
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
private fun StatusCard(
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
            containerColor = colors.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RowItem(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = colors.tertiary
                    )
                },
                title = "Current Status",
                body = "You have $remainingUnlockMinutes minutes remaining."
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

            if (dailyLimitMinutes > 0) {
                RowItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = colors.secondary
                        )
                    },
                    title = "Daily Limit",
                    body = "Used ${formatMinutes(usedMinutesToday)} of ${formatMinutes(dailyLimitMinutes.toLong())} today."
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(10.dp)
        ) {
            icon()
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
