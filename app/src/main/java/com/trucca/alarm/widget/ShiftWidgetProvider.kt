package com.trucca.alarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.trucca.alarm.MainActivity
import com.trucca.alarm.R
import com.trucca.alarm.data.ScheduleRepository

class ShiftWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.trucca.alarm.ACTION_REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ShiftWidgetProvider::class.java))
            for (id in ids) {
                updateSingleWidget(context, manager, id)
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repo = ScheduleRepository(context)
            val settings = repo.getSettings()
            val active = repo.getCurrentActiveShift()

            val views = RemoteViews(context.packageName, R.layout.widget_shift_card)

            if (active != null) {
                val range = settings.shiftTimeConfig.formattedRange(active.shiftType)
                val rem = ScheduleRepository.formatCountdown(active.remainingSeconds)
                views.setTextViewText(R.id.widget_status_label, "ĐANG TRỰC")
                views.setTextViewText(
                    R.id.widget_title,
                    "Kíp ${active.kip} • ${active.shiftType.displayName}"
                )
                views.setTextViewText(R.id.widget_time_countdown, "$range · còn $rem")
                views.setTextViewText(
                    R.id.widget_members,
                    active.membersText.ifBlank { "Kíp ${active.kip}" }
                )
            } else {
                views.setTextViewText(R.id.widget_status_label, "LỊCH TRỰC CA")
                views.setTextViewText(R.id.widget_title, "Chưa có lịch hôm nay")
                views.setTextViewText(R.id.widget_time_countdown, "Mở ứng dụng để nạp lịch tháng này")
                views.setTextViewText(R.id.widget_members, "Kíp của tôi: Kíp ${settings.selectedKip}")
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
