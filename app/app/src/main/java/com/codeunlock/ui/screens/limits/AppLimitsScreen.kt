package com.codeunlock.ui.screens.limits

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.settings.ReelsBlockingStore
import com.codeunlock.data.settings.StrictModeStore
import com.codeunlock.data.usage.UsageManager

private val ProGold = Color(0xFFFFC107)
private val AccentGreen = Color(0xFF4CAF50)

data class InstalledAppItem(
    val name: String,
    val packageName: String
)

data class LimitedAppDisplay(
    val packageName: String,
    val appName: String,
    val spentMinutes: Long,
    val limitMinutes: Int,
    val isBlocking: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitsRoute(
    onBack: () -> Unit,
    onOpenUsageStats: () -> Unit = {}
) {
    val context = LocalContext.current

    var reloadTrigger by remember { mutableIntStateOf(0) }
    val settings = remember(reloadTrigger) { BlockingSettingsStore.read(context) }
    val reelsSettings = remember(reloadTrigger) { ReelsBlockingStore.read(context) }
    val isGuardEnabled = remember(reloadTrigger) { StrictModeStore.isSettingsGuardEnabled(context) }

    var blockSplitScreen by rememberSaveable { mutableStateOf(false) }
    var blockFloatingWindow by rememberSaveable { mutableStateOf(false) }
    var blockWebsites by rememberSaveable { mutableStateOf(false) }

    var showAddAppDialog by remember { mutableStateOf(false) }
    var selectedAppToEdit by remember { mutableStateOf<LimitedAppDisplay?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Resolve apps with active limits
    val limitedApps: List<LimitedAppDisplay> = remember(settings, reloadTrigger) {
        val pm = context.packageManager
        settings.perAppLimits.map { (pkg, limit) ->
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrElse { pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() } }

            val spent = UsageManager.getTodayUsageMinutes(context, pkg)
            LimitedAppDisplay(
                packageName = pkg,
                appName = label,
                spentMinutes = spent,
                limitMinutes = limit,
                isBlocking = spent >= limit
            )
        }.sortedByDescending { it.isBlocking }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Blocks",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable { onOpenUsageStats() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Stats", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(onClick = { showHelpDialog = true }) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Help",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Help", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Section 1: App Limits ─────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Limits",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showAddAppDialog = true }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                            Text("Add App", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            if (limitedApps.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No per-app limits configured yet",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Tap '+ Add App' above to set a daily time limit for Instagram, Clash of Clans, or any distracting app.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(limitedApps, key = { it.packageName }) { app ->
                    AppLimitCard(
                        app = app,
                        onClick = { selectedAppToEdit = app }
                    )
                }
            }

            // ── Section 2: Block Shorts [PRO] ─────────────────────────
            item {
                SectionHeaderWithBadge(title = "Block Shorts", badge = "PRO")
            }

            item {
                BlockToggleCard(
                    title = "YouTube Shorts",
                    emoji = "▶️",
                    isActive = reelsSettings.shortsBlocked,
                    onToggle = { active ->
                        ReelsBlockingStore.write(
                            context,
                            reelsSettings.copy(shortsBlocked = active)
                        )
                        reloadTrigger++
                    }
                )
            }

            item {
                BlockToggleCard(
                    title = "IG Reels",
                    emoji = "📷",
                    isActive = reelsSettings.reelsBlocked,
                    onToggle = { active ->
                        ReelsBlockingStore.write(
                            context,
                            reelsSettings.copy(reelsBlocked = active)
                        )
                        reloadTrigger++
                    }
                )
            }

            item {
                BlockToggleCard(
                    title = "Snapchat Spotlight",
                    emoji = "👻",
                    isActive = reelsSettings.mindfulPauseEnabled,
                    onToggle = { active ->
                        ReelsBlockingStore.write(
                            context,
                            reelsSettings.copy(mindfulPauseEnabled = active)
                        )
                        reloadTrigger++
                    }
                )
            }

            // ── Section 3: Other Blocks [PRO] ─────────────────────────
            item {
                SectionHeaderWithBadge(title = "Other blocks", badge = "PRO")
            }

            item {
                BlockToggleCard(
                    title = "Block Websites",
                    emoji = "🌐",
                    isActive = blockWebsites,
                    onToggle = { blockWebsites = it }
                )
            }

            // ── Section 4: Strict Mode Protections [PRO] ───────────────
            item {
                SectionHeaderWithBadge(title = "Strict mode protections", badge = "PRO")
            }

            item {
                ProtectionSwitchCard(
                    title = "Block CodeUnlock app uninstall",
                    subtitle = "Cannot uninstall or log out of CodeUnlock from Settings",
                    checked = isGuardEnabled,
                    onCheckedChange = { checked ->
                        StrictModeStore.setSettingsGuardEnabled(context, checked)
                        reloadTrigger++
                        Toast.makeText(
                            context,
                            if (checked) "Settings Guard Anti-Bypass Enabled! 🛡️" else "Settings Guard disabled.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            item {
                ProtectionSwitchCard(
                    title = "Block split screen",
                    subtitle = "Cannot use blocked apps in split screen",
                    checked = blockSplitScreen,
                    onCheckedChange = { blockSplitScreen = it }
                )
            }

            item {
                ProtectionSwitchCard(
                    title = "Block floating window",
                    subtitle = "Cannot use blocked apps in floating window",
                    checked = blockFloatingWindow,
                    onCheckedChange = { blockFloatingWindow = it }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Add App Limit Dialog
    if (showAddAppDialog) {
        AddAppLimitDialog(
            context = context,
            onDismiss = { showAddAppDialog = false },
            onAppLimitAdded = { pkg, limitMinutes ->
                BlockingSettingsStore.setAppLimit(context, pkg, limitMinutes)
                reloadTrigger++
                showAddAppDialog = false
                Toast.makeText(context, "Daily limit set! ✓", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit/Delete App Limit Dialog
    selectedAppToEdit?.let { app ->
        EditAppLimitDialog(
            app = app,
            onDismiss = { selectedAppToEdit = null },
            onUpdateLimit = { newLimit ->
                BlockingSettingsStore.setAppLimit(context, app.packageName, newLimit)
                reloadTrigger++
                selectedAppToEdit = null
            },
            onRemoveLimit = {
                BlockingSettingsStore.removeAppLimit(context, app.packageName)
                reloadTrigger++
                selectedAppToEdit = null
                Toast.makeText(context, "Limit removed.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("App Limits & Blocks Help", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• App Limits: Sets maximum daily foreground usage time for any installed app.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("• When an app exceeds its limit, it is locked automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("• Solve a coding problem on LeetCode/Codeforces or CodeUnlock to earn an unlock session!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("• Block Shorts surgically blocks short-form doomscroll feeds inside YouTube and Instagram.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Understood", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

// ── App Limit Card Item ──────────────────────────────────────────────

@Composable
private fun AppLimitCard(
    app: LimitedAppDisplay,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppIconAvatar(packageName = app.packageName, appName = app.appName)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${UsageManager.formatMinutes(app.spentMinutes)} spent / ${UsageManager.formatMinutes(app.limitMinutes.toLong())}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status pill
                if (app.isBlocking) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentGreen.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentGreen))
                            Text("Blocking", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    val remaining = (app.limitMinutes - app.spentMinutes).coerceAtLeast(0)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "${UsageManager.formatMinutes(remaining)} left",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Section Header with Pro Badge ────────────────────────────────────

@Composable
private fun SectionHeaderWithBadge(title: String, badge: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ProGold.copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ProGold)
        ) {
            Text(
                text = badge,
                color = ProGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ── Block Toggle Card (Shorts, Reels, Websites) ──────────────────────

@Composable
private fun BlockToggleCard(
    title: String,
    emoji: String,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isActive) AccentGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) AccentGreen else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.clickable { onToggle(!isActive) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (isActive) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (isActive) AccentGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isActive) "Active" else "Add",
                        color = if (isActive) AccentGreen else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Protection Switch Card (Strict Mode Protections) ─────────────────

@Composable
private fun ProtectionSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

// ── Add App Limit Dialog ─────────────────────────────────────────────

@Composable
private fun AddAppLimitDialog(
    context: Context,
    onDismiss: () -> Unit,
    onAppLimitAdded: (String, Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var selectedMinutes by remember { mutableIntStateOf(60) }

    val installedApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        pm.queryIntentActivities(intent, 0).map { info ->
            InstalledAppItem(
                name = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName
            )
        }.distinctBy { it.packageName }
         .filter { it.packageName != context.packageName }
         .sortedBy { it.name }
    }

    val filtered = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps else installedApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (selectedPackage == null) "Select App to Limit" else "Set Daily Limit",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                if (selectedPackage == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed apps...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedPackage = app.packageName }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIconAvatar(packageName = app.packageName, appName = app.name)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                } else {
                    val appItem = installedApps.firstOrNull { it.packageName == selectedPackage }
                    val appName = appItem?.name ?: selectedPackage!!

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIconAvatar(packageName = selectedPackage!!, appName = appName)
                        Column {
                            Text(appName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Choose daily limit duration", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Limit: ${UsageManager.formatMinutes(selectedMinutes.toLong())}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    // Interactive Slider (5m to 360m / 6h, 5-minute increments)
                    Slider(
                        value = selectedMinutes.toFloat(),
                        onValueChange = { selectedMinutes = ((it / 5f).toInt() * 5).coerceIn(5, 360) },
                        valueRange = 5f..360f,
                        steps = 70,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick-select preset pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LimitOptionPill("15m", 15, selectedMinutes, { selectedMinutes = 15 }, Modifier.weight(1f))
                        LimitOptionPill("30m", 30, selectedMinutes, { selectedMinutes = 30 }, Modifier.weight(1f))
                        LimitOptionPill("1h", 60, selectedMinutes, { selectedMinutes = 60 }, Modifier.weight(1f))
                        LimitOptionPill("2h", 120, selectedMinutes, { selectedMinutes = 120 }, Modifier.weight(1f))
                        LimitOptionPill("3h", 180, selectedMinutes, { selectedMinutes = 180 }, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { selectedPackage = null }) {
                            Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { onAppLimitAdded(selectedPackage!!, selectedMinutes) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Set Limit", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── Edit/Delete App Limit Dialog ─────────────────────────────────────

@Composable
private fun EditAppLimitDialog(
    app: LimitedAppDisplay,
    onDismiss: () -> Unit,
    onUpdateLimit: (Int) -> Unit,
    onRemoveLimit: () -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(app.limitMinutes) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconAvatar(packageName = app.packageName, appName = app.appName)
                    Column {
                        Text(app.appName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${UsageManager.formatMinutes(app.spentMinutes)} used today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Daily Limit: ${UsageManager.formatMinutes(selectedMinutes.toLong())}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                // Interactive Slider (5m to 360m / 6h, 5-minute increments)
                Slider(
                    value = selectedMinutes.toFloat(),
                    onValueChange = { selectedMinutes = ((it / 5f).toInt() * 5).coerceIn(5, 360) },
                    valueRange = 5f..360f,
                    steps = 70,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick-select preset pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LimitOptionPill("15m", 15, selectedMinutes, { selectedMinutes = 15 }, Modifier.weight(1f))
                    LimitOptionPill("30m", 30, selectedMinutes, { selectedMinutes = 30 }, Modifier.weight(1f))
                    LimitOptionPill("1h", 60, selectedMinutes, { selectedMinutes = 60 }, Modifier.weight(1f))
                    LimitOptionPill("2h", 120, selectedMinutes, { selectedMinutes = 120 }, Modifier.weight(1f))
                    LimitOptionPill("3h", 180, selectedMinutes, { selectedMinutes = 180 }, Modifier.weight(1f))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRemoveLimit) {
                        Text("Remove Limit", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { onUpdateLimit(selectedMinutes) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitOptionPill(
    label: String,
    minutes: Int,
    current: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = minutes == current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.clickable { onSelect() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AppIconAvatar(packageName: String, appName: String) {
    val context = LocalContext.current
    val iconDrawable: Drawable? = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    if (iconDrawable != null) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            modifier = Modifier.size(42.dp)
        ) {
            AsyncImage(
                model = iconDrawable,
                contentDescription = appName,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
