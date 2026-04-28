package com.example.schoolbuswidget.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

data class NextDepartureResult(
    val departureAt: LocalDateTime,
    val minutesLeft: Long,
)

class NextDepartureCalculator {

    fun calculate(
        now: LocalDateTime,
        departures: List<DepartureTime>,
    ): NextDepartureResult? {
        if (departures.isEmpty()) return null

        val sortedTimes = departures.map { it.time }.distinct().sorted()
        val todayCandidate = sortedTimes.firstOrNull { !it.isBefore(now.toLocalTime()) }
        val departureAt = if (todayCandidate != null) {
            now.toLocalDate().atTime(todayCandidate)
        } else {
            now.toLocalDate().plusDays(1).atTime(sortedTimes.first())
        }

        return NextDepartureResult(
            departureAt = departureAt,
            minutesLeft = Duration.between(now, departureAt).toMinutes(),
        )
    }

    fun calculateFromStringTimes(
        now: LocalDateTime,
        departures: List<String>,
    ): NextDepartureResult? {
        return calculate(now, departures.map { DepartureTime(LocalTime.parse(it)) })
    }
}
