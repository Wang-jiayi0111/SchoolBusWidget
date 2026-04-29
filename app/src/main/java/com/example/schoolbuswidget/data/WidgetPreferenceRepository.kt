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

    suspend fun getGlobalDayTypeIndex(): Int {
        val prefs = context.timetableDataStore.data.first()
        return prefs[globalDayTypeKey] ?: 0
    }

    suspend fun setGlobalSelection(locationIndex: Int, dayTypeIndex: Int) {
        context.timetableDataStore.edit { prefs ->
            prefs[globalLocationKey] = locationIndex
            prefs[globalDayTypeKey] = dayTypeIndex
        }
    }

    suspend fun toggleGlobalLocation(): Int {
        val next = if (getGlobalLocationIndex() == 0) 1 else 0
        context.timetableDataStore.edit { prefs ->
            prefs[globalLocationKey] = next
        }
        return next
    }

    suspend fun toggleGlobalDayType(): Int {
        val next = if (getGlobalDayTypeIndex() == 0) 1 else 0
        context.timetableDataStore.edit { prefs ->
            prefs[globalDayTypeKey] = next
        }
        return next
    }

    suspend fun getLocationIndex(widgetId: Int): Int {
        val prefs = context.timetableDataStore.data.first()
        return prefs[locationKey(widgetId)] ?: getGlobalLocationIndex()
    }

    suspend fun getDayTypeIndex(widgetId: Int): Int {
        val prefs = context.timetableDataStore.data.first()
        return prefs[dayTypeKey(widgetId)] ?: getGlobalDayTypeIndex()
    }

    suspend fun toggleLocation(widgetId: Int): Int {
        return toggleGlobalLocation()
    }

    suspend fun toggleDayType(widgetId: Int): Int {
        return toggleGlobalDayType()
    }

    suspend fun clear(widgetId: Int) {
        context.timetableDataStore.edit { prefs ->
            prefs.remove(locationKey(widgetId))
            prefs.remove(dayTypeKey(widgetId))
        }
    }

    private fun locationKey(widgetId: Int) = intPreferencesKey("widget_${widgetId}_location")

    private fun dayTypeKey(widgetId: Int) = intPreferencesKey("widget_${widgetId}_daytype")

    private val globalLocationKey = intPreferencesKey("global_location")

    private val globalDayTypeKey = intPreferencesKey("global_daytype")
}
