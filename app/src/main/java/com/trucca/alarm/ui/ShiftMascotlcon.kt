package com.trucca.alarm.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.trucca.alarm.model.ShiftType
import com.trucca.alarm.ui.theme.LocalGlassPalette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive animated glass icon placed at top-right of the Active Shift card.
 * Tap to cycle:
 *   0 -> Cute Cat (alternates walking & sitting + tail wag; sleepy 'z' during night shift, jumps when tapped)
 *   1 -> Alarm Bell (gentle sway, rings energetically when tapped)
 *   2 -> Shift Sky (Sun / Sunset / Moon+Stars matching Morning/Afternoon/Night)
 */
@Composable
fun ShiftMascotIcon(
    shiftType: ShiftType,
    modifier: Modifier = Modifier
) {
    val p = LocalGlassPalette.current
    var mode by remember { mutableIntStateOf(0) }
    var isExcited by remember { mutableStateOf(false) }

    LaunchedEffect(isExcited) {
        if (isExcited) {
            delay(650L)
            isExcited = false
        }
    }

    val infinite = rememberInfiniteTransition(label = "mascot_loop")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val sway by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )
    val jumpOffset by animateFloatAsState(
        targetValue = if (isExcited) -8f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "jump"
    )

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isExcited = true
                mode = (mode + 1) % 3
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(38.dp)) {
            val w = size.width
            val h = size.height
            val accentColor = p.accent
            val bodyColor = p.softWhite

            when (mode) {
                0 -> {
                    // Mode 0: Cute Cat alternating walking (phase < 0.5) & sitting (phase >= 0.5)
                    val isWalking = phase < 0.5f && !isExcited
                    val walkStep = sin(phase * 8f * PI).toFloat()
                    val bob = (if (isWalking) kotlin.math.abs(walkStep) * -2.5f else 0f) + jumpOffset
                    val cx = w * 0.50f
                    val cy = h * 0.60f + bob

                    // Tail wagging
                    val tailAngle = sway * 24f
                    val tailStart = Offset(cx + w * 0.14f, cy + h * 0.10f)
                    val tailEnd = Offset(
                        tailStart.x + w * 0.18f,
                        tailStart.y - h * 0.14f + tailAngle * 0.18f
                    )
                    drawLine(
                        color = accentColor,
                        start = tailStart,
                        end = tailEnd,
                        strokeWidth = 3.2f,
                        cap = StrokeCap.Round
                    )

                    // Cat body (oval/circle)
                    drawCircle(
                        color = bodyColor.copy(alpha = 0.92f),
                        radius = w * 0.19f,
                        center = Offset(cx, cy + h * 0.08f)
                    )

                    // Cat head
                    val headCenter = Offset(cx - w * 0.04f, cy - h * 0.14f)
                    drawCircle(
                        color = bodyColor,
                        radius = w * 0.16f,
                        center = headCenter
                    )

                    // Left & Right Ears
                    val leftEar = Path().apply {
                        moveTo(headCenter.x - w * 0.13f, headCenter.y - h * 0.04f)
                        lineTo(headCenter.x - w * 0.15f, headCenter.y - h * 0.22f)
                        lineTo(headCenter.x - w * 0.02f, headCenter.y - h * 0.12f)
                        close()
                    }
                    val rightEar = Path().apply {
                        moveTo(headCenter.x + w * 0.02f, headCenter.y - h * 0.12f)
                        lineTo(headCenter.x + w * 0.14f, headCenter.y - h * 0.22f)
                        lineTo(headCenter.x + w * 0.13f, headCenter.y - h * 0.04f)
                        close()
                    }
                    drawPath(leftEar, color = bodyColor)
                    drawPath(rightEar, color = bodyColor)

                    // Eyes (blink or sleepy during Night shift when sitting)
                    val isBlinking = (!isWalking && shiftType == ShiftType.NIGHT) || (phase in 0.72f..0.77f)
                    val eyeColor = Color(0xFF0F172A)
                    if (isBlinking) {
                        drawLine(
                            color = eyeColor,
                            start = Offset(headCenter.x - w * 0.09f, headCenter.y),
                            end = Offset(headCenter.x - w * 0.03f, headCenter.y),
                            strokeWidth = 2.2f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = eyeColor,
                            start = Offset(headCenter.x + w * 0.03f, headCenter.y),
                            end = Offset(headCenter.x + w * 0.09f, headCenter.y),
                            strokeWidth = 2.2f,
                            cap = StrokeCap.Round
                        )
                    } else {
                        drawCircle(eyeColor, radius = 2.4f, center = Offset(headCenter.x - w * 0.06f, headCenter.y - 1f))
                        drawCircle(eyeColor, radius = 2.4f, center = Offset(headCenter.x + w * 0.06f, headCenter.y - 1f))
                    }

                    // Paws (moving when walking, tucked when sitting)
                    val legOffset = if (isWalking) walkStep * 4f else 0f
                    drawLine(
                        color = bodyColor,
                        start = Offset(cx - w * 0.10f, cy + h * 0.20f),
                        end = Offset(cx - w * 0.10f + legOffset, cy + h * 0.30f),
                        strokeWidth = 3.2f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = bodyColor,
                        start = Offset(cx + w * 0.08f, cy + h * 0.20f),
                        end = Offset(cx + w * 0.08f - legOffset, cy + h * 0.30f),
                        strokeWidth = 3.2f,
                        cap = StrokeCap.Round
                    )
                }

                1 -> {
                    // Mode 1: Animated Ringing Bell
                    val ringAngle = sway * (if (isExcited) 26f else 13f)
                    rotate(degrees = ringAngle, pivot = Offset(w * 0.5f, h * 0.16f)) {
                        // Bell dome
                        val bellPath = Path().apply {
                            moveTo(w * 0.25f, h * 0.68f)
                            cubicTo(w * 0.27f, h * 0.32f, w * 0.35f, h * 0.22f, w * 0.50f, h * 0.22f)
                            cubicTo(w * 0.65f, h * 0.22f, w * 0.73f, h * 0.32f, w * 0.75f, h * 0.68f)
                            close()
                        }
                        drawPath(bellPath, color = bodyColor.copy(alpha = 0.92f))
                        // Bell rim
                        drawLine(
                            color = bodyColor,
                            start = Offset(w * 0.20f, h * 0.70f),
                            end = Offset(w * 0.80f, h * 0.70f),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                        // Clapper
                        drawCircle(
                            color = accentColor,
                            radius = w * 0.08f,
                            center = Offset(w * 0.50f, h * 0.78f)
                        )
                        // Top knob
                        drawCircle(
                            color = accentColor,
                            radius = w * 0.055f,
                            center = Offset(w * 0.50f, h * 0.17f)
                        )
                    }
                }

                else -> {
                    // Mode 2: Shift-aware Sun (Morning/Afternoon) or Crescent Moon + Star (Night)
                    if (shiftType == ShiftType.NIGHT) {
                        val twinkle = 0.6f + 0.4f * kotlin.math.abs(sway)
                        drawCircle(
                            color = accentColor,
                            radius = w * 0.25f,
                            center = Offset(w * 0.46f, h * 0.52f)
                        )
                        drawCircle(
                            color = Color(0xFF142136),
                            radius = w * 0.20f,
                            center = Offset(w * 0.56f, h * 0.44f)
                        )
                        // Twinkling star
                        drawCircle(
                            color = bodyColor.copy(alpha = twinkle),
                            radius = w * 0.065f,
                            center = Offset(w * 0.76f, h * 0.26f)
                        )
                    } else {
                        val sunColor = if (shiftType == ShiftType.MORNING) p.highlightYellow else accentColor
                        val spin = phase * 360f
                        drawCircle(
                            color = sunColor,
                            radius = w * 0.18f,
                            center = Offset(w * 0.5f, h * 0.5f)
                        )
                        rotate(degrees = spin, pivot = Offset(w * 0.5f, h * 0.5f)) {
                            for (i in 0 until 8) {
                                val rad = (i * 45.0) * PI / 180.0
                                val start = Offset(
                                    w * 0.5f + (w * 0.25f * cos(rad)).toFloat(),
                                    h * 0.5f + (w * 0.25f * sin(rad)).toFloat()
                                )
                                val end = Offset(
                                    w * 0.5f + (w * 0.34f * cos(rad)).toFloat(),
                                    h * 0.5f + (w * 0.34f * sin(rad)).toFloat()
                                )
                                drawLine(
                                    color = sunColor.copy(alpha = 0.9f),
                                    start = start,
                                    end = end,
                                    strokeWidth = 2.6f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
