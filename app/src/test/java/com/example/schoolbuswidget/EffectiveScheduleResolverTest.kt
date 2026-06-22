package com.example.schoolbuswidget

import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.EffectiveScheduleResolver
import com.example.schoolbuswidget.domain.ScheduleRuleKind
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.domain.TimetableSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EffectiveScheduleResolverTest {

    @Test
    fun dayTypeRule_matchesResolvedDayType() {
        val schedules = listOf(
            schedule("wd", ScheduleRuleKind.DAY_TYPE, dayType = ServiceDayType.WORKDAY),
            schedule("hol", ScheduleRuleKind.DAY_TYPE, dayType = ServiceDayType.HOLIDAY),
        )
        val monday = LocalDate.of(2026, 5, 25)
        val matched = EffectiveScheduleResolver.matchingSchedules(
            schedules,
            monday,
            ServiceDayType.WORKDAY,
        )
        assertEquals(listOf("wd"), matched.map { it.id })
    }

    @Test
    fun weeklyRule_matchesIsoWeekday() {
        val schedules = listOf(
            schedule("tue", ScheduleRuleKind.WEEKLY, weekdays = setOf(2)),
        )
        val tuesday = LocalDate.of(2026, 5, 26)
        val matched = EffectiveScheduleResolver.matchingSchedules(
            schedules,
            tuesday,
            ServiceDayType.WORKDAY,
        )
        assertEquals(1, matched.size)
    }

    @Test
    fun dateRangeRule_isInclusive() {
        val schedules = listOf(
            schedule(
                "exam",
                ScheduleRuleKind.DATE_RANGE,
                startDate = LocalDate.of(2026, 6, 1),
                endDate = LocalDate.of(2026, 6, 7),
            ),
        )
        assertTrue(
            EffectiveScheduleResolver.matchingSchedules(
                schedules,
                LocalDate.of(2026, 6, 1),
                ServiceDayType.WORKDAY,
            ).isNotEmpty(),
        )
        assertTrue(
            EffectiveScheduleResolver.matchingSchedules(
                schedules,
                LocalDate.of(2026, 6, 8),
                ServiceDayType.WORKDAY,
            ).isEmpty(),
        )
    }

    @Test
    fun mergeDepartures_deduplicatesAndSorts() {
        val merged = EffectiveScheduleResolver.mergeDepartures(
            listOf(
                listOf(DepartureTime(LocalTime.of(8, 0)), DepartureTime(LocalTime.of(8, 30))),
                listOf(DepartureTime(LocalTime.of(8, 0)), DepartureTime(LocalTime.of(9, 0))),
            ),
        )
        assertEquals(listOf("08:00", "08:30", "09:00"), merged.map { it.time.toString() })
    }

    private fun schedule(
        id: String,
        kind: ScheduleRuleKind,
        dayType: ServiceDayType? = null,
        weekdays: Set<Int>? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ) = TimetableSchedule(
        id = id,
        name = id,
        ruleKind = kind,
        dayType = dayType,
        weekdays = weekdays,
        startDate = startDate,
        endDate = endDate,
    )
}
