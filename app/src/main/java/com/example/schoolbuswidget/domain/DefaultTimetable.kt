package com.example.schoolbuswidget.domain

import java.time.LocalTime

object DefaultTimetable {

    private val everyTenMinutesSevenToTwentyOne: List<DepartureTime> = buildList {
        for (hour in 7..21) {
            for (minute in 0 until 60 step 10) {
                add(DepartureTime(LocalTime.of(hour, minute)))
            }
        }
    }

    val table: Map<Pair<CampusLocation, ServiceDayType>, List<DepartureTime>> = mapOf(
        (CampusLocation.NORTH to ServiceDayType.WORKDAY) to everyTenMinutesSevenToTwentyOne,
        (CampusLocation.NORTH to ServiceDayType.HOLIDAY) to everyTenMinutesSevenToTwentyOne,
        (CampusLocation.SOUTH to ServiceDayType.WORKDAY) to everyTenMinutesSevenToTwentyOne,
        (CampusLocation.SOUTH to ServiceDayType.HOLIDAY) to everyTenMinutesSevenToTwentyOne,
    )
}
