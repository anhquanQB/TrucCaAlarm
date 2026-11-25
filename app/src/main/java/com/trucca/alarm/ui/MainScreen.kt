package com.trucca.alarm.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.trucca.alarm.data.ScheduleRepository
import com.trucca.alarm.model.ActiveShiftInfo
import com.trucca.alarm.model.AppSettings
import com.trucca.alarm.model.DaySchedule
import com.trucca.alarm.model.DefaultScheduleData
import com.trucca.alarm.model.GlassThemeId
import com.trucca.alarm.model.LunarCalendarUtil
import com.trucca.alarm.model.ShiftSwapEngine
import com.trucca.alarm.model.ShiftType
import com.trucca.alarm.model.SwapStep
import com.trucca.alarm.model.SwapSuggestion
import com.trucca.alarm.model.UpcomingShiftInfo
import com.trucca.alarm.ui.theme.GlassPanel
import com.trucca.alarm.ui.theme.LiquidGlassBackdrop
import com.trucca.alarm.ui.theme.LocalGlassPalette
import com.trucca.alarm.ui.theme.paletteFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    repo: ScheduleRepository,
    settings: AppSettings,
    statusMessage: String?,
    onClearStatus: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onSchedulesUpdated: () -> Unit,
    onPickExcelOrWord: (Int, Int) -> Unit,
    onPickImageOcr: (Int, Int) -> Unit,
    onTestAlarm: () -> Unit,
    onTestWatchNotification: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var schedulesVersion by remember { mutableIntStateOf(0) }
    var infoDialogData by remember { mutableStateOf<Pair<String, String>?>(null) }
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    LaunchedEffect(Unit) { weatherInfo = fetchQuangTrachWeather() }
    val showInfo: (String, String) -> Unit = { t, m -> infoDialogData = t to m }

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

    LiquidGlassBackdrop {
        WeatherAtmosphereOverlay(weather = weatherInfo)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> HomeTab(
                        now = now,
                        settings = settings,
                        activeShift = activeShift,
                        upcomingMyShifts = upcomingMyShifts,
                        weather = weatherInfo,
                        onRefreshWeather = {
                            coroutineScope.launch {
                                weatherInfo = fetchQuangTrachWeather()
                            }
                        },
                        onSelectKip = { kip ->
                            onSettingsChanged(settings.copy(selectedKip = kip))
                        }
                    )
                    1 -> CalendarTab(
                        repo = repo,
                        settings = settings,
                        schedulesVersion = schedulesVersion,
                        isSelectedTab = pagerState.currentPage == 1,
                        onShowInfo = showInfo,
                        onDayEdited = { day ->
                            repo.saveDayExact(day)
                            schedulesVersion++
                            onSchedulesUpdated()
                        },
                        onSwapApplied = { updatedDays ->
                            repo.upsertSchedules(updatedDays)
                            schedulesVersion++
                            onSchedulesUpdated()
                        }
                    )
                    2 -> ImportTab(
                        onShowInfo = showInfo,
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
                        onShowInfo = showInfo,
                        onSave = onSettingsChanged,
                        onTestAlarm = onTestAlarm,
                        onTestWatchNotification = onTestWatchNotification
                    )
                }
            }

            FloatingGlassBottomBar(
                selectedTab = pagerState.currentPage,
                onSelectTab = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
            )
        }

        infoDialogData?.let { (infoTitle, infoBody) ->
            BouncyGlassDialog(onDismissRequest = { infoDialogData = null }) {
                val p = LocalGlassPalette.current
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(infoTitle, color = p.softWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(infoBody, color = p.mutedText, fontSize = 14.sp, lineHeight = 20.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { infoDialogData = null }) {
                            Text("Đã hiểu", color = p.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            BouncyGlassDialog(onDismissRequest = onClearStatus) {
                val p = LocalGlassPalette.current
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Thông báo", color = p.softWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(statusMessage, color = p.mutedText, fontSize = 14.sp, lineHeight = 20.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            schedulesVersion++
                            onClearStatus()
                        }) { Text("Đóng", color = p.accent, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingGlassBottomBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val p = LocalGlassPalette.current
    val items = listOf(
        Triple(0, "Trực ca", Icons.Default.Home),
        Triple(1, "Lịch tháng", Icons.Default.DateRange),
        Triple(2, "Nhập lịch", Icons.Default.AddCircle),
        Triple(3, "Cài đặt", Icons.Default.Settings)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 28.dp,
            alphaBoost = 0.04f
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (index, label, icon) ->
                    val selected = selectedTab == index
                    GlassNavItem(
                        icon = icon,
                        label = label,
                        selected = selected,
                        accent = p.accent,
                        onClick = { onSelectTab(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.28f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xFF94A3B8),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_fg"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nav_scale"
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(pillColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun HomeTab(
    now: LocalDateTime,
    settings: AppSettings,
    activeShift: ActiveShiftInfo?,
    upcomingMyShifts: List<UpcomingShiftInfo>,
    weather: WeatherInfo?,
    onRefreshWeather: () -> Unit,
    onSelectKip: (String) -> Unit
) {
    val p = LocalGlassPalette.current
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    var kipExpanded by remember { mutableStateOf(false) }

    val islandCorner by animateDpAsState(
        targetValue = if (kipExpanded) 24.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "island_corner"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lịch Trực Ca",
                            color = p.softWhite,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${vietnameseWeekday(now.dayOfWeek)}, ${now.format(dateFmt)} ${LunarCalendarUtil.fromSolar(now.toLocalDate()).fullTag()} • ${now.format(timeFmt)}",
                            color = p.mutedText,
                            fontSize = 12.sp
                        )
                    }
                    // Dynamic Island Morphing Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(islandCorner))
                            .background(
                                if (kipExpanded) p.highlightYellow.copy(alpha = 0.26f)
                                else p.highlightYellow.copy(alpha = 0.16f)
                            )
                            .border(
                                1.2.dp,
                                p.highlightYellow.copy(alpha = if (kipExpanded) 0.9f else 0.65f),
                                RoundedCornerShape(islandCorner)
                            )
                            .clickable { kipExpanded = !kipExpanded }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (kipExpanded) "Chọn Kíp ▴" else "Kíp tôi: ${settings.selectedKip} ▾",
                            color = p.highlightYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Expanded Dynamic Island Glass Strip (morphs smoothly in/out)
                if (kipExpanded) {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = islandCorner,
                        alphaBoost = 0.06f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DefaultScheduleData.ALL_KIPS.forEach { kip ->
                                val isSelected = kip == settings.selectedKip
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) p.highlightYellow
                                            else Color.White.copy(alpha = 0.09f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) p.highlightYellow else Color.White.copy(alpha = 0.22f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            kipExpanded = false
                                            onSelectKip(kip)
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Kíp $kip",
                                        color = if (isSelected) Color(0xFF0F172A) else p.softWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Liquid Glass Hero Card (with compact Weather pill integrated cleanly inside header row)
        item {
            ActiveShiftHeroCard(
                activeShift = activeShift,
                settings = settings,
                weather = weather,
                onRefreshWeather = onRefreshWeather
            )
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
                color = p.softWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (upcomingMyShifts.isEmpty()) {
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Text(
                        text = "Chưa có lịch trực sắp tới cho Kíp ${settings.selectedKip}. Hãy sang mục Nhập lịch để nạp file Excel/Word/Ảnh hoặc tạo chu kỳ tháng mới.",
                        color = p.mutedText,
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
    settings: AppSettings,
    weather: WeatherInfo?,
    onRefreshWeather: () -> Unit
) {
    val p = LocalGlassPalette.current
    val shape = RoundedCornerShape(28.dp)
    val baseGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.20f),
            p.reflectionPrimary.copy(alpha = 0.10f),
            p.reflectionSecondary.copy(alpha = 0.14f)
        )
    )
    val glassSheen = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent, p.reflectionPrimary.copy(alpha = 0.12f))
    )
    val rimBorder = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.88f), p.reflectionPrimary.copy(alpha = 0.55f), p.reflectionSecondary.copy(alpha = 0.72f))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(baseGradient)
            .background(glassSheen)
            .border(1.2.dp, rimBorder, shape)
            .padding(20.dp)
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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ĐANG TRỰC",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                        if (isMyKip) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(p.highlightYellow)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CA CỦA TÔI",
                                    color = Color(0xFF0F172A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        WeatherPillBadge(
                            weather = weather,
                            onClick = onRefreshWeather
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    ShiftMascotIcon(shiftType = activeShift.shiftType)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Kíp ${activeShift.kip} • ${activeShift.shiftType.displayName}",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "$range · còn $remainingText",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isMyKip) p.highlightYellow else Color.White.copy(alpha = 0.88f),
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = activeShift.membersText.ifBlank { "Chưa cập nhật nhân sự Kíp ${activeShift.kip}" },
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            }
        } else {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ĐANG TRỰC",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                        WeatherPillBadge(
                            weather = weather,
                            onClick = onRefreshWeather
                        )
                    }
                }
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
                    color = Color.White.copy(alpha = 0.85f),
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
    val p = LocalGlassPalette.current
    val isCurrentlyWorking = activeShift?.kip.equals(settings.selectedKip, ignoreCase = true)
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        cornerRadius = 22.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = p.highlightYellow,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCurrentlyWorking) {
                        "Kíp ${settings.selectedKip} của bạn đang trong ca"
                    } else {
                        "Đếm ngược tới ca kế tiếp của Kíp ${settings.selectedKip}"
                    },
                    color = p.softWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (isCurrentlyWorking && activeShift != null) {
                Text(
                    text = "Còn ${ScheduleRepository.formatCountdown(activeShift.remainingSeconds)} là hết ${activeShift.shiftType.displayName}",
                    color = p.highlightYellow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (nextShift != null) {
                Text(
                    text = "Còn ${ScheduleRepository.formatCountdown(nextShift.secondsUntilStart)}",
                    color = p.highlightYellow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${nextShift.shiftType.displayName} • ${vietnameseWeekday(nextShift.shiftDate.dayOfWeek)} ${nextShift.shiftDate.format(dateFmt)} ${LunarCalendarUtil.fromSolar(nextShift.shiftDate).fullTag()} (${settings.shiftTimeConfig.formattedRange(nextShift.shiftType)})",
                    color = p.softWhite,
                    fontSize = 14.sp
                )
                if (settings.alarmEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Báo thức lúc ${nextShift.alarmDateTime.format(timeFmt)} ngày ${nextShift.alarmDateTime.format(dateFmt)}" +
                            if (settings.watchSyncEnabled) " • Đẩy Huawei Watch" else "",
                        color = p.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = "Chưa tìm thấy ca tiếp theo trong dữ liệu.",
                    color = p.mutedText,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun UpcomingShiftRow(shift: UpcomingShiftInfo, settings: AppSettings) {
    val p = LocalGlassPalette.current
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${vietnameseWeekday(shift.shiftDate.dayOfWeek)}, ${shift.shiftDate.format(dateFmt)} ${LunarCalendarUtil.fromSolar(shift.shiftDate).fullTag()}",
                    color = p.softWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${shift.shiftType.displayName} • ${settings.shiftTimeConfig.formattedRange(shift.shiftType)}",
                    color = p.highlightYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Báo thức", color = p.mutedText, fontSize = 11.sp)
                Text(
                    text = if (settings.alarmEnabled) shift.alarmDateTime.format(timeFmt) else "Đang tắt",
                    color = if (settings.alarmEnabled) p.accent else p.mutedText,
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
    isSelectedTab: Boolean,
    onShowInfo: (String, String) -> Unit,
    onDayEdited: (DaySchedule) -> Unit,
    onSwapApplied: (List<DaySchedule>) -> Unit
) {
    val p = LocalGlassPalette.current
    val today = LocalDate.now()
    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }
    val listState = rememberLazyListState()
    val allSchedules = remember(schedulesVersion) { repo.getAllSchedules() }
    val monthSchedules = remember(currentMonth, schedulesVersion) {
        repo.getSchedulesForMonth(currentMonth).associateBy { it.date }
    }
    val kipConflicts = remember(allSchedules, settings.selectedKip) {
        ShiftSwapEngine.findConflictsForKip(allSchedules, settings.selectedKip)
    }
    val conflictCells = remember(kipConflicts) {
        ShiftSwapEngine.conflictCellKeys(kipConflicts)
    }
    var editingDay by remember { mutableStateOf<DaySchedule?>(null) }
    var swapTargetDay by remember { mutableStateOf<LocalDate?>(null) }
    var showSwapDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectedTab) {
        if (isSelectedTab) {
            val nowDate = LocalDate.now()
            val targetMonth = YearMonth.from(nowDate)
            if (currentMonth != targetMonth) {
                currentMonth = targetMonth
            }
            val targetIndex = (nowDate.dayOfMonth - 1).coerceIn(0, targetMonth.lengthOfMonth() - 1)
            listState.scrollToItem(index = targetIndex, scrollOffset = 0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Tháng trước", tint = p.softWhite)
            }
            Text(
                text = "Tháng ${currentMonth.monthValue} / ${currentMonth.year}",
                color = p.softWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Tháng sau", tint = p.softWhite)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.accent.copy(alpha = 0.22f))
                        .border(1.dp, p.accent.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .clickable {
                            swapTargetDay = LocalDate.now()
                            showSwapDialog = true
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("Đổi ca", color = p.softWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                InfoHintButton(
                    title = "Hướng dẫn Lịch tháng & Đổi ca",
                    message = "Ô nổi bật là Kíp ${settings.selectedKip}. Ô viền đỏ là ca vi phạm luật nghỉ tối thiểu 12 giờ (2 ca). Bấm 'Đổi ca' để tìm đổi vòng 2–4 kíp trong ±30 ngày hoặc dạy app mẫu đổi mới.",
                    onShowInfo = onShowInfo
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp, alphaBoost = 0.04f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 8.dp)
            ) {
                Text("Ngày", color = p.softWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontSize = 13.sp)
                Text("Sáng", color = p.softWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
                Text("Chiều", color = p.softWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
                Text("Đêm", color = p.softWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
            }
        }

        if (kipConflicts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.22f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "⚠️ Cảnh báo phạm luật nghỉ 12h (tối thiểu 2 ca) — Kíp ${settings.selectedKip}:",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    kipConflicts.take(3).forEach { c ->
                        Text(
                            text = "• ${c.summary()}",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        val daysInMonth = (1..currentMonth.lengthOfMonth()).toList()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
        ) {
            items(daysInMonth) { dayNum ->
                val date = currentMonth.atDay(dayNum)
                val sched = monthSchedules[date.toString()] ?: DaySchedule(date.toString(), "-", "-", "-")
                val isToday = date == LocalDate.now()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isToday) p.accent.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.06f)
                        )
                        .border(
                            1.dp,
                            if (isToday) p.accent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { editingDay = sched }
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.weight(1.2f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (sched.hasCustomMembers()) "$dayNum*" else "$dayNum",
                                color = if (isToday) p.highlightYellow else p.softWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${vietnameseShortWeekday(date.dayOfWeek)} • ${LunarCalendarUtil.fromSolar(date).shortLabel()}",
                                color = p.mutedText,
                                fontSize = 10.sp
                            )
                        }
                        ShiftCellBadge(
                            kip = sched.morningKip,
                            isHighlighted = sched.morningKip == settings.selectedKip,
                            isConflict = (date.toString() to ShiftType.MORNING) in conflictCells,
                            modifier = Modifier.weight(1f)
                        )
                        ShiftCellBadge(
                            kip = sched.afternoonKip,
                            isHighlighted = sched.afternoonKip == settings.selectedKip,
                            isConflict = (date.toString() to ShiftType.AFTERNOON) in conflictCells,
                            modifier = Modifier.weight(1f)
                        )
                        ShiftCellBadge(
                            kip = sched.nightKip,
                            isHighlighted = sched.nightKip == settings.selectedKip,
                            isConflict = (date.toString() to ShiftType.NIGHT) in conflictCells,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    editingDay?.let { day ->
        EditDayDialog(
            day = day,
            onDismiss = { editingDay = null },
            onOpenSwap = {
                editingDay = null
                swapTargetDay = try { LocalDate.parse(day.date) } catch (_: Exception) { LocalDate.now() }
                showSwapDialog = true
            },
            onSave = { updated ->
                onDayEdited(updated)
                editingDay = null
            }
        )
    }

    if (showSwapDialog) {
        ShiftSwapDialog(
            repo = repo,
            settings = settings,
            initialDate = swapTargetDay ?: LocalDate.now(),
            onDismiss = { showSwapDialog = false },
            onApplySwap = { updatedDays ->
                onSwapApplied(updatedDays)
                showSwapDialog = false
            }
        )
    }
}

@Composable
private fun ShiftCellBadge(
    kip: String,
    isHighlighted: Boolean,
    isConflict: Boolean = false,
    modifier: Modifier
) {
    val p = LocalGlassPalette.current
    val bgColor = when {
        isConflict -> Color(0xFFEF4444).copy(alpha = 0.35f)
        isHighlighted -> p.highlightYellow
        else -> Color.White.copy(alpha = 0.09f)
    }
    val txtColor = when {
        isConflict -> Color(0xFFFECACA)
        isHighlighted -> Color(0xFF0F172A)
        else -> p.softWhite
    }
    Box(modifier = modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(bgColor)
                .then(
                    if (isConflict) Modifier.border(1.4.dp, Color(0xFFEF4444), RoundedCornerShape(9.dp))
                    else Modifier
                )
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isConflict) "$kip!" else kip,
                color = txtColor,
                fontWeight = if (isHighlighted || isConflict) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EditDayDialog(
    day: DaySchedule,
    onDismiss: () -> Unit,
    onOpenSwap: () -> Unit,
    onSave: (DaySchedule) -> Unit
) {
    var morning by remember { mutableStateOf(day.morningKip) }
    var afternoon by remember { mutableStateOf(day.afternoonKip) }
    var night by remember { mutableStateOf(day.nightKip) }
    var morningMembers by remember { mutableStateOf(day.morningMembers) }
    var afternoonMembers by remember { mutableStateOf(day.afternoonMembers) }
    var nightMembers by remember { mutableStateOf(day.nightMembers) }

    BouncyGlassDialog(onDismissRequest = onDismiss) {
        val p = LocalGlassPalette.current
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sửa ca & người trực (${day.date})", color = p.softWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Để trống ô tên người trực nếu muốn dùng danh sách mặc định của Kíp.",
                color = p.mutedText,
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = morning,
                onValueChange = { morning = it.uppercase().take(3) },
                label = { Text("Kíp Ca Sáng (A/B/C/D/E)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = morningMembers,
                onValueChange = { morningMembers = it },
                label = { Text("Người đi Ca Sáng (tùy chọn)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = afternoon,
                onValueChange = { afternoon = it.uppercase().take(3) },
                label = { Text("Kíp Ca Chiều (A/B/C/D/E)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = afternoonMembers,
                onValueChange = { afternoonMembers = it },
                label = { Text("Người đi Ca Chiều (tùy chọn)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = night,
                onValueChange = { night = it.uppercase().take(3) },
                label = { Text("Kíp Ca Khuya (A/B/C/D/E)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = nightMembers,
                onValueChange = { nightMembers = it },
                label = { Text("Người đi Ca Khuya (tùy chọn)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenSwap) {
                    Text("Đổi ca ngày này", color = p.highlightYellow, fontWeight = FontWeight.Bold)
                }
                Row {
                TextButton(onClick = onDismiss) { Text("Hủy", color = p.mutedText) }
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
                }) { Text("Lưu", color = p.accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ShiftSwapDialog(
    repo: ScheduleRepository,
    settings: AppSettings,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onApplySwap: (List<DaySchedule>) -> Unit
) {
    val p = LocalGlassPalette.current
    val allSchedules = remember { repo.getAllSchedules() }
    val myKip = settings.selectedKip
    var mode by remember { mutableIntStateOf(0) } // 0 = Gợi ý, 1 = Tự đổi & Kiểm tra, 2 = Dạy app

    // Collect upcoming/nearby shifts of myKip within -7..+30 days from initialDate
    val myShiftOptions = remember(allSchedules, myKip, initialDate) {
        val list = mutableListOf<Pair<LocalDate, ShiftType>>()
        var d = initialDate.minusDays(7)
        val end = initialDate.plusDays(30)
        while (!d.isAfter(end)) {
            val s = allSchedules[d.toString()]
            if (s != null) {
                for (t in ShiftType.entries) {
                    if (s.kipFor(t).equals(myKip, ignoreCase = true)) {
                        list.add(d to t)
                    }
                }
            }
            d = d.plusDays(1)
        }
        list
    }

    var selectedIdx by remember {
        val idx = myShiftOptions.indexOfFirst { !it.first.isBefore(initialDate) }.coerceAtLeast(0)
        mutableIntStateOf(idx)
    }
    val selectedTarget = myShiftOptions.getOrNull(selectedIdx)
    var learnedPatterns by remember { mutableStateOf(repo.getLearnedSwapPatterns()) }

    val suggestions = remember(selectedTarget, learnedPatterns, allSchedules, myKip) {
        if (selectedTarget == null) emptyList()
        else ShiftSwapEngine.findSwapSuggestions(
            schedules = allSchedules,
            targetDate = selectedTarget.first,
            targetShiftType = selectedTarget.second,
            myKip = myKip,
            learnedPatterns = learnedPatterns,
            maxDays = 30
        )
    }

    // Manual swap / Teach states
    var manualPayDate by remember { mutableStateOf((selectedTarget?.first ?: initialDate).plusDays(2).toString()) }
    var manualPayShift by remember { mutableStateOf(ShiftType.MORNING) }
    var patternName by remember { mutableStateOf("") }
    var teachStatus by remember { mutableStateOf<String?>(null) }

    val dateFmt = DateTimeFormatter.ofPattern("dd/MM")

    BouncyGlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Đổi ca thông minh (Kíp $myKip)",
                color = p.softWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // 3 compact mode pills
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Gợi ý", "Tự đổi", "Dạy app").forEachIndexed { idx, label ->
                    val active = mode == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) p.accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                if (active) p.accent else Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { mode = idx; teachStatus = null }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else p.mutedText,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Target shift selector
            if (selectedTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (selectedIdx > 0) selectedIdx-- },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = p.softWhite)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ca muốn nghỉ", color = p.mutedText, fontSize = 10.sp)
                        Text(
                            text = "${selectedTarget.second.displayName} • ${selectedTarget.first.format(dateFmt)}",
                            color = p.highlightYellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { if (selectedIdx < myShiftOptions.lastIndex) selectedIdx++ },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = p.softWhite)
                    }
                }
            } else {
                Text("Không tìm thấy ca trực nào của Kíp $myKip quanh ngày này.", color = p.mutedText, fontSize = 13.sp)
            }

            if (mode == 0 && selectedTarget != null) {
                Text(
                    text = "Phạm vi ±30 ngày • Ưu tiên 2 → 3 → 4 kíp, gần nhất & trong tháng",
                    color = p.mutedText,
                    fontSize = 11.sp
                )
                if (suggestions.isEmpty()) {
                    Text(
                        text = "Chưa tìm thấy phương án đổi ca hợp lệ trong ±30 ngày. Bạn có thể chuyển sang tab 'Dạy app' nếu tự tìm được cách đổi.",
                        color = p.mutedText,
                        fontSize = 13.sp
                    )
                } else {
                    suggestions.take(6).forEach { sug ->
                        val headerTag = buildString {
                            if (sug.isFromLearnedPattern) append("★ Đã học (${sug.patternLabel}) • ")
                            append("Đổi ${sug.participantCount} kíp • ")
                            append(if (sug.allSameMonth) "Trong tháng" else "Sang tháng")
                            append(" (${sug.maxDayDistance} ngày)")
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(
                                    1.dp,
                                    if (sug.isFromLearnedPattern) p.highlightYellow.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(headerTag, color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            sug.steps.forEach { st ->
                                val dShort = try { LocalDate.parse(st.dateStr).format(dateFmt) } catch (_: Exception) { st.dateStr }
                                Text(
                                    text = "• ${st.shiftType.shortName} $dShort: Kíp ${st.fromKip} → Kíp ${st.toKip}",
                                    color = p.softWhite,
                                    fontSize = 12.sp
                                )
                            }
                            val partnerKips = sug.steps.map { it.toKip }.filter { it != myKip }.distinct()
                            val partnerText = partnerKips.joinToString(" | ") { k ->
                                "Kíp $k: ${settings.kipMembers[k].orEmpty()}"
                            }
                            if (partnerText.isNotBlank()) {
                                Text(partnerText, color = p.mutedText, fontSize = 11.sp, maxLines = 2)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    val updated = ShiftSwapEngine.applySwap(allSchedules, sug.steps)
                                    onApplySwap(updated)
                                }) {
                                    Text("Áp dụng đổi ca", color = p.highlightYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else if ((mode == 1 || mode == 2) && selectedTarget != null) {
                Text(
                    text = if (mode == 1) "Nhập ngày & ca bạn sẽ trực trả để kiểm tra luật nghỉ 12h (2 ca):"
                    else "Nhập ca trả hợp lệ để lưu thành mẫu cho thuật toán học:",
                    color = p.mutedText,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = manualPayDate,
                    onValueChange = { manualPayDate = it.take(10) },
                    label = { Text("Ngày trực trả (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShiftType.entries.forEach { st ->
                        val active = manualPayShift == st
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) p.highlightYellow else Color.White.copy(alpha = 0.08f))
                                .clickable { manualPayShift = st }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = st.shortName,
                                color = if (active) Color(0xFF0F172A) else p.softWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val payDaySched = allSchedules[manualPayDate.trim()]
                val partnerKip = payDaySched?.kipFor(manualPayShift)
                val manualSteps = if (partnerKip != null && partnerKip != "-" && !partnerKip.equals(myKip, ignoreCase = true)) {
                    listOf(
                        SwapStep(selectedTarget.first.toString(), selectedTarget.second, myKip, partnerKip),
                        SwapStep(manualPayDate.trim(), manualPayShift, partnerKip, myKip)
                    )
                } else emptyList()

                val manualConflicts = remember(manualSteps, allSchedules) {
                    if (manualSteps.isEmpty()) emptyList()
                    else ShiftSwapEngine.validateSwap(allSchedules, manualSteps)
                }

                if (partnerKip == null || partnerKip == "-") {
                    Text("Ngày $manualPayDate chưa có dữ liệu lịch.", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                } else if (partnerKip.equals(myKip, ignoreCase = true)) {
                    Text("Ca này đã thuộc Kíp $myKip của bạn rồi.", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                } else if (manualConflicts.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("⚠️ Vi phạm luật nghỉ tối thiểu 12h (2 ca):", color = Color(0xFFFCA5A5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            manualConflicts.take(3).forEach { c ->
                                Text("• ${c.summary()}", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "✅ Hợp lệ! Đổi với Kíp $partnerKip (${settings.kipMembers[partnerKip].orEmpty()})",
                        color = Color(0xFF86EFAC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (mode == 2) {
                    OutlinedTextField(
                        value = patternName,
                        onValueChange = { patternName = it.take(30) },
                        label = { Text("Tên mẫu đổi ca (ví dụ: Đổi chéo 2 ngày)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (manualSteps.isNotEmpty() && manualConflicts.isEmpty()) {
                                val pat = ShiftSwapEngine.extractLearnedPattern(
                                    targetDate = selectedTarget.first,
                                    steps = manualSteps,
                                    myKip = myKip,
                                    name = patternName
                                )
                                repo.saveLearnedSwapPattern(pat)
                                learnedPatterns = repo.getLearnedSwapPatterns()
                                teachStatus = "Đã lưu mẫu '${pat.name}'. Thuật toán sẽ ưu tiên gợi ý mẫu này cho các lần đổi sau!"
                                patternName = ""
                            }
                        },
                        enabled = manualSteps.isNotEmpty() && manualConflicts.isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Lưu mẫu đào tạo thuật toán") }
                    teachStatus?.let {
                        Text(it, color = p.highlightYellow, fontSize = 12.sp)
                    }
                    if (learnedPatterns.isNotEmpty()) {
                        Text("Mẫu đã học (${learnedPatterns.size}): ${learnedPatterns.joinToString { it.name }}", color = p.mutedText, fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            if (manualSteps.isNotEmpty()) {
                                val updated = ShiftSwapEngine.applySwap(allSchedules, manualSteps)
                                onApplySwap(updated)
                            }
                        },
                        enabled = manualSteps.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (manualConflicts.isEmpty()) "Áp dụng đổi ca" else "Vẫn áp dụng (sẽ báo đỏ)")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Đóng", color = p.accent) }
            }
        }
    }
}

@Composable
private fun ImportTab(
    onShowInfo: (String, String) -> Unit,
    onPickExcelOrWord: (Int, Int) -> Unit,
    onPickImageOcr: (Int, Int) -> Unit,
    onGenerateCycle: (Int, Int, Int) -> Unit
) {
    val p = LocalGlassPalette.current
    var targetMonth by remember { mutableStateOf(YearMonth.now()) }
    var cycleOffset by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nhập lịch trực ca", color = p.softWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                InfoHintButton(
                    title = "Nhập lịch trực ca",
                    message = "Hỗ trợ đọc trực tiếp PDF (.pdf), Excel (.xlsx), Word (.docx), ảnh chụp bảng lịch và tự sinh theo chu kỳ 10 ngày.",
                    onShowInfo = onShowInfo
                )
            }
        }

        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Áp dụng cho tháng:", color = p.softWhite, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { targetMonth = targetMonth.minusMonths(1) }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = p.softWhite)
                        }
                        Text(
                            text = "%02d/%04d".format(targetMonth.monthValue, targetMonth.year),
                            color = p.highlightYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = { targetMonth = targetMonth.plusMonths(1) }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = p.softWhite)
                        }
                    }
                }
            }
        }

        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Nhập từ PDF, Excel hoặc Word", color = p.softWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        InfoHintButton(
                            title = "Nhập từ PDF / Excel / Word",
                            message = "Tự động đọc bảng có các dòng Ngày, Sáng, Chiều, Đêm giống mẫu Phụ lục lịch trực cơ quan bạn.",
                            onShowInfo = onShowInfo
                        )
                    }
                    Button(
                        onClick = { onPickExcelOrWord(targetMonth.year, targetMonth.monthValue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Chọn file PDF / Excel / Word / CSV")
                    }
                }
            }
        }

        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Nhập từ hình ảnh bảng lịch (OCR)", color = p.softWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        InfoHintButton(
                            title = "Nhập từ hình ảnh",
                            message = "Chọn ảnh chụp rõ nét bảng lịch trực. Sau khi nhận diện, bạn có thể vào tab Lịch tháng để rà soát lại nếu cần.",
                            onShowInfo = onShowInfo
                        )
                    }
                    OutlinedButton(
                        onClick = { onPickImageOcr(targetMonth.year, targetMonth.monthValue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Chọn ảnh lịch trực từ thư viện")
                    }
                }
            }
        }

        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tạo nhanh theo chu kỳ 5 kíp (10 ngày)", color = p.softWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        InfoHintButton(
                            title = "Tạo lịch theo chu kỳ",
                            message = "Tự sinh lịch đủ 30/31 ngày theo đúng quy luật xoay vòng A–E như tháng 11 & 12/2026.",
                            onShowInfo = onShowInfo
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Độ lệch ngày đầu (0–9):", color = p.softWhite, fontSize = 13.sp)
                        OutlinedButton(onClick = { cycleOffset = (cycleOffset + 9) % 10 }) { Text("-") }
                        Text("$cycleOffset", color = p.highlightYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        OutlinedButton(onClick = { cycleOffset = (cycleOffset + 1) % 10 }) { Text("+") }
                    }
                    Button(
                        onClick = { onGenerateCycle(targetMonth.year, targetMonth.monthValue, cycleOffset) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color(0xFF07101F))
                    ) {
                        Text("Tạo lịch tháng %02d/%04d".format(targetMonth.monthValue, targetMonth.year))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    settings: AppSettings,
    onShowInfo: (String, String) -> Unit,
    onSave: (AppSettings) -> Unit,
    onTestAlarm: () -> Unit,
    onTestWatchNotification: () -> Unit
) {
    val p = LocalGlassPalette.current
    var alarmEnabled by remember(settings) { mutableStateOf(settings.alarmEnabled) }
    var watchSyncEnabled by remember(settings) { mutableStateOf(settings.watchSyncEnabled) }
    var watchAutoCallEnabled by remember(settings) { mutableStateOf(settings.watchAutoCallEnabled) }
    var watchCallNumber by remember(settings) { mutableStateOf(settings.watchCallNumber) }
    var morningBefore by remember(settings) { mutableStateOf(settings.morningAlarmBeforeMin.toString()) }
    var afternoonBefore by remember(settings) { mutableStateOf(settings.afternoonAlarmBeforeMin.toString()) }
    var nightBefore by remember(settings) { mutableStateOf(settings.nightAlarmBeforeMin.toString()) }
    var membersMap by remember(settings) { mutableStateOf(settings.kipMembers.toMutableMap()) }
    var selectedTheme by remember(settings) { mutableStateOf(settings.resolvedTheme()) }
    var autoRandomTheme by remember(settings) { mutableStateOf(settings.autoRandomThemeEnabled) }

    var showThemePopup by remember { mutableStateOf(false) }
    var showAlarmPopup by remember { mutableStateOf(false) }
    var showWatchPopup by remember { mutableStateOf(false) }
    var showCrewPopup by remember { mutableStateOf(false) }

    fun buildUpdatedSettings(
        themeOverride: GlassThemeId = selectedTheme,
        autoRandomOverride: Boolean = autoRandomTheme
    ): AppSettings = settings.copy(
        themeId = themeOverride.name,
        autoRandomThemeEnabled = autoRandomOverride,
        alarmEnabled = alarmEnabled,
        watchSyncEnabled = watchSyncEnabled,
        watchAutoCallEnabled = watchAutoCallEnabled,
        watchCallNumber = watchCallNumber.trim().ifBlank { "0974844351" },
        morningAlarmBeforeMin = morningBefore.toIntOrNull() ?: 60,
        afternoonAlarmBeforeMin = afternoonBefore.toIntOrNull() ?: 60,
        nightAlarmBeforeMin = nightBefore.toIntOrNull() ?: 60,
        kipMembers = membersMap.toMap()
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cài đặt",
                    color = p.softWhite,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                InfoHintButton(
                    title = "Hướng dẫn Cài đặt",
                    message = "Chạm vào từng thẻ bên dưới để tùy chỉnh nhanh màu ánh phản chiếu, số phút báo thức, thử rung Huawei Watch hoặc sửa danh sách nhân sự.",
                    onShowInfo = onShowInfo
                )
            }
        }

        // 1. Compact Theme Card
        item {
            val tp = paletteFor(selectedTheme)
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemePopup = true },
                cornerRadius = 22.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Màu ánh phản chiếu", color = p.softWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(tp.heroStart, tp.accent)))
                            )
                            Text(if (autoRandomTheme) "Ngẫu nhiên (1 phút) ▾" else "${selectedTheme.label} ▾", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    InfoHintButton(
                        title = "Màu ánh phản chiếu",
                        message = "Chạm vào thẻ để mở bảng 6 màu ánh sáng phản chiếu trên các tấm kính trong suốt.",
                        onShowInfo = onShowInfo
                    )
                }
            }
        }

        // 2. Compact Alarm Card
        item {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAlarmPopup = true },
                cornerRadius = 22.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Báo thức Kíp ${settings.selectedKip}", color = p.softWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            InfoHintButton(
                                title = "Báo thức tự động",
                                message = "Chạm vào thẻ để đặt số phút báo trước từng ca và thử chuông. Chuông kêu có xác nhận 3 đợt (10 phút rồi 5 phút).",
                                onShowInfo = onShowInfo
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sáng ${settings.morningAlarmBeforeMin}p • Chiều ${settings.afternoonAlarmBeforeMin}p • Khuya ${settings.nightAlarmBeforeMin}p ▾",
                            color = p.mutedText,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = alarmEnabled,
                        onCheckedChange = {
                            alarmEnabled = it
                            onSave(buildUpdatedSettings())
                        }
                    )
                }
            }
        }

        // 3. Compact Huawei Watch GT 6 Pro Card
        item {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWatchPopup = true },
                cornerRadius = 22.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Huawei Watch GT 6 Pro", color = p.softWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            InfoHintButton(
                                title = "Liên kết Huawei Watch GT 6 Pro",
                                message = "Mở Huawei Sức khỏe → Thiết bị → chọn Huawei Watch GT 6 Pro → Thông báo → bật 'Trực Ca & Báo Thức'. Chạm vào thẻ để gửi thông báo thử.",
                                onShowInfo = onShowInfo
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (watchSyncEnabled) {
                                if (settings.watchAutoCallEnabled && settings.watchCallNumber.isNotBlank())
                                    "Gọi rung ${settings.watchCallNumber} • Chạm để chỉnh ▾"
                                else
                                    "Đẩy thông báo đồng hồ • Chạm để chỉnh ▾"
                            } else "Đang tắt đẩy đồng hồ ▾",
                            color = p.mutedText,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = watchSyncEnabled,
                        onCheckedChange = {
                            watchSyncEnabled = it
                            onSave(buildUpdatedSettings())
                        }
                    )
                }
            }
        }

        // 4. Compact Crew Card
        item {
            val currentCrew = membersMap[settings.selectedKip].orEmpty()
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCrewPopup = true },
                cornerRadius = 22.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nhân sự mặc định (TC TĐĐK)", color = p.softWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kíp ${settings.selectedKip}: $currentCrew",
                            color = p.mutedText,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoHintButton(
                        title = "Nhân sự mặc định",
                        message = "Chạm vào thẻ để sửa danh sách thành viên 5 Kíp (A–E). Nếu cần đổi riêng cho 1 ngày cụ thể, hãy chạm vào ngày đó trong tab Lịch tháng.",
                        onShowInfo = onShowInfo
                    )
                }
            }
        }
    }

    // Pop-up 1: Theme Picker (BouncyGlassDialog)
    if (showThemePopup) {
        BouncyGlassDialog(onDismissRequest = { showThemePopup = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Chọn màu ánh phản chiếu", color = p.softWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (autoRandomTheme) p.accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.07f))
                        .border(
                            width = if (autoRandomTheme) 1.5.dp else 1.dp,
                            color = if (autoRandomTheme) p.accent else Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            autoRandomTheme = !autoRandomTheme
                            onSave(buildUpdatedSettings(autoRandomOverride = autoRandomTheme))
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tự động ngẫu nhiên (1 phút)",
                        color = p.softWhite,
                        fontSize = 13.sp,
                        fontWeight = if (autoRandomTheme) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoRandomTheme,
                        onCheckedChange = {
                            autoRandomTheme = it
                            onSave(buildUpdatedSettings(autoRandomOverride = it))
                        }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassThemeId.entries.chunked(2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { t ->
                                val tp = paletteFor(t)
                                val active = !autoRandomTheme && t == selectedTheme
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (active) tp.heroStart.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.07f))
                                        .border(
                                            width = if (active) 1.5.dp else 1.dp,
                                            color = if (active) tp.accent else Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            selectedTheme = t
                                            autoRandomTheme = false
                                            onSave(buildUpdatedSettings(themeOverride = t, autoRandomOverride = false))
                                            showThemePopup = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(tp.heroStart, tp.accent))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (active) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(t.label, color = p.softWhite, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showThemePopup = false }) { Text("Đóng", color = p.accent) }
                }
            }
        }
    }

    // Pop-up 2: Alarm Lead Time & Test (BouncyGlassDialog)
    if (showAlarmPopup) {
        BouncyGlassDialog(onDismissRequest = { showAlarmPopup = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cài đặt Báo thức Kíp ${settings.selectedKip}", color = p.softWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = morningBefore,
                    onValueChange = { morningBefore = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("Ca Sáng (07:00) — báo trước (phút)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = afternoonBefore,
                    onValueChange = { afternoonBefore = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("Ca Chiều (15:00) — báo trước (phút)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nightBefore,
                    onValueChange = { nightBefore = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("Ca Khuya (23:00) — báo trước (phút)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSave(buildUpdatedSettings())
                            showAlarmPopup = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Lưu") }
                    OutlinedButton(
                        onClick = {
                            showAlarmPopup = false
                            onTestAlarm()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Thử chuông") }
                }
            }
        }
    }

    // Pop-up 3: Huawei Watch Test, Auto-Call Phone Number & Guide (BouncyGlassDialog)
    if (showWatchPopup) {
        BouncyGlassDialog(onDismissRequest = { showWatchPopup = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Huawei Watch GT 6 Pro", color = p.softWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tự động gọi điện để rung liên hồi",
                        color = p.softWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = watchAutoCallEnabled,
                        onCheckedChange = { watchAutoCallEnabled = it }
                    )
                }
                OutlinedTextField(
                    value = watchCallNumber,
                    onValueChange = { watchCallNumber = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15) },
                    label = { Text("Số điện thoại gọi tới đồng hồ") },
                    placeholder = { Text("0974844351") },
                    singleLine = true,
                    enabled = watchAutoCallEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSave(buildUpdatedSettings())
                            showWatchPopup = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Lưu") }
                    OutlinedButton(
                        onClick = {
                            onSave(buildUpdatedSettings())
                            showWatchPopup = false
                            onTestWatchNotification()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Thử đồng hồ") }
                }
            }
        }
    }

    // Pop-up 4: Default Crew Editor (BouncyGlassDialog)
    if (showCrewPopup) {
        BouncyGlassDialog(onDismissRequest = { showCrewPopup = false }) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Nhân sự mặc định (TC TĐĐK)", color = p.softWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showCrewPopup = false }) { Text("Hủy", color = p.mutedText) }
                    TextButton(onClick = {
                        onSave(buildUpdatedSettings())
                        showCrewPopup = false
                    }) { Text("Lưu danh sách", color = p.accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun BouncyGlassDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val p = LocalGlassPalette.current
    Dialog(onDismissRequest = onDismissRequest) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        SideEffect {
            dialogWindowProvider?.window?.let { win ->
                win.setDimAmount(0.32f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    win.attributes = win.attributes.apply {
                        blurBehindRadius = 48
                    }
                }
            }
        }

        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.72f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "popup_scale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "popup_alpha"
        )

        val frostedTint = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0E131F).copy(alpha = 0.82f),
                Color(0xFF0A0E17).copy(alpha = 0.88f)
            )
        )

        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(RoundedCornerShape(26.dp))
                .background(frostedTint)
                .background(p.reflectionPrimary.copy(alpha = 0.08f)),
            cornerRadius = 26.dp,
            alphaBoost = 0.14f
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoHintButton(
    title: String,
    message: String,
    onShowInfo: (String, String) -> Unit
) {
    val p = LocalGlassPalette.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, p.accent.copy(alpha = 0.55f), CircleShape)
            .clickable { onShowInfo(title, message) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "i",
            color = p.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
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
