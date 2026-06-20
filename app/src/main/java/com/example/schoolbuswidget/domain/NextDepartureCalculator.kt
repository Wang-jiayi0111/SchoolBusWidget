package com.example.schoolbuswidget.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

data class NextDepartureResult(
    val departureAt: LocalDateTime,
    val minutesLeft: Long,
)

data class UpcomingDeparturesResult(
    val next: NextDepartureResult,
    val following: NextDepartureResult?,
)

class NextDepartureCalculator {

    fun calculate(
        now: LocalDateTime,
        departures: List<DepartureTime>,
    ): NextDepartureResult? = calculateUpcoming(now, departures)?.next

    fun calculateUpcoming(
        now: LocalDateTime,
        departures: List<DepartureTime>,
    ): UpcomingDeparturesResult? {
        val upcoming = upcomingDepartures(now, departures, maxCount = 2)
        if (upcoming.isEmpty()) return null
        return UpcomingDeparturesResult(
            next = upcoming.first(),
            following = upcoming.getOrNull(1),
        )
    }

    fun calculateFromStringTimes(
        now: LocalDateTime,
        departures: List<String>,
    ): NextDepartureResult? {
        return calculate(now, departures.map { DepartureTime(LocalTime.parse(it)) })
    }

    private fun upcomingDepartures(
        now: LocalDateTime,
        departures: List<DepartureTime>,
        maxCount: Int,
    ): List<NextDepartureResult> {
        if (departures.isEmpty() || maxCount <= 0) return emptyList()

        val sortedTimes = departures.map { it.time }.distinct().sorted()
        val upcoming = mutableListOf<NextDepartureResult>()
        var dayOffset = 0L
        while (upcoming.size < maxCount && dayOffset < 366) {
            val date = now.toLocalDate().plusDays(dayOffset)
            for (time in sortedTimes) {
                val departureAt = date.atTime(time)
                if (departureAt.isBefore(now)) continue
                upcoming.add(
                    NextDepartureResult(
                        departureAt = departureAt,
                        minutesLeft = Duration.between(now, departureAt).toMinutes(),
                    ),
                )
                if (upcoming.size >= maxCount) return upcoming
            }
            dayOffset++
        }
        return upcoming
    }
}
