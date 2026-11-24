package com.trucca.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trucca.alarm.alarm.ShiftAlarmScheduler
import com.trucca.alarm.alarm.WatchNotifier
import com.trucca.alarm.data.ScheduleRepository
import com.trucca.alarm.parser.OfficeScheduleParser
import com.trucca.alarm.ui.MainScreen
import com.trucca.alarm.ui.theme.TrucCaTheme
import com.trucca.alarm.widget.ShiftWidgetProvider

class MainActivity : ComponentActivity() {

    private lateinit var repo: ScheduleRepository
    private var statusMessage by mutableStateOf<String?>(null)
    private var pendingYear = 2026
    private var pendingMonth = 11

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val documentPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                handleDocumentImport(uri)
            }
        }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                handleImageOcrImport(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ScheduleRepository(this)

        requestNeededPermissions()
        WatchNotifier.ensureChannel(this)
        ShiftAlarmScheduler.rescheduleAll(this)
        ShiftWidgetProvider.updateAllWidgets(this)

        setContent {
            TrucCaTheme {
                MainScreen(
                    repo = repo,
                    statusMessage = statusMessage,
                    onClearStatus = { statusMessage = null },
                    onSettingsChanged = { updated ->
                        repo.saveSettings(updated)
                        val count = ShiftAlarmScheduler.rescheduleAll(this)
                        ShiftWidgetProvider.updateAllWidgets(this)
                        statusMessage = if (updated.alarmEnabled) {
                            "Đã lưu cài đặt Kíp ${updated.selectedKip} và lên lịch $count báo thức sắp tới."
                        } else {
                            "Đã lưu cài đặt Kíp ${updated.selectedKip} (báo thức đang tắt)."
                        }
                    },
                    onSchedulesUpdated = {
                        val count = ShiftAlarmScheduler.rescheduleAll(this)
                        ShiftWidgetProvider.updateAllWidgets(this)
                        statusMessage = "Đã cập nhật lịch trực và đồng bộ $count báo thức."
                    },
                    onPickExcelOrWord = { year, month ->
                        pendingYear = year
                        pendingMonth = month
                        documentPickerLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/csv",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    onPickImageOcr = { year, month ->
                        pendingYear = year
                        pendingMonth = month
                        imagePickerLauncher.launch("image/*")
                    },
                    onTestAlarm = {
                        ShiftAlarmScheduler.scheduleTestAlarm(this, 5)
                        statusMessage = "Chuông báo thức thử nghiệm sẽ kêu sau 5 giây (kèm gửi thông báo sang đồng hồ nếu đã bật)."
                    },
                    onTestWatchNotification = {
                        val kip = repo.getSettings().selectedKip
                        WatchNotifier.sendTestWatchNotification(this, kip)
                        statusMessage = "Đã gửi thông báo thử sang Huawei Watch GT 6 Pro qua Huawei Sức khỏe."
                    }
                )
            }
        }
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleDocumentImport(uri: Uri) {
        val fileName = queryFileName(uri)
        val result = OfficeScheduleParser.parseDocumentUri(
            context = this,
            uri = uri,
            fileName = fileName,
            targetYear = pendingYear,
            targetMonth = pendingMonth
        )
        if (result.schedules.isNotEmpty()) {
            repo.upsertSchedules(result.schedules)
            val count = ShiftAlarmScheduler.rescheduleAll(this)
            ShiftWidgetProvider.updateAllWidgets(this)
            statusMessage = "${result.summary} Đã tự động lên lịch $count báo thức."
        } else {
            statusMessage = result.summary
        }
    }

    private fun handleImageOcrImport(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = OfficeScheduleParser.parseOcrText(
                        rawText = visionText.text,
                        targetYear = pendingYear,
                        targetMonth = pendingMonth
                    )
                    if (result.schedules.isNotEmpty()) {
                        repo.upsertSchedules(result.schedules)
                        val count = ShiftAlarmScheduler.rescheduleAll(this)
                        ShiftWidgetProvider.updateAllWidgets(this)
                        statusMessage = "${result.summary} Hãy mở tab Lịch tháng để rà soát lại các ô OCR và sửa nếu cần. Đã đặt $count báo thức."
                    } else {
                        statusMessage = "Ảnh chưa đủ rõ để tách tự động 3 hàng Sáng/Chiều/Đêm. Bạn nên dùng file Excel (.xlsx), Word (.docx) hoặc nút 'Tạo nhanh theo chu kỳ 10 ngày'."
                    }
                }
                .addOnFailureListener {
                    statusMessage = "Không thể nhận diện chữ từ ảnh đã chọn."
                }
        } catch (e: Exception) {
            statusMessage = "Lỗi đọc ảnh: ${e.localizedMessage}"
        }
    }

    private fun queryFileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "schedule.xlsx"
            }
        }
        return uri.lastPathSegment ?: "schedule.xlsx"
    }
}
