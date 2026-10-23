package com.codeunlock.ui.components.mascot

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeunlock.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * Mood of the CodeUnlock Focus Buddy Mascot
 */
enum class FocusBuddyMood {
    IDLE,       // Gentle 3D floating, friendly smile
    HAPPY,      // Smiling and content
    FOCUS,      // Concentrated, headphones pulsing with cyan glow
    CHEERING,   // Excited, happy sparkles, celebratory bounce
    SLEEPING    // Resting, slow calm breathing
}

/**
 * Animated 3D Mascot — "Kodi the Coding Fox" 🦊🎧
 *
 * Renders the authentic, studio-quality 3D vinyl/clay character
 * with continuous 3D orbital floating, interactive touch look-at physics,
 * ground depth shadow, headphone neon LED pulses, and inter-page flight transitions.
 */
@Composable
fun FocusBuddyMascot(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    mood: FocusBuddyMood = FocusBuddyMood.IDLE,
    showHeadphones: Boolean = true,
    showGlasses: Boolean = false,
    entranceFlight: Boolean = true,
    onTap: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ── 1. Page Flight Entrance Transition ──────────────────────────────
    val flightProgress = remember { Animatable(if (entranceFlight) 1f else 0f) }
    LaunchedEffect(entranceFlight, mood) {
        if (entranceFlight) {
            flightProgress.snapTo(1f)
            flightProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // ── 2. Continuous 3D Orbital Floating Physics (Pitch, Yaw, Roll, Sway)
    val infiniteTransition = rememberInfiniteTransition(label = "Kodi3DOrbit")

    // Vertical breath / hover float (Y-axis)
    val floatPhaseY by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == FocusBuddyMood.SLEEPING) 2600 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatY"
    )

    // Lateral sway (X-axis) — slightly offset frequency for natural figure-8 orbit
    val swayPhaseX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == FocusBuddyMood.SLEEPING) 3600 else 2400,
                easing = SineInOutEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SwayX"
    )

    // 3D Head Turning (Yaw — Rotation Y): Looking curiously left and right
    val yawPhase by infiniteTransition.animateFloat(
        initialValue = if (mood == FocusBuddyMood.FOCUS) -4f else -9f,
        targetValue = if (mood == FocusBuddyMood.FOCUS) 4f else 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == FocusBuddyMood.FOCUS) 2800 else 3200,
                easing = SineInOutEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "YawY"
    )

    // 3D Head Nod (Pitch — Rotation X): Inquisitive up/down perspective tilt
    val pitchPhase by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PitchX"
    )

    // Inquiring head tilt (Roll — Rotation Z)
    val rollPhase by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RollZ"
    )

    // ── 3. Headphone Neon Glow Pulse ─────────────────────────────────────
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == FocusBuddyMood.FOCUS) 750 else 1400,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HeadphoneGlow"
    )

    // Cheering sparkle rotation
    val sparkleAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SparkleSpin"
    )

    // ── 4. Interactive 3D Touch Look-At & Tap Physics ───────────────────
    val dragRotationX = remember { Animatable(0f) }
    val dragRotationY = remember { Animatable(0f) }
    val tapJumpY = remember { Animatable(0f) }
    val tapScaleX = remember { Animatable(1f) }
    val tapScaleY = remember { Animatable(1f) }
    val tapPitch = remember { Animatable(0f) }
    val tapRoll = remember { Animatable(0f) }

    // Combined 3D angles & offsets
    val flight = flightProgress.value
    val currentRotationX = pitchPhase + dragRotationX.value + tapPitch.value + (flight * -15f)
    val currentRotationY = yawPhase + dragRotationY.value + (flight * 35f)
    val currentRotationZ = rollPhase + tapRoll.value + (flight * -18f)

    val currentTranslationX = (swayPhaseX * with(density) { 4.dp.toPx() }) + (flight * with(density) { 55.dp.toPx() })
    val currentTranslationY = (floatPhaseY * with(density) { 5.dp.toPx() }) + tapJumpY.value + (-flight * with(density) { 40.dp.toPx() })
    val currentScale = (1f - (flight * 0.35f))

    val glowColor = if (mood == FocusBuddyMood.FOCUS) Color(0xFF00E5FF) else Color(0xFF60A5FA)

    // Outer Container holding Shadow + Mascot in proper 3D perspective
    Box(
        modifier = modifier
            .size(size)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                coroutineScope.launch {
                    // Joyful 3D somersault jump with squash & stretch
                    tapJumpY.snapTo(-30f)
                    tapScaleX.snapTo(1.15f)
                    tapScaleY.snapTo(0.88f)
                    tapPitch.snapTo(-16f)
                    tapRoll.snapTo(14f)

                    // Spring back with high-energy landing
                    tapJumpY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                }
                coroutineScope.launch {
                    tapScaleX.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                coroutineScope.launch {
                    tapScaleY.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                coroutineScope.launch {
                    tapPitch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                coroutineScope.launch {
                    tapRoll.animateTo(0f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
                }
                onTap?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        // ── A. 3D Dynamic Contact Ground Shadow ───────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter)
        ) {
            val canvasW = this.size.width
            val canvasH = this.size.height

            // Shadow grows smaller and softer as mascot floats higher
            val shadowFactor = 1f - (floatPhaseY * 0.14f) - (flight * 0.4f)
            val shadowWidth = canvasW * 0.52f * shadowFactor
            val shadowHeight = canvasH * 0.12f * shadowFactor
            val shadowX = (canvasW / 2f) + (currentTranslationX * 0.4f)
            val shadowY = canvasH * 0.94f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.28f * shadowFactor),
                        Color.Black.copy(alpha = 0.10f * shadowFactor),
                        Color.Transparent
                    ),
                    center = Offset(shadowX, shadowY),
                    radius = shadowWidth / 2f
                ),
                topLeft = Offset(shadowX - shadowWidth / 2f, shadowY - shadowHeight / 2f),
                size = Size(shadowWidth, shadowHeight)
            )
        }

        // ── B. Focus Mode Radial Energy Aura ─────────────────────────────
        if (mood == FocusBuddyMood.FOCUS) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.toPx() / 2f
                val cy = size.toPx() / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.35f * glowPulse),
                            glowColor.copy(alpha = 0.10f * glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = size.toPx() * 0.65f
                    ),
                    center = Offset(cx, cy),
                    radius = size.toPx() * 0.65f
                )
            }
        }

        // ── C. 3D Animated Kodi Character ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.cameraDistance = 16f * density.density
                    this.rotationX = currentRotationX
                    this.rotationY = currentRotationY
                    this.rotationZ = currentRotationZ
                    this.translationX = currentTranslationX
                    this.translationY = currentTranslationY
                    this.scaleX = currentScale * tapScaleX.value
                    this.scaleY = currentScale * tapScaleY.value
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                dragRotationX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                            coroutineScope.launch {
                                dragRotationY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragRotationX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                            coroutineScope.launch {
                                dragRotationY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newY = (dragRotationY.value + dragAmount.x * 0.28f).coerceIn(-28f, 28f)
                            val newX = (dragRotationX.value - dragAmount.y * 0.28f).coerceIn(-22f, 22f)
                            coroutineScope.launch {
                                dragRotationY.snapTo(newY)
                                dragRotationX.snapTo(newX)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Authentic 3D Rendered Kodi Asset
            Image(
                painter = painterResource(id = R.drawable.mascot_kodi_3d),
                contentDescription = "Kodi 3D Focus Companion",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Dynamic 3D Headphone LED Ring Pulse Overlay
            if (showHeadphones) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.toPx()
                    val h = size.toPx()

                    // Headphone ear cups positions in 3D perspective
                    val yawShift = (currentRotationY / 25f).coerceIn(-1f, 1f) * w * 0.05f
                    val leftCupCenter = Offset(w * 0.28f + yawShift, h * 0.42f)
                    val rightCupCenter = Offset(w * 0.80f + yawShift, h * 0.42f)
                    val ringRadius = w * 0.12f

                    // Left Ear Cup Glowing LED Halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.55f * glowPulse),
                                glowColor.copy(alpha = 0.15f * glowPulse),
                                Color.Transparent
                            ),
                            center = leftCupCenter,
                            radius = ringRadius * 1.5f
                        ),
                        center = leftCupCenter,
                        radius = ringRadius * 1.5f
                    )
                    drawCircle(
                        color = glowColor.copy(alpha = 0.85f * glowPulse),
                        center = leftCupCenter,
                        radius = ringRadius,
                        style = Stroke(width = w * 0.024f)
                    )

                    // Right Ear Cup Glowing LED Halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.55f * glowPulse),
                                glowColor.copy(alpha = 0.15f * glowPulse),
                                Color.Transparent
                            ),
                            center = rightCupCenter,
                            radius = ringRadius * 1.5f
                        ),
                        center = rightCupCenter,
                        radius = ringRadius * 1.5f
                    )
                    drawCircle(
                        color = glowColor.copy(alpha = 0.85f * glowPulse),
                        center = rightCupCenter,
                        radius = ringRadius,
                        style = Stroke(width = w * 0.024f)
                    )
                }
            }

            // Dynamic 3D Specular Light Glide across vinyl surface
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.toPx()
                val h = size.toPx()
                val specX = w * 0.44f + (currentRotationY / 25f) * w * 0.12f
                val specY = h * 0.32f - (currentRotationX / 20f) * h * 0.08f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(specX, specY),
                        radius = w * 0.22f
                    ),
                    center = Offset(specX, specY),
                    radius = w * 0.22f
                )
            }

            // Cheering Celebration Sparkles
            if (mood == FocusBuddyMood.CHEERING) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.toPx()
                    val h = size.toPx()
                    val cx = w / 2f
                    val cy = h / 2f

                    drawSparkle(
                        center = Offset(cx - w * 0.38f, cy - h * 0.25f),
                        radius = w * 0.07f,
                        color = Color(0xFFFFD700),
                        rotation = sparkleAngle
                    )
                    drawSparkle(
                        center = Offset(cx + w * 0.36f, cy - h * 0.30f),
                        radius = w * 0.06f,
                        color = Color(0xFF00E5FF),
                        rotation = -sparkleAngle * 1.2f
                    )
                    drawSparkle(
                        center = Offset(cx + w * 0.40f, cy + h * 0.10f),
                        radius = w * 0.05f,
                        color = Color(0xFFFF80BF),
                        rotation = sparkleAngle * 0.8f
                    )
                }
            }
        }
    }
}

/** Draws a 4-point decorative sparkle star */
private fun DrawScope.drawSparkle(
    center: Offset,
    radius: Float,
    color: Color,
    rotation: Float
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticBezierTo(center.x, center.y, center.x + radius, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + radius)
        quadraticBezierTo(center.x, center.y, center.x - radius, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    rotate(degrees = rotation, pivot = center) {
        drawPath(path = path, color = color)
    }
}

private val SineInOutEasing: Easing = Easing { fraction ->
    ((1 - sin(Math.PI * (fraction + 0.5))) / 2.0).toFloat()
}
