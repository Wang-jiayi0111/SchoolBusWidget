package com.example.schoolbuswidget.domain

import java.time.LocalTime

enum class CampusLocation {
    NORTH,
    SOUTH,
}

enum class ServiceDayType {
    WORKDAY,
    HOLIDAY,
}

data class DepartureTime(
    val time: LocalTime,
)

data class DailyTimetable(
    val location: CampusLocation,
    val dayType: ServiceDayType,
    val departures: List<DepartureTime>,
)
