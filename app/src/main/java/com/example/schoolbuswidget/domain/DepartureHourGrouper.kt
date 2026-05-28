package com.example.schoolbuswidget.domain

import java.time.LocalTime

object DepartureHourGrouper {

    data class HourGroup(
        val hour: Int,
        val times: List<LocalTime>,
    )

    fun group(times: List<LocalTime>): List<HourGroup> {
        if (times.isEmpty()) return emptyList()
        return times
            .distinct()
            .sorted()
            .groupBy { it.hour }
            .toSortedMap()
            .map { (hour, list) -> HourGroup(hour, list.sorted()) }
    }
}
