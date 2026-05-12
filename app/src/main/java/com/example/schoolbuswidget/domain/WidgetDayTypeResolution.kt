package com.example.schoolbuswidget.domain

import java.time.LocalDate

suspend fun resolveServiceDayTypeForMode(
    mode: Int,
    date: LocalDate,
    effectiveDayTypeResolver: EffectiveDayTypeResolver,
): DayTypeResolution {
    return when (mode) {
        1 -> DayTypeResolution(ServiceDayType.WORKDAY, DayTypeResolutionSource.MANUAL_OVERRIDE)
        2 -> DayTypeResolution(ServiceDayType.HOLIDAY, DayTypeResolutionSource.MANUAL_OVERRIDE)
        else -> effectiveDayTypeResolver.resolve(date)
    }
}
