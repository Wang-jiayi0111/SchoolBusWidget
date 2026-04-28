package com.example.schoolbuswidget.domain

import java.time.LocalTime

object DefaultTimetable {
    val table: Map<Pair<CampusLocation, ServiceDayType>, List<DepartureTime>> = mapOf(
        (CampusLocation.NORTH to ServiceDayType.WORKDAY) to listOf("07:20", "08:00", "08:40", "10:00", "12:00", "14:00", "16:00", "18:00"),
        (CampusLocation.NORTH to ServiceDayType.HOLIDAY) to listOf("08:30", "10:30", "12:30", "15:00", "17:30"),
        (CampusLocation.SOUTH to ServiceDayType.WORKDAY) to listOf("07:10", "07:50", "08:30", "09:50", "11:50", "13:50", "15:50", "17:50"),
        (CampusLocation.SOUTH to ServiceDayType.HOLIDAY) to listOf("08:20", "10:20", "12:20", "14:50", "17:20"),
    )
        .mapValues { (_, values) -> values.map { DepartureTime(LocalTime.parse(it)) } }
}
