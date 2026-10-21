package com.codeunlock.ui.screens.permissions

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeunlock.data.settings.BlockingSettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.pm.PackageManager
import com.codeunlock.data.usage.UsageManager
import com.codeunlock.service.ServiceAliveStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private enum class OnboardingStep {
    Permissions,
    RestrictApps,
    ModeAndTarget,
    LinkAccounts,
    Safety,
}

@Immutable
private data class RestrictableApp(
    val name: String,
    val packageName: String,
    val emoji: String,
    val category: String,
)

@Immutable
private data class UsageModeOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val dailyMinutes: Int,
)

private val DEFAULT_INITIAL_RESTRICTED_APPS = listOf(
    "com.google.android.youtube",
    "com.instagram.android",
    "com.twitter.android",
    "com.zhiliaoapp.musically"
)

@Stable
private class LocalOnboardingSettingsState(
    selectedApps: List<String> = DEFAULT_INITIAL_RESTRICTED_APPS,
    usageMode: String = DEFAULT_MODE,
    dailySolveTarget: Int = DEFAULT_DAILY_TARGET,
    emergencyUnlockEnabled: Boolean = true,
    freeUnlockEnabled: Boolean = true,
    leetcodeUsername: String = "",
    codeforcesHandle: String = "",
) {
    val selectedApps = mutableStateListOf<String>().apply { addAll(selectedApps) }
    var usageMode by mutableStateOf(usageMode)
    var dailySolveTarget by mutableIntStateOf(dailySolveTarget)
    var emergencyUnlockEnabled by mutableStateOf(emergencyUnlockEnabled)
    var freeUnlockEnabled by mutableStateOf(freeUnlockEnabled)
    var leetcodeUsername by mutableStateOf(leetcodeUsername)
    var codeforcesHandle by mutableStateOf(codeforcesHandle)

    fun toggleApp(packageName: String) {
        if (selectedApps.contains(packageName)) {
            selectedApps.remove(packageName)
        } else {
            selectedApps.add(packageName)
        }
    }
}

private val onboardingStateSaver = listSaver<LocalOnboardingSettingsState, Any>(
    save = {
        listOf(
            it.selectedApps.joinToString(","),
            it.usageMode,
            it.dailySolveTarget,
            it.emergencyUnlockEnabled,
            it.freeUnlockEnabled,
            it.leetcodeUsername,
            it.codeforcesHandle,
        )
    },
    restore = { restored ->
        val appsString = restored[0] as String
        LocalOnboardingSettingsState(
            selectedApps = if (appsString.isBlank()) emptyList() else appsString.split(","),
            usageMode = restored[1] as String,
            dailySolveTarget = restored[2] as Int,
            emergencyUnlockEnabled = restored[3] as Boolean,
            freeUnlockEnabled = restored[4] as Boolean,
            leetcodeUsername = restored.getOrNull(5) as? String ?: "",
            codeforcesHandle = restored.getOrNull(6) as? String ?: "",
        )
    },
)

private const val DEFAULT_MODE = "focused"
private const val DEFAULT_DAILY_TARGET = 3

private val candidateApps = listOf(
    RestrictableApp("Instagram", "com.instagram.android", "📷", "Social"),
    RestrictableApp("YouTube", "com.google.android.youtube", "▶️", "Entertainment"),
    RestrictableApp("X / Twitter", "com.twitter.android", "🐦", "Social"),
    RestrictableApp("TikTok", "com.zhiliaoapp.musically", "🎵", "Entertainment"),
    RestrictableApp("Facebook", "com.facebook.katana", "👥", "Social"),
    RestrictableApp("Reddit", "com.reddit.frontpage", "🤖", "Community"),
    RestrictableApp("Chrome", "com.android.chrome", "🌐", "Browsing"),
    RestrictableApp("Netflix", "com.netflix.mediaclient", "🎬", "Streaming"),
    RestrictableApp("Snapchat", "com.snapchat.android", "👻", "Social"),
    RestrictableApp("WhatsApp", "com.whatsapp", "💬", "Social"),
    RestrictableApp("Telegram", "org.telegram.messenger", "✈️", "Social"),
    RestrictableApp("Discord", "com.discord", "🎮", "Social"),
    RestrictableApp("Pinterest", "com.pinterest", "📌", "Social"),
    RestrictableApp("Spotify", "com.spotify.music", "🎧", "Entertainment"),
    RestrictableApp("LinkedIn", "com.linkedin.android", "💼", "Social"),
)

/** Returns all real launcher apps installed on the device dynamically. */
private fun getInstalledRestrictableApps(context: android.content.Context): List<RestrictableApp> {
    val pm = context.packageManager
    val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
    val emojiMap = mapOf(
        "instagram" to "📷",
        "youtube" to "▶️",
        "twitter" to "🐦",
        "musically" to "🎵", // TikTok
        "facebook" to "👥",
        "reddit" to "🤖",
        "chrome" to "🌐",
        "netflix" to "🎬",
        "snapchat" to "👻",
        "whatsapp" to "💬",
        "telegram" to "✈️",
        "discord" to "🎮",
        "pinterest" to "📌",
        "spotify" to "🎧",
        "linkedin" to "💼",
        "maps" to "🗺️",
        "gmail" to "✉️",
        "photos" to "🖼️",
        "settings" to "⚙️",
        "clock" to "⏰",
        "calendar" to "📅",
        "camera" to "📸",
        "gallery" to "🖼️",
        "music" to "🎵"
    )
    return resolveInfos.map { info ->
        val appLabel = info.loadLabel(pm).toString()
        val packageName = info.activityInfo.packageName
        val lowercasePkg = packageName.lowercase()
        val customEmoji = emojiMap.entries.firstOrNull { lowercasePkg.contains(it.key) }?.value ?: "📱"
        RestrictableApp(
            name = appLabel,
            packageName = packageName,
            emoji = customEmoji,
            category = "Installed App"
        )
    }.distinctBy { it.packageName }
     .filter { it.packageName != context.packageName }
     .sortedBy { it.name }
}

private val usageModes = listOf(
    UsageModeOption(
        id = "hardcore",
        title = "Hardcore",
        subtitle = "2h daily limit",
        description = "Maximum discipline. 2 hours allowed time for restricted apps per day. Best for exam prep.",
        dailyMinutes = 120,
    ),
    UsageModeOption(
        id = "focused",
        title = "Focused",
        subtitle = "4h daily limit",
        description = "Strong limits with breathing room. 4 hours allowed time for restricted apps per day. (Recommended)",
        dailyMinutes = 240,
    ),
    UsageModeOption(
        id = "relaxed",
        title = "Relaxed",
        subtitle = "6–8h daily limit",
        description = "Moderate control. Up to 7 hours of allowed time for restricted apps while staying mindful.",
        dailyMinutes = 420,
    ),
    UsageModeOption(
        id = "custom",
        title = "Custom",
        subtitle = "Zero quota / Strict lock",
        description = "Restricted apps are blocked immediately. Must solve coding problems to unlock screen time.",
        dailyMinutes = 0,
    ),
)

private fun isAccessibilityPermissionGranted(context: android.content.Context): Boolean {
    if (ServiceAliveStore.isAlive(context)) return true
    val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val enabled = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
    if (enabled.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }) return true

    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName)
}

private fun isUsageStatsPermissionGranted(context: android.content.Context): Boolean {
    val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
    if (appOps != null) {
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        if (mode == android.app.AppOpsManager.MODE_ALLOWED) return true
    }
    return UsageManager.hasUsageStatsPermission(context)
}

@Composable
fun PermissionSetupRoute(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val syncState by viewModel.uiState.collectAsStateWithLifecycle()

    var isAccessibilityGranted by remember { mutableStateOf(isAccessibilityPermissionGranted(context)) }
    var isUsageStatsGranted by remember { mutableStateOf(isUsageStatsPermissionGranted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = isAccessibilityPermissionGranted(context)
                isUsageStatsGranted = isUsageStatsPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var currentStep by rememberSaveable { mutableStateOf(OnboardingStep.Permissions) }
    val settingsState = rememberSaveable(saver = onboardingStateSaver) {
        LocalOnboardingSettingsState()
    }

    val totalSteps = OnboardingStep.entries.size
    val currentIndex = OnboardingStep.entries.indexOf(currentStep) + 1
    val progress = currentIndex.toFloat() / totalSteps.toFloat()

    fun moveNext() {
        currentStep = when (currentStep) {
            OnboardingStep.Permissions -> OnboardingStep.RestrictApps
            OnboardingStep.RestrictApps -> OnboardingStep.ModeAndTarget
            OnboardingStep.ModeAndTarget -> OnboardingStep.LinkAccounts
            OnboardingStep.LinkAccounts -> OnboardingStep.Safety
            OnboardingStep.Safety -> OnboardingStep.Safety
        }
    }

    fun moveBack() {
        currentStep = when (currentStep) {
            OnboardingStep.Permissions -> OnboardingStep.Permissions
            OnboardingStep.RestrictApps -> OnboardingStep.Permissions
            OnboardingStep.ModeAndTarget -> OnboardingStep.RestrictApps
            OnboardingStep.LinkAccounts -> OnboardingStep.ModeAndTarget
            OnboardingStep.Safety -> OnboardingStep.LinkAccounts
        }
    }

    fun validateCurrentStep(): String? {
        return when (currentStep) {
            OnboardingStep.Permissions -> null
            OnboardingStep.RestrictApps ->
                if (settingsState.selectedApps.isEmpty()) {
                    "Select at least one distracting app to restrict."
                } else {
                    null
                }

            OnboardingStep.ModeAndTarget ->
                if (settingsState.dailySolveTarget < 1) {
                    "Daily solve target should be at least 1 problem."
                } else {
                    null
                }

            OnboardingStep.LinkAccounts -> null
            OnboardingStep.Safety -> null
        }
    }

    fun persistSettingsLocally() {
        BlockingSettingsStore.markOnboardingComplete(
            context = context,
            restrictedApps = settingsState.selectedApps.toSet(),
            usageMode = settingsState.usageMode,
            dailySolveTarget = settingsState.dailySolveTarget,
            emergencyUnlockEnabled = settingsState.emergencyUnlockEnabled,
            freeUnlockEnabled = settingsState.freeUnlockEnabled,
            leetcodeUsername = settingsState.leetcodeUsername.takeIf { it.isNotBlank() },
            codeforcesHandle = settingsState.codeforcesHandle.takeIf { it.isNotBlank() },
        )
    }

    suspend fun commitAndContinue() {
        val input = OnboardingSettingsInput(
            restrictedApps = settingsState.selectedApps.toSet(),
            usageMode = settingsState.usageMode,
            dailySolveTarget = settingsState.dailySolveTarget,
            emergencyUnlockEnabled = settingsState.emergencyUnlockEnabled,
            freeUnlockEnabled = settingsState.freeUnlockEnabled,
        )

        runCatching {
            persistSettingsLocally()
        }.onSuccess {
            viewModel.savePlatformLinks(
                leetcode = settingsState.leetcodeUsername,
                codeforces = settingsState.codeforcesHandle
            )

            var nextMessage: String? = null

            viewModel.syncOnboardingSettings(input) { result ->
                nextMessage = when (result) {
                    is PermissionSetupSaveResult.Success -> result.message
                    is PermissionSetupSaveResult.Failure -> result.message
                }
            }

            if (!nextMessage.isNullOrBlank()) {
                snackbarHostState.showSnackbar(nextMessage!!)
            }

            onContinue()
        }.onFailure {
            snackbarHostState.showSnackbar("Could not save setup locally. Please try again.")
        }
    }

    PermissionSetupScreen(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        step = currentStep,
        stepIndex = currentIndex,
        totalSteps = totalSteps,
        progress = progress,
        settingsState = settingsState,
        isSaving = syncState.isSaving,
        isAccessibilityGranted = isAccessibilityGranted,
        isUsageStatsGranted = isUsageStatsGranted,
        onOpenAccessibilitySettings = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onOpenUsageAccessSettings = {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        },
        onBack = {
            moveBack()
        },
        onNext = {
            val validationError = validateCurrentStep()
            if (validationError != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(validationError)
                }
            } else if (currentStep == OnboardingStep.Safety) {
                scope.launch {
                    try {
                        commitAndContinue()
                    } catch (_: Exception) {
                        // Fallback: save locally and continue
                        persistSettingsLocally()
                        onContinue()
                    }
                }
            } else {
                moveNext()
            }
        },
    )
}

@Composable
private fun PermissionSetupScreen(
    step: OnboardingStep,
    stepIndex: Int,
    totalSteps: Int,
    progress: Float,
    settingsState: LocalOnboardingSettingsState,
    isSaving: Boolean,
    isAccessibilityGranted: Boolean,
    isUsageStatsGranted: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val nextButtonLabel = when (step) {
        OnboardingStep.Permissions ->
            if (isAccessibilityGranted && isUsageStatsGranted) "Next: Select Apps ✓" else "Next: Select Apps"
        OnboardingStep.RestrictApps -> "Next: Choose Mode"
        OnboardingStep.ModeAndTarget -> "Next: Link Accounts"
        OnboardingStep.LinkAccounts -> "Next: Review Settings"
        OnboardingStep.Safety -> if (isSaving) "Saving..." else "Finish Setup"
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SetupHeader(
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                progress = progress,
                title = when (step) {
                    OnboardingStep.Permissions -> "Grant the required permissions"
                    OnboardingStep.RestrictApps -> "Choose what to restrict"
                    OnboardingStep.ModeAndTarget -> "Set your discipline rules"
                    OnboardingStep.LinkAccounts -> "Link your coding accounts"
                    OnboardingStep.Safety -> "Review & finish setup"
                },
                subtitle = when (step) {
                    OnboardingStep.Permissions ->
                        "CodeUnlock needs Android permissions to detect blocked apps and measure screen-time usage."
                    OnboardingStep.RestrictApps ->
                        "Pick the distracting apps you want CodeUnlock to control."
                    OnboardingStep.ModeAndTarget ->
                        "Choose your daily usage mode and the minimum number of problems you must solve."
                    OnboardingStep.LinkAccounts ->
                        "Enter your LeetCode and Codeforces usernames so CodeUnlock can automatically verify your solves."
                    OnboardingStep.Safety ->
                        "Configure unlock safety settings before applying your setup."
                },
            )

            when (step) {
                OnboardingStep.Permissions -> PermissionsStepContent(
                    isAccessibilityGranted = isAccessibilityGranted,
                    isUsageStatsGranted = isUsageStatsGranted,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                )

                OnboardingStep.RestrictApps -> RestrictAppsStepContent(
                    selectedApps = settingsState.selectedApps,
                    onToggleApp = settingsState::toggleApp,
                )

                OnboardingStep.ModeAndTarget -> ModeAndTargetStepContent(
                    selectedMode = settingsState.usageMode,
                    dailySolveTarget = settingsState.dailySolveTarget,
                    onSelectMode = { settingsState.usageMode = it },
                    onIncrementTarget = {
                        settingsState.dailySolveTarget =
                            (settingsState.dailySolveTarget + 1).coerceAtMost(10)
                    },
                    onDecrementTarget = {
                        settingsState.dailySolveTarget =
                            (settingsState.dailySolveTarget - 1).coerceAtLeast(1)
                    },
                )

                OnboardingStep.LinkAccounts -> LinkAccountsStepContent(
                    leetcodeUsername = settingsState.leetcodeUsername,
                    codeforcesHandle = settingsState.codeforcesHandle,
                    onLeetcodeChange = { settingsState.leetcodeUsername = it },
                    onCodeforcesChange = { settingsState.codeforcesHandle = it },
                )

                OnboardingStep.Safety -> SafetyStepContent(
                    selectedAppsCount = settingsState.selectedApps.size,
                    selectedMode = settingsState.usageMode,
                    dailySolveTarget = settingsState.dailySolveTarget,
                    emergencyUnlockEnabled = settingsState.emergencyUnlockEnabled,
                    freeUnlockEnabled = settingsState.freeUnlockEnabled,
                    onToggleEmergencyUnlock = {
                        settingsState.emergencyUnlockEnabled = it
                    },
                    onToggleFreeUnlock = {
                        settingsState.freeUnlockEnabled = it
                    },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step != OnboardingStep.Permissions) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        enabled = !isSaving,
                    ) {
                        Text("Back")
                    }
                }

                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = nextButtonLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (step == OnboardingStep.Permissions) {
                TextButton(
                    onClick = onNext,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = !isSaving,
                ) {
                    Text("Continue for now")
                }
            }
        }
    }
}

@Composable
private fun SetupHeader(
    stepIndex: Int,
    totalSteps: Int,
    progress: Float,
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Setup $stepIndex of $totalSteps",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                        ),
                    ),
                    shape = RoundedCornerShape(28.dp),
                )
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                        )
                        .padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionsStepContent(
    isAccessibilityGranted: Boolean,
    isUsageStatsGranted: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (isAccessibilityGranted && isUsageStatsGranted) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp),
                    )
                    Column {
                        Text(
                            text = "All Permissions Active! 🎉",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Both permissions detected. Tap 'Next: Select Apps ✓' below to proceed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        PermissionCard(
            icon = if (isAccessibilityGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
            title = "Accessibility Permission",
            description = "Required to detect foreground apps and launch the blocking overlay when a restricted app is opened.",
            actionLabel = "Open Accessibility Settings",
            onActionClick = onOpenAccessibilitySettings,
            isGranted = isAccessibilityGranted,
        )

        PermissionCard(
            icon = if (isUsageStatsGranted) Icons.Outlined.CheckCircle else Icons.Outlined.BarChart,
            title = "Usage Access Permission",
            description = "Required to read app usage data, calculate screen time, and support wellbeing sync.",
            actionLabel = "Open Usage Access Settings",
            onActionClick = onOpenUsageAccessSettings,
            isGranted = isUsageStatsGranted,
        )

        InfoNoticeCard(
            title = "Why these permissions matter",
            description = "CodeUnlock only asks for the permissions needed to enforce blocking rules and track your device habits. Protected apps like Phone, Messages, and system settings should never be part of your restricted list.",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestrictAppsStepContent(
    selectedApps: List<String>,
    onToggleApp: (String) -> Unit,
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf(candidateApps) }
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            val apps = getInstalledRestrictableApps(context)
            withContext(Dispatchers.Main) {
                installedApps = apps
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Distracting apps to restrict",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Select the apps CodeUnlock should guard. You can change this later in settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    installedApps.forEach { app ->
                        val isSelected = selectedApps.contains(app.packageName)
                        AppSelectionChip(
                            app = app,
                            selected = isSelected,
                            onClick = { onToggleApp(app.packageName) },
                        )
                    }
                }
            }
        }

        if (selectedApps.isNotEmpty()) {
            SummaryPill(
                title = "Selected",
                value = "${selectedApps.size} app${if (selectedApps.size == 1) "" else "s"}",
            )
        }
    }
}

@Composable
private fun ModeAndTargetStepContent(
    selectedMode: String,
    dailySolveTarget: Int,
    onSelectMode: (String) -> Unit,
    onIncrementTarget: () -> Unit,
    onDecrementTarget: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Choose a blocking mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                usageModes.forEach { option ->
                    ModeOptionCard(
                        option = option,
                        selected = selectedMode == option.id,
                        onClick = { onSelectMode(option.id) },
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Daily solve target",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "This is the minimum number of verified problems you aim to solve each day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = onDecrementTarget,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("−")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dailySolveTarget.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "problems / day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    OutlinedButton(
                        onClick = onIncrementTarget,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("+")
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyStepContent(
    selectedAppsCount: Int,
    selectedMode: String,
    dailySolveTarget: Int,
    emergencyUnlockEnabled: Boolean,
    freeUnlockEnabled: Boolean,
    onToggleEmergencyUnlock: (Boolean) -> Unit,
    onToggleFreeUnlock: (Boolean) -> Unit,
) {
    val modeTitle = usageModes.firstOrNull { it.id == selectedMode }?.title ?: "Focused"

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Review your setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                SummaryPill(title = "Restricted apps", value = selectedAppsCount.toString())
                SummaryPill(title = "Mode", value = modeTitle)
                SummaryPill(title = "Daily target", value = "$dailySolveTarget problems")
            }
        }

        ToggleSettingCard(
            title = "Emergency unlock",
            description = "Allow a one-time emergency unlock path when you absolutely need temporary access.",
            checked = emergencyUnlockEnabled,
            onCheckedChange = onToggleEmergencyUnlock,
        )

        ToggleSettingCard(
            title = "Free unlock",
            description = "Allow a short free unlock grace period before requiring a verified solve.",
            checked = freeUnlockEnabled,
            onCheckedChange = onToggleFreeUnlock,
        )

        InfoNoticeCard(
            title = "How this is applied",
            description = "On first setup, your selections are saved immediately on the device. In the next integration phase, these same settings can also be synced to your backend profile and used by more advanced unlock/session logic.",
        )
    }
}

@Composable
private fun LinkAccountsStepContent(
    leetcodeUsername: String,
    codeforcesHandle: String,
    onLeetcodeChange: (String) -> Unit,
    onCodeforcesChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "LeetCode Username",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = leetcodeUsername,
                    onValueChange = onLeetcodeChange,
                    placeholder = { Text("e.g. your_leetcode_handle") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Codeforces Handle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = codeforcesHandle,
                    onValueChange = onCodeforcesChange,
                    placeholder = { Text("e.g. your_cf_handle") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        InfoNoticeCard(
            title = "Why link accounts?",
            description = "CodeUnlock verifies your solves by checking your submission history on LeetCode and Codeforces. Without a linked account, solve verification won't work for that platform.",
        )

        Text(
            text = "You can always update these in Settings later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    isGranted: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        ),
        border = if (isGranted) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isGranted) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (isGranted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = "Granted ✓",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isGranted) {
                OutlinedButton(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Permission Active (Tap to change)")
                }
            } else {
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
private fun AppSelectionChip(
    app: RestrictableApp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        selectedLabelColor = MaterialTheme.colorScheme.primary,
        labelColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        iconColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text("${app.emoji} ${app.name}")
        },
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        colors = colors,
    )
}

@Composable
private fun ModeOptionCard(
    option: UsageModeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            )
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = option.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleSettingCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun InfoNoticeCard(
    title: String,
    description: String,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryPill(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}
