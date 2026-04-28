package com.example.schoolbuswidget.domain

import java.time.DayOfWeek
import java.time.LocalDate

class DayTypeResolver {
    fun resolveByWeekRule(date: LocalDate): ServiceDayType {
        return if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            ServiceDayType.HOLIDAY
        } else {
            ServiceDayType.WORKDAY
        }
    }
}
