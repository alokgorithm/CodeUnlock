package com.codeunlock.ui.components.mascot

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Mood of the CodeUnlock Focus Buddy Mascot
 */
enum class FocusBuddyMood {
    IDLE,       // Gentle breathing, blinking, friendly smile
    HAPPY,      // Smiling and content
    FOCUS,      // Concentrated, headphones pulsing with cyan glow
    CHEERING,   // Excited, happy sparkles, wide open smile
    SLEEPING    // Closed eyes (^_^) resting, slow breathing
}

/**
 * Animated Vector Mascot — "Kodi the Coding Fox"
 *
 * A charming, live companion inspired by Plopp, Foku, and Foco.
 * Renders cleanly at any size (40.dp avatar up to 200.dp hero).
 *
 * Features:
 * - Procedural breathing idle float
 * - Natural blinking cycles with glossy catchlights
 * - Over-ear coder headphones with ambient neon glow
 * - Dynamic mood expressions (Idle, Focus, Cheering, Sleeping)
 * - Interactive tap reaction (bounce + ear twitch)
 */
@Composable
fun FocusBuddyMascot(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    mood: FocusBuddyMood = FocusBuddyMood.IDLE,
    showHeadphones: Boolean = true,
    showGlasses: Boolean = false,
    onTap: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    // ── 1. Breathing Float Animation ─────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "BuddyBreathing")
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (mood == FocusBuddyMood.SLEEPING) 2400 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathFloat"
    )

    // ── 2. Headphone Glow Pulse ──────────────────────────────────────────
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (mood == FocusBuddyMood.FOCUS) 800 else 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    // ── 3. Periodic Natural Blinking ─────────────────────────────────────
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(mood) {
        if (mood == FocusBuddyMood.SLEEPING) {
            isBlinking = true
            return@LaunchedEffect
        }
        while (true) {
            val waitTime = Random.nextLong(2800L, 5200L)
            delay(waitTime)
            isBlinking = true
            delay(130L)
            isBlinking = false
            // Occasional double blink
            if (Random.nextFloat() < 0.25f) {
                delay(100L)
                isBlinking = true
                delay(120L)
                isBlinking = false
            }
        }
    }

    // ── 4. Interactive Bounce on Tap ─────────────────────────────────────
    val tapBounce = remember { Animatable(0f) }
    val earTwitch = remember { Animatable(0f) }

    val primaryBlue = Color(0xFF3B82F6)
    val lightBlue = Color(0xFF60A5FA)
    val bellyColor = Color(0xFFDBEAFE)
    val glowColor = if (mood == FocusBuddyMood.FOCUS) Color(0xFF00E5FF) else Color(0xFF60A5FA)
    val earInnerColor = Color(0xFFFFD1DC) // soft cute pink inside ears

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                coroutineScope.launch {
                    tapBounce.snapTo(-14f)
                    earTwitch.snapTo(12f)
                    tapBounce.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                }
                coroutineScope.launch {
                    earTwitch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
                }
                onTap?.invoke()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = this.size.width
            val canvasH = this.size.height

            // Calculate animated vertical translation (breath float + tap bounce)
            val breathOffset = (breathProgress * 6.dp.toPx()) + tapBounce.value
            val currentEarTwitch = earTwitch.value

            // Global Mascot Bounds
            val centerX = canvasW / 2f
            val centerY = canvasH / 2f + breathOffset

            val bodyRadius = canvasW * 0.36f

            // ── Background Glow Aura (Focus Mode) ────────────────────────
            if (mood == FocusBuddyMood.FOCUS) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.25f * glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = bodyRadius * 1.55f
                    ),
                    center = Offset(centerX, centerY),
                    radius = bodyRadius * 1.55f
                )
            }

            // ── 1. Fox Ears ──────────────────────────────────────────────
            val earWidth = bodyRadius * 0.55f
            val earHeight = bodyRadius * 0.72f

            // Left Ear
            rotate(degrees = -18f - currentEarTwitch, pivot = Offset(centerX - bodyRadius * 0.55f, centerY - bodyRadius * 0.7f)) {
                val leftEarPath = Path().apply {
                    moveTo(centerX - bodyRadius * 0.8f, centerY - bodyRadius * 0.4f)
                    lineTo(centerX - bodyRadius * 0.55f, centerY - bodyRadius * 1.25f)
                    lineTo(centerX - bodyRadius * 0.15f, centerY - bodyRadius * 0.7f)
                    close()
                }
                drawPath(leftEarPath, color = primaryBlue)

                // Inner Left Ear Pink
                val leftInnerEarPath = Path().apply {
                    moveTo(centerX - bodyRadius * 0.72f, centerY - bodyRadius * 0.45f)
                    lineTo(centerX - bodyRadius * 0.55f, centerY - bodyRadius * 1.12f)
                    lineTo(centerX - bodyRadius * 0.26f, centerY - bodyRadius * 0.7f)
                    close()
                }
                drawPath(leftInnerEarPath, color = earInnerColor)
            }

            // Right Ear
            rotate(degrees = 18f + currentEarTwitch, pivot = Offset(centerX + bodyRadius * 0.55f, centerY - bodyRadius * 0.7f)) {
                val rightEarPath = Path().apply {
                    moveTo(centerX + bodyRadius * 0.8f, centerY - bodyRadius * 0.4f)
                    lineTo(centerX + bodyRadius * 0.55f, centerY - bodyRadius * 1.25f)
                    lineTo(centerX + bodyRadius * 0.15f, centerY - bodyRadius * 0.7f)
                    close()
                }
                drawPath(rightEarPath, color = primaryBlue)

                // Inner Right Ear Pink
                val rightInnerEarPath = Path().apply {
                    moveTo(centerX + bodyRadius * 0.72f, centerY - bodyRadius * 0.45f)
                    lineTo(centerX + bodyRadius * 0.55f, centerY - bodyRadius * 1.12f)
                    lineTo(centerX + bodyRadius * 0.26f, centerY - bodyRadius * 0.7f)
                    close()
                }
                drawPath(rightInnerEarPath, color = earInnerColor)
            }

            // ── 2. Headband of Over-Ear Headphones (Behind body top) ──────
            if (showHeadphones) {
                drawArc(
                    color = Color(0xFF1E293B),
                    startAngle = 185f,
                    sweepAngle = 170f,
                    useCenter = false,
                    topLeft = Offset(centerX - bodyRadius * 1.02f, centerY - bodyRadius * 1.15f),
                    size = Size(bodyRadius * 2.04f, bodyRadius * 1.6f),
                    style = Stroke(width = bodyRadius * 0.14f, cap = StrokeCap.Round)
                )
            }

            // ── 3. Main Fox Body / Face ──────────────────────────────────
            // Slightly squircle rounded fox face/body shape
            val bodyGradient = Brush.verticalGradient(
                colors = listOf(lightBlue, primaryBlue),
                startY = centerY - bodyRadius,
                endY = centerY + bodyRadius
            )

            drawRoundRect(
                brush = bodyGradient,
                topLeft = Offset(centerX - bodyRadius, centerY - bodyRadius * 0.95f),
                size = Size(bodyRadius * 2f, bodyRadius * 1.9f),
                cornerRadius = CornerRadius(bodyRadius * 0.75f, bodyRadius * 0.75f)
            )

            // Cute tuft of fur on forehead
            val tuftPath = Path().apply {
                moveTo(centerX - bodyRadius * 0.12f, centerY - bodyRadius * 0.92f)
                quadraticBezierTo(centerX, centerY - bodyRadius * 1.12f, centerX + bodyRadius * 0.12f, centerY - bodyRadius * 0.92f)
                close()
            }
            drawPath(tuftPath, color = Color.White.copy(alpha = 0.85f))

            // White / Cream Fox Cheeks & Belly Patch
            val bellyPath = Path().apply {
                moveTo(centerX - bodyRadius * 0.7f, centerY + bodyRadius * 0.2f)
                quadraticBezierTo(
                    centerX, centerY - bodyRadius * 0.15f,
                    centerX + bodyRadius * 0.7f, centerY + bodyRadius * 0.2f
                )
                quadraticBezierTo(
                    centerX + bodyRadius * 0.65f, centerY + bodyRadius * 0.95f,
                    centerX, centerY + bodyRadius * 0.95f
                )
                quadraticBezierTo(
                    centerX - bodyRadius * 0.65f, centerY + bodyRadius * 0.95f,
                    centerX - bodyRadius * 0.7f, centerY + bodyRadius * 0.2f
                )
                close()
            }
            drawPath(bellyPath, color = bellyColor)

            // Rosy Pink Cheeks Blush
            val blushRadiusX = bodyRadius * 0.18f
            val blushRadiusY = bodyRadius * 0.11f
            drawOval(
                color = Color(0xFFF472B6).copy(alpha = 0.45f),
                topLeft = Offset(centerX - bodyRadius * 0.65f, centerY + bodyRadius * 0.22f),
                size = Size(blushRadiusX * 2f, blushRadiusY * 2f)
            )
            drawOval(
                color = Color(0xFFF472B6).copy(alpha = 0.45f),
                topLeft = Offset(centerX + bodyRadius * 0.65f - blushRadiusX * 2f, centerY + bodyRadius * 0.22f),
                size = Size(blushRadiusX * 2f, blushRadiusY * 2f)
            )

            // ── 4. Eyes (Cartoon Expressive with Sparkles) ────────────────
            val eyeSpacing = bodyRadius * 0.38f
            val eyeWidth = bodyRadius * 0.28f
            val eyeHeight = bodyRadius * 0.34f
            val eyeCenterY = centerY + bodyRadius * 0.05f

            if (isBlinking || mood == FocusBuddyMood.SLEEPING) {
                // Happy curved closed eye lines (^ ^)
                val strokeW = bodyRadius * 0.09f
                drawArc(
                    color = Color(0xFF0F172A),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(centerX - eyeSpacing - eyeWidth, eyeCenterY - eyeHeight * 0.2f),
                    size = Size(eyeWidth * 1.3f, eyeHeight * 0.7f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF0F172A),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(centerX + eyeSpacing - eyeWidth * 0.3f, eyeCenterY - eyeHeight * 0.2f),
                    size = Size(eyeWidth * 1.3f, eyeHeight * 0.7f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            } else {
                // Open big shiny cartoon eyes (like Foku / Plopp)
                // Left Eye White & Pupil
                drawOval(
                    color = Color.White,
                    topLeft = Offset(centerX - eyeSpacing - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
                    size = Size(eyeWidth, eyeHeight)
                )
                drawOval(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(centerX - eyeSpacing - eyeWidth * 0.4f, eyeCenterY - eyeHeight * 0.42f),
                    size = Size(eyeWidth * 0.8f, eyeHeight * 0.84f)
                )
                // Catchlight sparkles (Top Left & Bottom Right)
                drawCircle(
                    color = Color.White,
                    radius = eyeWidth * 0.22f,
                    center = Offset(centerX - eyeSpacing - eyeWidth * 0.12f, eyeCenterY - eyeHeight * 0.18f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = eyeWidth * 0.11f,
                    center = Offset(centerX - eyeSpacing + eyeWidth * 0.15f, eyeCenterY + eyeHeight * 0.16f)
                )

                // Right Eye White & Pupil
                drawOval(
                    color = Color.White,
                    topLeft = Offset(centerX + eyeSpacing - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
                    size = Size(eyeWidth, eyeHeight)
                )
                drawOval(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(centerX + eyeSpacing - eyeWidth * 0.4f, eyeCenterY - eyeHeight * 0.42f),
                    size = Size(eyeWidth * 0.8f, eyeHeight * 0.84f)
                )
                // Catchlight sparkles (Top Left & Bottom Right)
                drawCircle(
                    color = Color.White,
                    radius = eyeWidth * 0.22f,
                    center = Offset(centerX + eyeSpacing - eyeWidth * 0.12f, eyeCenterY - eyeHeight * 0.18f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = eyeWidth * 0.11f,
                    center = Offset(centerX + eyeSpacing + eyeWidth * 0.15f, eyeCenterY + eyeHeight * 0.16f)
                )
            }

            // Eyebrows
            val browY = eyeCenterY - eyeHeight * 0.65f
            val browWidth = eyeWidth * 0.7f
            val browStroke = bodyRadius * 0.06f
            val browAngle = if (mood == FocusBuddyMood.FOCUS) -8f else if (mood == FocusBuddyMood.CHEERING) 8f else 0f

            rotate(degrees = browAngle, pivot = Offset(centerX - eyeSpacing, browY)) {
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(centerX - eyeSpacing - browWidth / 2f, browY),
                    end = Offset(centerX - eyeSpacing + browWidth / 2f, browY),
                    strokeWidth = browStroke,
                    cap = StrokeCap.Round
                )
            }
            rotate(degrees = -browAngle, pivot = Offset(centerX + eyeSpacing, browY)) {
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(centerX + eyeSpacing - browWidth / 2f, browY),
                    end = Offset(centerX + eyeSpacing + browWidth / 2f, browY),
                    strokeWidth = browStroke,
                    cap = StrokeCap.Round
                )
            }

            // ── 5. Nose & Smile ──────────────────────────────────────────
            val noseY = eyeCenterY + bodyRadius * 0.2f
            drawOval(
                color = Color(0xFF0F172A),
                topLeft = Offset(centerX - bodyRadius * 0.08f, noseY - bodyRadius * 0.05f),
                size = Size(bodyRadius * 0.16f, bodyRadius * 0.11f)
            )

            // Mouth
            val mouthY = noseY + bodyRadius * 0.12f
            if (mood == FocusBuddyMood.CHEERING) {
                // Wide Open Happy Smile with Pink Tongue
                val mouthWidth = bodyRadius * 0.32f
                val mouthHeight = bodyRadius * 0.22f
                val mouthPath = Path().apply {
                    moveTo(centerX - mouthWidth / 2f, mouthY)
                    quadraticBezierTo(centerX, mouthY + mouthHeight, centerX + mouthWidth / 2f, mouthY)
                    close()
                }
                drawPath(mouthPath, color = Color(0xFF0F172A))
                // Pink Tongue
                val tonguePath = Path().apply {
                    moveTo(centerX - mouthWidth * 0.3f, mouthY + mouthHeight * 0.45f)
                    quadraticBezierTo(centerX, mouthY + mouthHeight * 0.95f, centerX + mouthWidth * 0.3f, mouthY + mouthHeight * 0.45f)
                    close()
                }
                drawPath(tonguePath, color = Color(0xFFFF6B81))
            } else {
                // Gentle W-curved cat/fox smile
                val mouthW = bodyRadius * 0.12f
                val mouthH = bodyRadius * 0.09f
                drawArc(
                    color = Color(0xFF0F172A),
                    startAngle = 10f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(centerX - mouthW, mouthY),
                    size = Size(mouthW, mouthH),
                    style = Stroke(width = bodyRadius * 0.05f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF0F172A),
                    startAngle = 10f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(centerX, mouthY),
                    size = Size(mouthW, mouthH),
                    style = Stroke(width = bodyRadius * 0.05f, cap = StrokeCap.Round)
                )
            }

            // ── 6. Over-Ear Headphone Ear Cups (with Glowing Ring) ────────
            if (showHeadphones) {
                val cupW = bodyRadius * 0.28f
                val cupH = bodyRadius * 0.52f
                val cupY = centerY - bodyRadius * 0.15f

                // Left Ear Cup
                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(centerX - bodyRadius * 1.05f - cupW / 2f, cupY - cupH / 2f),
                    size = Size(cupW, cupH),
                    cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f)
                )
                // Left Glowing Ring
                drawRoundRect(
                    color = glowColor.copy(alpha = glowPulse),
                    topLeft = Offset(centerX - bodyRadius * 1.05f - cupW * 0.35f, cupY - cupH * 0.35f),
                    size = Size(cupW * 0.7f, cupH * 0.7f),
                    cornerRadius = CornerRadius(cupW * 0.35f, cupW * 0.35f),
                    style = Stroke(width = bodyRadius * 0.05f)
                )

                // Right Ear Cup
                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(centerX + bodyRadius * 1.05f - cupW / 2f, cupY - cupH / 2f),
                    size = Size(cupW, cupH),
                    cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f)
                )
                // Right Glowing Ring
                drawRoundRect(
                    color = glowColor.copy(alpha = glowPulse),
                    topLeft = Offset(centerX + bodyRadius * 1.05f - cupW * 0.35f, cupY - cupH * 0.35f),
                    size = Size(cupW * 0.7f, cupH * 0.7f),
                    cornerRadius = CornerRadius(cupW * 0.35f, cupW * 0.35f),
                    style = Stroke(width = bodyRadius * 0.05f)
                )
            }

            // ── 7. Cute Paws resting on the bottom ───────────────────────
            val pawRadius = bodyRadius * 0.18f
            val pawY = centerY + bodyRadius * 0.88f
            drawCircle(
                color = lightBlue,
                radius = pawRadius,
                center = Offset(centerX - bodyRadius * 0.42f, pawY)
            )
            drawCircle(
                color = lightBlue,
                radius = pawRadius,
                center = Offset(centerX + bodyRadius * 0.42f, pawY)
            )
            // Little paw pads
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = pawRadius * 0.45f,
                center = Offset(centerX - bodyRadius * 0.42f, pawY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = pawRadius * 0.45f,
                center = Offset(centerX + bodyRadius * 0.42f, pawY)
            )
        }
    }
}
