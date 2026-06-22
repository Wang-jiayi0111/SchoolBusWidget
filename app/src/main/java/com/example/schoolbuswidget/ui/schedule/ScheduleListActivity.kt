package com.example.schoolbuswidget.ui.schedule

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.schoolbuswidget.ui.ThemedActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.data.ScheduleRepository
import com.example.schoolbuswidget.data.ScenarioRepository
import com.example.schoolbuswidget.domain.ScheduleRuleKind
import com.example.schoolbuswidget.domain.Scenario
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.domain.TimetableSchedule
import com.example.schoolbuswidget.ui.ScheduleLabels
import com.example.schoolbuswidget.ui.timetable.TimetableManageActivity
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.radiobutton.MaterialRadioButton
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ScheduleListActivity : ThemedActivity() {

    private lateinit var scenario: Scenario
    private val scheduleRepository by lazy { ScheduleRepository(this) }
    private lateinit var adapter: ScheduleListAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenarioId = intent.getStringExtra(EXTRA_SCENARIO_ID)
        if (scenarioId.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val loaded = ScenarioRepository(this@ScheduleListActivity).getScenario(scenarioId)
            if (loaded == null) {
                finish()
                return@launch
            }
            scenario = loaded
            setContentView(R.layout.activity_schedule_list)
            bindUi()
            loadSchedules()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::scenario.isInitialized) loadSchedules()
    }

    private fun bindUi() {
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = getString(R.string.schedule_list_title_for, scenario.name)
            setNavigationOnClickListener { finish() }
        }
        emptyText = findViewById(R.id.textScheduleEmpty)
        adapter = ScheduleListAdapter(
            onClick = { schedule ->
                startActivity(
                    TimetableManageActivity.intent(this, scenario.id, schedule.id),
                )
            },
            onLongClick = { schedule -> confirmDeleteSchedule(schedule) },
        )
        findViewById<RecyclerView>(R.id.recyclerSchedules).apply {
            layoutManager = LinearLayoutManager(this@ScheduleListActivity)
            adapter = this@ScheduleListActivity.adapter
        }
        findViewById<ExtendedFloatingActionButton>(R.id.fabAddSchedule).setOnClickListener {
            showAddScheduleDialog()
        }
    }

    private fun loadSchedules() {
        lifecycleScope.launch {
            val schedules = scheduleRepository.listSchedules(scenario.id)
            val items = schedules.map { schedule ->
                ScheduleListItem(
                    schedule = schedule,
                    ruleSummary = ScheduleLabels.ruleSummary(this@ScheduleListActivity, schedule),
                    timeCount = scheduleRepository.getScheduleTimes(schedule.id).size,
                )
            }
            adapter.submitList(items)
            emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddScheduleDialog() {
        val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        val subPad = pad + (8 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val nameInput = EditText(this).apply {
            setSingleLine()
            hint = getString(R.string.schedule_name_optional_hint)
        }
        root.addView(nameInput)

        val dayTypeRuleId = View.generateViewId()
        val weeklyRuleId = View.generateViewId()
        val dateRangeRuleId = View.generateViewId()

        val dayTypeRuleRadio = MaterialRadioButton(this).apply {
            id = dayTypeRuleId
            text = getString(R.string.schedule_rule_day_type)
            isChecked = true
        }
        val dayTypeOptionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(subPad, pad / 4, 0, pad / 2)
        }
        val workdayButton = createChoiceButton(getString(R.string.label_day_workday), checked = true)
        val holidayButton = createChoiceButton(getString(R.string.label_day_holiday))
        wireExclusiveChoiceButtons(workdayButton, holidayButton)
        dayTypeOptionPanel.addView(createEqualWidthChoiceRow(workdayButton, holidayButton))

        val weeklyRuleRadio = MaterialRadioButton(this).apply {
            id = weeklyRuleId
            text = getString(R.string.schedule_rule_weekly)
        }
        val weeklyOptionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(subPad, pad / 4, 0, pad / 2)
        }
        val weekdayChoices = (1..7).map { iso ->
            createChoiceButton(
                text = ScheduleLabels.weekdayLabel(this@ScheduleListActivity, iso),
            ) to iso
        }
        weeklyOptionPanel.addView(
            createSeparatedChoiceRows(weekdayChoices.map { it.first }, columnsPerRow = 3),
        )

        val dateRangeRuleRadio = MaterialRadioButton(this).apply {
            id = dateRangeRuleId
            text = getString(R.string.schedule_rule_date_range)
        }
        val dateRangeOptionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(subPad, pad / 4, 0, pad / 2)
        }
        var startDate: LocalDate? = null
        var endDate: LocalDate? = null
        val dateRangeButton = createDateRangeButton()
        fun refreshDateRangeButton() {
            dateRangeButton.text = if (startDate != null && endDate != null) {
                getString(
                    R.string.schedule_date_range_display,
                    ScheduleLabels.formatDisplayDate(startDate!!),
                    ScheduleLabels.formatDisplayDate(endDate!!),
                )
            } else {
                getString(R.string.schedule_date_empty)
            }
        }
        dateRangeButton.setOnClickListener {
            showDateRangePicker(startDate, endDate) { start, end ->
                startDate = start
                endDate = end
                refreshDateRangeButton()
            }
        }
        refreshDateRangeButton()
        dateRangeOptionPanel.addView(dateRangeButton)

        val rulesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, pad, 0, 0)
        }
        rulesContainer.addView(ruleSection(dayTypeRuleRadio, dayTypeOptionPanel))
        rulesContainer.addView(ruleSection(weeklyRuleRadio, weeklyOptionPanel))
        rulesContainer.addView(ruleSection(dateRangeRuleRadio, dateRangeOptionPanel))
        root.addView(rulesContainer)

        wireExclusiveRuleSections(
            dayTypeRuleRadio to dayTypeOptionPanel,
            weeklyRuleRadio to weeklyOptionPanel,
            dateRangeRuleRadio to dateRangeOptionPanel,
        )

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.schedule_add)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val customName = nameInput.text?.toString().orEmpty()
                val selectedRuleId = when {
                    weeklyRuleRadio.isChecked -> weeklyRuleId
                    dateRangeRuleRadio.isChecked -> dateRangeRuleId
                    else -> dayTypeRuleId
                }
                val schedule = when (selectedRuleId) {
                    weeklyRuleId -> {
                        val days = weekdayChoices
                            .filter { (button, _) -> button.isChecked }
                            .map { it.second }
                            .toSet()
                        if (days.isEmpty()) {
                            Toast.makeText(this, R.string.schedule_weekday_empty, Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        TimetableSchedule(
                            id = UUID.randomUUID().toString(),
                            name = ScheduleLabels.resolveScheduleName(
                                this,
                                customName,
                                ScheduleRuleKind.WEEKLY,
                                weekdays = days,
                            ),
                            ruleKind = ScheduleRuleKind.WEEKLY,
                            weekdays = days,
                        )
                    }
                    dateRangeRuleId -> {
                        val start = startDate
                        val end = endDate
                        if (start == null || end == null) {
                            Toast.makeText(this, R.string.schedule_date_empty, Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        if (end.isBefore(start)) {
                            Toast.makeText(this, R.string.schedule_date_invalid, Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        TimetableSchedule(
                            id = UUID.randomUUID().toString(),
                            name = ScheduleLabels.resolveScheduleName(
                                this,
                                customName,
                                ScheduleRuleKind.DATE_RANGE,
                                startDate = start,
                                endDate = end,
                            ),
                            ruleKind = ScheduleRuleKind.DATE_RANGE,
                            startDate = start,
                            endDate = end,
                        )
                    }
                    else -> {
                        val dayType = if (holidayButton.isChecked) {
                            ServiceDayType.HOLIDAY
                        } else {
                            ServiceDayType.WORKDAY
                        }
                        TimetableSchedule(
                            id = UUID.randomUUID().toString(),
                            name = ScheduleLabels.resolveScheduleName(
                                this,
                                customName,
                                ScheduleRuleKind.DAY_TYPE,
                                dayType = dayType,
                            ),
                            ruleKind = ScheduleRuleKind.DAY_TYPE,
                            dayType = dayType,
                        )
                    }
                }
                lifecycleScope.launch {
                    scheduleRepository.addSchedule(scenario, schedule)
                    loadSchedules()
                    refreshWidgets()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createEqualWidthChoiceRow(vararg buttons: MaterialButton): LinearLayout {
        val spacing = resources.getDimensionPixelSize(R.dimen.chip_schedule_spacing)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEachIndexed { index, button ->
                val params = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    if (index < buttons.lastIndex) {
                        marginEnd = spacing
                    }
                }
                addView(button, params)
            }
        }
    }

    private fun wireExclusiveChoiceButtons(vararg buttons: MaterialButton) {
        buttons.forEach { button ->
            button.setOnClickListener { clicked ->
                buttons.forEach { other ->
                    other.isChecked = other === clicked
                }
            }
        }
    }

    private fun createSeparatedChoiceRows(
        buttons: List<MaterialButton>,
        columnsPerRow: Int,
    ): LinearLayout {
        val spacing = resources.getDimensionPixelSize(R.dimen.chip_schedule_spacing)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            buttons.chunked(columnsPerRow).forEach { rowButtons ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                rowButtons.forEach { button ->
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setMargins(0, 0, spacing, spacing)
                    }
                    row.addView(button, params)
                }
                addView(row)
            }
        }
    }

    private fun createChoiceButton(
        text: CharSequence,
        checked: Boolean = false,
        viewId: Int = View.NO_ID,
    ): MaterialButton {
        val bgColors = ContextCompat.getColorStateList(this, R.color.chip_schedule_bg)
        val textColors = ContextCompat.getColorStateList(this, R.color.chip_schedule_text)
        return MaterialButton(ContextThemeWrapper(this, R.style.Widget_SchoolBus_ScheduleChoiceButton)).apply {
            id = if (viewId != View.NO_ID) viewId else View.generateViewId()
            this.text = text
            isCheckable = true
            isChecked = checked
            backgroundTintList = bgColors
            setTextColor(textColors)
        }
    }

    private fun ruleSection(ruleRadio: RadioButton, optionPanel: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ruleRadio)
            addView(optionPanel)
        }
    }

    private fun wireExclusiveRuleSections(vararg sections: Pair<RadioButton, View>) {
        var suppress = false
        fun updateSelection(selected: RadioButton) {
            if (suppress) return
            suppress = true
            sections.forEach { (radio, panel) ->
                val active = radio === selected
                radio.isChecked = active
                panel.visibility = if (active) View.VISIBLE else View.GONE
            }
            suppress = false
        }
        sections.forEach { (radio, _) ->
            radio.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    updateSelection(radio)
                } else if (!sections.any { it.first.isChecked }) {
                    suppress = true
                    radio.isChecked = true
                    suppress = false
                }
            }
        }
        updateSelection(sections.first().first)
    }

    private fun createDateRangeButton(): MaterialButton {
        return MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            isAllCaps = false
            minimumHeight = (48 * resources.displayMetrics.density).toInt()
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            strokeColor = ContextCompat.getColorStateList(this@ScheduleListActivity, R.color.chip_schedule_stroke)
            setTextColor(ContextCompat.getColor(this@ScheduleListActivity, R.color.chip_choice_unselected_text))
        }
    }

    private fun showDateRangePicker(
        start: LocalDate?,
        end: LocalDate?,
        onPicked: (LocalDate, LocalDate) -> Unit,
    ) {
        val zone = ZoneId.systemDefault()
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.schedule_rule_date_range))
            .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
        if (start != null && end != null) {
            builder.setSelection(
                androidx.core.util.Pair(
                    start.atStartOfDay(zone).toInstant().toEpochMilli(),
                    end.atStartOfDay(zone).toInstant().toEpochMilli(),
                ),
            )
        } else if (start != null) {
            val millis = start.atStartOfDay(zone).toInstant().toEpochMilli()
            builder.setSelection(androidx.core.util.Pair(millis, millis))
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val startMillis = selection.first ?: return@addOnPositiveButtonClickListener
            val endMillis = selection.second ?: return@addOnPositiveButtonClickListener
            onPicked(
                Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate(),
                Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate(),
            )
        }
        picker.show(supportFragmentManager, "date_range_picker")
    }

    private fun confirmDeleteSchedule(schedule: TimetableSchedule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.schedule_delete)
            .setMessage(getString(R.string.schedule_delete_confirm, schedule.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    scheduleRepository.deleteSchedule(scenario.id, schedule.id)
                    loadSchedules()
                    refreshWidgets()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshWidgets() {
        sendBroadcast(
            Intent(this, SchoolBusAppWidgetProvider::class.java).apply {
                action = SchoolBusAppWidgetProvider.ACTION_REFRESH_ALL
            },
        )
    }

    companion object {
        const val EXTRA_SCENARIO_ID = "scenario_id"

        fun intent(context: Context, scenarioId: String): Intent {
            return Intent(context, ScheduleListActivity::class.java).apply {
                putExtra(EXTRA_SCENARIO_ID, scenarioId)
            }
        }
    }
}
