package com.codeunlock.ui.screens.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeunlock.audio.AmbientSoundManager
import com.codeunlock.audio.AmbientSoundType
import com.codeunlock.data.focus.FocusSessionStore
import com.codeunlock.service.FocusTimerService

private val PRESET_SUBJECTS = listOf(
    "Dynamic Programming",
    "Arrays & Strings",
    "Trees & Graphs",
    "System Design",
    "Contest Prep"
)

private val DEEP_FOCUS_PRESETS = listOf(15, 30, 45, 60, 90)

@Composable
fun FocusTimerRoute(
    onBack: () -> Unit,
    onOpenProblem: (Int) -> Unit
) {
    FocusTimerScreen(
        onBack = onBack,
        onOpenProblem = onOpenProblem
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerScreen(
    onBack: () -> Unit,
    onOpenProblem: (Int) -> Unit
) {
    val context = LocalContext.current

    // Live Service State
    val isRunning by FocusTimerService.isRunning.collectAsStateWithLifecycle()
    val isPaused by FocusTimerService.isPaused.collectAsStateWithLifecycle()
    val isBreakTime by FocusTimerService.isBreakTime.collectAsStateWithLifecycle()
    val remainingSeconds by FocusTimerService.remainingSeconds.collectAsStateWithLifecycle()
    val elapsedSeconds by FocusTimerService.elapsedSeconds.collectAsStateWithLifecycle()
    val totalDurationSeconds by FocusTimerService.totalDurationSeconds.collectAsStateWithLifecycle()
    val activeMode by FocusTimerService.activeMode.collectAsStateWithLifecycle()
    val activeSubject by FocusTimerService.activeSubject.collectAsStateWithLifecycle()

    // Sound Manager State
    val currentSound by AmbientSoundManager.currentSound.collectAsStateWithLifecycle()
    val volume by AmbientSoundManager.volume.collectAsStateWithLifecycle()

    // UI Configuration State (when idle)
    var selectedMode by remember { mutableStateOf(FocusTimerService.MODE_POMODORO) }
    var selectedDeepMinutes by remember { mutableIntStateOf(45) }
    var selectedSubject by remember { mutableStateOf(PRESET_SUBJECTS.first()) }

    // Today's Focus Stats & Schedule
    var todayFocusMinutes by remember { mutableIntStateOf(0) }
    var todaySessions by remember { mutableStateOf(emptyList<com.codeunlock.data.focus.FocusSessionRecord>()) }
    LaunchedEffect(isRunning) {
        todayFocusMinutes = FocusSessionStore.getTodayFocusMinutes(context)
        todaySessions = FocusSessionStore.getTodaySessions(context)
    }

    val displaySeconds = if (isRunning) {
        if (activeMode == FocusTimerService.MODE_STOPWATCH) elapsedSeconds else remainingSeconds
    } else {
        when (selectedMode) {
            FocusTimerService.MODE_POMODORO -> 25 * 60L
            FocusTimerService.MODE_DEEP_FOCUS -> selectedDeepMinutes * 60L
            FocusTimerService.MODE_STOPWATCH -> 0L
            else -> 25 * 60L
        }
    }

    val progressFraction: Float = when {
        !isRunning -> 1.0f
        activeMode == FocusTimerService.MODE_STOPWATCH -> ((elapsedSeconds % 60) / 60f)
        totalDurationSeconds > 0 -> (remainingSeconds.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 300),
        label = "FocusTimerProgress"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Coding Focus",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${todayFocusMinutes}m Today",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Mode Selector Chips (only editable when idle)
            if (!isRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == FocusTimerService.MODE_POMODORO,
                        onClick = { selectedMode = FocusTimerService.MODE_POMODORO },
                        label = { Text("🍅 Pomodoro (25m)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMode == FocusTimerService.MODE_DEEP_FOCUS,
                        onClick = { selectedMode = FocusTimerService.MODE_DEEP_FOCUS },
                        label = { Text("⚡ Deep Focus") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMode == FocusTimerService.MODE_STOPWATCH,
                        onClick = { selectedMode = FocusTimerService.MODE_STOPWATCH },
                        label = { Text("⏱️ Stopwatch") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // If Deep Focus mode selected, show duration chips
                if (selectedMode == FocusTimerService.MODE_DEEP_FOCUS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DEEP_FOCUS_PRESETS.forEach { mins ->
                            FilterChip(
                                selected = selectedDeepMinutes == mins,
                                onClick = { selectedDeepMinutes = mins },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                }

                // Subject Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Focus Subject",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PRESET_SUBJECTS) { subject ->
                            FilterChip(
                                selected = selectedSubject == subject,
                                onClick = { selectedSubject = subject },
                                label = { Text(subject) }
                            )
                        }
                    }
                }
            } else {
                // Session Active Status Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isBreakTime) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBreakTime) "☕ Rest Break Active" else "🎯 Focusing: $activeSubject",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isBreakTime) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isPaused) "PAUSED" else "RUNNING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50)
                            )
                        }
                        Text(
                            text = "🛡️ Strict Focus Active: YouTube and distracting apps are locked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Central Circular Countdown Arc
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .padding(12.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                val breakColor = Color(0xFF4CAF50)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    // Background track
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = strokeWidth)
                    )
                    // Progress arc
                    val sweep = animatedProgress * 360f
                    drawArc(
                        color = if (isBreakTime) breakColor else primaryColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val minutes = displaySeconds / 60
                    val seconds = displaySeconds % 60
                    Text(
                        text = "%02d:%02d".format(minutes, seconds),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isBreakTime) "Recharge Eyes & Mind" else if (isRunning) activeSubject else "Ready to Focus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Primary Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isRunning) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (!android.provider.Settings.canDrawOverlays(context)) {
                                    try {
                                        val overlayIntent = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(overlayIntent)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Enable 'Display over other apps' for the floating timer sidebar.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    } catch (_: Exception) {}
                                }
                                val duration = when (selectedMode) {
                                    FocusTimerService.MODE_POMODORO -> 25 * 60L
                                    FocusTimerService.MODE_DEEP_FOCUS -> selectedDeepMinutes * 60L
                                    FocusTimerService.MODE_STOPWATCH -> 0L
                                    else -> 25 * 60L
                                }
                                FocusTimerService.start(context, duration, selectedMode, selectedSubject)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Start Focus Session",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Tap Start to activate focus lock across YouTube and restricted apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                } else {
                    // Running: Pause/Resume + Stop
                    if (isPaused) {
                        Button(
                            onClick = { FocusTimerService.resume(context) },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Resume")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { FocusTimerService.pause(context) },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Pause")
                        }
                    }

                    Button(
                        onClick = { FocusTimerService.stop(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("End Session")
                    }
                }
            }

            // "Solve Next Problem" Shortcut Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProblem(1) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Solve Problem While Focusing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Solve Top 200 DSA problems while procedural sound plays",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Ambient Sound Mixer Section (Regain-Inspired Procedural Audio)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Ambient Soundscape",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Procedural audio engine (0 MB, 100% offline)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Sound Type Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmbientSoundType.values().forEach { sound ->
                            val isSelected = currentSound == sound
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            AmbientSoundManager.stopSound()
                                        } else {
                                            AmbientSoundManager.startSound(sound, volume)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = sound.emoji, fontSize = 20.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = sound.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            val sub = when (sound) {
                                                AmbientSoundType.BROWN_NOISE -> "Deep focus, masks inner chatter"
                                                AmbientSoundType.ALPHA_FLOW -> "10Hz brainwaves for complex algorithms"
                                                AmbientSoundType.WHITE_NOISE -> "Broad spectrum noise cancellation"
                                                AmbientSoundType.RAIN_SOUND -> "Soothing procedural precipitation"
                                                AmbientSoundType.NONE -> "Silence"
                                            }
                                            Text(
                                                text = sub,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Volume Slider
                    if (currentSound != AmbientSoundType.NONE) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Soundscape Volume",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Text(
                                    text = "$volume%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = volume.toFloat(),
                                onValueChange = { AmbientSoundManager.setVolume(it.toInt()) },
                                valueRange = 0f..100f
                            )
                        }
                    }
                }
            }

            // Daily Focus Schedule & Completed Sessions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Today's Focus Schedule",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${todaySessions.size} sessions (${todayFocusMinutes}m)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (todaySessions.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No focus sessions completed today yet. Start a session above to lock distractions and build your coding streak!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        todaySessions.forEach { session ->
                            val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(session.completedAtMillis))
                            val modeEmoji = when (session.mode) {
                                FocusTimerService.MODE_POMODORO -> "🍅"
                                FocusTimerService.MODE_DEEP_FOCUS -> "⚡"
                                else -> "⏱"
                            }
                            val durationMins = session.durationSeconds / 60

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(text = modeEmoji, fontSize = 20.sp)
                                        Column {
                                            Text(
                                                text = session.subjectTag,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "$timeStr · ${session.mode.replaceFirstChar { it.uppercase() }}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${durationMins}m",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
