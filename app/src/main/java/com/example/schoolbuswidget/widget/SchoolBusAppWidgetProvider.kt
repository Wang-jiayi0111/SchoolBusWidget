package com.example.schoolbuswidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.data.TimetableDataStoreRepository
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DayTypeResolver
import com.example.schoolbuswidget.domain.NextDepartureCalculator
import com.example.schoolbuswidget.domain.ServiceDayType
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class SchoolBusAppWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleMinuteRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMinuteRefresh(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (
            intent.action == ACTION_MANUAL_REFRESH ||
            intent.action == ACTION_MINUTE_REFRESH ||
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_DATE_CHANGED
        ) {
            scheduleMinuteRefresh(context)
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                android.content.ComponentName(context, SchoolBusAppWidgetProvider::class.java),
            )
            onUpdate(context, manager, widgetIds)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_school_bus).apply {
            setOnClickPendingIntent(R.id.buttonRefresh, manualRefreshPendingIntent(context))
        }

        runBlocking {
            val now = LocalDateTime.now()
            val dayType = DayTypeResolver().resolveByWeekRule(now.toLocalDate())
            val repository = TimetableDataStoreRepository(context)
            val departures = repository.getDepartures(CampusLocation.NORTH, dayType)
            val result = NextDepartureCalculator().calculate(now, departures)

            views.setTextViewText(
                R.id.textSelection,
                context.getString(R.string.widget_selection_north, dayTypeLabel(dayType)),
            )

            if (result == null) {
                views.setTextViewText(R.id.textNextDeparture, context.getString(R.string.widget_no_departure))
                views.setTextViewText(R.id.textMinutesLeft, context.getString(R.string.widget_minutes_unavailable))
            } else {
                views.setTextViewText(
                    R.id.textNextDeparture,
                    context.getString(R.string.widget_next_departure, result.departureAt.toLocalTime().toString()),
                )
                views.setTextViewText(
                    R.id.textMinutesLeft,
                    context.getString(R.string.widget_minutes_left, result.minutesLeft),
                )
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun dayTypeLabel(dayType: ServiceDayType): String {
        return if (dayType == ServiceDayType.WORKDAY) "工作日" else "假期"
    }

    private fun scheduleMinuteRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + 60_000L,
            60_000L,
            minuteRefreshPendingIntent(context),
        )
    }

    private fun cancelMinuteRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(minuteRefreshPendingIntent(context))
    }

    private fun manualRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SchoolBusAppWidgetProvider::class.java).apply {
            action = ACTION_MANUAL_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_MANUAL_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun minuteRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SchoolBusAppWidgetProvider::class.java).apply {
            action = ACTION_MINUTE_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_MINUTE_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REQUEST_MANUAL_REFRESH = 1001
        private const val REQUEST_MINUTE_REFRESH = 1002
        private const val ACTION_MANUAL_REFRESH = "com.example.schoolbuswidget.action.MANUAL_REFRESH"
        private const val ACTION_MINUTE_REFRESH = "com.example.schoolbuswidget.action.MINUTE_REFRESH"
    }
}
