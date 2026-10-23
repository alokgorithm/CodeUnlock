package com.codeunlock.ui.components.mascot

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Whatshot
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
import kotlinx.coroutines.delay
import kotlin.random.Random

private val MOTIVATIONAL_QUOTES = listOf(
    "Ready to code? Let's solve today's challenge!",
    "One line of code at a time. You've got this! 🚀",
    "Stay focused, debug relentlessly. 🔥",
    "Your future self will thank you for this focus session.",
    "Doomscrolling is temporary, clean code is forever. ✨",
    "Hydrate, code, conquer! 💧",
    "Don't break the streak! Let's lock in."
)

/**
 * FocusBuddyCard — Interactive Companion Widget inspired by Foku and Plopp.
 *
 * Displays Kodi the Coding Fox with an animated speech bubble,
 * streak & focus stat chips, and action prompts.
 */
@Composable
fun FocusBuddyCard(
    modifier: Modifier = Modifier,
    streakDays: Int = 1,
    solvedCount: Int = 0,
    dailyTarget: Int = 3,
    remainingMinutes: Int = 0,
    isFocusActive: Boolean = false,
    onStartFocus: () -> Unit = {},
    onSolveProblem: () -> Unit = {}
) {
    var quoteIndex by remember { mutableIntStateOf(0) }
    var userTappedCount by remember { mutableIntStateOf(0) }

    // Dynamic greeting based on progress
    val defaultSpeechText = remember(solvedCount, dailyTarget, isFocusActive) {
        when {
            isFocusActive -> "Locked in! No distractions allowed. 🎧"
            solvedCount >= dailyTarget -> "Daily target reached! You're unstoppable! 🎉"
            solvedCount > 0 -> "Only ${dailyTarget - solvedCount} more to unlock free time! Keep going! ⚡"
            else -> "I'm ready to focus with you! Let's solve a problem."
        }
    }

    val currentSpeech = if (userTappedCount > 0) MOTIVATIONAL_QUOTES[quoteIndex % MOTIVATIONAL_QUOTES.size] else defaultSpeechText

    val mood = when {
        isFocusActive -> FocusBuddyMood.FOCUS
        solvedCount >= dailyTarget -> FocusBuddyMood.CHEERING
        else -> FocusBuddyMood.IDLE
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Top Header Row ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isFocusActive) Color(0xFF00E5FF) else Color(0xFF4CAF50))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isFocusActive) "Kodi is Focusing" else "Kodi the Focus Buddy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFFF8A65),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${streakDays}d Streak",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── Animated Speech Bubble ───────────────────────────────────
            AnimatedContent(
                targetState = currentSpeech,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.92f) togetherWith
                        fadeOut(animationSpec = tween(180))
                },
                label = "SpeechBubbleTransition"
            ) { speechText ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💬 \"$speechText\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // ── Animated Mascot Character ────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                FocusBuddyMascot(
                    size = 110.dp,
                    mood = mood,
                    showHeadphones = true,
                    onTap = {
                        userTappedCount++
                        quoteIndex = Random.nextInt(MOTIVATIONAL_QUOTES.size)
                    }
                )
            }

            Text(
                text = "Tap Kodi to interact 🐾",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // ── Companion Quick Actions ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onStartFocus,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFocusActive) "View Focus" else "Start Focus", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onSolveProblem,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Solve to Unlock", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
