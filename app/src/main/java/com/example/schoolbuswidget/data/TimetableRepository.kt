package com.example.schoolbuswidget.data

import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.ServiceDayType

interface TimetableRepository {
    suspend fun getDepartures(
        location: CampusLocation,
        dayType: ServiceDayType,
    ): List<DepartureTime>

    suspend fun saveDepartures(
        location: CampusLocation,
        dayType: ServiceDayType,
        departures: List<DepartureTime>,
    )
}
