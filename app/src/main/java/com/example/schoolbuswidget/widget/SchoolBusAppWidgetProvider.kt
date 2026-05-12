package com.example.schoolbuswidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.data.TimetableDataStoreRepository
import com.example.schoolbuswidget.data.WidgetPreferenceRepository
import com.example.schoolbuswidget.data.holiday.HolidayCalendarRepository
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.EffectiveDayTypeResolver
import com.example.schoolbuswidget.domain.NextDepartureCalculator
import com.example.schoolbuswidget.domain.resolveServiceDayTypeForMode
import com.example.schoolbuswidget.ui.DayTypeLabels
import com.example.schoolbuswidget.util.AppLog
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class SchoolBusAppWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ensureAlarmForWidgets(context)
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
        ensureAlarmForWidgets(context)
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        runBlocking {
            val prefs = WidgetPreferenceRepository(context)
            when (action) {
                ACTION_TOGGLE_LOCATION -> if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    prefs.toggleLocation(widgetId)
                }

                ACTION_TOGGLE_DAY_TYPE -> if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    prefs.toggleDayType(widgetId)
                }
            }
        }

        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            ensureAlarmForWidgets(context)
        }

        if (
            action == ACTION_MANUAL_REFRESH ||
            action == ACTION_REFRESH_ALL ||
            action == ACTION_MINUTE_REFRESH ||
            action == ACTION_TOGGLE_LOCATION ||
            action == ACTION_TOGGLE_DAY_TYPE ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            refreshAllWidgets(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        runBlocking {
            val repository = WidgetPreferenceRepository(context)
            appWidgetIds.forEach { repository.clear(it) }
        }
        ensureAlarmForWidgets(context)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_school_bus).apply {
            setOnClickPendingIntent(R.id.buttonRefresh, manualRefreshPendingIntent(context, appWidgetId))
            setOnClickPendingIntent(R.id.buttonLocation, toggleLocationPendingIntent(context, appWidgetId))
            setOnClickPendingIntent(R.id.buttonDayType, toggleDayTypePendingIntent(context, appWidgetId))
        }

        runBlocking {
            try {
                val now = LocalDateTime.now()
                val preferences = WidgetPreferenceRepository(context)
                val northSelected = preferences.getLocationIndex(appWidgetId) == 0
                val selectedLocation = if (northSelected) {
                    CampusLocation.NORTH
                } else {
                    CampusLocation.SOUTH
                }
                val mode = preferences.getDayTypeModeForWidget(appWidgetId)
                val holidayRepository = HolidayCalendarRepository(context)
                val effectiveResolver = EffectiveDayTypeResolver(holidayRepository)
                val dayResolution = resolveServiceDayTypeForMode(mode, now.toLocalDate(), effectiveResolver)

                val repository = TimetableDataStoreRepository(context)
                val departures = repository.getDepartures(selectedLocation, dayResolution.dayType)
                val result = NextDepartureCalculator().calculate(now, departures)

                views.setTextViewText(
                    R.id.textSelection,
                    DayTypeLabels.selectionSummary(
                        context,
                        northSelected,
                        mode,
                        dayResolution.dayType,
                    ),
                )

                if (result == null) {
                    views.setTextViewText(R.id.textNextDeparture, context.getString(R.string.main_time_placeholder))
                    views.setTextViewText(R.id.textMinutesLeft, context.getString(R.string.widget_no_departure))
                } else {
                    views.setTextViewText(
                        R.id.textNextDeparture,
                        result.departureAt.toLocalTime().toString(),
                    )
                    views.setTextViewText(
                        R.id.textMinutesLeft,
                        context.getString(R.string.widget_minutes_left, result.minutesLeft),
                    )
                }
            } catch (e: Exception) {
                AppLog.e("Widget update failed for id=$appWidgetId", e)
                views.setTextViewText(R.id.textNextDeparture, context.getString(R.string.widget_error_generic))
                views.setTextViewText(R.id.textMinutesLeft, "")
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun refreshAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(context, SchoolBusAppWidgetProvider::class.java),
        )
        onUpdate(context, manager, widgetIds)
    }

    private fun ensureAlarmForWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(context, SchoolBusAppWidgetProvider::class.java),
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (widgetIds.isEmpty()) {
            alarmManager.cancel(minuteRefreshPendingIntent(context))
            return
        }
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + REFRESH_INTERVAL_MS,
            REFRESH_INTERVAL_MS,
            minuteRefreshPendingIntent(context),
        )
    }

    private fun cancelMinuteRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(minuteRefreshPendingIntent(context))
    }

    private fun manualRefreshPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, SchoolBusAppWidgetProvider::class.java).apply {
            action = ACTION_MANUAL_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_MANUAL_REFRESH + appWidgetId,
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

    private fun toggleLocationPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, SchoolBusAppWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_LOCATION
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE_LOCATION + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun toggleDayTypePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, SchoolBusAppWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_DAY_TYPE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE_DAY_TYPE + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REFRESH_ALL = "com.example.schoolbuswidget.action.REFRESH_ALL"
        private const val REFRESH_INTERVAL_MS = 60_000L
        private const val REQUEST_MANUAL_REFRESH = 1001
        private const val REQUEST_MINUTE_REFRESH = 1002
        private const val REQUEST_TOGGLE_LOCATION = 2001
        private const val REQUEST_TOGGLE_DAY_TYPE = 2002
        private const val ACTION_MANUAL_REFRESH = "com.example.schoolbuswidget.action.MANUAL_REFRESH"
        private const val ACTION_MINUTE_REFRESH = "com.example.schoolbuswidget.action.MINUTE_REFRESH"
        private const val ACTION_TOGGLE_LOCATION = "com.example.schoolbuswidget.action.TOGGLE_LOCATION"
        private const val ACTION_TOGGLE_DAY_TYPE = "com.example.schoolbuswidget.action.TOGGLE_DAY_TYPE"
    }
}
