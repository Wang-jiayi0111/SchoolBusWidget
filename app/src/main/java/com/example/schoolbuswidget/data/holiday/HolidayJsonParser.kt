package com.example.schoolbuswidget.data.holiday

import org.json.JSONArray
import org.json.JSONObject

object HolidayJsonParser {

    private val monthDayKey = Regex("^\\d{2}-\\d{2}$")

    fun parseYearHolidayFlags(json: String): Map<String, Boolean>? {
        return try {
            val root = JSONObject(json)
            parseTimorHolidayObject(root) ?: parseHolidayCnDays(root.optJSONArray("days"))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTimorHolidayObject(root: JSONObject): Map<String, Boolean>? {
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
        return out.takeIf { it.isNotEmpty() }
    }

    /** NateScarlet/holiday-cn on jsDelivr: `{ "days": [{ "date":"2026-01-01", "isOffDay": true }] }` */
    private fun parseHolidayCnDays(days: JSONArray?): Map<String, Boolean>? {
        if (days == null || days.length() == 0) return null
        val out = linkedMapOf<String, Boolean>()
        for (i in 0 until days.length()) {
            val item = days.optJSONObject(i) ?: continue
            val date = item.optString("date")
            if (date.length < 10) continue
            val mmdd = date.substring(5, 10)
            if (!mmdd.matches(monthDayKey)) continue
            out[mmdd] = item.optBoolean("isOffDay", false)
        }
        return out.takeIf { it.isNotEmpty() }
    }
}
