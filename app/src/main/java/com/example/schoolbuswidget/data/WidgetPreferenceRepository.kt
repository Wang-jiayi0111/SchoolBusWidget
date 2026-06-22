package com.example.schoolbuswidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.schoolbuswidget.domain.ScenarioDefaults
import kotlinx.coroutines.flow.first

class WidgetPreferenceRepository(
    private val context: Context,
) {
    suspend fun getGlobalLocationIndex(): Int {
        val prefs = context.timetableDataStore.data.first()
        return prefs[globalLocationKey] ?: 0
    }

    suspend fun getDayTypeMode(): Int {
        val prefs = context.timetableDataStore.data.first()
        val stored = prefs[globalDayTypeModeKey]
        if (stored != null) return stored.coerceIn(DAY_TYPE_MODE_MIN, DAY_TYPE_MODE_MAX)

        val legacy = prefs[legacyGlobalDayTypeKey]
        val migrated = when (legacy) {
            0 -> DAY_TYPE_MODE_MANUAL_WORKDAY
            1 -> DAY_TYPE_MODE_MANUAL_HOLIDAY
            else -> DAY_TYPE_MODE_AUTO
        }
        context.timetableDataStore.edit { data ->
            data[globalDayTypeModeKey] = migrated
        }
        return migrated
    }

    suspend fun cycleDayTypeMode(): Int {
        val current = getDayTypeMode()
        val next = when (current) {
            DAY_TYPE_MODE_AUTO -> DAY_TYPE_MODE_MANUAL_WORKDAY
            DAY_TYPE_MODE_MANUAL_WORKDAY -> DAY_TYPE_MODE_MANUAL_HOLIDAY
            else -> DAY_TYPE_MODE_AUTO
        }
        context.timetableDataStore.edit { data ->
            data[globalDayTypeModeKey] = next
        }
        return next
    }

    suspend fun setGlobalLocationAndDayTypeMode(locationIndex: Int, dayTypeMode: Int) {
        context.timetableDataStore.edit { data ->
            data[globalLocationKey] = locationIndex
            data[globalDayTypeModeKey] = dayTypeMode.coerceIn(DAY_TYPE_MODE_MIN, DAY_TYPE_MODE_MAX)
        }
    }

    suspend fun toggleGlobalLocation(): Int {
        val next = if (getGlobalLocationIndex() == 0) 1 else 0
        context.timetableDataStore.edit { data ->
            data[globalLocationKey] = next
        }
        return next
    }

    suspend fun getScenarioId(widgetId: Int): String {
        val prefs = context.timetableDataStore.data.first()
        return prefs[perWidgetScenarioKey(widgetId)] ?: ScenarioDefaults.BUS_SCENARIO_ID
    }

    suspend fun setScenarioId(widgetId: Int, scenarioId: String) {
        context.timetableDataStore.edit { data ->
            data[perWidgetScenarioKey(widgetId)] = scenarioId
        }
    }

    suspend fun getLocationIndex(widgetId: Int): Int {
        val prefs = context.timetableDataStore.data.first()
        return prefs[perWidgetLocationKey(widgetId)] ?: getGlobalLocationIndex()
    }

    suspend fun getDayTypeModeForWidget(widgetId: Int): Int {
        val prefs = context.timetableDataStore.data.first()
        return prefs[perWidgetDayTypeModeKey(widgetId)] ?: getDayTypeMode()
    }

    suspend fun toggleLocation(widgetId: Int): Int {
        val current = getLocationIndex(widgetId)
        val next = if (current == 0) 1 else 0
        context.timetableDataStore.edit { data ->
            data[perWidgetLocationKey(widgetId)] = next
        }
        return next
    }

    suspend fun toggleDayType(widgetId: Int): Int {
        val current = getDayTypeModeForWidget(widgetId)
        val next = when (current) {
            DAY_TYPE_MODE_AUTO -> DAY_TYPE_MODE_MANUAL_WORKDAY
            DAY_TYPE_MODE_MANUAL_WORKDAY -> DAY_TYPE_MODE_MANUAL_HOLIDAY
            else -> DAY_TYPE_MODE_AUTO
        }
        context.timetableDataStore.edit { data ->
            data[perWidgetDayTypeModeKey(widgetId)] = next
        }
        return next
    }

    suspend fun clear(widgetId: Int) {
        context.timetableDataStore.edit { data ->
            data.remove(perWidgetScenarioKey(widgetId))
            data.remove(perWidgetLocationKey(widgetId))
            data.remove(perWidgetDayTypeModeKey(widgetId))
        }
    }

    suspend fun setSelectedScheduleId(scenarioId: String, scheduleId: String) {
        context.timetableDataStore.edit { data ->
            data[perScenarioScheduleKey(scenarioId)] = scheduleId
        }
    }

    suspend fun getSelectedScheduleId(scenarioId: String): String? {
        return context.timetableDataStore.data.first()[perScenarioScheduleKey(scenarioId)]
    }

    private fun perScenarioScheduleKey(scenarioId: String) =
        stringPreferencesKey("scenario_${scenarioId}_selected_schedule")

    private fun perWidgetScenarioKey(widgetId: Int) = stringPreferencesKey("widget_${widgetId}_scenario")

    private fun perWidgetLocationKey(widgetId: Int) = intPreferencesKey("widget_${widgetId}_location")

    private fun perWidgetDayTypeModeKey(widgetId: Int) = intPreferencesKey("widget_${widgetId}_daytype_mode")

    private val globalLocationKey = intPreferencesKey("global_location")

    private val globalDayTypeModeKey = intPreferencesKey("global_daytype_mode")

    private val legacyGlobalDayTypeKey = intPreferencesKey("global_daytype")

    companion object {
        const val DAY_TYPE_MODE_AUTO = 0
        const val DAY_TYPE_MODE_MANUAL_WORKDAY = 1
        const val DAY_TYPE_MODE_MANUAL_HOLIDAY = 2
        private const val DAY_TYPE_MODE_MIN = 0
        private const val DAY_TYPE_MODE_MAX = 2
    }
}
