package com.example.schoolbuswidget

import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DayTypeResolver
import com.example.schoolbuswidget.domain.DefaultTimetable
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.NextDepartureCalculator
import com.example.schoolbuswidget.domain.ServiceDayType
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ExampleUnitTest {
    private val calculator = NextDepartureCalculator()
    private val dayTypeResolver = DayTypeResolver()

    @Test
    fun returns_next_departure_in_same_day() {
        val now = LocalDateTime.of(2026, 4, 28, 8, 15)
        val departures = listOf("08:00", "08:40", "10:00").map { DepartureTime(LocalTime.parse(it)) }

        val result = calculator.calculate(now, departures)

        assertNotNull(result)
        assertEquals(LocalDateTime.of(2026, 4, 28, 8, 40), result!!.departureAt)
        assertEquals(25, result.minutesLeft)
    }

    @Test
    fun returns_exact_time_when_now_matches_departure() {
        val now = LocalDateTime.of(2026, 4, 28, 8, 40)
        val departures = listOf("08:00", "08:40", "10:00").map { DepartureTime(LocalTime.parse(it)) }

        val result = calculator.calculate(now, departures)

        assertNotNull(result)
        assertEquals(LocalDateTime.of(2026, 4, 28, 8, 40), result!!.departureAt)
        assertEquals(0, result.minutesLeft)
    }

    @Test
    fun rolls_over_to_next_day_after_last_departure() {
        val now = LocalDateTime.of(2026, 4, 28, 23, 0)
        val departures = listOf("08:00", "18:00").map { DepartureTime(LocalTime.parse(it)) }

        val result = calculator.calculate(now, departures)

        assertNotNull(result)
        assertEquals(LocalDateTime.of(2026, 4, 29, 8, 0), result!!.departureAt)
        assertEquals(540, result.minutesLeft)
    }

    @Test
    fun default_timetable_has_data_for_each_location_and_day_type() {
        val northWorkday = DefaultTimetable.table[CampusLocation.NORTH to ServiceDayType.WORKDAY]
        val northHoliday = DefaultTimetable.table[CampusLocation.NORTH to ServiceDayType.HOLIDAY]
        val southWorkday = DefaultTimetable.table[CampusLocation.SOUTH to ServiceDayType.WORKDAY]
        val southHoliday = DefaultTimetable.table[CampusLocation.SOUTH to ServiceDayType.HOLIDAY]

        assertTrue(!northWorkday.isNullOrEmpty())
        assertTrue(!northHoliday.isNullOrEmpty())
        assertTrue(!southWorkday.isNullOrEmpty())
        assertTrue(!southHoliday.isNullOrEmpty())
    }

    @Test
    fun week_rule_resolves_workday_and_weekend() {
        val weekday = dayTypeResolver.resolveByWeekRule(LocalDate.of(2026, 4, 29))
        val weekend = dayTypeResolver.resolveByWeekRule(LocalDate.of(2026, 5, 2))

        assertEquals(ServiceDayType.WORKDAY, weekday)
        assertEquals(ServiceDayType.HOLIDAY, weekend)
    }
}