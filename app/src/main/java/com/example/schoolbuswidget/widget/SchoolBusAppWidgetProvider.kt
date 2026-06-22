package com.example.schoolbuswidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.schoolbuswidget.MainActivity
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.data.ScenarioRepository
import com.example.schoolbuswidget.data.ScheduleRepository
import com.example.schoolbuswidget.data.TimetableDataStoreRepository
import com.example.schoolbuswidget.data.WidgetPreferenceRepository
import com.example.schoolbuswidget.data.holiday.HolidayCalendarRepository
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.EffectiveDayTypeResolver
import com.example.schoolbuswidget.domain.NextDepartureCalculator
import com.example.schoolbuswidget.domain.Scenario
import com.example.schoolbuswidget.domain.ScenarioTemplate
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.domain.resolveServiceDayTypeForMode
import com.example.schoolbuswidget.ui.DayTypeLabels
import com.example.schoolbuswidget.ui.ScheduleLabels
import com.example.schoolbuswidget.ui.scenario.ScenarioListActivity
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
        runBlocking {
            val preferences = WidgetPreferenceRepository(context)
            val scenarioRepository = ScenarioRepository(context)
            val scenarioId = preferences.getScenarioId(appWidgetId)
            val scenario = scenarioRepository.getScenario(scenarioId)

            val views = RemoteViews(context.packageName, R.layout.widget_school_bus)
            views.setOnClickPendingIntent(
                R.id.widget_open_app_area,
                openAppPendingIntent(context, appWidgetId, scenario?.id),
            )
            views.setOnClickPendingIntent(R.id.buttonRefresh, manualRefreshPendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.buttonLocation, toggleLocationPendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.buttonDayType, toggleDayTypePendingIntent(context, appWidgetId))

            if (scenario == null) {
                views.setTextViewText(R.id.textTitle, context.getString(R.string.widget_scenario_missing))
                views.setViewVisibility(R.id.textSelection, View.GONE)
                views.setTextViewText(R.id.textNextDeparture, context.getString(R.string.main_time_placeholder))
                views.setTextViewText(R.id.textMinutesLeft, context.getString(R.string.widget_error_generic))
                views.setViewVisibility(R.id.textFollowingDeparture, View.GONE)
                views.setViewVisibility(R.id.buttonLocation, View.GONE)
                views.setViewVisibility(R.id.buttonDayType, View.GONE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return@runBlocking
            }

            views.setTextViewText(R.id.textTitle, scenario.name)
            applyTemplateUi(views, scenario.template)

            try {
                val now = LocalDateTime.now()
                val northSelected = preferences.getLocationIndex(appWidgetId) == 0
                val selectedLocation = if (northSelected) CampusLocation.NORTH else CampusLocation.SOUTH
                val mode = preferences.getDayTypeModeForWidget(appWidgetId)
                val holidayRepository = HolidayCalendarRepository(context)
                val effectiveResolver = EffectiveDayTypeResolver(holidayRepository)
                val dayResolution = when (scenario.template) {
                    ScenarioTemplate.SIMPLE -> null
                    ScenarioTemplate.MULTI_SCHEDULE -> resolveServiceDayTypeForMode(
                        mode,
                        now.toLocalDate(),
                        effectiveResolver,
                    )
                    else -> resolveServiceDayTypeForMode(mode, now.toLocalDate(), effectiveResolver)
                }
                val effectiveDayType = dayResolution?.dayType ?: ServiceDayType.WORKDAY

                val repository = TimetableDataStoreRepository(context)
                val (departures, activeNames) = when (scenario.template) {
                    ScenarioTemplate.MULTI_SCHEDULE -> {
                        val result = ScheduleRepository(context).getEffectiveDepartures(
                            scenario = scenario,
                            date = now.toLocalDate(),
                            resolvedDayType = effectiveDayType,
                        )
                        result.first to result.second.map { it.name }
                    }
                    else -> {
                        repository.getScenarioDepartures(
                            scenario = scenario,
                            location = selectedLocation,
                            dayType = effectiveDayType,
                        ) to emptyList<String>()
                    }
                }
                val upcoming = NextDepartureCalculator().calculateUpcoming(now, departures)

                when (scenario.template) {
                    ScenarioTemplate.SIMPLE -> views.setViewVisibility(R.id.textSelection, View.GONE)
                    ScenarioTemplate.MULTI_SCHEDULE -> {
                        views.setViewVisibility(R.id.textSelection, View.VISIBLE)
                        views.setTextViewText(
                            R.id.textSelection,
                            DayTypeLabels.widgetCompactDayType(
                                context,
                                mode,
                                effectiveDayType,
                            ),
                        )
                    }
                    ScenarioTemplate.MULTI_PROFILE -> views.setTextViewText(
                        R.id.textSelection,
                        DayTypeLabels.compactSelection(
                            context,
                            northSelected,
                            mode,
                            effectiveDayType,
                        ),
                    )
                }

                if (upcoming == null) {
                    views.setTextViewText(R.id.textNextDeparture, context.getString(R.string.main_time_placeholder))
                    views.setTextViewText(R.id.textMinutesLeft, context.getString(R.string.widget_no_departure))
                    views.setViewVisibility(R.id.textFollowingDeparture, View.GONE)
                } else {
                    val result = upcoming.next
                    views.setTextViewText(
                        R.id.textNextDeparture,
                        result.departureAt.toLocalTime().toString(),
                    )
                    views.setTextViewText(
                        R.id.textMinutesLeft,
                        context.getString(R.string.widget_minutes_left, result.minutesLeft),
                    )
                    val following = upcoming.following
                    if (following == null) {
                        views.setViewVisibility(R.id.textFollowingDeparture, View.GONE)
                    } else {
                        views.setViewVisibility(R.id.textFollowingDeparture, View.VISIBLE)
                        views.setTextViewText(
                            R.id.textFollowingDeparture,
                            context.getString(
                                R.string.widget_following_departure,
                                following.departureAt.toLocalTime().toString(),
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                AppLog.e("Widget update failed for id=$appWidgetId", e)
                views.setTextViewText(R.id.textNextDeparture, context.getString(R.string.widget_error_generic))
                views.setTextViewText(R.id.textMinutesLeft, "")
                views.setViewVisibility(R.id.textFollowingDeparture, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun applyTemplateUi(views: RemoteViews, template: ScenarioTemplate) {
        when (template) {
            ScenarioTemplate.SIMPLE -> {
                views.setViewVisibility(R.id.buttonLocation, View.GONE)
                views.setViewVisibility(R.id.buttonDayType, View.GONE)
            }
            ScenarioTemplate.MULTI_SCHEDULE -> {
                views.setViewVisibility(R.id.buttonLocation, View.GONE)
                views.setViewVisibility(R.id.buttonDayType, View.VISIBLE)
            }
            ScenarioTemplate.MULTI_PROFILE -> {
                views.setViewVisibility(R.id.buttonLocation, View.VISIBLE)
                views.setViewVisibility(R.id.buttonDayType, View.VISIBLE)
            }
        }
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

    private fun openAppPendingIntent(
        context: Context,
        appWidgetId: Int,
        scenarioId: String?,
    ): PendingIntent {
        val intent = if (scenarioId.isNullOrBlank()) {
            Intent(context, ScenarioListActivity::class.java)
        } else {
            MainActivity.intent(context, scenarioId)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
        private const val REQUEST_OPEN_APP = 3000
        private const val ACTION_MANUAL_REFRESH = "com.example.schoolbuswidget.action.MANUAL_REFRESH"
        private const val ACTION_MINUTE_REFRESH = "com.example.schoolbuswidget.action.MINUTE_REFRESH"
        private const val ACTION_TOGGLE_LOCATION = "com.example.schoolbuswidget.action.TOGGLE_LOCATION"
        private const val ACTION_TOGGLE_DAY_TYPE = "com.example.schoolbuswidget.action.TOGGLE_DAY_TYPE"
    }
}
