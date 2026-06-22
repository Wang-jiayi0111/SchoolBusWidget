package com.example.schoolbuswidget.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DefaultTimetable
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.Scenario
import com.example.schoolbuswidget.domain.ScenarioTemplate
import com.example.schoolbuswidget.domain.ServiceDayType
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.time.LocalTime

val Context.timetableDataStore by preferencesDataStore(name = "timetable_store")

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

    override suspend fun clearSavedDepartures(
        location: CampusLocation,
        dayType: ServiceDayType,
    ) {
        val key = keyFor(location, dayType)
        context.timetableDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    override suspend fun hasCustomDepartures(
        location: CampusLocation,
        dayType: ServiceDayType,
    ): Boolean {
        val prefs = context.timetableDataStore.data.first()
        val raw = prefs[keyFor(location, dayType)]
        return !raw.isNullOrBlank()
    }

    suspend fun getScenarioDepartures(
        scenario: Scenario,
        location: CampusLocation = CampusLocation.NORTH,
        dayType: ServiceDayType = ServiceDayType.WORKDAY,
    ): List<DepartureTime> {
        return when (scenario.template) {
            ScenarioTemplate.SIMPLE -> {
                val raw = context.timetableDataStore.data.first()[simpleScenarioKey(scenario.id)]
                if (raw.isNullOrBlank()) emptyList() else decodeDepartures(raw)
            }
            ScenarioTemplate.MULTI_SCHEDULE -> {
                val raw = context.timetableDataStore.data.first()[daySplitScenarioKey(scenario.id, dayType)]
                if (raw.isNullOrBlank()) emptyList() else decodeDepartures(raw)
            }
            ScenarioTemplate.MULTI_PROFILE -> getDepartures(location, dayType)
        }
    }

    suspend fun saveScenarioDepartures(
        scenario: Scenario,
        departures: List<DepartureTime>,
        location: CampusLocation = CampusLocation.NORTH,
        dayType: ServiceDayType = ServiceDayType.WORKDAY,
    ) {
        val payload = encodeDepartures(departures)
        context.timetableDataStore.edit { prefs ->
            when (scenario.template) {
                ScenarioTemplate.SIMPLE -> prefs[simpleScenarioKey(scenario.id)] = payload
                ScenarioTemplate.MULTI_SCHEDULE -> prefs[daySplitScenarioKey(scenario.id, dayType)] = payload
                ScenarioTemplate.MULTI_PROFILE -> prefs[keyFor(location, dayType)] = payload
            }
        }
    }

    suspend fun clearScenarioSavedDepartures(
        scenario: Scenario,
        location: CampusLocation = CampusLocation.NORTH,
        dayType: ServiceDayType = ServiceDayType.WORKDAY,
    ) {
        context.timetableDataStore.edit { prefs ->
            when (scenario.template) {
                ScenarioTemplate.SIMPLE -> prefs.remove(simpleScenarioKey(scenario.id))
                ScenarioTemplate.MULTI_SCHEDULE -> prefs.remove(daySplitScenarioKey(scenario.id, dayType))
                ScenarioTemplate.MULTI_PROFILE -> prefs.remove(keyFor(location, dayType))
            }
        }
    }

    suspend fun hasCustomScenarioDepartures(
        scenario: Scenario,
        location: CampusLocation = CampusLocation.NORTH,
        dayType: ServiceDayType = ServiceDayType.WORKDAY,
    ): Boolean {
        val prefs = context.timetableDataStore.data.first()
        val raw = when (scenario.template) {
            ScenarioTemplate.SIMPLE -> prefs[simpleScenarioKey(scenario.id)]
            ScenarioTemplate.MULTI_SCHEDULE -> prefs[daySplitScenarioKey(scenario.id, dayType)]
            ScenarioTemplate.MULTI_PROFILE -> prefs[keyFor(location, dayType)]
        }
        return !raw.isNullOrBlank()
    }

    suspend fun clearScenarioTimetables(scenarioId: String) {
        context.timetableDataStore.edit { prefs ->
            prefs.asMap().keys.filter { key ->
                key.name.startsWith("timetable_scenario_${scenarioId}") ||
                    key.name.startsWith("schedules_$scenarioId")
            }.forEach { prefs.remove(it) }
        }
        ScheduleRepository(context).clearScenarioSchedules(scenarioId)
    }

    fun encodeDeparturesPublic(departures: List<DepartureTime>): String = encodeDepartures(departures)

    fun decodeDeparturesPublic(raw: String): List<DepartureTime> = decodeDepartures(raw)

    private fun simpleScenarioKey(scenarioId: String): Preferences.Key<String> =
        stringPreferencesKey("timetable_scenario_${scenarioId}")

    private fun daySplitScenarioKey(
        scenarioId: String,
        dayType: ServiceDayType,
    ): Preferences.Key<String> = stringPreferencesKey("timetable_scenario_${scenarioId}_${dayType.name}")

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
