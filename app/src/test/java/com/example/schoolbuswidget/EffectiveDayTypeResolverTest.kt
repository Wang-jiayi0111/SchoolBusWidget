package com.example.schoolbuswidget

import com.example.schoolbuswidget.data.holiday.HolidayCalendarSource
import com.example.schoolbuswidget.data.holiday.HolidayDayLookup
import com.example.schoolbuswidget.domain.DayTypeResolutionSource
import com.example.schoolbuswidget.domain.EffectiveDayTypeResolver
import com.example.schoolbuswidget.domain.ServiceDayType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EffectiveDayTypeResolverTest {

    @Test
    fun network_rest_day_maps_to_holiday_schedule() = runTest {
        val source = HolidayCalendarSource {
            HolidayDayLookup.Resolved(isRestDay = true, fromNetwork = true)
        }
        val resolver = EffectiveDayTypeResolver(source)
        val r = resolver.resolve(LocalDate.of(2026, 1, 1))
        assertEquals(ServiceDayType.HOLIDAY, r.dayType)
        assertEquals(DayTypeResolutionSource.HOLIDAY_API, r.source)
    }

    @Test
    fun cached_work_day_maps_to_workday_schedule() = runTest {
        val source = HolidayCalendarSource {
            HolidayDayLookup.Resolved(isRestDay = false, fromNetwork = false)
        }
        val resolver = EffectiveDayTypeResolver(source)
        val r = resolver.resolve(LocalDate.of(2026, 2, 14))
        assertEquals(ServiceDayType.WORKDAY, r.dayType)
        assertEquals(DayTypeResolutionSource.HOLIDAY_CACHE, r.source)
    }

    @Test
    fun unknown_lookup_falls_back_to_week_rule() = runTest {
        val source = HolidayCalendarSource { HolidayDayLookup.Unknown }
        val resolver = EffectiveDayTypeResolver(source)
        val saturday = LocalDate.of(2026, 5, 2)
        val r = resolver.resolve(saturday)
        assertEquals(ServiceDayType.HOLIDAY, r.dayType)
        assertEquals(DayTypeResolutionSource.WEEK_RULE, r.source)
    }
}
