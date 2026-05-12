package com.example.schoolbuswidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    suspend fun getLocationIndex(widgetId: Int): Int {
        val prefs = context.timetableDataStore.data.first()
        return prefs[perWidgetLocationKey(widgetId)] ?: getGlobalLocationIndex()
    }

    suspend fun getDayTypeModeForWidget(widgetId: Int): Int {
        val prefs = context.timetableDataStore.data.first()
        return prefs[perWidgetDayTypeModeKey(widgetId)] ?: getDayTypeMode()
    }

    suspend fun toggleLocation(widgetId: Int): Int {
        return toggleGlobalLocation()
    }

    suspend fun toggleDayType(widgetId: Int): Int {
        return cycleDayTypeMode()
    }

    suspend fun clear(widgetId: Int) {
        context.timetableDataStore.edit { data ->
            data.remove(perWidgetLocationKey(widgetId))
            data.remove(perWidgetDayTypeModeKey(widgetId))
        }
    }

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
