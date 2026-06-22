package com.example.schoolbuswidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.EffectiveScheduleResolver
import com.example.schoolbuswidget.domain.ScheduleRuleKind
import com.example.schoolbuswidget.domain.Scenario
import com.example.schoolbuswidget.domain.ScenarioTemplate
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.domain.TimetableSchedule
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class ScheduleRepository(
    private val context: Context,
) {
    private val timetableRepository = TimetableDataStoreRepository(context)

    suspend fun listSchedules(scenarioId: String): List<TimetableSchedule> {
        ensureMigrated(scenarioId)
        val raw = context.timetableDataStore.data.first()[schedulesKey(scenarioId)] ?: return emptyList()
        return decodeSchedules(raw)
    }

    suspend fun getSchedule(scenarioId: String, scheduleId: String): TimetableSchedule? {
        return listSchedules(scenarioId).find { it.id == scheduleId }
    }

    suspend fun addSchedule(scenario: Scenario, schedule: TimetableSchedule) {
        val schedules = listSchedules(scenario.id).toMutableList()
        schedules.add(schedule)
        saveSchedules(scenario.id, schedules)
    }

    suspend fun updateSchedule(scenarioId: String, schedule: TimetableSchedule) {
        val schedules = listSchedules(scenarioId).map { existing ->
            if (existing.id == schedule.id) schedule else existing
        }
        saveSchedules(scenarioId, schedules)
    }

    suspend fun deleteSchedule(scenarioId: String, scheduleId: String) {
        saveSchedules(scenarioId, listSchedules(scenarioId).filter { it.id != scheduleId })
        context.timetableDataStore.edit { prefs ->
            prefs.remove(scheduleTimesKey(scheduleId))
        }
    }

    suspend fun getScheduleTimes(scheduleId: String): List<DepartureTime> {
        val raw = context.timetableDataStore.data.first()[scheduleTimesKey(scheduleId)]
        return if (raw.isNullOrBlank()) emptyList() else timetableRepository.decodeDeparturesPublic(raw)
    }

    suspend fun saveScheduleTimes(scheduleId: String, departures: List<DepartureTime>) {
        val payload = timetableRepository.encodeDeparturesPublic(departures)
        context.timetableDataStore.edit { prefs ->
            prefs[scheduleTimesKey(scheduleId)] = payload
        }
    }

    suspend fun hasCustomScheduleTimes(scheduleId: String): Boolean {
        val raw = context.timetableDataStore.data.first()[scheduleTimesKey(scheduleId)]
        return !raw.isNullOrBlank()
    }

    suspend fun clearScheduleTimes(scheduleId: String) {
        context.timetableDataStore.edit { prefs ->
            prefs.remove(scheduleTimesKey(scheduleId))
        }
    }

    suspend fun createDefaultSchedules(scenarioId: String) {
        if (listSchedules(scenarioId).isNotEmpty()) return
        val workdayId = UUID.randomUUID().toString()
        val holidayId = UUID.randomUUID().toString()
        saveSchedules(
            scenarioId,
            listOf(
                TimetableSchedule(
                    id = workdayId,
                    name = DEFAULT_WORKDAY_NAME,
                    ruleKind = ScheduleRuleKind.DAY_TYPE,
                    dayType = ServiceDayType.WORKDAY,
                ),
                TimetableSchedule(
                    id = holidayId,
                    name = DEFAULT_HOLIDAY_NAME,
                    ruleKind = ScheduleRuleKind.DAY_TYPE,
                    dayType = ServiceDayType.HOLIDAY,
                ),
            ),
        )
    }

    suspend fun getEffectiveDepartures(
        scenario: Scenario,
        date: LocalDate,
        resolvedDayType: ServiceDayType,
    ): Pair<List<DepartureTime>, List<TimetableSchedule>> {
        ensureMigrated(scenario.id)
        val schedules = listSchedules(scenario.id)
        val active = EffectiveScheduleResolver.matchingSchedules(schedules, date, resolvedDayType)
        val times = active.map { getScheduleTimes(it.id) }
        return EffectiveScheduleResolver.mergeDepartures(times) to active
    }

    suspend fun clearScenarioSchedules(scenarioId: String) {
        listSchedules(scenarioId).forEach { clearScheduleTimes(it.id) }
        context.timetableDataStore.edit { prefs ->
            prefs.remove(schedulesKey(scenarioId))
        }
    }

    private suspend fun ensureMigrated(scenarioId: String) {
        val prefs = context.timetableDataStore.data.first()
        if (prefs.contains(schedulesKey(scenarioId))) return

        val workdayRaw = prefs[stringPreferencesKey("timetable_scenario_${scenarioId}_WORKDAY")]
        val holidayRaw = prefs[stringPreferencesKey("timetable_scenario_${scenarioId}_HOLIDAY")]
        if (workdayRaw.isNullOrBlank() && holidayRaw.isNullOrBlank()) return

        val workdayId = UUID.randomUUID().toString()
        val holidayId = UUID.randomUUID().toString()
        saveSchedules(
            scenarioId,
            listOf(
                TimetableSchedule(
                    id = workdayId,
                    name = DEFAULT_WORKDAY_NAME,
                    ruleKind = ScheduleRuleKind.DAY_TYPE,
                    dayType = ServiceDayType.WORKDAY,
                ),
                TimetableSchedule(
                    id = holidayId,
                    name = DEFAULT_HOLIDAY_NAME,
                    ruleKind = ScheduleRuleKind.DAY_TYPE,
                    dayType = ServiceDayType.HOLIDAY,
                ),
            ),
        )
        if (!workdayRaw.isNullOrBlank()) {
            context.timetableDataStore.edit { it[scheduleTimesKey(workdayId)] = workdayRaw }
        }
        if (!holidayRaw.isNullOrBlank()) {
            context.timetableDataStore.edit { it[scheduleTimesKey(holidayId)] = holidayRaw }
        }
    }

    private suspend fun saveSchedules(scenarioId: String, schedules: List<TimetableSchedule>) {
        context.timetableDataStore.edit { prefs ->
            prefs[schedulesKey(scenarioId)] = encodeSchedules(schedules)
        }
    }

    private fun schedulesKey(scenarioId: String) = stringPreferencesKey("schedules_$scenarioId")

    private fun scheduleTimesKey(scheduleId: String) = stringPreferencesKey("timetable_schedule_$scheduleId")

    private fun encodeSchedules(schedules: List<TimetableSchedule>): String {
        val array = JSONArray()
        schedules.forEach { schedule ->
            array.put(
                JSONObject().apply {
                    put("id", schedule.id)
                    put("name", schedule.name)
                    put("ruleKind", schedule.ruleKind.name)
                    schedule.dayType?.let { put("dayType", it.name) }
                    schedule.weekdays?.let { days ->
                        put("weekdays", JSONArray(days.sorted()))
                    }
                    schedule.startDate?.let { put("startDate", it.toString()) }
                    schedule.endDate?.let { put("endDate", it.toString()) }
                },
            )
        }
        return array.toString()
    }

    private fun decodeSchedules(raw: String): List<TimetableSchedule> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                val weekdaysArray = obj.optJSONArray("weekdays")
                val weekdays = if (weekdaysArray != null) {
                    buildSet<Int> {
                        for (i in 0 until weekdaysArray.length()) {
                            add(weekdaysArray.getInt(i))
                        }
                    }
                } else {
                    null
                }
                add(
                    TimetableSchedule(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        ruleKind = ScheduleRuleKind.valueOf(obj.getString("ruleKind")),
                        dayType = obj.optString("dayType").takeIf { it.isNotBlank() }?.let {
                            ServiceDayType.valueOf(it)
                        },
                        weekdays = weekdays,
                        startDate = obj.optString("startDate").takeIf { it.isNotBlank() }?.let {
                            LocalDate.parse(it)
                        },
                        endDate = obj.optString("endDate").takeIf { it.isNotBlank() }?.let {
                            LocalDate.parse(it)
                        },
                    ),
                )
            }
        }
    }

    companion object {
        const val DEFAULT_WORKDAY_NAME = "工作日"
        const val DEFAULT_HOLIDAY_NAME = "假期"
    }
}
