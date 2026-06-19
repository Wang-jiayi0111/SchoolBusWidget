package com.example.schoolbuswidget.data.holiday

sealed class HolidayDayLookup {
    data class Resolved(
        val isRestDay: Boolean,
        val origin: HolidayDataOrigin,
    ) : HolidayDayLookup()

    data object Unknown : HolidayDayLookup()
}
