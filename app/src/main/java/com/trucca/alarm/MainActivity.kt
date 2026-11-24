package com.trucca.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.trucca.alarm.model.AppSettings
import com.trucca.alarm.parser.OfficeScheduleParser
import com.trucca.alarm.ui.MainScreen
import com.trucca.alarm.ui.theme.TrucCaTheme
import com.trucca.alarm.widget.ShiftWidgetProvider

class MainActivity : ComponentActivity() {

    private lateinit var repo: ScheduleRepository
    private var appSettings by mutableStateOf(AppSettings())
    private var statusMessage by mutableStateOf<String?>(null)
    private var pendingYear = 2026
    private var pendingMonth = 11

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val documentPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) handleDocumentImport(uri)
        }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) handleImageOcrImport(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ScheduleRepository(this)
        appSettings = repo.getSettings()

        requestNeededPermissions()
        WatchNotifier.ensureChannel(this)
        ShiftAlarmScheduler.rescheduleAll(this)
        ShiftWidgetProvider.updateAllWidgets(this)

        setContent {
            TrucCaTheme(themeId = appSettings.resolvedTheme()) {
                MainScreen(
                    repo = repo,
                    settings = appSettings,
                    statusMessage = statusMessage,
                    onClearStatus = { statusMessage = null },
                    onSettingsChanged = { updated ->
                        val onlyThemeChanged = updated.copy(themeId = appSettings.themeId) == appSettings
                        appSettings = updated
                        repo.saveSettings(updated)
                        if (!onlyThemeChanged) {
                            val count = ShiftAlarmScheduler.rescheduleAll(this)
                            ShiftWidgetProvider.updateAllWidgets(this)
                            statusMessage = if (updated.alarmEnabled) {
                                "Đã lưu cài đặt Kíp ${updated.selectedKip} và lên lịch $count báo thức sắp tới."
                            } else {
                                "Đã lưu cài đặt Kíp ${updated.selectedKip} (báo thức đang tắt)."
                            }
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
                                "application/pdf",
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
                        val kip = appSettings.selectedKip
                        val members = appSettings.kipMembers[kip].orEmpty()
                        WatchNotifier.sendTestWatchNotification(this, kip, members)
                        statusMessage = "Đã gửi thông báo thử (kèm tên thành viên Kíp $kip) sang Huawei Watch GT 6 Pro."
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
        val mimeType = contentResolver.getType(uri).orEmpty().lowercase()
        if (fileName.lowercase().endsWith(".pdf") || mimeType == "application/pdf") {
            handlePdfImport(uri)
            return
        }
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

    private fun handlePdfImport(uri: Uri) {
        statusMessage = "Đang đọc lịch trực từ file PDF..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    withContext(Dispatchers.Main) { statusMessage = "Không mở được file PDF đã chọn." }
                    return@launch
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                var bestSchedules = emptyList<com.trucca.alarm.model.DaySchedule>()
                var matchedPage = -1

                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        // Scan from last page first (Phụ lục 2 lịch trực usually at end of PDF)
                        for (pageIndex in (renderer.pageCount - 1) downTo 0) {
                            val bitmap = renderer.openPage(pageIndex).use { page ->
                                val scale = 2
                                val w = (page.width * scale).coerceAtMost(2400)
                                val h = (page.height * scale).coerceAtMost(2400)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                            try {
                                val visionText = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                                val parsed = OfficeScheduleParser.parseOcrText(
                                    rawText = visionText.text,
                                    targetYear = pendingYear,
                                    targetMonth = pendingMonth
                                )
                                if (parsed.schedules.size > bestSchedules.size) {
                                    bestSchedules = parsed.schedules
                                    matchedPage = pageIndex + 1
                                }
                                if (bestSchedules.size >= 25) break
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (bestSchedules.isNotEmpty()) {
                        repo.upsertSchedules(bestSchedules)
                        val count = ShiftAlarmScheduler.rescheduleAll(this@MainActivity)
                        ShiftWidgetProvider.updateAllWidgets(this@MainActivity)
                        statusMessage = "Đã đọc được ${bestSchedules.size} ngày trực từ trang $matchedPage file PDF cho tháng %02d/%04d và đồng bộ $count báo thức.".format(pendingMonth, pendingYear)
                    } else {
                        statusMessage = "Không tách được bảng 3 hàng Sáng/Chiều/Đêm trong file PDF này. Bạn có thể thử nhập bằng file Excel (.xlsx) hoặc nút Tạo nhanh theo chu kỳ 10 ngày."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "Lỗi đọc file PDF: ${e.localizedMessage}"
                }
            }
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
                        statusMessage = "${result.summary} Hãy mở tab Lịch tháng để rà soát lại. Đã đặt $count báo thức."
                    } else {
                        statusMessage = "Ảnh chưa đủ rõ để tách 3 hàng Sáng/Chiều/Đêm. Bạn nên dùng file Excel (.xlsx), Word (.docx) hoặc nút 'Tạo nhanh theo chu kỳ 10 ngày'."
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
