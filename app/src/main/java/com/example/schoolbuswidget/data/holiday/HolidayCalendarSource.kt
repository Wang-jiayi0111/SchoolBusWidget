package com.example.schoolbuswidget.data.holiday

import java.time.LocalDate

fun interface HolidayCalendarSource {
    suspend fun lookup(date: LocalDate): HolidayDayLookup
}
