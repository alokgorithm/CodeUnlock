package com.codeunlock.ui.screens.usage

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.usage.AppCategory
import com.codeunlock.data.usage.AppCategoryStore
import com.codeunlock.data.usage.UsageManager
import java.util.Calendar
import kotlin.math.roundToInt

private val ColorDistracting = Color(0xFFFF8A00)
private val ColorProductive = Color(0xFF4CAF50)
private val ColorOthers = Color(0xFF8E99A8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsRoute(
    onBack: () -> Unit,
    onOpenAppLimits: () -> Unit = {}
) {
    val context = LocalContext.current
    val hasPermission = remember(context) { UsageManager.hasUsageStatsPermission(context) }

    var isDaily by rememberSaveable { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var targetDateMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    var selectedAppForEdit by remember { mutableStateOf<UsageManager.AppUsageDetail?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val summary = remember(isDaily, targetDateMillis, selectedCategory, searchQuery, reloadTrigger) {
        UsageManager.getUsageStatsSummary(
            context = context,
            isDaily = isDaily,
            targetDateMillis = targetDateMillis,
            filterCategory = selectedCategory,
            searchQuery = searchQuery
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App Breakdown",
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
                    // Quick button to jump to Blocks / Limits
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable { onOpenAppLimits() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFFF5252)
                            )
                            Text("Blocks", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasPermission) {
                PermissionRequiredBanner(context = context)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Daily / Weekly Tabs
                    item {
                        TabHeader(
                            isDaily = isDaily,
                            onTabSelected = { selected ->
                                isDaily = selected
                                targetDateMillis = System.currentTimeMillis()
                            }
                        )
                    }

                    // 2. Category Filter Pills Row: [All apps ✓] [Distracting] [Productive] [Others]
                    item {
                        FilterPillsRow(
                            selectedCategory = selectedCategory,
                            onSelect = { selectedCategory = it }
                        )
                    }

                    // 3. Time Hero with Chevron Date Navigation
                    item {
                        HeroTimeCard(
                            title = summary.headerTitle,
                            subtitle = summary.headerSubtitle,
                            onPrev = {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = targetDateMillis
                                    if (isDaily) add(Calendar.DAY_OF_MONTH, -1) else add(Calendar.WEEK_OF_YEAR, -1)
                                }
                                targetDateMillis = cal.timeInMillis
                            },
                            onNext = {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = targetDateMillis
                                    if (isDaily) add(Calendar.DAY_OF_MONTH, 1) else add(Calendar.WEEK_OF_YEAR, 1)
                                }
                                if (cal.timeInMillis <= System.currentTimeMillis() + 86_400_000L) {
                                    targetDateMillis = cal.timeInMillis
                                }
                            }
                        )
                    }

                    // 4. 7-Day Stacked Bar Chart (M, T, W, T, F, S, S)
                    item {
                        WeeklyStackedBarChart(
                            days = summary.daysOfWeek,
                            onDaySelected = { day ->
                                isDaily = true
                                targetDateMillis = day.dayStartMillis
                            }
                        )
                    }

                    // 5. Category Breakdown Totals Row (Distracting, Productive, Others)
                    item {
                        CategoryBreakdownLegend(
                            distractingFormatted = summary.distractingFormatted,
                            productiveFormatted = summary.productiveFormatted,
                            othersFormatted = summary.othersFormatted
                        )
                    }

                    // 6. Search Bar
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search apps", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 7. Apps List
                    if (summary.apps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching apps found" else "No usage recorded for this period",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        items(summary.apps, key = { it.packageName }) { app ->
                            AppUsageCardRow(
                                app = app,
                                onClick = { selectedAppForEdit = app }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // App Limit & Category Setting Dialog
    selectedAppForEdit?.let { app ->
        AppLimitSettingDialog(
            app = app,
            onDismiss = { selectedAppForEdit = null },
            onSaveLimit = { limitMinutes ->
                BlockingSettingsStore.setAppLimit(context, app.packageName, limitMinutes)
                reloadTrigger++
                selectedAppForEdit = null
            },
            onSaveCategory = { newCategory ->
                AppCategoryStore.setCategory(context, app.packageName, newCategory)
                reloadTrigger++
                selectedAppForEdit = null
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("About Usage Stats", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Daily tab displays usage for a specific day.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("• Weekly tab aggregates usage across Monday to Sunday.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("• Filter chips allow focusing on Distracting, Productive, or Other apps.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("• Tap any app in the list to set daily usage limits or change its category.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("• Once a daily limit is reached, CodeUnlock locks the app until a coding problem is solved!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

// ── Daily / Weekly Top Tabs ──────────────────────────────────────────

@Composable
private fun TabHeader(
    isDaily: Boolean,
    onTabSelected: (Boolean) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        val dailyColor by animateColorAsState(if (isDaily) primary else Color.Transparent, label = "daily")
        val weeklyColor by animateColorAsState(if (!isDaily) primary else Color.Transparent, label = "weekly")

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(dailyColor)
                .clickable { onTabSelected(true) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Daily",
                fontWeight = if (isDaily) FontWeight.Bold else FontWeight.Medium,
                color = if (isDaily) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(weeklyColor)
                .clickable { onTabSelected(false) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Weekly",
                fontWeight = if (!isDaily) FontWeight.Bold else FontWeight.Medium,
                color = if (!isDaily) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

// ── Filter Chips Row: [All apps ✓] [Distracting] [Productive] [Others] ──

@Composable
private fun FilterPillsRow(
    selectedCategory: AppCategory?,
    onSelect: (AppCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChipPill(
            label = "All apps",
            isSelected = selectedCategory == null,
            onClick = { onSelect(null) },
            dotColor = null
        )
        FilterChipPill(
            label = "Distracting",
            isSelected = selectedCategory == AppCategory.DISTRACTING,
            onClick = { onSelect(AppCategory.DISTRACTING) },
            dotColor = ColorDistracting
        )
        FilterChipPill(
            label = "Productive",
            isSelected = selectedCategory == AppCategory.PRODUCTIVE,
            onClick = { onSelect(AppCategory.PRODUCTIVE) },
            dotColor = ColorProductive
        )
        FilterChipPill(
            label = "Others",
            isSelected = selectedCategory == AppCategory.OTHERS,
            onClick = { onSelect(AppCategory.OTHERS) },
            dotColor = ColorOthers
        )
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    dotColor: Color?
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected && dotColor == null) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ── Time Hero Display ────────────────────────────────────────────────

@Composable
private fun HeroTimeCard(
    title: String,
    subtitle: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onPrev) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = title,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.SansSerif
            )

            IconButton(onClick = onNext) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Stacked 7-Day Bar Chart ──────────────────────────────────────────

@Composable
private fun WeeklyStackedBarChart(
    days: List<UsageManager.DayUsageSummary>,
    onDaySelected: (UsageManager.DayUsageSummary) -> Unit
) {
    val maxMinutes = days.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(60L) ?: 600L
    val yMaxHours = ((maxMinutes + 119L) / 60L).coerceAtLeast(6L)
    val yMidHours = yMaxHours / 2

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Background Y-axis horizontal gridlines
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    YAxisLine(label = "${yMaxHours}h")
                    YAxisLine(label = "${yMidHours}h")
                    YAxisLine(label = "0h")
                }

                // 7 Day Bars
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 32.dp, end = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEach { day ->
                        DayBarColumn(
                            day = day,
                            maxMinutes = yMaxHours * 60L,
                            onClick = { onDaySelected(day) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // X-axis Day of week labels (M, T, W, T, F, S, S)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    Text(
                        text = day.dayLabel,
                        color = if (day.isSelected) MaterialTheme.colorScheme.primary else if (day.isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = if (day.isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun YAxisLine(label: String) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.width(26.dp)
        )
        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
            drawLine(
                color = gridColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun DayBarColumn(
    day: UsageManager.DayUsageSummary,
    maxMinutes: Long,
    onClick: () -> Unit
) {
    val barHeightRatio = (day.totalMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
    val emptyBarColor = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Stacked rounded pill bar
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight(fraction = barHeightRatio.coerceAtLeast(0.04f))
                .clip(RoundedCornerShape(8.dp))
                .background(if (day.totalMinutes == 0L) emptyBarColor else Color.Transparent)
        ) {
            if (day.totalMinutes > 0L) {
                val distRatio = day.distractingMinutes.toFloat() / day.totalMinutes.toFloat()
                val prodRatio = day.productiveMinutes.toFloat() / day.totalMinutes.toFloat()
                val otherRatio = day.othersMinutes.toFloat() / day.totalMinutes.toFloat()

                Column(modifier = Modifier.fillMaxSize()) {
                    // Others on top (slate gray)
                    if (otherRatio > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(otherRatio)
                                .fillMaxWidth()
                                .background(ColorOthers)
                        )
                    }
                    // Productive in middle (green)
                    if (prodRatio > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(prodRatio)
                                .fillMaxWidth()
                                .background(ColorProductive)
                        )
                    }
                    // Distracting at bottom (orange)
                    if (distRatio > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(distRatio)
                                .fillMaxWidth()
                                .background(ColorDistracting)
                        )
                    }
                }
            }
        }

        if (day.isSelected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}

// ── Breakdown Summary Legend Row ─────────────────────────────────────

@Composable
private fun CategoryBreakdownLegend(
    distractingFormatted: String,
    productiveFormatted: String,
    othersFormatted: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(
            color = ColorDistracting,
            duration = distractingFormatted,
            label = "Distracting"
        )
        LegendItem(
            color = ColorProductive,
            duration = productiveFormatted,
            label = "Productive"
        )
        LegendItem(
            color = ColorOthers,
            duration = othersFormatted,
            label = "Others"
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    duration: String,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(
                text = duration,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

// ── App Usage List Card Row ──────────────────────────────────────────

@Composable
private fun AppUsageCardRow(
    app: UsageManager.AppUsageDetail,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Icon / Initial
            AppIconAvatar(packageName = app.packageName, appName = app.appName)

            // App Label and Category pill
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val dotColor = when (app.category) {
                        AppCategory.DISTRACTING -> ColorDistracting
                        AppCategory.PRODUCTIVE -> ColorProductive
                        AppCategory.OTHERS -> ColorOthers
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = app.category.label,
                        color = dotColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (app.limitMinutes != null && app.limitMinutes > 0) {
                        Text(
                            text = "• Limit: ${UsageManager.formatMinutes(app.limitMinutes.toLong())}",
                            color = if (app.isBlocking) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (app.isBlocking) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Duration and Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = app.durationFormatted,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
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

// ── App Limit & Category Setting Dialog ──────────────────────────────

@Composable
private fun AppLimitSettingDialog(
    app: UsageManager.AppUsageDetail,
    onDismiss: () -> Unit,
    onSaveLimit: (Int) -> Unit,
    onSaveCategory: (AppCategory) -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(app.limitMinutes ?: 0) }
    var selectedCat by remember { mutableStateOf(app.category) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconAvatar(packageName = app.packageName, appName = app.appName)
                    Column {
                        Text(
                            text = app.appName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Used today: ${app.durationFormatted}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Category Selection
                Text(
                    text = "App Category",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppCategory.values().forEach { cat ->
                        val isSelected = selectedCat == cat
                        val catColor = when (cat) {
                            AppCategory.DISTRACTING -> ColorDistracting
                            AppCategory.PRODUCTIVE -> ColorProductive
                            AppCategory.OTHERS -> ColorOthers
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) catColor else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedCat = cat }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat.label,
                                    color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Daily Limit — heading with current value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily App Limit",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (selectedMinutes == 0) "No Limit" else {
                            val h = selectedMinutes / 60
                            val m = selectedMinutes % 60
                            buildString {
                                if (h > 0) append("${h}h ")
                                if (m > 0) append("${m}m")
                            }.trim()
                        },
                        color = if (selectedMinutes == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Slider (0 to 360 minutes, step 15)
                Slider(
                    value = selectedMinutes.toFloat(),
                    onValueChange = { selectedMinutes = (it / 15f).roundToInt() * 15 },
                    valueRange = 0f..360f,
                    steps = (360 / 15) - 1, // 23 steps for 15-minute increments
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick-select chips: No Limit, 30m, 1h, 2h
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LimitPill(label = "No Limit", minutes = 0, current = selectedMinutes, onSelect = { selectedMinutes = 0 }, modifier = Modifier.weight(1f))
                    LimitPill(label = "30m", minutes = 30, current = selectedMinutes, onSelect = { selectedMinutes = 30 }, modifier = Modifier.weight(1f))
                    LimitPill(label = "1h", minutes = 60, current = selectedMinutes, onSelect = { selectedMinutes = 60 }, modifier = Modifier.weight(1f))
                    LimitPill(label = "2h", minutes = 120, current = selectedMinutes, onSelect = { selectedMinutes = 120 }, modifier = Modifier.weight(1f))
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            onSaveCategory(selectedCat)
                            onSaveLimit(selectedMinutes)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitPill(
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

// ── Permission Banner ────────────────────────────────────────────────

@Composable
private fun PermissionRequiredBanner(context: Context) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444)
                )
                Text(
                    text = "Usage Access Required",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Text(
                text = "To track real daily and weekly app usage and enforce your custom app limits, CodeUnlock requires Usage Access permission.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Usage Access", fontWeight = FontWeight.Bold)
            }
        }
    }
}
