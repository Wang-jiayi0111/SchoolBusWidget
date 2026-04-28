package com.example.schoolbuswidget.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DefaultTimetable
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.ServiceDayType
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.time.LocalTime

private val Context.timetableDataStore by preferencesDataStore(name = "timetable_store")

class TimetableDataStoreRepository(
    private val context: Context,
) : TimetableRepository {

    override suspend fun getDepartures(
        location: CampusLocation,
        dayType: ServiceDayType,
    ): List<DepartureTime> {
        val preferences = context.timetableDataStore.data.first()
        val key = keyFor(location, dayType)
        val raw = preferences[key]

        return if (raw.isNullOrBlank()) {
            DefaultTimetable.table[location to dayType].orEmpty()
        } else {
            decodeDepartures(raw)
        }
    }

    override suspend fun saveDepartures(
        location: CampusLocation,
        dayType: ServiceDayType,
        departures: List<DepartureTime>,
    ) {
        val key = keyFor(location, dayType)
        val payload = encodeDepartures(departures)
        context.timetableDataStore.edit { prefs ->
            prefs[key] = payload
        }
    }

    private fun keyFor(
        location: CampusLocation,
        dayType: ServiceDayType,
    ): Preferences.Key<String> = stringPreferencesKey("timetable_${location.name}_${dayType.name}")

    private fun encodeDepartures(
        departures: List<DepartureTime>,
    ): String {
        val array = JSONArray()
        departures
            .map { it.time }
            .distinct()
            .sorted()
            .forEach { array.put(it.toString()) }
        return array.toString()
    }

    private fun decodeDepartures(raw: String): List<DepartureTime> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                add(DepartureTime(LocalTime.parse(array.getString(index))))
            }
        }
    }
}
