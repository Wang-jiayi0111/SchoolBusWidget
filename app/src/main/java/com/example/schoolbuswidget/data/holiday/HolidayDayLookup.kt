package com.example.schoolbuswidget.data.holiday

sealed class HolidayDayLookup {
    data class Resolved(
        val isRestDay: Boolean,
        val fromNetwork: Boolean,
    ) : HolidayDayLookup()

    data object Unknown : HolidayDayLookup()
}
