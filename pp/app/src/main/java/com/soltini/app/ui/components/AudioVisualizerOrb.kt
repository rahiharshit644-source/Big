package com.soltini.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.soltini.app.ui.theme.PrimaryPink
import com.soltini.app.ui.theme.PrimaryPurple
import com.soltini.app.ui.theme.SecondaryMint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iridescent Glass Orb Hero Visualizer + Animated Soft Purple Waveform Bars beneath it.
 *
 * Renders a glossy gradient 3D glass sphere (pink-purple-blue with specular highlights,
 * inner iridescence, and rim reflection) accompanied by soft purple-pink waveform bars below.
 */
@Composable
fun AudioVisualizerOrb(
    micAmplitude: Float,
    speakerAmplitude: Float,
    isLive: Boolean,
    isSpeaking: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")

    val pulseScaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val phaseAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val activeAmplitude = if (isSpeaking) speakerAmplitude else micAmplitude
    val animatedAmp by animateFloatAsState(
        targetValue = activeAmplitude.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "amp"
    )

    val baseScale = if (isLive) pulseScaleAnim else 1f
    val dynamicRadiusScale = baseScale * (1f + animatedAmp * 0.45f)

    // Iridescent palette states (pink - purple - sky blue)
    val corePink by animateColorAsState(
        targetValue = when {
            isMuted -> Color(0xFFD8D4E6)
            isSpeaking -> Color(0xFFF687D6)
            isLive -> Color(0xFFE8A0E0)
            else -> Color(0xFFE5CCF5)
        },
        animationSpec = tween(500),
        label = "corePink"
    )

    val midPurple by animateColorAsState(
        targetValue = when {
            isMuted -> Color(0xFFB8B3C8)
            isSpeaking -> Color(0xFFA86BF4)
            isLive -> Color(0xFFB993F0)
            else -> Color(0xFFC5A8F2)
        },
        animationSpec = tween(500),
        label = "midPurple"
    )

    val rimBlue by animateColorAsState(
        targetValue = when {
            isMuted -> Color(0xFFA29EB5)
            isSpeaking -> Color(0xFF7CB8F7)
            isLive -> Color(0xFF8EC5FC)
            else -> Color(0xFFA8D5FA)
        },
        animationSpec = tween(500),
        label = "rimBlue"
    )

    Column(
        modifier = modifier.testTag("audio_visualizer_orb"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Large Iridescent Glass Orb
        Box(
            modifier = Modifier.size(210.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(210.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.34f
                val orbRadius = baseRadius * dynamicRadiusScale

                // Soft ambient pastel glow behind the glass sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            midPurple.copy(alpha = 0.38f),
                            corePink.copy(alpha = 0.20f),
                            rimBlue.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(center.x, center.y + orbRadius * 0.12f),
                        radius = orbRadius * 1.65f
                    ),
                    radius = orbRadius * 1.65f,
                    center = Offset(center.x, center.y + orbRadius * 0.12f)
                )

                // Subtle outer ripple ring when live
                if (isLive) {
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                corePink.copy(alpha = 0.45f),
                                midPurple.copy(alpha = 0.35f),
                                rimBlue.copy(alpha = 0.45f)
                            )
                        ),
                        radius = orbRadius * (1.20f + animatedAmp * 0.25f),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Main glossy 3D sphere body (pink -> purple -> blue iridescent gradient)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.White.copy(alpha = 0.95f),
                        0.22f to corePink,
                        0.58f to midPurple,
                        0.85f to rimBlue,
                        1.0f to midPurple.copy(alpha = 0.92f),
                        center = Offset(
                            center.x - orbRadius * 0.26f + sin(phaseAnim) * 6f,
                            center.y - orbRadius * 0.28f + cos(phaseAnim) * 6f
                        ),
                        radius = orbRadius * 1.35f
                    ),
                    radius = orbRadius,
                    center = center
                )

                // Inner swirling iridescent caustic wave
                val swirlOffset = Offset(
                    center.x + orbRadius * 0.22f * cos(phaseAnim),
                    center.y + orbRadius * 0.18f * sin(phaseAnim)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SecondaryMint.copy(alpha = 0.42f),
                            corePink.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = swirlOffset,
                        radius = orbRadius * 0.78f
                    ),
                    radius = orbRadius * 0.78f,
                    center = swirlOffset
                )

                // Bottom-right internal glass bounce light (rim refraction)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            rimBlue.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(center.x + orbRadius * 0.36f, center.y + orbRadius * 0.38f),
                        radius = orbRadius * 0.55f
                    ),
                    radius = orbRadius,
                    center = center
                )

                // Top-left glossy specular highlight (curved glass reflection)
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.88f),
                            Color.White.copy(alpha = 0.12f)
                        ),
                        startY = center.y - orbRadius * 0.78f,
                        endY = center.y - orbRadius * 0.15f
                    ),
                    topLeft = Offset(center.x - orbRadius * 0.52f, center.y - orbRadius * 0.76f),
                    size = Size(orbRadius * 0.92f, orbRadius * 0.52f)
                )

                // Crisp outer glass rim border
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            corePink.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.65f)
                        ),
                        start = Offset(center.x - orbRadius, center.y - orbRadius),
                        end = Offset(center.x + orbRadius, center.y + orbRadius)
                    ),
                    radius = orbRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Animated Soft Purple Waveform Bars beneath the orb
        val barCount = 19
        Canvas(
            modifier = Modifier
                .width(190.dp)
                .height(32.dp)
        ) {
            val totalWidth = size.width
            val maxBarHeight = size.height
            val minBarHeight = 5.dp.toPx()
            val barWidth = 4.5.dp.toPx()
            val gap = (totalWidth - barCount * barWidth) / (barCount - 1).coerceAtLeast(1)

            for (i in 0 until barCount) {
                val normalizedPos = (i.toFloat() / (barCount - 1)) * 2f - 1f // -1..1
                val envelope = 1f - (normalizedPos * normalizedPos * 0.72f) // Taller in center
                val wave = ((sin(phaseAnim * 2f + i * 0.55f) + 1f) * 0.5f).toFloat()

                val activityFactor = when {
                    isMuted -> 0.12f
                    isLive -> (0.28f + animatedAmp * 1.35f).coerceIn(0.25f, 1f)
                    else -> 0.22f
                }

                val targetHeight = (minBarHeight + (maxBarHeight - minBarHeight) * envelope * wave * activityFactor)
                    .coerceIn(minBarHeight, maxBarHeight)

                val x = i * (barWidth + gap)
                val y = (maxBarHeight - targetHeight) / 2f

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PrimaryPurple.copy(alpha = 0.90f),
                            PrimaryPink.copy(alpha = 0.85f)
                        ),
                        startY = y,
                        endY = y + targetHeight
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, targetHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
