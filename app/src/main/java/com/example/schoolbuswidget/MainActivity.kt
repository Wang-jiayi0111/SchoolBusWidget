package com.example.schoolbuswidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.schoolbuswidget.ui.ThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.data.ScheduleRepository
import com.example.schoolbuswidget.data.ScenarioRepository
import com.example.schoolbuswidget.data.TimetableDataStoreRepository
import com.example.schoolbuswidget.data.WidgetPreferenceRepository
import com.example.schoolbuswidget.data.holiday.HolidayCalendarRepository
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.EffectiveDayTypeResolver
import com.example.schoolbuswidget.domain.EffectiveScheduleResolver
import com.example.schoolbuswidget.domain.NextDepartureCalculator
import com.example.schoolbuswidget.domain.Scenario
import com.example.schoolbuswidget.domain.ScenarioTemplate
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.domain.resolveServiceDayTypeForMode
import com.example.schoolbuswidget.domain.TimetableSchedule
import com.example.schoolbuswidget.ui.DepartureHourGroupAdapter
import com.example.schoolbuswidget.ui.DayTypeLabels
import com.example.schoolbuswidget.ui.ScheduleChoiceButtons
import com.example.schoolbuswidget.ui.ScheduleLabels
import com.example.schoolbuswidget.ui.timetable.TimetableManageActivity
import com.example.schoolbuswidget.ui.schedule.ScheduleListActivity
import com.example.schoolbuswidget.util.AppLog
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class MainActivity : ThemedActivity() {

    private lateinit var scenario: Scenario
    private var selectedLocation = CampusLocation.NORTH
    private var dayTypeMode = WidgetPreferenceRepository.DAY_TYPE_MODE_AUTO
    private var selectedScheduleId: String? = null
    private var lastBuiltScheduleSignature: String = ""
    private val departureListAdapter = DepartureHourGroupAdapter()
    private var syncingSelectionButtons = false

    private lateinit var buttonCampusNorth: MaterialButton
    private lateinit var buttonCampusSouth: MaterialButton
    private lateinit var buttonDayAuto: MaterialButton
    private lateinit var buttonDayWorkday: MaterialButton
    private lateinit var buttonDayHoliday: MaterialButton
    private lateinit var containerCampusChoices: LinearLayout
    private lateinit var containerDayTypeChoices: LinearLayout
    private lateinit var textDayTypeSource: TextView
    private lateinit var textMainSectionActions: TextView
    private lateinit var containerScheduleChoices: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenarioId = intent.getStringExtra(EXTRA_SCENARIO_ID)
        if (scenarioId.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val loaded = ScenarioRepository(this@MainActivity).getScenario(scenarioId)
            if (loaded == null) {
                finish()
                return@launch
            }
            scenario = loaded
            setContentView(R.layout.activity_main)
            bindViews()
            setupDepartureListRecycler()
            setupAppControls()
            applyScenarioUi()
            loadSelectionFromPreferences()
            renderAppTimetable()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::scenario.isInitialized) return
        loadSelectionFromPreferences()
        renderAppTimetable()
    }

    private fun bindViews() {
        containerCampusChoices = findViewById(R.id.containerCampusChoices)
        containerDayTypeChoices = findViewById(R.id.containerDayTypeChoices)
        textDayTypeSource = findViewById(R.id.textDayTypeSource)
        textMainSectionActions = findViewById(R.id.textMainSectionActions)
        containerScheduleChoices = findViewById(R.id.containerScheduleChoices)
        findViewById<TextView>(R.id.textMainHeadline).text = scenario.name
    }

    private fun applyScenarioUi() {
        when (scenario.template) {
            ScenarioTemplate.SIMPLE -> {
                containerCampusChoices.visibility = View.GONE
                containerDayTypeChoices.visibility = View.GONE
                containerScheduleChoices.visibility = View.GONE
                textMainSectionActions.visibility = View.GONE
            }
            ScenarioTemplate.MULTI_SCHEDULE -> {
                containerCampusChoices.visibility = View.GONE
                containerDayTypeChoices.visibility = View.GONE
                containerScheduleChoices.visibility = View.VISIBLE
                textMainSectionActions.visibility = View.VISIBLE
                textMainSectionActions.text = getString(R.string.main_section_schedule)
            }
            ScenarioTemplate.MULTI_PROFILE -> {
                containerCampusChoices.visibility = View.VISIBLE
                containerDayTypeChoices.visibility = View.VISIBLE
                containerScheduleChoices.visibility = View.GONE
                textMainSectionActions.visibility = View.VISIBLE
                textMainSectionActions.text = getString(R.string.main_section_actions)
            }
        }
    }

    private fun setupDepartureListRecycler() {
        val recycler = findViewById<RecyclerView>(R.id.recyclerDepartureList)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = departureListAdapter
        recycler.isNestedScrollingEnabled = false
    }

    private fun setupAppControls() {
        setupProfileChoiceButtons()

        findViewById<MaterialButton>(R.id.buttonAppManageTimetable).setOnClickListener {
            val target = when (scenario.template) {
                ScenarioTemplate.MULTI_SCHEDULE -> ScheduleListActivity.intent(this, scenario.id)
                else -> TimetableManageActivity.intent(this, scenario.id)
            }
            startActivity(target)
        }
    }

    private fun setupProfileChoiceButtons() {
        if (containerCampusChoices.childCount > 0) return

        buttonCampusNorth = ScheduleChoiceButtons.createButton(
            this,
            getString(R.string.label_campus_north),
            checked = true,
        )
        buttonCampusSouth = ScheduleChoiceButtons.createButton(
            this,
            getString(R.string.label_campus_south),
        )
        containerCampusChoices.addView(
            ScheduleChoiceButtons.createEqualWidthRow(this, buttonCampusNorth, buttonCampusSouth),
        )

        buttonDayAuto = ScheduleChoiceButtons.createButton(
            this,
            getString(R.string.label_day_auto),
            checked = true,
        )
        buttonDayWorkday = ScheduleChoiceButtons.createButton(
            this,
            getString(R.string.label_day_workday),
        )
        buttonDayHoliday = ScheduleChoiceButtons.createButton(
            this,
            getString(R.string.label_day_holiday),
        )
        containerDayTypeChoices.addView(
            ScheduleChoiceButtons.createSeparatedChoiceRows(
                this,
                listOf(buttonDayAuto, buttonDayWorkday, buttonDayHoliday),
                columnsPerRow = 3,
            ),
        )

        ScheduleChoiceButtons.wireExclusive(buttonCampusNorth, buttonCampusSouth) {
            if (syncingSelectionButtons) return@wireExclusive
            selectedLocation = if (buttonCampusNorth.isChecked) {
                CampusLocation.NORTH
            } else {
                CampusLocation.SOUTH
            }
            persistSelectionAndSyncWidget()
            renderAppTimetable()
        }
        ScheduleChoiceButtons.wireExclusive(buttonDayAuto, buttonDayWorkday, buttonDayHoliday) {
            if (syncingSelectionButtons) return@wireExclusive
            dayTypeMode = when {
                buttonDayWorkday.isChecked -> WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_WORKDAY
                buttonDayHoliday.isChecked -> WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_HOLIDAY
                else -> WidgetPreferenceRepository.DAY_TYPE_MODE_AUTO
            }
            persistSelectionAndSyncWidget()
            renderAppTimetable()
        }
    }

    private fun syncSelectionButtonsFromState() {
        if (scenario.template == ScenarioTemplate.MULTI_SCHEDULE) return
        if (containerCampusChoices.childCount == 0) return
        syncingSelectionButtons = true
        buttonCampusNorth.isChecked = selectedLocation == CampusLocation.NORTH
        buttonCampusSouth.isChecked = selectedLocation == CampusLocation.SOUTH
        buttonDayAuto.isChecked = dayTypeMode == WidgetPreferenceRepository.DAY_TYPE_MODE_AUTO
        buttonDayWorkday.isChecked = dayTypeMode == WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_WORKDAY
        buttonDayHoliday.isChecked = dayTypeMode == WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_HOLIDAY
        syncingSelectionButtons = false
    }

    private fun loadSelectionFromPreferences() {
        if (scenario.template == ScenarioTemplate.SIMPLE) {
            return
        }
        runBlocking {
            val preferences = WidgetPreferenceRepository(this@MainActivity)
            when (scenario.template) {
                ScenarioTemplate.MULTI_PROFILE -> {
                    selectedLocation = if (preferences.getGlobalLocationIndex() == 0) {
                        CampusLocation.NORTH
                    } else {
                        CampusLocation.SOUTH
                    }
                    dayTypeMode = preferences.getDayTypeMode()
                }
                ScenarioTemplate.MULTI_SCHEDULE -> {
                    selectedScheduleId = preferences.getSelectedScheduleId(scenario.id)
                }
                else -> Unit
            }
        }
        syncSelectionButtonsFromState()
    }

    private fun persistSelectionAndSyncWidget() {
        if (scenario.template != ScenarioTemplate.MULTI_PROFILE) return
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

    private fun rebuildScheduleChoices(
        schedules: List<TimetableSchedule>,
        selectedId: String?,
    ) {
        val signature = schedules.joinToString("|") { "${it.id}:${it.name}" }
        if (signature == lastBuiltScheduleSignature && containerScheduleChoices.childCount > 0) {
            ScheduleChoiceButtons.updateChecked(containerScheduleChoices, selectedId)
            return
        }
        lastBuiltScheduleSignature = signature
        containerScheduleChoices.removeAllViews()
        if (schedules.isEmpty()) return

        val selector = ScheduleChoiceButtons.buildExclusiveSelector(
            context = this,
            items = schedules.map { it.id to it.name },
            selectedId = selectedId,
            onSelected = { scheduleId ->
                selectedScheduleId = scheduleId
                lifecycleScope.launch {
                    WidgetPreferenceRepository(this@MainActivity)
                        .setSelectedScheduleId(scenario.id, scheduleId)
                    renderAppTimetable()
                }
            },
        )
        containerScheduleChoices.addView(selector)
    }

    private suspend fun resolveSelectedScheduleId(
        schedules: List<TimetableSchedule>,
        activeToday: List<TimetableSchedule>,
    ): String? {
        if (schedules.isEmpty()) return null
        val saved = selectedScheduleId
        if (saved != null && schedules.any { it.id == saved }) return saved
        return activeToday.firstOrNull()?.id ?: schedules.first().id
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

        runBlocking {
            try {
                val now = LocalDateTime.now()
                val repository = TimetableDataStoreRepository(this@MainActivity)
                val holidayRepository = HolidayCalendarRepository(this@MainActivity)
                val effectiveResolver = EffectiveDayTypeResolver(holidayRepository)
                val dayResolution = when (scenario.template) {
                    ScenarioTemplate.SIMPLE -> null
                    ScenarioTemplate.MULTI_SCHEDULE -> effectiveResolver.resolve(now.toLocalDate())
                    else -> resolveServiceDayTypeForMode(
                        dayTypeMode,
                        now.toLocalDate(),
                        effectiveResolver,
                    )
                }

                val departures = when (scenario.template) {
                    ScenarioTemplate.MULTI_SCHEDULE -> {
                        val scheduleRepository = ScheduleRepository(this@MainActivity)
                        val schedules = scheduleRepository.listSchedules(scenario.id)
                        val activeToday = EffectiveScheduleResolver.matchingSchedules(
                            schedules,
                            now.toLocalDate(),
                            dayResolution!!.dayType,
                        )
                        selectedScheduleId = resolveSelectedScheduleId(schedules, activeToday)
                        rebuildScheduleChoices(schedules, selectedScheduleId)

                        val selected = schedules.find { it.id == selectedScheduleId }
                        if (selected == null) {
                            emptyList()
                        } else {
                            scheduleRepository.getScheduleTimes(selected.id)
                        }
                    }
                    else -> {
                        repository.getScenarioDepartures(
                            scenario = scenario,
                            location = selectedLocation,
                            dayType = dayResolution?.dayType ?: ServiceDayType.WORKDAY,
                        )
                    }
                }

                val upcoming = NextDepartureCalculator().calculateUpcoming(now, departures)

                selectionText.text = when (scenario.template) {
                    ScenarioTemplate.SIMPLE -> getString(
                        R.string.main_scenario_simple_selection,
                        scenario.name,
                    )
                    ScenarioTemplate.MULTI_SCHEDULE -> {
                        val scheduleRepository = ScheduleRepository(this@MainActivity)
                        val selected = scheduleRepository.listSchedules(scenario.id)
                            .find { it.id == selectedScheduleId }
                        if (selected == null) {
                            getString(R.string.schedule_active_none)
                        } else {
                            getString(R.string.main_schedule_selected, selected.name)
                        }
                    }
                    ScenarioTemplate.MULTI_PROFILE -> DayTypeLabels.selectionSummary(
                        this@MainActivity,
                        selectedLocation == CampusLocation.NORTH,
                        dayTypeMode,
                        dayResolution!!.dayType,
                    )
                }

                when (scenario.template) {
                    ScenarioTemplate.SIMPLE -> textDayTypeSource.visibility = View.GONE
                    ScenarioTemplate.MULTI_SCHEDULE -> {
                        val scheduleRepository = ScheduleRepository(this@MainActivity)
                        val schedules = scheduleRepository.listSchedules(scenario.id)
                        val selected = schedules.find { it.id == selectedScheduleId }
                        val activeToday = EffectiveScheduleResolver.matchingSchedules(
                            schedules,
                            now.toLocalDate(),
                            dayResolution!!.dayType,
                        )
                        textDayTypeSource.visibility = View.VISIBLE
                        textDayTypeSource.text = when {
                            selected == null -> ""
                            activeToday.any { it.id == selected.id } ->
                                ScheduleLabels.ruleSummary(this@MainActivity, selected)
                            else -> getString(
                                R.string.schedule_not_active_today,
                                ScheduleLabels.ruleSummary(this@MainActivity, selected),
                            )
                        }
                    }
                    else -> {
                        textDayTypeSource.visibility = View.VISIBLE
                        textDayTypeSource.text = DayTypeLabels.resolutionSourceCaption(
                            this@MainActivity,
                            dayResolution!!.source,
                        )
                    }
                }

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

    companion object {
        const val EXTRA_SCENARIO_ID = "scenario_id"

        fun intent(context: Context, scenarioId: String): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_SCENARIO_ID, scenarioId)
            }
        }
    }
}
