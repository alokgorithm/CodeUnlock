package com.codeunlock.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import com.codeunlock.data.usage.UsageManager
import com.codeunlock.service.ServiceAliveStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codeunlock.api.dto.CodeforcesStatsDto
import com.codeunlock.api.dto.LeetCodeStatsDto
import com.codeunlock.api.dto.NextProblemResponse
import com.codeunlock.api.dto.TrackProgressDto
import com.codeunlock.api.dto.UserSettingsDto
import com.codeunlock.api.dto.WellbeingStatsDto
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.session.SessionManager
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.unlock.UnlockSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private fun isAccessibilityActive(context: Context): Boolean {
    if (ServiceAliveStore.isAlive(context)) return true
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val enabled = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
    if (enabled.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }) return true

    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName)
}

@Composable
fun DashboardRoute(
    onOpenProblem: (Int) -> Unit,
    onOpenTracks: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWellbeing: () -> Unit,
    onOpenRankings: () -> Unit,
    onOpenSocial: () -> Unit,
    onOpenFocus: () -> Unit = {},
    onOpenSprintRooms: () -> Unit = {},
    onManageRestrictedApps: () -> Unit = {},
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    isLoggedIn: Boolean = false,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isAccessibilityGranted by remember { mutableStateOf(isAccessibilityActive(context)) }
    var isUsageStatsGranted by remember { mutableStateOf(UsageManager.hasUsageStatsPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = isAccessibilityActive(context)
                isUsageStatsGranted = UsageManager.hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    DashboardScreen(
        state = uiState,
        onRefresh = viewModel::load,
        onOpenProblem = onOpenProblem,
        onOpenTracks = onOpenTracks,
        onOpenBadges = onOpenBadges,
        onOpenProfile = onOpenProfile,
        onOpenWellbeing = onOpenWellbeing,
        onOpenRankings = onOpenRankings,
        onOpenSocial = onOpenSocial,
        onOpenFocus = onOpenFocus,
        onOpenSprintRooms = onOpenSprintRooms,
        onManageRestrictedApps = onManageRestrictedApps,
        isAccessibilityGranted = isAccessibilityGranted,
        isUsageStatsGranted = isUsageStatsGranted,
        onLogin = onLogin,
        onLogout = {
            viewModel.logout()
            onLogout()
        },
        isLoggedIn = isLoggedIn
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenProblem: (Int) -> Unit,
    onOpenTracks: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWellbeing: () -> Unit,
    onOpenRankings: () -> Unit,
    onOpenSocial: () -> Unit,
    onOpenFocus: () -> Unit = {},
    onOpenSprintRooms: () -> Unit = {},
    onManageRestrictedApps: () -> Unit = {},
    isAccessibilityGranted: Boolean = true,
    isUsageStatsGranted: Boolean = true,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    isLoggedIn: Boolean = false
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (state.isLoading && state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardHeader(
                    dateLabel = state.dateLabel,
                    userLabel = state.userLabel,
                    onRefresh = onRefresh,
                    onLogin = onLogin,
                    onLogout = onLogout,
                    isLoggedIn = isLoggedIn
                )

                if (!isAccessibilityGranted) {
                    AccessibilityWarningBanner(
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }

                if (!isUsageStatsGranted) {
                    UsageStatsWarningBanner(
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    )
                }

                if (state.errorMessage != null) {
                    ErrorBanner(message = state.errorMessage)
                }

                ProgressHero(
                    solved = state.problemsSolvedToday,
                    target = state.dailyTarget,
                    remainingMinutes = state.remainingUnlockMinutes
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Streak",
                        value = "${state.currentStreak}",
                        subtitle = "days",
                        icon = Icons.Default.Whatshot,
                        accent = Color(0xFFFF8A65)
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Global Rank",
                        value = state.globalRankLabel,
                        subtitle = "live standing",
                        icon = Icons.Default.EmojiEvents,
                        accent = Color(0xFFFFC107)
                    )
                }

                SectionTitle("Platform Stats")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlatformCard(
                        modifier = Modifier.weight(1f),
                        title = "LeetCode",
                        primary = state.leetcodeSolvedLabel,
                        secondary = state.leetcodeDifficultyBreakdown,
                        accent = Color(0xFFFF9800),
                        icon = Icons.Default.Code
                    )
                    PlatformCard(
                        modifier = Modifier.weight(1f),
                        title = "Codeforces",
                        primary = state.codeforcesRatingLabel,
                        secondary = state.codeforcesRankLabel,
                        accent = Color(0xFF64B5F6),
                        icon = Icons.Default.TrendingUp
                    )
                }

                SectionTitle("Next Problem")
                NextProblemCard(
                    problem = state.nextProblem,
                    onOpenProblem = onOpenProblem
                )

                SectionTitle("Wellbeing")
                WellbeingCard(
                    wellbeingScore = state.wellbeingScoreLabel,
                    screenTime = state.screenTimeLabel,
                    pickupCount = state.pickupCountLabel
                )

                SectionTitle("Quick Actions")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val id = state.nextProblem?.id ?: 1
                            onOpenProblem(id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Solve Next Problem")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenFocus,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("🎧 Focus Timer")
                        }
                        OutlinedButton(
                            onClick = onOpenSprintRooms,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("👥 Sprint Rooms")
                        }
                    }

                    QuickActionRow("Tracks", "Badges", onOpenTracks, onOpenBadges)
                    QuickActionRow("Rankings", "Wellbeing", onOpenRankings, onOpenWellbeing)
                    QuickActionRow("Social", "Profile", onOpenSocial, onOpenProfile)
                }

                SectionTitle("Restricted Apps")
                RestrictedAppsCard(apps = state.restrictedApps, onManageApps = onManageRestrictedApps)

                if (!isLoggedIn) {
                    SectionTitle("Sync & Cloud")
                    LoginPromptCard(onLogin = onLogin)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    dateLabel: String,
    userLabel: String,
    onRefresh: () -> Unit,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    isLoggedIn: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CodeUnlock",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = userLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Row {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            if (isLoggedIn) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                }
            }
        }
    }
}

@Composable
private fun LoginPromptCard(onLogin: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sign in to unlock cloud features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Login with Google to enable LeetCode tracking, cloud sync, rankings, and social features.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Login with Google")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ProgressHero(
    solved: Int,
    target: Int,
    remainingMinutes: Int
) {
    val safeTarget = if (target <= 0) 1 else target
    val progress = (solved.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Today's Progress",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "$solved/$target",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "problems solved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(target - solved).coerceAtLeast(0)} more to hit target",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    Text(
                        text = "$remainingMinutes min unlocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .padding(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlatformCard(
    modifier: Modifier = Modifier,
    title: String,
    primary: String,
    secondary: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun NextProblemCard(
    problem: ProblemCardModel?,
    onOpenProblem: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (problem == null) {
                Text(
                    text = "No problem available right now.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { onOpenProblem(1) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Open verification flow")
                }
            } else {
                Text(
                    text = problem.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${problem.difficulty} · ${problem.topicsLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "#${problem.trackPosition} in Top 200",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { onOpenProblem(problem.id) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Problem")
                }
            }
        }
    }
}

@Composable
private fun WellbeingCard(
    wellbeingScore: String,
    screenTime: String,
    pickupCount: String
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Digital wellbeing snapshot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStatPill(
                    title = "Score",
                    value = wellbeingScore
                )
                SmallStatPill(
                    title = "Screen Time",
                    value = screenTime
                )
                SmallStatPill(
                    title = "Pickups",
                    value = pickupCount
                )
            }
        }
    }
}

@Composable
private fun SmallStatPill(
    title: String,
    value: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RestrictedAppsCard(
    apps: List<String>,
    onManageApps: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${apps.size} Controlled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = onManageApps,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Manage Apps")
                }
            }

            if (apps.isEmpty()) {
                Text(
                    text = "No restricted apps selected yet. Tap Manage Apps above to add distracting apps.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                apps.take(6).forEachIndexed { index, app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = app,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (index != apps.take(6).lastIndex) {
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    leftLabel: String,
    rightLabel: String,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onLeft,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(leftLabel)
        }
        OutlinedButton(
            onClick = onRight,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(rightLabel)
        }
    }
}

@Immutable
data class ProblemCardModel(
    val id: Int,
    val title: String,
    val difficulty: String,
    val topicsLabel: String,
    val trackPosition: Int,
    val url: String?
)

@Immutable
data class DashboardUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val userLabel: String = "Loading…",
    val dateLabel: String = "Today",
    val problemsSolvedToday: Int = 0,
    val dailyTarget: Int = 2,
    val remainingUnlockMinutes: Int = 0,
    val currentStreak: Int = 0,
    val globalRankLabel: String = "—",
    val leetcodeSolvedLabel: String = "—",
    val leetcodeDifficultyBreakdown: String = "No sync yet",
    val codeforcesRatingLabel: String = "—",
    val codeforcesRankLabel: String = "No sync yet",
    val wellbeingScoreLabel: String = "—",
    val screenTimeLabel: String = "—",
    val pickupCountLabel: String = "—",
    val restrictedApps: List<String> = emptyList(),
    val nextProblem: ProblemCardModel? = null
) {
    val isEmpty: Boolean
        get() = nextProblem == null &&
            restrictedApps.isEmpty() &&
            problemsSolvedToday == 0 &&
            currentStreak == 0 &&
            leetcodeSolvedLabel == "—" &&
            codeforcesRatingLabel == "—"
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: CodeUnlockRepository,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            isLoading = true,
            userLabel = sessionManager.currentState().email ?: "CodeUnlock User",
            dateLabel = todayLabel()
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun load() {
        // Allow refresh: only block if a load is already in-flight
        if (_uiState.value.isLoading && hasLoaded) return
        hasLoaded = true
        _uiState.value = _uiState.value.copy(dateLabel = todayLabel())

        val session = sessionManager.currentState()
        val localSettings = BlockingSettingsStore.read(context)
        val localRemainingUnlockMinutes = UnlockSessionStore.getRemainingUnlockMinutes(context)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                dailyTarget = localSettings.dailySolveTarget,
                remainingUnlockMinutes = localRemainingUnlockMinutes,
                restrictedApps = localSettings.restrictedApps.map(::displayNameForPackage),
                userLabel = session.email ?: "CodeUnlock User",
                dateLabel = todayLabel(),
                errorMessage = null
            )

            val settingsDeferred = async { repository.getSettings() }
            val progressDeferred = async { repository.getTrackProgress() }
            val nextProblemDeferred = async { repository.getNextProblem() }
            val wellbeingDeferred = async { repository.getWellbeingStats() }
            val leetcodeDeferred = async { repository.getLeetCodeStats() }
            val codeforcesDeferred = async { repository.getCodeforcesStats() }
            val rankingsDeferred = async { repository.getGlobalRankings(limit = 1, offset = 0) }

            val settings = settingsDeferred.await().getOrNull()
            val progress = progressDeferred.await().getOrNull()
            val nextProblem = nextProblemDeferred.await().getOrNull()
            val wellbeing = wellbeingDeferred.await().getOrNull()
            val leetcode = leetcodeDeferred.await().getOrNull()
            val codeforces = codeforcesDeferred.await().getOrNull()
            val rankings = rankingsDeferred.await().getOrNull()

            val hadAnyData = listOf(
                settings,
                progress,
                nextProblem,
                wellbeing,
                leetcode,
                codeforces,
                rankings
            ).any { it != null }

            val errorMessage = if (hadAnyData || !session.hasRealSession) {
                null
            } else {
                "Could not load live dashboard data. Pull to refresh after checking your connection."
            }

            _uiState.value = DashboardUiState(
                isLoading = false,
                errorMessage = errorMessage,
                userLabel = session.email ?: "CodeUnlock User",
                dateLabel = todayLabel(),
                problemsSolvedToday = leetcode?.problemsSolvedToday ?: progress?.solved ?: 0,
                dailyTarget = settings?.dailySolveTarget ?: localSettings.dailySolveTarget,
                remainingUnlockMinutes = estimateRemainingMinutes(
                    progress = progress,
                    settings = settings,
                    localSettings = localSettings,
                    localRemainingUnlockMinutes = localRemainingUnlockMinutes
                ),
                currentStreak = progress?.currentStreak ?: 0,
                globalRankLabel = rankings?.myRank?.rank?.let { "#$it" } ?: "—",
                leetcodeSolvedLabel = leetcode?.totalSolved?.toString() ?: "—",
                leetcodeDifficultyBreakdown = leetcode.formatBreakdown(),
                codeforcesRatingLabel = codeforces?.currentRating?.toString() ?: "—",
                codeforcesRankLabel = codeforces?.rank ?: "No sync yet",
                wellbeingScoreLabel = wellbeing?.today?.wellbeingScore?.toInt()?.toString() ?: "—",
                screenTimeLabel = wellbeing.formatScreenTime(),
                pickupCountLabel = wellbeing?.today?.pickupCount?.toString() ?: "—",
                restrictedApps = settings?.restrictedApps?.takeIf { it.isNotEmpty() }
                    ?: localSettings.restrictedApps.map(::displayNameForPackage),
                nextProblem = nextProblem.toProblemCardModel()
            )
        }
    }

    fun logout() {
        sessionManager.clearSession()
        _uiState.value = DashboardUiState(
            isLoading = false,
            userLabel = "Signed out"
        )
    }

    private fun unusedLocalState(
        email: String?,
        localSettings: BlockingSettingsStore.BlockingSettings,
        localRemainingUnlockMinutes: Int
    ): DashboardUiState {
        val localRestrictedApps = if (localSettings.restrictedApps.isNotEmpty()) {
            localSettings.restrictedApps.map(::displayNameForPackage)
        } else {
            listOf(
                "Instagram",
                "YouTube",
                "Twitter",
                "TikTok"
            )
        }

        val localDailyTarget = localSettings.dailySolveTarget.takeIf { it >= 2 } ?: 5

        return DashboardUiState(
            isLoading = false,
            errorMessage = "Live dashboard data is unavailable.",
            userLabel = email ?: "CodeUnlock User",
            dateLabel = todayLabel(),
            problemsSolvedToday = 3,
            dailyTarget = localDailyTarget,
            remainingUnlockMinutes = localRemainingUnlockMinutes,
            currentStreak = 14,
            globalRankLabel = "—",
            leetcodeSolvedLabel = "—",
            leetcodeDifficultyBreakdown = "89 easy · 123 medium · 35 hard",
            codeforcesRatingLabel = "—",
            codeforcesRankLabel = "No sync yet",
            wellbeingScoreLabel = "78",
            screenTimeLabel = "—",
            pickupCountLabel = "46",
            restrictedApps = localRestrictedApps,
            nextProblem = ProblemCardModel(
                id = 1,
                title = "No synced problem",
                difficulty = "Medium",
                topicsLabel = "Dynamic Programming · Array",
                trackPosition = 65,
                url = "https://leetcode.com/problems/house-robber-ii/"
            )
        )
    }

    private fun estimateRemainingMinutes(
        progress: TrackProgressDto?,
        settings: UserSettingsDto?,
        localSettings: BlockingSettingsStore.BlockingSettings,
        localRemainingUnlockMinutes: Int
    ): Int {
        // Prefer fresh local unlock session data (computed from expiry time)
        if (localRemainingUnlockMinutes > 0) return localRemainingUnlockMinutes

        // Fallback: estimate from solved count × 30 min per solve
        val solvedCount = progress?.solved ?: 0
        val target = settings?.dailySolveTarget ?: localSettings.dailySolveTarget
        val capped = solvedCount.coerceAtMost(target)
        return capped * 30
    }

    private fun todayLabel(): String {
        return try {
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            )
        } catch (_: Exception) {
            "Today"
        }
    }

    private fun displayNameForPackage(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.google.android.youtube" -> "YouTube"
            "com.twitter.android" -> "Twitter"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.facebook.katana" -> "Facebook"
            "com.reddit.frontpage" -> "Reddit"
            "com.android.chrome" -> "Chrome"
            "com.netflix.mediaclient" -> "Netflix"
            else -> packageName.substringAfterLast('.')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun NextProblemResponse?.toProblemCardModel(): ProblemCardModel? {
        val problem = this?.problem ?: return null
        return ProblemCardModel(
            id = problem.id,
            title = problem.title,
            difficulty = problem.difficulty,
            topicsLabel = when {
                problem.topics.isNotEmpty() -> problem.topics.joinToString(" · ")
                problem.topic.isNotBlank() -> problem.topic
                else -> "Interview Prep"
            },
            trackPosition = problem.trackPosition,
            url = problem.url
        )
    }

    private fun LeetCodeStatsDto?.formatBreakdown(): String {
        if (this == null) return "No sync yet"
        return "${easySolved} easy · ${mediumSolved} medium · ${hardSolved} hard"
    }

    private fun WellbeingStatsDto?.formatScreenTime(): String {
        val minutes = this?.today?.totalScreenTimeMinutes ?: return "—"
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "$hours h $mins m" else "$mins m"
    }
}


@Composable
private fun AccessibilityWarningBanner(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Blocker Service Inactive ⚠️",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = "CodeUnlock needs Accessibility Permission enabled in Android settings to lock distracting apps and enforce Focus Mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Enable Accessibility Service")
            }
        }
    }
}

@Composable
private fun UsageStatsWarningBanner(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Usage Access Missing ⚠️",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = "Screen time tracking and daily quota calculation require Usage Access permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Grant Usage Access")
            }
        }
    }
}
