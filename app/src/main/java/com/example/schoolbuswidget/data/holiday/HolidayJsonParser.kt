package com.example.schoolbuswidget.data.holiday

import org.json.JSONObject

object HolidayJsonParser {

    private val monthDayKey = Regex("^\\d{2}-\\d{2}$")

    fun parseYearHolidayFlags(json: String): Map<String, Boolean>? {
        return try {
            val root = JSONObject(json)
            val container = root.optJSONObject("holiday") ?: root
            val out = linkedMapOf<String, Boolean>()
            val keyIterator = container.keys()
            while (keyIterator.hasNext()) {
                val key = keyIterator.next()
                if (!key.matches(monthDayKey)) continue
                when (val raw = container.opt(key)) {
                    is JSONObject -> out[key] = raw.optBoolean("holiday", false)
                    java.lang.Boolean.TRUE -> out[key] = true
                    java.lang.Boolean.FALSE -> out[key] = false
                    else -> continue
                }
            }
            out.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
