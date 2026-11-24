package com.trucca.alarm.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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

        setContent {
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
                onDismissAll = {
                    startService(Intent(this, AlarmService::class.java).apply {
                        action = AlarmService.ACTION_DISMISS_ALL
                    })
                    finish()
                },
                onSnooze = {
                    startService(Intent(this, AlarmService::class.java).apply {
                        action = AlarmService.ACTION_SNOOZE
                        putExtra(AlarmService.EXTRA_TITLE, title)
                        putExtra(AlarmService.EXTRA_DETAIL, detail)
                        putExtra(AlarmService.EXTRA_STAGE, stage)
                    })
                    finish()
                }
            )
        }
    }
}

@Composable
private fun AlarmRingScreen(
    title: String,
    detail: String,
    stage: Int,
    onDismissStage: () -> Unit,
    onDismissAll: () -> Unit,
    onSnooze: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1D4ED8), Color(0xFF0A1D42), Color(0xFF050811))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.2.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(30.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BÁO THỨC TRỰC CA • ĐỢT $stage / 3",
                color = Color(0xFF7DD3FC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = detail,
                color = Color(0xFFE2EEFF),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(36.dp))
            val nextStageNote = when (stage) {
                1 -> "Tắt đợt 1 (tự nhắc lại đợt 2 sau 10 phút)"
                2 -> "Tắt đợt 2 (tự nhắc lại đợt 3 sau 5 phút)"
                else -> "Tắt báo thức (đợt cuối 3/3)"
            }
            Button(
                onClick = onDismissStage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text(nextStageNote, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            }
            if (stage < 3) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDismissAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Đã tỉnh hẳn (Hủy các đợt sau)", fontSize = 14.sp, color = Color(0xFF7DD3FC))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Báo lại sau 5 phút", fontSize = 14.sp, color = Color.White)
            }
        }
    }
}
