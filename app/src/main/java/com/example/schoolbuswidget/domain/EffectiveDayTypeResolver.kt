package com.example.schoolbuswidget.domain

import com.example.schoolbuswidget.data.holiday.HolidayCalendarSource
import com.example.schoolbuswidget.data.holiday.HolidayDataOrigin
import com.example.schoolbuswidget.data.holiday.HolidayDayLookup
import java.time.LocalDate

enum class DayTypeResolutionSource {
    HOLIDAY_API,
    HOLIDAY_CACHE,
    HOLIDAY_BUNDLED,
    WEEK_RULE,
    MANUAL_OVERRIDE,
}

data class DayTypeResolution(
    val dayType: ServiceDayType,
    val source: DayTypeResolutionSource,
)

class EffectiveDayTypeResolver(
    private val holidayCalendarSource: HolidayCalendarSource,
    private val weekRule: DayTypeResolver = DayTypeResolver(),
) {

    suspend fun resolve(date: LocalDate): DayTypeResolution {
        return when (val lookup = holidayCalendarSource.lookup(date)) {
            is HolidayDayLookup.Resolved -> {
                val dayType = if (lookup.isRestDay) {
                    ServiceDayType.HOLIDAY
                } else {
                    ServiceDayType.WORKDAY
                }
                val source = when (lookup.origin) {
                    HolidayDataOrigin.NETWORK -> DayTypeResolutionSource.HOLIDAY_API
                    HolidayDataOrigin.DISK_CACHE -> DayTypeResolutionSource.HOLIDAY_CACHE
                    HolidayDataOrigin.BUNDLED -> DayTypeResolutionSource.HOLIDAY_BUNDLED
                }
                DayTypeResolution(dayType, source)
            }

            HolidayDayLookup.Unknown -> {
                DayTypeResolution(
                    weekRule.resolveByWeekRule(date),
                    DayTypeResolutionSource.WEEK_RULE,
                )
            }
        }
    }
}
