package com.example.schoolbuswidget

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.schoolbuswidget.data.TimetableDataStoreRepository
import com.example.schoolbuswidget.data.WidgetPreferenceRepository
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.NextDepartureCalculator
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {
    private var selectedLocation = CampusLocation.NORTH
    private var selectedDayType = ServiceDayType.WORKDAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadSelectionFromSharedPreference()
        setupAppControls()
        renderAppTimetable()
    }

    override fun onResume() {
        super.onResume()
        loadSelectionFromSharedPreference()
        renderAppTimetable()
    }

    private fun setupAppControls() {
        val toggleLocationButton = findViewById<Button>(R.id.buttonAppToggleLocation)
        val toggleDayTypeButton = findViewById<Button>(R.id.buttonAppToggleDayType)

        toggleLocationButton.setOnClickListener {
            selectedLocation = if (selectedLocation == CampusLocation.NORTH) {
                CampusLocation.SOUTH
            } else {
                CampusLocation.NORTH
            }
            persistSelectionAndSyncWidget()
            renderAppTimetable()
        }

        toggleDayTypeButton.setOnClickListener {
            selectedDayType = if (selectedDayType == ServiceDayType.WORKDAY) {
                ServiceDayType.HOLIDAY
            } else {
                ServiceDayType.WORKDAY
            }
            persistSelectionAndSyncWidget()
            renderAppTimetable()
        }
    }

    private fun loadSelectionFromSharedPreference() {
        runBlocking {
            val preferences = WidgetPreferenceRepository(this@MainActivity)
            selectedLocation = if (preferences.getGlobalLocationIndex() == 0) {
                CampusLocation.NORTH
            } else {
                CampusLocation.SOUTH
            }
            selectedDayType = if (preferences.getGlobalDayTypeIndex() == 0) {
                ServiceDayType.WORKDAY
            } else {
                ServiceDayType.HOLIDAY
            }
        }
    }

    private fun persistSelectionAndSyncWidget() {
        runBlocking {
            WidgetPreferenceRepository(this@MainActivity).setGlobalSelection(
                locationIndex = if (selectedLocation == CampusLocation.NORTH) 0 else 1,
                dayTypeIndex = if (selectedDayType == ServiceDayType.WORKDAY) 0 else 1,
            )
        }
        sendBroadcast(
            Intent(this, SchoolBusAppWidgetProvider::class.java).apply {
                action = SchoolBusAppWidgetProvider.ACTION_REFRESH_ALL
            },
        )
    }

    private fun renderAppTimetable() {
        val selectionText = findViewById<TextView>(R.id.textAppSelection)
        val nextDepartureText = findViewById<TextView>(R.id.textAppNextDeparture)
        val minutesLeftText = findViewById<TextView>(R.id.textAppMinutesLeft)
        val departureListText = findViewById<TextView>(R.id.textDepartureList)

        runBlocking {
            val now = LocalDateTime.now()
            val repository = TimetableDataStoreRepository(this@MainActivity)
            val departures = repository.getDepartures(selectedLocation, selectedDayType)
            val nextResult = NextDepartureCalculator().calculate(now, departures)

            selectionText.text = getString(
                R.string.main_selection,
                locationLabel(selectedLocation),
                dayTypeLabel(selectedDayType),
            )

            if (nextResult == null) {
                nextDepartureText.text = getString(R.string.main_no_departure)
                minutesLeftText.text = getString(R.string.widget_minutes_unavailable)
            } else {
                nextDepartureText.text = getString(
                    R.string.main_next_departure,
                    nextResult.departureAt.toLocalTime().toString(),
                )
                minutesLeftText.text = getString(R.string.main_minutes_left, nextResult.minutesLeft)
            }

            departureListText.text = getString(
                R.string.main_departure_list_item_prefix,
                departures.toDisplayString(),
            )
        }
    }

    private fun locationLabel(location: CampusLocation): String {
        return if (location == CampusLocation.NORTH) "北区" else "南区"
    }

    private fun dayTypeLabel(dayType: ServiceDayType): String {
        return if (dayType == ServiceDayType.WORKDAY) "工作日" else "假期"
    }

    private fun List<DepartureTime>.toDisplayString(): String {
        if (isEmpty()) return getString(R.string.main_departure_list_placeholder)
        return joinToString("、") { it.time.toString() }
    }

}
