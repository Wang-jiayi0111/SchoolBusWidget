package com.example.schoolbuswidget.domain

import java.time.LocalDate

object EffectiveScheduleResolver {

    fun matchingSchedules(
        schedules: List<TimetableSchedule>,
        date: LocalDate,
        resolvedDayType: ServiceDayType,
    ): List<TimetableSchedule> {
        return schedules.filter { schedule ->
            when (schedule.ruleKind) {
                ScheduleRuleKind.DAY_TYPE ->
                    schedule.dayType == resolvedDayType
                ScheduleRuleKind.WEEKLY ->
                    date.dayOfWeek.value in (schedule.weekdays ?: emptySet())
                ScheduleRuleKind.DATE_RANGE -> {
                    val start = schedule.startDate ?: return@filter false
                    val end = schedule.endDate ?: return@filter false
                    !date.isBefore(start) && !date.isAfter(end)
                }
            }
        }
    }

    fun mergeDepartures(lists: List<List<DepartureTime>>): List<DepartureTime> {
        return lists
            .flatten()
            .distinctBy { it.time }
            .sortedBy { it.time }
    }
}
