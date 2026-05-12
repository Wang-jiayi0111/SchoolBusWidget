package com.example.schoolbuswidget

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.schoolbuswidget.ui.timetable.TimetableManageActivity
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AppCompatActivity
import com.example.schoolbuswidget.data.TimetableDataStoreRepository
import com.example.schoolbuswidget.data.WidgetPreferenceRepository
import com.example.schoolbuswidget.data.holiday.HolidayCalendarRepository
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.EffectiveDayTypeResolver
import com.example.schoolbuswidget.domain.NextDepartureCalculator
import com.example.schoolbuswidget.domain.resolveServiceDayTypeForMode
import com.example.schoolbuswidget.ui.DayTypeLabels
import com.example.schoolbuswidget.util.AppLog
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {
    private var selectedLocation = CampusLocation.NORTH
    private var dayTypeMode = WidgetPreferenceRepository.DAY_TYPE_MODE_AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadSelectionFromPreferences()
        setupAppControls()
        renderAppTimetable()
    }

    override fun onResume() {
        super.onResume()
        loadSelectionFromPreferences()
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
            runBlocking {
                dayTypeMode = WidgetPreferenceRepository(this@MainActivity).cycleDayTypeMode()
            }
            persistSelectionAndSyncWidget()
            renderAppTimetable()
        }

        findViewById<MaterialButton>(R.id.buttonAppManageTimetable).setOnClickListener {
            startActivity(Intent(this, TimetableManageActivity::class.java))
        }
    }

    private fun loadSelectionFromPreferences() {
        runBlocking {
            val preferences = WidgetPreferenceRepository(this@MainActivity)
            selectedLocation = if (preferences.getGlobalLocationIndex() == 0) {
                CampusLocation.NORTH
            } else {
                CampusLocation.SOUTH
            }
            dayTypeMode = preferences.getDayTypeMode()
        }
    }

    private fun persistSelectionAndSyncWidget() {
        runBlocking {
            WidgetPreferenceRepository(this@MainActivity).setGlobalLocationAndDayTypeMode(
                locationIndex = if (selectedLocation == CampusLocation.NORTH) 0 else 1,
                dayTypeMode = dayTypeMode,
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
        val heroTimeText = findViewById<TextView>(R.id.textNextTimeHero)
        val minutesLeftText = findViewById<TextView>(R.id.textAppMinutesLeft)
        val departureListText = findViewById<TextView>(R.id.textDepartureList)
        val sourceText = findViewById<TextView>(R.id.textDayTypeSource)

        runBlocking {
            try {
                val now = LocalDateTime.now()
                val repository = TimetableDataStoreRepository(this@MainActivity)
                val holidayRepository = HolidayCalendarRepository(this@MainActivity)
                val effectiveResolver = EffectiveDayTypeResolver(holidayRepository)
                val dayResolution = resolveServiceDayTypeForMode(dayTypeMode, now.toLocalDate(), effectiveResolver)
                val departures = repository.getDepartures(selectedLocation, dayResolution.dayType)
                val nextResult = NextDepartureCalculator().calculate(now, departures)

                selectionText.text = DayTypeLabels.selectionSummary(
                    this@MainActivity,
                    selectedLocation == CampusLocation.NORTH,
                    dayTypeMode,
                    dayResolution.dayType,
                )
                sourceText.text = DayTypeLabels.resolutionSourceCaption(this@MainActivity, dayResolution.source)

                if (nextResult == null) {
                    heroTimeText.text = getString(R.string.main_time_placeholder)
                    nextDepartureText.visibility = View.VISIBLE
                    nextDepartureText.text = getString(R.string.main_no_departure)
                    minutesLeftText.text = getString(R.string.widget_minutes_unavailable)
                } else {
                    heroTimeText.text = nextResult.departureAt.toLocalTime().toString()
                    nextDepartureText.visibility = View.GONE
                    minutesLeftText.text = getString(R.string.main_minutes_left, nextResult.minutesLeft)
                }

                departureListText.text = getString(
                    R.string.main_departure_list_item_prefix,
                    departures.toDisplayString(),
                )
            } catch (e: Exception) {
                AppLog.e("Main screen timetable render failed", e)
                heroTimeText.text = getString(R.string.main_time_placeholder)
                nextDepartureText.visibility = View.VISIBLE
                nextDepartureText.text = getString(R.string.main_error_generic)
                minutesLeftText.text = ""
            }
        }
    }

    private fun List<DepartureTime>.toDisplayString(): String {
        if (isEmpty()) return getString(R.string.main_departure_list_placeholder)
        return joinToString("、") { it.time.toString() }
    }
}
