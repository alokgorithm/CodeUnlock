package com.codeunlock.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeunlock.api.dto.UpdateSettingsRequest
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.settings.ReelsBlockingStore
import com.codeunlock.data.settings.StrictModeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RestrictableAppItem(
    val name: String,
    val packageName: String,
    val emoji: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: CodeUnlockRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun syncToBackend(
        usageMode: String,
        dailyTarget: Int,
        restrictedApps: List<String>,
        emergencyEnabled: Boolean,
        freeEnabled: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                repository.updateSettings(
                    UpdateSettingsRequest(
                        usageMode = usageMode,
                        dailySolveTarget = dailyTarget,
                        restrictedApps = restrictedApps,
                        emergencyUnlockEnabled = emergencyEnabled,
                        freeUnlockEnabled = freeEnabled
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val initialSettings = remember { BlockingSettingsStore.read(context) }
    val initialGuard = remember { StrictModeStore.isSettingsGuardEnabled(context) }
    val initialReels = remember { ReelsBlockingStore.read(context) }

    var selectedApps by remember { mutableStateOf(initialSettings.restrictedApps.toSet()) }
    var usageMode by rememberSaveable { mutableStateOf(initialSettings.usageMode) }
    var dailyTarget by rememberSaveable { mutableIntStateOf(initialSettings.dailySolveTarget) }
    var emergencyUnlock by rememberSaveable { mutableStateOf(initialSettings.emergencyUnlockEnabled) }
    var freeUnlock by rememberSaveable { mutableStateOf(initialSettings.freeUnlockEnabled) }
    var settingsGuard by rememberSaveable { mutableStateOf(initialGuard) }
    var reelsBlock by rememberSaveable { mutableStateOf(initialReels.reelsBlocked || initialReels.shortsBlocked) }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val allInstalledApps = remember(context) {
        loadInstalledLauncherApps(context)
    }

    val filteredApps = remember(searchQuery, allInstalledApps) {
        if (searchQuery.isBlank()) {
            allInstalledApps
        } else {
            allInstalledApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Discipline & Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            saveSettings(
                                context = context,
                                viewModel = viewModel,
                                usageMode = usageMode,
                                dailyTarget = dailyTarget,
                                restrictedApps = selectedApps,
                                emergencyUnlock = emergencyUnlock,
                                freeUnlock = freeUnlock,
                                settingsGuard = settingsGuard,
                                reelsBlock = reelsBlock,
                                onSaved = onBack
                            )
                        }
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            saveSettings(
                                context = context,
                                viewModel = viewModel,
                                usageMode = usageMode,
                                dailyTarget = dailyTarget,
                                restrictedApps = selectedApps,
                                emergencyUnlock = emergencyUnlock,
                                freeUnlock = freeUnlock,
                                settingsGuard = settingsGuard,
                                reelsBlock = reelsBlock,
                                onSaved = onBack
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Changes (${selectedApps.size} apps restricted)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader("Discipline Mode")
                ModeSelector(
                    selectedMode = usageMode,
                    onModeSelected = { usageMode = it }
                )
            }

            item {
                SectionHeader("Daily Problem Target")
                DailyTargetSelector(
                    selectedTarget = dailyTarget,
                    onTargetSelected = { dailyTarget = it }
                )
            }

            item {
                SectionHeader("Unlock Safeguards")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ToggleRow(
                            title = "Emergency Unlock",
                            subtitle = "Allow a one-time emergency unlock when temporary access is required.",
                            checked = emergencyUnlock,
                            onCheckedChange = { emergencyUnlock = it }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Free Unlock Grace Period",
                            subtitle = "Allow a short grace period before locking kicks in.",
                            checked = freeUnlock,
                            onCheckedChange = { freeUnlock = it }
                        )
                    }
                }
            }

            item {
                SectionHeader("Anti-Bypass Protection")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ToggleRow(
                            title = "Settings Guard",
                            subtitle = "Block Android Settings & App Info to prevent disabling accessibility service during locks.",
                            checked = settingsGuard,
                            onCheckedChange = { settingsGuard = it }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Shorts & Reels Blocker",
                            subtitle = "Specifically block short-form doomscroll feeds inside Instagram & YouTube.",
                            checked = reelsBlock,
                            onCheckedChange = { reelsBlock = it }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Restricted Apps (${selectedApps.size})")
                    TextButton(
                        onClick = {
                            val popular = setOf(
                                "com.google.android.youtube",
                                "com.instagram.android",
                                "com.twitter.android",
                                "com.zhiliaoapp.musically",
                                "com.facebook.katana",
                                "com.reddit.frontpage"
                            )
                            val installedPopular = allInstalledApps.map { it.packageName }.filter { it in popular }
                            selectedApps = selectedApps + installedPopular
                        }
                    ) {
                        Text("Add Popular", fontSize = 13.sp)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }

            items(filteredApps, key = { it.packageName }) { app ->
                val isSelected = selectedApps.contains(app.packageName)
                AppSelectionRow(
                    app = app,
                    isSelected = isSelected,
                    onToggle = {
                        selectedApps = if (isSelected) {
                            selectedApps - app.packageName
                        } else {
                            selectedApps + app.packageName
                        }
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun ModeSelector(selectedMode: String, onModeSelected: (String) -> Unit) {
    val modes = listOf(
        Triple("strict_lock", "🔒 Strict Lock (Recommended)", "Zero quota. Restricted apps are locked until you solve a coding problem."),
        Triple("hardcore", "⚡ Hardcore", "Strict lockdown. No grace quota; problems required to unlock."),
        Triple("focused", "🎯 Focused", "30-minute daily grace limit before blocking kicks in."),
        Triple("relaxed", "☕ Relaxed", "60-minute daily limit before blocking kicks in.")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { (modeId, title, desc) ->
            val isSelected = selectedMode == modeId || (modeId == "strict_lock" && (selectedMode == "custom" || selectedMode == "strict"))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(modeId) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onModeSelected(modeId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyTargetSelector(selectedTarget: Int, onTargetSelected: (Int) -> Unit) {
    val targets = listOf(1, 2, 3, 5)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        targets.forEach { target ->
            val isSelected = selectedTarget == target
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTargetSelected(target) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$target",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (target == 1) "problem" else "problems",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AppSelectionRow(
    app: RestrictableAppItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

private fun saveSettings(
    context: Context,
    viewModel: SettingsViewModel,
    usageMode: String,
    dailyTarget: Int,
    restrictedApps: Set<String>,
    emergencyUnlock: Boolean,
    freeUnlock: Boolean,
    settingsGuard: Boolean,
    reelsBlock: Boolean,
    onSaved: () -> Unit
) {
    val existing = BlockingSettingsStore.read(context)
    val updated = existing.copy(
        usageMode = usageMode,
        dailySolveTarget = dailyTarget,
        restrictedApps = restrictedApps,
        emergencyUnlockEnabled = emergencyUnlock,
        freeUnlockEnabled = freeUnlock,
        isFirstSetupComplete = true,
        lastSavedAt = System.currentTimeMillis()
    )

    BlockingSettingsStore.write(context, updated)
    StrictModeStore.setSettingsGuardEnabled(context, settingsGuard)
    ReelsBlockingStore.write(
        context,
        ReelsBlockingStore.ReelsSettings(
            reelsBlocked = reelsBlock,
            shortsBlocked = reelsBlock,
            oneVideoRule = true,
            mindfulPauseEnabled = true,
            mindfulPauseSeconds = 5
        )
    )

    viewModel.syncToBackend(
        usageMode = usageMode,
        dailyTarget = dailyTarget,
        restrictedApps = restrictedApps.toList(),
        emergencyEnabled = emergencyUnlock,
        freeEnabled = freeUnlock
    )

    Toast.makeText(context, "Settings saved successfully! ✓", Toast.LENGTH_SHORT).show()
    onSaved()
}

private fun loadInstalledLauncherApps(context: Context): List<RestrictableAppItem> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
    val emojiMap = mapOf(
        "youtube" to "▶️",
        "instagram" to "📷",
        "twitter" to "🐦",
        "musically" to "🎵",
        "facebook" to "👥",
        "reddit" to "🤖",
        "chrome" to "🌐",
        "netflix" to "🎬",
        "snapchat" to "👻",
        "whatsapp" to "💬",
        "telegram" to "✈️",
        "discord" to "🎮",
        "spotify" to "🎧",
        "linkedin" to "💼",
        "photos" to "🖼️",
        "settings" to "⚙️",
        "clock" to "⏰",
        "camera" to "📸"
    )

    return resolveInfos.map { info ->
        val appLabel = info.loadLabel(pm).toString()
        val packageName = info.activityInfo.packageName
        val lowercasePkg = packageName.lowercase()
        val customEmoji = emojiMap.entries.firstOrNull { lowercasePkg.contains(it.key) }?.value ?: "📱"
        RestrictableAppItem(
            name = appLabel,
            packageName = packageName,
            emoji = customEmoji
        )
    }.distinctBy { it.packageName }
     .filter { it.packageName != context.packageName }
     .sortedBy { it.name }
}
