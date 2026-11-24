package com.trucca.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucca.alarm.data.ScheduleRepository
import com.trucca.alarm.model.ActiveShiftInfo
import com.trucca.alarm.model.AppSettings
import com.trucca.alarm.model.DaySchedule
import com.trucca.alarm.model.DefaultScheduleData
import com.trucca.alarm.model.UpcomingShiftInfo
import com.trucca.alarm.ui.theme.AccentBlue
import com.trucca.alarm.ui.theme.AccentYellow
import com.trucca.alarm.ui.theme.BlueEnd
import com.trucca.alarm.ui.theme.BlueMid
import com.trucca.alarm.ui.theme.BlueStart
import com.trucca.alarm.ui.theme.CardSurface
import com.trucca.alarm.ui.theme.CardSurfaceAlt
import com.trucca.alarm.ui.theme.DarkBg
import com.trucca.alarm.ui.theme.MutedText
import com.trucca.alarm.ui.theme.SoftWhite
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
    repo: ScheduleRepository,
    statusMessage: String?,
    onClearStatus: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onSchedulesUpdated: () -> Unit,
    onPickExcelOrWord: (Int, Int) -> Unit,
    onPickImageOcr: (Int, Int) -> Unit,
    onTestAlarm: () -> Unit,
    onTestWatchNotification: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var settings by remember { mutableStateOf(repo.getSettings()) }
    var schedulesVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L)
        }
    }

    val activeShift = remember(now, settings, schedulesVersion) {
        repo.getCurrentActiveShift(now)
    }
    val upcomingMyShifts = remember(now, settings, schedulesVersion) {
        repo.getUpcomingShiftsForKip(settings.selectedKip, now, 10)
    }

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0C1424)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Trang chủ") },
                    label = { Text("Trực ca") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Lịch tháng") },
                    label = { Text("Lịch tháng") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = "Nhập lịch") },
                    label = { Text("Nhập lịch") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Cài đặt") },
                    label = { Text("Báo thức") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeTab(
                    now = now,
                    settings = settings,
                    activeShift = activeShift,
                    upcomingMyShifts = upcomingMyShifts,
                    onSelectKip = { kip ->
                        val updated = settings.copy(selectedKip = kip)
                        settings = updated
                        onSettingsChanged(updated)
                    }
                )
                1 -> CalendarTab(
                    repo = repo,
                    settings = settings,
                    schedulesVersion = schedulesVersion,
                    onDayEdited = { day ->
                        repo.saveDayExact(day)
                        schedulesVersion++
                        onSchedulesUpdated()
                    }
                )
                2 -> ImportTab(
                    onPickExcelOrWord = onPickExcelOrWord,
                    onPickImageOcr = onPickImageOcr,
                    onGenerateCycle = { year, month, offset ->
                        val generated = DefaultScheduleData.generateCycleForMonth(year, month, offset)
                        repo.upsertSchedules(generated)
                        schedulesVersion++
                        onSchedulesUpdated()
                    }
                )
                3 -> SettingsTab(
                    settings = settings,
                    onSave = { updated ->
                        settings = updated
                        onSettingsChanged(updated)
                    },
                    onTestAlarm = onTestAlarm,
                    onTestWatchNotification = onTestWatchNotification
                )
            }

            if (!statusMessage.isNullOrBlank()) {
                AlertDialog(
                    onDismissRequest = onClearStatus,
                    confirmButton = {
                        TextButton(onClick = {
                            schedulesVersion++
                            onClearStatus()
                        }) { Text("Đóng") }
                    },
                    title = { Text("Thông báo") },
                    text = { Text(statusMessage) }
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    now: LocalDateTime,
    settings: AppSettings,
    activeShift: ActiveShiftInfo?,
    upcomingMyShifts: List<UpcomingShiftInfo>,
    onSelectKip: (String) -> Unit
) {
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lịch Trực Ca",
                        color = SoftWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${vietnameseWeekday(now.dayOfWeek)}, ${now.format(dateFmt)} • ${now.format(timeFmt)}",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentYellow.copy(alpha = 0.16f))
                        .border(1.dp, AccentYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Kíp tôi: ${settings.selectedKip}",
                        color = AccentYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            ActiveShiftHeroCard(activeShift = activeShift, settings = settings)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Chọn Kíp trực của bạn",
                    color = MutedText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefaultScheduleData.ALL_KIPS.forEach { kip ->
                        val isSelected = kip == settings.selectedKip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) AccentYellow else CardSurface)
                                .clickable { onSelectKip(kip) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Kíp $kip",
                                color = if (isSelected) Color(0xFF0F172A) else SoftWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            MyShiftCountdownCard(
                settings = settings,
                activeShift = activeShift,
                nextShift = upcomingMyShifts.firstOrNull()
            )
        }

        item {
            Text(
                text = "Các ca sắp tới của Kíp ${settings.selectedKip}",
                color = SoftWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (upcomingMyShifts.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Chưa có lịch trực sắp tới cho Kíp ${settings.selectedKip}. Hãy sang mục Nhập lịch để nạp file Excel/Word/Ảnh hoặc tạo chu kỳ tháng mới.",
                        color = MutedText,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(upcomingMyShifts.take(6)) { shift ->
                UpcomingShiftRow(shift = shift, settings = settings)
            }
        }
    }
}

@Composable
private fun ActiveShiftHeroCard(
    activeShift: ActiveShiftInfo?,
    settings: AppSettings
) {
    val gradient = Brush.linearGradient(
        colors = listOf(BlueStart, BlueMid, BlueEnd)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(gradient)
            .padding(22.dp)
    ) {
        if (activeShift != null) {
            val range = settings.shiftTimeConfig.formattedRange(activeShift.shiftType)
            val remainingText = ScheduleRepository.formatCountdown(activeShift.remainingSeconds)
            val progress = 1f - (activeShift.remainingSeconds.toFloat() / activeShift.totalSeconds.toFloat())
            val isMyKip = activeShift.kip.equals(settings.selectedKip, ignoreCase = true)

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ĐANG TRỰC",
                        color = Color(0xFFA8C7FA),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                    if (isMyKip) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentYellow)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "CA CỦA TÔI",
                                color = Color(0xFF0F172A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kíp ${activeShift.kip} • ${activeShift.shiftType.displayName}",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$range · còn $remainingText",
                    color = Color(0xFFD6E4FF),
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isMyKip) AccentYellow else Color(0xFF93C5FD),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = activeShift.membersText.ifBlank { "Chưa cập nhật nhân sự Kíp ${activeShift.kip}" },
                    color = Color(0xFFF1F5FF),
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            }
        } else {
            Column {
                Text(
                    text = "ĐANG TRỰC",
                    color = Color(0xFFA8C7FA),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chưa có dữ liệu hôm nay",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Vào mục Nhập lịch để nạp file Excel/Word/Ảnh hoặc tạo lịch theo chu kỳ.",
                    color = Color(0xFFD6E4FF),
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun MyShiftCountdownCard(
    settings: AppSettings,
    activeShift: ActiveShiftInfo?,
    nextShift: UpcomingShiftInfo?
) {
    val isCurrentlyWorking = activeShift?.kip.equals(settings.selectedKip, ignoreCase = true)
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = AccentYellow,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCurrentlyWorking) {
                        "Kíp ${settings.selectedKip} của bạn đang trong ca"
                    } else {
                        "Đếm ngược tới ca kế tiếp của Kíp ${settings.selectedKip}"
                    },
                    color = SoftWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (isCurrentlyWorking && activeShift != null) {
                Text(
                    text = "Còn ${ScheduleRepository.formatCountdown(activeShift.remainingSeconds)} là hết ${activeShift.shiftType.displayName}",
                    color = AccentYellow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (nextShift != null) {
                Text(
                    text = "Còn ${ScheduleRepository.formatCountdown(nextShift.secondsUntilStart)}",
                    color = AccentYellow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${nextShift.shiftType.displayName} • ${vietnameseWeekday(nextShift.shiftDate.dayOfWeek)} ${nextShift.shiftDate.format(dateFmt)} (${settings.shiftTimeConfig.formattedRange(nextShift.shiftType)})",
                    color = SoftWhite,
                    fontSize = 14.sp
                )
                if (settings.alarmEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Báo thức lúc ${nextShift.alarmDateTime.format(timeFmt)} ngày ${nextShift.alarmDateTime.format(dateFmt)}" +
                                if (settings.watchSyncEnabled) " • Đẩy Huawei Watch" else "",
                            color = AccentBlue,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "Chưa tìm thấy ca tiếp theo trong dữ liệu.",
                    color = MutedText,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun UpcomingShiftRow(shift: UpcomingShiftInfo, settings: AppSettings) {
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurfaceAlt),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${vietnameseWeekday(shift.shiftDate.dayOfWeek)}, ${shift.shiftDate.format(dateFmt)}",
                    color = SoftWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${shift.shiftType.displayName} • ${settings.shiftTimeConfig.formattedRange(shift.shiftType)}",
                    color = AccentYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Báo thức",
                    color = MutedText,
                    fontSize = 11.sp
                )
                Text(
                    text = if (settings.alarmEnabled) shift.alarmDateTime.format(timeFmt) else "Đang tắt",
                    color = if (settings.alarmEnabled) AccentBlue else MutedText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CalendarTab(
    repo: ScheduleRepository,
    settings: AppSettings,
    schedulesVersion: Int,
    onDayEdited: (DaySchedule) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.of(2026, 11)) }
    val monthSchedules = remember(currentMonth, schedulesVersion) {
        repo.getSchedulesForMonth(currentMonth).associateBy { it.date }
    }
    var editingDay by remember { mutableStateOf<DaySchedule?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Tháng trước", tint = SoftWhite)
            }
            Text(
                text = "Tháng ${currentMonth.monthValue} / ${currentMonth.year}",
                color = SoftWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Tháng sau", tint = SoftWhite)
            }
        }

        Text(
            text = "Ô vàng là Kíp ${settings.selectedKip} • Chạm vào từng ngày để sửa Kíp & nhập tên người trực riêng từng ca",
            color = MutedText,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Color(0xFF1E293B))
                .padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Text("Ngày", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontSize = 13.sp)
            Text("Sáng", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
            Text("Chiều", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
            Text("Đêm", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
        }

        val daysInMonth = (1..currentMonth.lengthOfMonth()).toList()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)
        ) {
            items(daysInMonth) { dayNum ->
                val date = currentMonth.atDay(dayNum)
                val sched = monthSchedules[date.toString()] ?: DaySchedule(date.toString(), "-", "-", "-")
                val isToday = date == LocalDate.now()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isToday) Color(0xFF1D3B6E) else CardSurface)
                        .clickable { editingDay = sched }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1.2f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (sched.hasCustomMembers()) "$dayNum*" else "$dayNum",
                            color = if (isToday) AccentYellow else SoftWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = vietnameseShortWeekday(date.dayOfWeek),
                            color = MutedText,
                            fontSize = 11.sp
                        )
                    }
                    ShiftCellBadge(sched.morningKip, sched.morningKip == settings.selectedKip, Modifier.weight(1f))
                    ShiftCellBadge(sched.afternoonKip, sched.afternoonKip == settings.selectedKip, Modifier.weight(1f))
                    ShiftCellBadge(sched.nightKip, sched.nightKip == settings.selectedKip, Modifier.weight(1f))
                }
            }
        }
    }

    editingDay?.let { day ->
        EditDayDialog(
            day = day,
            onDismiss = { editingDay = null },
            onSave = { updated ->
                onDayEdited(updated)
                editingDay = null
            }
        )
    }
}

@Composable
private fun ShiftCellBadge(kip: String, isHighlighted: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHighlighted) AccentYellow else Color(0xFF1B273D))
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = kip,
                color = if (isHighlighted) Color(0xFF0F172A) else SoftWhite,
                fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EditDayDialog(
    day: DaySchedule,
    onDismiss: () -> Unit,
    onSave: (DaySchedule) -> Unit
) {
    var morning by remember { mutableStateOf(day.morningKip) }
    var afternoon by remember { mutableStateOf(day.afternoonKip) }
    var night by remember { mutableStateOf(day.nightKip) }
    var morningMembers by remember { mutableStateOf(day.morningMembers) }
    var afternoonMembers by remember { mutableStateOf(day.afternoonMembers) }
    var nightMembers by remember { mutableStateOf(day.nightMembers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa ca & người trực (${day.date})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Để trống ô tên người trực nếu muốn dùng danh sách mặc định của Kíp.",
                    color = MutedText,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = morning,
                    onValueChange = { morning = it.uppercase().take(3) },
                    label = { Text("Kíp Ca sáng (A/B/C/D/E)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = morningMembers,
                    onValueChange = { morningMembers = it },
                    label = { Text("Người đi Ca sáng (tùy chọn)") },
                    maxLines = 2
                )
                OutlinedTextField(
                    value = afternoon,
                    onValueChange = { afternoon = it.uppercase().take(3) },
                    label = { Text("Kíp Ca chiều (A/B/C/D/E)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = afternoonMembers,
                    onValueChange = { afternoonMembers = it },
                    label = { Text("Người đi Ca chiều (tùy chọn)") },
                    maxLines = 2
                )
                OutlinedTextField(
                    value = night,
                    onValueChange = { night = it.uppercase().take(3) },
                    label = { Text("Kíp Ca đêm (A/B/C/D/E)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = nightMembers,
                    onValueChange = { nightMembers = it },
                    label = { Text("Người đi Ca đêm (tùy chọn)") },
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    day.copy(
                        morningKip = morning.trim(),
                        afternoonKip = afternoon.trim(),
                        nightKip = night.trim(),
                        morningMembers = morningMembers.trim(),
                        afternoonMembers = afternoonMembers.trim(),
                        nightMembers = nightMembers.trim()
                    )
                )
            }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun ImportTab(
    onPickExcelOrWord: (Int, Int) -> Unit,
    onPickImageOcr: (Int, Int) -> Unit,
    onGenerateCycle: (Int, Int, Int) -> Unit
) {
    var targetMonth by remember { mutableStateOf(YearMonth.now()) }
    var cycleOffset by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Nhập lịch trực ca",
                color = SoftWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Hỗ trợ Excel (.xlsx), Word (.docx), ảnh chụp bảng lịch và tự sinh theo chu kỳ 10 ngày.",
                color = MutedText,
                fontSize = 13.sp
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Áp dụng cho tháng:", color = SoftWhite, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { targetMonth = targetMonth.minusMonths(1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = SoftWhite)
                        }
                        Text(
                            text = "%02d/%04d".format(targetMonth.monthValue, targetMonth.year),
                            color = AccentYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = { targetMonth = targetMonth.plusMonths(1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftWhite)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = AccentBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nhập từ Excel (.xlsx) hoặc Word (.docx)", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        text = "Tự động đọc bảng có các dòng Ngày, Sáng, Chiều, Đêm giống mẫu Phụ lục lịch trực cơ quan bạn.",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = { onPickExcelOrWord(targetMonth.year, targetMonth.monthValue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Chọn file Excel / Word / CSV")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = AccentYellow)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nhập từ hình ảnh bảng lịch (OCR)", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        text = "Chọn ảnh chụp rõ nét bảng lịch trực. Sau khi nhận diện, bạn có thể vào tab Lịch tháng để kiểm tra và chỉnh tay nếu có ô sai.",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                    OutlinedButton(
                        onClick = { onPickImageOcr(targetMonth.year, targetMonth.monthValue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Chọn ảnh lịch trực từ thư viện")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tạo nhanh theo chu kỳ 5 kíp (10 ngày)", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "Tự sinh lịch đủ 30/31 ngày theo đúng quy luật xoay vòng A–E như tháng 11 & 12/2026.",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Độ lệch ngày đầu (0–9):", color = SoftWhite, fontSize = 13.sp)
                        OutlinedButton(onClick = { cycleOffset = (cycleOffset + 9) % 10 }) { Text("-") }
                        Text("$cycleOffset", color = AccentYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        OutlinedButton(onClick = { cycleOffset = (cycleOffset + 1) % 10 }) { Text("+") }
                    }
                    Button(
                        onClick = { onGenerateCycle(targetMonth.year, targetMonth.monthValue, cycleOffset) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Tạo lịch tháng %02d/%04d".format(targetMonth.monthValue, targetMonth.year), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onTestAlarm: () -> Unit,
    onTestWatchNotification: () -> Unit
) {
    var alarmEnabled by remember(settings) { mutableStateOf(settings.alarmEnabled) }
    var watchSyncEnabled by remember(settings) { mutableStateOf(settings.watchSyncEnabled) }
    var watchRepeatCount by remember(settings) { mutableIntStateOf(settings.watchRepeatCount) }
    var morningBefore by remember(settings) { mutableStateOf(settings.morningAlarmBeforeMin.toString()) }
    var afternoonBefore by remember(settings) { mutableStateOf(settings.afternoonAlarmBeforeMin.toString()) }
    var nightBefore by remember(settings) { mutableStateOf(settings.nightAlarmBeforeMin.toString()) }
    var membersMap by remember(settings) { mutableStateOf(settings.kipMembers.toMutableMap()) }

    fun buildUpdatedSettings(): AppSettings = settings.copy(
        alarmEnabled = alarmEnabled,
        watchSyncEnabled = watchSyncEnabled,
        watchRepeatCount = watchRepeatCount,
        morningAlarmBeforeMin = morningBefore.toIntOrNull() ?: 60,
        afternoonAlarmBeforeMin = afternoonBefore.toIntOrNull() ?: 60,
        nightAlarmBeforeMin = nightBefore.toIntOrNull() ?: 60,
        kipMembers = membersMap.toMap()
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Báo thức, Đồng hồ & Nhân sự",
                color = SoftWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Alarm card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bật báo thức tự động cho Kíp ${settings.selectedKip}", color = SoftWhite, fontWeight = FontWeight.Bold)
                            Text("Tự đặt chuông trước mỗi ca trực của bạn", color = MutedText, fontSize = 12.sp)
                        }
                        Switch(
                            checked = alarmEnabled,
                            onCheckedChange = {
                                alarmEnabled = it
                                onSave(buildUpdatedSettings())
                            }
                        )
                    }

                    OutlinedTextField(
                        value = morningBefore,
                        onValueChange = { morningBefore = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Ca sáng (07:00) — báo trước (phút)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = afternoonBefore,
                        onValueChange = { afternoonBefore = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Ca chiều (15:00) — báo trước (phút)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nightBefore,
                        onValueChange = { nightBefore = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Ca đêm (23:00) — báo trước (phút)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSave(buildUpdatedSettings()) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Lưu cài đặt")
                        }
                        OutlinedButton(
                            onClick = onTestAlarm,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử chuông")
                        }
                    }
                }
            }
        }

        // Huawei Watch GT 6 Pro / Huawei Health sync card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = AccentBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Liên kết Huawei Watch GT 6 Pro",
                            color = SoftWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Đẩy thông báo rung sang đồng hồ", color = SoftWhite, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Tự phát thông báo chuẩn để Huawei Sức khỏe chuyển sang đồng hồ khi báo thức kêu", color = MutedText, fontSize = 12.sp)
                        }
                        Switch(
                            checked = watchSyncEnabled,
                            onCheckedChange = {
                                watchSyncEnabled = it
                                onSave(buildUpdatedSettings())
                            }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Số nhịp rung lặp (cách 20 giây):", color = SoftWhite, fontSize = 13.sp)
                        OutlinedButton(onClick = {
                            watchRepeatCount = (watchRepeatCount - 1).coerceAtLeast(1)
                            onSave(buildUpdatedSettings())
                        }) { Text("-") }
                        Text("$watchRepeatCount", color = AccentYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        OutlinedButton(onClick = {
                            watchRepeatCount = (watchRepeatCount + 1).coerceAtMost(5)
                            onSave(buildUpdatedSettings())
                        }) { Text("+") }
                    }
                    Text(
                        text = "Cách bật trên điện thoại: Mở Huawei Sức khỏe → Thiết bị → chọn Huawei Watch GT 6 Pro → Thông báo → bật ứng dụng 'Trực Ca & Báo Thức'.",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                    OutlinedButton(
                        onClick = onTestWatchNotification,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Watch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gửi thông báo thử sang đồng hồ")
                    }
                }
            }
        }

        // Default crew per Kíp
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Danh sách nhân sự mặc định theo Kíp", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Hiển thị trên thẻ ĐANG TRỰC và Widget. Nếu ngày nào có đổi người đi ca, bạn chạm trực tiếp vào ngày đó ở tab Lịch tháng để nhập riêng cho từng ca.",
                        color = MutedText,
                        fontSize = 12.sp
                    )

                    DefaultScheduleData.ALL_KIPS.forEach { kip ->
                        var text by remember(settings) { mutableStateOf(membersMap[kip].orEmpty()) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                membersMap[kip] = it
                            },
                            label = { Text("Thành viên Kíp $kip") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }

                    Button(
                        onClick = { onSave(buildUpdatedSettings()) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Lưu danh sách nhân sự")
                    }
                }
            }
        }
    }
}

private fun vietnameseWeekday(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "Thứ 2"
    DayOfWeek.TUESDAY -> "Thứ 3"
    DayOfWeek.WEDNESDAY -> "Thứ 4"
    DayOfWeek.THURSDAY -> "Thứ 5"
    DayOfWeek.FRIDAY -> "Thứ 6"
    DayOfWeek.SATURDAY -> "Thứ 7"
    DayOfWeek.SUNDAY -> "Chủ nhật"
}

private fun vietnameseShortWeekday(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "T2"
    DayOfWeek.TUESDAY -> "T3"
    DayOfWeek.WEDNESDAY -> "T4"
    DayOfWeek.THURSDAY -> "T5"
    DayOfWeek.FRIDAY -> "T6"
    DayOfWeek.SATURDAY -> "T7"
    DayOfWeek.SUNDAY -> "CN"
}
