package com.example.schoolbuswidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.Scenario
import com.example.schoolbuswidget.domain.ScenarioDefaults
import com.example.schoolbuswidget.domain.ScenarioTemplate
import com.example.schoolbuswidget.domain.ServiceDayType
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ScenarioRepository(
    private val context: Context,
) {
    suspend fun listScenarios(): List<Scenario> {
        ensureInitialized()
        val raw = context.timetableDataStore.data.first()[scenariosKey] ?: return emptyList()
        return decodeScenarios(raw)
    }

    suspend fun getScenario(id: String): Scenario? = listScenarios().find { it.id == id }

    suspend fun addScenario(name: String, template: ScenarioTemplate): Scenario {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name empty" }
        val scenarios = listScenarios().toMutableList()
        require(scenarios.size < MAX_SCENARIOS) { "max scenarios reached" }
        val scenario = Scenario(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            template = template,
            builtIn = false,
        )
        scenarios.add(scenario)
        saveScenarios(scenarios)
        if (template == ScenarioTemplate.MULTI_SCHEDULE) {
            ScheduleRepository(context).createDefaultSchedules(scenario.id)
        }
        return scenario
    }

    suspend fun renameScenario(id: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "name empty" }
        val scenarios = listScenarios().map { scenario ->
            if (scenario.id == id) scenario.copy(name = trimmed) else scenario
        }
        saveScenarios(scenarios)
    }

    suspend fun deleteScenario(id: String) {
        val scenarios = listScenarios()
        val target = scenarios.find { it.id == id } ?: return
        saveScenarios(scenarios.filter { it.id != id })
        val timetableRepository = TimetableDataStoreRepository(context)
        when (target.template) {
            ScenarioTemplate.MULTI_PROFILE -> {
                CampusLocation.entries.forEach { location ->
                    ServiceDayType.entries.forEach { dayType ->
                        timetableRepository.clearSavedDepartures(location, dayType)
                    }
                }
            }
            else -> timetableRepository.clearScenarioTimetables(id)
        }
    }

    private suspend fun ensureInitialized() {
        val prefs = context.timetableDataStore.data.first()
        if (prefs.contains(scenariosKey)) return
        saveScenarios(
            listOf(
                Scenario(
                    id = ScenarioDefaults.BUS_SCENARIO_ID,
                    name = DEFAULT_BUS_NAME,
                    template = ScenarioTemplate.MULTI_PROFILE,
                    builtIn = true,
                ),
            ),
        )
    }

    private suspend fun saveScenarios(scenarios: List<Scenario>) {
        context.timetableDataStore.edit { prefs ->
            prefs[scenariosKey] = encodeScenarios(scenarios)
        }
    }

    private fun encodeScenarios(scenarios: List<Scenario>): String {
        val array = JSONArray()
        scenarios.forEach { scenario ->
            array.put(
                JSONObject().apply {
                    put("id", scenario.id)
                    put("name", scenario.name)
                    put("template", scenario.template.name)
                    put("builtIn", scenario.builtIn)
                },
            )
        }
        return array.toString()
    }

    private fun decodeScenarios(raw: String): List<Scenario> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    Scenario(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        template = parseTemplate(obj.getString("template")),
                        builtIn = obj.optBoolean("builtIn", false),
                    ),
                )
            }
        }
    }

    private fun parseTemplate(raw: String): ScenarioTemplate {
        return when (raw) {
            "DAY_SPLIT" -> ScenarioTemplate.MULTI_SCHEDULE
            else -> ScenarioTemplate.valueOf(raw)
        }
    }

    private val scenariosKey = stringPreferencesKey("scenarios_index")

    companion object {
        private const val MAX_SCENARIOS = 20
        const val DEFAULT_BUS_NAME = "校巴"
    }
}
