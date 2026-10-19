package com.codeunlock.ui.screens.mindful

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun MindfulPauseScreen(
    appName: String,
    onGrantMinutes: (Int) -> Unit,
    onSolveProblem: () -> Unit,
    onDismiss: () -> Unit,
    pauseDurationSeconds: Int = 5,
    modifier: Modifier = Modifier
) {
    var secondsLeft by remember { mutableIntStateOf(pauseDurationSeconds) }
    var isCountdownFinished by remember { mutableStateOf(false) }

    // Breathing pulse animation (4 seconds per in/out cycle)
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Countdown tick
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        isCountdownFinished = true
    }

    val breathText = if (breathScale > 1.05f) "Breathe in..." else "Breathe out..."

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Regain-class deep slate
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "MINDFUL PAUSE",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Opening $appName",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Animated Breathing Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .scale(breathScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.35f),
                                Color(0xFF38BDF8).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, Color(0xFF6366F1).copy(alpha = 0.6f), CircleShape)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = breathText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.Medium
                    )
                    if (!isCountdownFinished) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${secondsLeft}s",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Intention choices appear after breathing countdown completes
            AnimatedVisibility(
                visible = isCountdownFinished,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 40 })
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "What is your intention?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFCBD5E1),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onGrantMinutes(2) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Quick reply (2 min session)",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Button(
                        onClick = { onGrantMinutes(5) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Quick lookup (5 min session)",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Button(
                        onClick = onSolveProblem,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Icon(Icons.Outlined.Psychology, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Solve Problem (Earn 30 min) 🧠",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Never mind, close app", color = Color(0xFF94A3B8))
                    }
                }
            }

            if (!isCountdownFinished) {
                Text(
                    text = "Take a moment to center yourself before opening.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
