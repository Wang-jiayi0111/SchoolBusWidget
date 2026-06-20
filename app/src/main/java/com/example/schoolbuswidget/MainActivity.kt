package com.example.schoolbuswidget

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.schoolbuswidget.ui.timetable.TimetableManageActivity
import com.example.schoolbuswidget.ui.DepartureHourGroupAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private val departureListAdapter = DepartureHourGroupAdapter()
    private var syncingSelectionChips = false

    private lateinit var chipAppNorth: Chip
    private lateinit var chipAppSouth: Chip
    private lateinit var chipAppAuto: Chip
    private lateinit var chipAppWorkday: Chip
    private lateinit var chipAppHoliday: Chip

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupDepartureListRecycler()
        setupAppControls()
        loadSelectionFromPreferences()
        renderAppTimetable()
    }

    override fun onResume() {
        super.onResume()
        loadSelectionFromPreferences()
        renderAppTimetable()
    }

    private fun setupDepartureListRecycler() {
        val recycler = findViewById<RecyclerView>(R.id.recyclerDepartureList)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = departureListAdapter
        recycler.isNestedScrollingEnabled = false
    }

    private fun setupAppControls() {
        chipAppNorth = findViewById(R.id.chipAppNorth)
        chipAppSouth = findViewById(R.id.chipAppSouth)
        chipAppAuto = findViewById(R.id.chipAppAuto)
        chipAppWorkday = findViewById(R.id.chipAppWorkday)
        chipAppHoliday = findViewById(R.id.chipAppHoliday)

        findViewById<ChipGroup>(R.id.chipGroupAppCampus).setOnCheckedStateChangeListener { _, checkedIds ->
            if (syncingSelectionChips || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            selectedLocation = when (checkedIds.first()) {
                R.id.chipAppNorth -> CampusLocation.NORTH
                else -> CampusLocation.SOUTH
            }
            persistSelectionAndSyncWidget()
            renderAppTimetable()
        }

        findViewById<ChipGroup>(R.id.chipGroupAppDayType).setOnCheckedStateChangeListener { _, checkedIds ->
            if (syncingSelectionChips || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            dayTypeMode = when (checkedIds.first()) {
                R.id.chipAppWorkday -> WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_WORKDAY
                R.id.chipAppHoliday -> WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_HOLIDAY
                else -> WidgetPreferenceRepository.DAY_TYPE_MODE_AUTO
            }
            persistSelectionAndSyncWidget()
            renderAppTimetable()
        }

        findViewById<MaterialButton>(R.id.buttonAppManageTimetable).setOnClickListener {
            startActivity(Intent(this, TimetableManageActivity::class.java))
        }
    }

    private fun syncSelectionChipsFromState() {
        syncingSelectionChips = true
        chipAppNorth.isChecked = selectedLocation == CampusLocation.NORTH
        chipAppSouth.isChecked = selectedLocation == CampusLocation.SOUTH
        when (dayTypeMode) {
            WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_WORKDAY -> chipAppWorkday.isChecked = true
            WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_HOLIDAY -> chipAppHoliday.isChecked = true
            else -> chipAppAuto.isChecked = true
        }
        syncingSelectionChips = false
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
        syncSelectionChipsFromState()
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
        val followingDepartureText = findViewById<TextView>(R.id.textAppFollowingDeparture)
        val departureListSummary = findViewById<TextView>(R.id.textDepartureListSummary)
        val departureListEmpty = findViewById<TextView>(R.id.textDepartureListEmpty)
        val departureListRecycler = findViewById<RecyclerView>(R.id.recyclerDepartureList)
        val sourceText = findViewById<TextView>(R.id.textDayTypeSource)

        runBlocking {
            try {
                val now = LocalDateTime.now()
                val repository = TimetableDataStoreRepository(this@MainActivity)
                val holidayRepository = HolidayCalendarRepository(this@MainActivity)
                val effectiveResolver = EffectiveDayTypeResolver(holidayRepository)
                val dayResolution = resolveServiceDayTypeForMode(dayTypeMode, now.toLocalDate(), effectiveResolver)
                val departures = repository.getDepartures(selectedLocation, dayResolution.dayType)
                val upcoming = NextDepartureCalculator().calculateUpcoming(now, departures)

                selectionText.text = DayTypeLabels.selectionSummary(
                    this@MainActivity,
                    selectedLocation == CampusLocation.NORTH,
                    dayTypeMode,
                    dayResolution.dayType,
                )
                sourceText.text = DayTypeLabels.resolutionSourceCaption(this@MainActivity, dayResolution.source)

                if (upcoming == null) {
                    heroTimeText.text = getString(R.string.main_time_placeholder)
                    nextDepartureText.visibility = View.VISIBLE
                    nextDepartureText.text = getString(R.string.main_no_departure)
                    minutesLeftText.text = getString(R.string.widget_minutes_unavailable)
                    followingDepartureText.visibility = View.GONE
                } else {
                    val nextResult = upcoming.next
                    heroTimeText.text = nextResult.departureAt.toLocalTime().toString()
                    nextDepartureText.visibility = View.GONE
                    minutesLeftText.text = getString(R.string.main_minutes_left, nextResult.minutesLeft)
                    val following = upcoming.following
                    if (following == null) {
                        followingDepartureText.visibility = View.GONE
                    } else {
                        followingDepartureText.visibility = View.VISIBLE
                        followingDepartureText.text = getString(
                            R.string.main_following_departure,
                            following.departureAt.toLocalTime().toString(),
                            following.minutesLeft,
                        )
                    }
                }

                val times = departures.map { it.time }
                if (times.isEmpty()) {
                    departureListSummary.visibility = View.GONE
                    departureListRecycler.visibility = View.GONE
                    departureListEmpty.visibility = View.VISIBLE
                } else {
                    departureListSummary.visibility = View.VISIBLE
                    departureListSummary.text = getString(
                        R.string.main_departure_list_summary,
                        times.size,
                    )
                    departureListRecycler.visibility = View.VISIBLE
                    departureListEmpty.visibility = View.GONE
                    departureListAdapter.submit(times)
                }
            } catch (e: Exception) {
                AppLog.e("Main screen timetable render failed", e)
                heroTimeText.text = getString(R.string.main_time_placeholder)
                nextDepartureText.visibility = View.VISIBLE
                nextDepartureText.text = getString(R.string.main_error_generic)
                minutesLeftText.text = ""
                followingDepartureText.visibility = View.GONE
                departureListSummary.visibility = View.GONE
                departureListRecycler.visibility = View.GONE
                departureListEmpty.visibility = View.VISIBLE
            }
        }
    }
}
