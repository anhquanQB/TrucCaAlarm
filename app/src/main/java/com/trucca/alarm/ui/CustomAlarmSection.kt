package com.trucca.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucca.alarm.alarm.CustomAlarmItem
import com.trucca.alarm.alarm.CustomAlarmManager
import com.trucca.alarm.ui.theme.LocalGlassPalette

@Composable
fun CustomAlarmSection() {
    val context = LocalContext.current
    val p = LocalGlassPalette.current

    var alarms by remember { mutableStateOf(CustomAlarmManager.getAll(context)) }
    var hourText by remember { mutableStateOf("06") }
    var minuteText by remember { mutableStateOf("00") }
    var labelText by remember { mutableStateOf("") }
    var repeatDaily by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )

        Text(
            text = "Báo thức tự do (không theo lịch ca)",
            color = p.softWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = hourText,
                onValueChange = {
                    hourText = it.filter { ch -> ch.isDigit() }.take(2)
                    errorMsg = null
                },
                label = { Text("Giờ (0-23)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minuteText,
                onValueChange = {
                    minuteText = it.filter { ch -> ch.isDigit() }.take(2)
                    errorMsg = null
                },
                label = { Text("Phút (0-59)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = labelText,
            onValueChange = { labelText = it.take(32) },
            label = { Text("Nhãn báo thức (tùy chọn)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (repeatDaily) p.accent.copy(alpha = 0.24f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        width = if (repeatDaily) 1.5.dp else 1.dp,
                        color = if (repeatDaily) p.accent else Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { repeatDaily = !repeatDaily },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (repeatDaily) "Lặp: Hằng ngày" else "Lặp: Một lần",
                    color = p.softWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.accent.copy(alpha = 0.34f))
                    .border(1.dp, p.accent.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .clickable {
                        val h = hourText.toIntOrNull()
                        val m = minuteText.toIntOrNull()
                        if (h == null || h !in 0..23 || m == null || m !in 0..59) {
                            errorMsg = "Giờ phải từ 0–23 và phút từ 0–59."
                        } else {
                            alarms = CustomAlarmManager.addAlarm(
                                context = context,
                                hour = h,
                                minute = m,
                                label = labelText,
                                repeatDaily = repeatDaily
                            )
                            labelText = ""
                            errorMsg = null
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Thêm giờ",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        errorMsg?.let { msg ->
            Text(
                text = msg,
                color = Color(0xFFFCA5A5),
                fontSize = 11.sp
            )
        }

        if (alarms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                alarms.forEach { item ->
                    CustomAlarmRow(
                        item = item,
                        onToggle = { enabled ->
                            alarms = CustomAlarmManager.toggleAlarm(context, item.id, enabled)
                        },
                        onDelete = {
                            alarms = CustomAlarmManager.deleteAlarm(context, item.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomAlarmRow(
    item: CustomAlarmItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val p = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = if (item.enabled) 1.5.dp else 1.dp,
                color = if (item.enabled) p.accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.formattedTime(),
                color = if (item.enabled) p.highlightYellow else p.mutedText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${item.label} •${if (item.repeatDaily) "Hằng ngày" else "Một lần"}",
                color = p.mutedText,
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(if (item.enabled) p.accent else Color.White.copy(alpha = 0.22f))
                    .clickable { onToggle(!item.enabled) }
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (item.enabled) Color(0xFF07101F) else Color.White)
                        .align(if (item.enabled) Alignment.CenterEnd else Alignment.CenterStart)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.24f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.70f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Xóa",
                    color = Color(0xFFFECACA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
