package com.trucca.alarm.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucca.alarm.data.ScheduleRepository
import com.trucca.alarm.ui.theme.GlassPanel
import com.trucca.alarm.ui.theme.LiquidGlassBackdrop
import com.trucca.alarm.ui.theme.LocalGlassPalette
import com.trucca.alarm.ui.theme.TrucCaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val title = intent.getStringExtra(AlarmService.EXTRA_TITLE) ?: "Sắp vào ca trực!"
        val detail = intent.getStringExtra(AlarmService.EXTRA_DETAIL) ?: "Chuẩn bị đi làm."
        val stage = intent.getIntExtra(AlarmService.EXTRA_STAGE, 1)
        val themeId = ScheduleRepository(this).getSettings().resolvedTheme()

        setContent {
            TrucCaTheme(themeId = themeId) {
                AlarmRingScreen(
                    title = title,
                    detail = detail,
                    stage = stage,
                    onDismissStage = {
                        startService(Intent(this, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_STOP
                            putExtra(AlarmService.EXTRA_TITLE, title)
                            putExtra(AlarmService.EXTRA_DETAIL, detail)
                            putExtra(AlarmService.EXTRA_STAGE, stage)
                        })
                        finish()
                    },
                    onCancelAll = {
                        startService(Intent(this, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_DISMISS_ALL
                        })
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun AlarmRingScreen(
    title: String,
    detail: String,
    stage: Int,
    onDismissStage: () -> Unit,
    onCancelAll: () -> Unit
) {
    val p = LocalGlassPalette.current
    val redGlow = Color(0xFFFF4D6D)

    val infinite = rememberInfiniteTransition(label = "crystal_halo")
    val t1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI * 7.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t1"
    )
    val t2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI * 11.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 23000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t2"
    )

    LiquidGlassBackdrop(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 30.dp,
                alphaBoost = 0.04f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Header + Title + Time WITH soft translucent white-sparkle halo behind text
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                        ) {
                            val center = Offset(size.width * 0.5f, size.height * 0.52f)
                            val maxR = size.minDimension * 0.92f
                            val breath = 0.94f + 0.06f * sin(t1 * 0.43f + cos(t2 * 0.29f))

                            // 1. Crystal ring aura: completely clear in center (behind text), glowing softly around text perimeter
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.00f to Color.Transparent,
                                    0.28f to Color.White.copy(alpha = 0.03f),
                                    0.56f to Color.White.copy(alpha = 0.19f),
                                    0.78f to p.accent.copy(alpha = 0.11f),
                                    1.00f to Color.Transparent,
                                    center = center,
                                    radius = maxR * breath
                                ),
                                center = center,
                                radius = maxR * breath
                            )

                            // 2. Non-rotating, organic shimmering crystal rays (each ray breathes independently)
                            val rayCount = 22
                            for (i in 0 until rayCount) {
                                // Golden-angle-like irregular spacing so rays look natural, never mechanical
                                val angle = (i * 0.285f + 0.11f * sin(i * 1.7f))
                                val shimmer = (0.5f + 0.5f * sin(t1 * (0.37f + (i % 5) * 0.13f) + t2 * 0.23f + i * 2.1f))
                                val lengthWave = 0.68f + 0.32f * (0.5f + 0.5f * cos(t2 * (0.31f + (i % 3) * 0.17f) + i * 1.3f))
                                val isLong = (i % 3 == 0) || (i % 5 == 2)

                                // Start outside center text zone so text stays 100% clear
                                val innerR = maxR * (0.34f + 0.08f * sin(i * 2.4f))
                                val outerR = maxR * (if (isLong) 1.04f else 0.72f) * lengthWave
                                val start = Offset(center.x + cos(angle) * innerR, center.y + sin(angle) * innerR)
                                val end = Offset(center.x + cos(angle) * outerR, center.y + sin(angle) * outerR)

                                drawLine(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.34f * shimmer),
                                            p.accent.copy(alpha = 0.16f * shimmer),
                                            Color.Transparent
                                        ),
                                        start = start,
                                        end = end
                                    ),
                                    start = start,
                                    end = end,
                                    strokeWidth = if (isLong) 2.5f else 1.6f,
                                    cap = StrokeCap.Round
                                )
                            }

                            // 3. Tiny 4-point diamond glints sparkling gently around the outer rim
                            val glintCount = 10
                            for (j in 0 until glintCount) {
                                val gAngle = j * 0.628f + 0.22f * cos(j * 2.3f)
                                val gDist = maxR * (0.58f + 0.26f * ((j * 37 % 10) / 10f))
                                val gSpark = (0.5f + 0.5f * sin(t1 * (0.53f + j * 0.09f) - t2 * 0.41f + j * 1.9f))
                                val alpha = (gSpark * gSpark) * 0.68f
                                val gx = center.x + cos(gAngle) * gDist
                                val gy = center.y + sin(gAngle) * gDist
                                val arm = 5.5f + 6.5f * gSpark

                                drawLine(
                                    color = Color.White.copy(alpha = alpha),
                                    start = Offset(gx - arm, gy),
                                    end = Offset(gx + arm, gy),
                                    strokeWidth = 1.6f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = alpha),
                                    start = Offset(gx, gy - arm),
                                    end = Offset(gx, gy + arm),
                                    strokeWidth = 1.6f,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = alpha * 0.9f),
                                    radius = 2.0f * gSpark,
                                    center = Offset(gx, gy)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "BÁO THỨC TRỰC CA",
                                color = p.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = title,
                                color = p.softWhite,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = detail,
                                color = p.softWhite.copy(alpha = 0.92f),
                                fontSize = 16.sp,
                                lineHeight = 23.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Button 1: "Tắt báo thức" + circled stage number
                    val dismissShape = RoundedCornerShape(18.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(dismissShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        p.reflectionPrimary.copy(alpha = 0.16f)
                                    )
                                )
                            )
                            .border(
                                width = 1.3.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.85f),
                                        p.reflectionPrimary.copy(alpha = 0.65f)
                                    )
                                ),
                                shape = dismissShape
                            )
                            .clickable(onClick = onDismissStage)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.size(28.dp))
                            Text(
                                text = "Tắt báo thức",
                                color = p.softWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(p.accent.copy(alpha = 0.22f))
                                    .border(1.2.dp, p.accent.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$stage",
                                    color = p.softWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Button 2: "Hủy" (cancels all upcoming follow-up stages)
                    val cancelShape = RoundedCornerShape(18.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(cancelShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        redGlow.copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .border(1.3.dp, redGlow.copy(alpha = 0.88f), cancelShape)
                            .clickable(onClick = onCancelAll),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hủy",
                            color = redGlow,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
