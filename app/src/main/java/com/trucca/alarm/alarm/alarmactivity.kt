package com.trucca.alarm.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucca.alarm.data.ScheduleRepository
import com.trucca.alarm.ui.theme.GlassPanel
import com.trucca.alarm.ui.theme.LiquidGlassBackdrop
import com.trucca.alarm.ui.theme.LocalGlassPalette
import com.trucca.alarm.ui.theme.TrucCaTheme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

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
                        .padding(26.dp),
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
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = title,
                        color = p.softWhite,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = detail,
                        color = p.mutedText,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(34.dp))

                    // Button 1: "Tắt báo thức" + circled stage number on the right (matching app glass theme)
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

                    // Button 2: "Hủy" (cancels all upcoming follow-up stages, bright red glowing border & text)
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