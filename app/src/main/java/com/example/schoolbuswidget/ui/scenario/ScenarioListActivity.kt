package com.example.schoolbuswidget.ui.scenario

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.MainActivity
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.data.ScenarioRepository
import com.example.schoolbuswidget.data.ThemePreferenceRepository
import com.example.schoolbuswidget.domain.Scenario
import com.example.schoolbuswidget.domain.ScenarioTemplate
import com.example.schoolbuswidget.ui.ThemedActivity
import com.example.schoolbuswidget.ui.ThemedDialogs
import com.example.schoolbuswidget.ui.applyThemedSurface
import com.example.schoolbuswidget.ui.theme.ThemeChoiceButtons
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch

class ScenarioListActivity : ThemedActivity() {

    private val scenarioRepository by lazy { ScenarioRepository(this) }
    private val themeRepository by lazy { ThemePreferenceRepository(this) }
    private lateinit var adapter: ScenarioListAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scenario_list)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.menu_scenario_list)
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_theme) {
                    showThemePickerDialog()
                    true
                } else {
                    false
                }
            }
        }

        emptyText = findViewById(R.id.textScenarioEmpty)
        adapter = ScenarioListAdapter(
            subtitleFor = { scenario -> templateSubtitle(scenario.template) },
            onClick = { scenario -> openScenario(scenario) },
            onLongClick = { scenario -> showScenarioActionsDialog(scenario) },
        )
        findViewById<RecyclerView>(R.id.recyclerScenarios).apply {
            layoutManager = LinearLayoutManager(this@ScenarioListActivity)
            adapter = this@ScenarioListActivity.adapter
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddScenario).setOnClickListener {
            showAddScenarioDialog()
        }

        loadScenarios()
    }

    override fun onResume() {
        super.onResume()
        loadScenarios()
    }

    private fun loadScenarios() {
        lifecycleScope.launch {
            val scenarios = scenarioRepository.listScenarios()
            adapter.submitList(scenarios)
            emptyText.visibility = if (scenarios.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openScenario(scenario: Scenario) {
        startActivity(MainActivity.intent(this, scenario.id))
    }

    private fun templateSubtitle(template: ScenarioTemplate): String {
        return when (template) {
            ScenarioTemplate.SIMPLE -> getString(R.string.scenario_template_simple_subtitle)
            ScenarioTemplate.MULTI_SCHEDULE -> getString(R.string.scenario_template_multi_schedule_subtitle)
            ScenarioTemplate.MULTI_PROFILE -> getString(R.string.scenario_template_multi_profile_subtitle)
        }
    }

    private fun showAddScenarioDialog() {
        val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val nameInput = EditText(this).apply {
            setSingleLine()
        }
        root.addView(nameInput)

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, pad, 0, 0)
        }
        val simpleId = View.generateViewId()
        val multiScheduleId = View.generateViewId()
        radioGroup.addView(
            RadioButton(this).apply {
                id = simpleId
                text = getString(R.string.scenario_template_simple_subtitle)
                isChecked = true
            },
        )
        radioGroup.addView(
            RadioButton(this).apply {
                id = multiScheduleId
                text = getString(R.string.scenario_template_multi_schedule_subtitle)
            },
        )
        root.addView(radioGroup)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.scenario_add)
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString().orEmpty()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.scenario_name_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val template = if (radioGroup.checkedRadioButtonId == multiScheduleId) {
                    ScenarioTemplate.MULTI_SCHEDULE
                } else {
                    ScenarioTemplate.SIMPLE
                }
                lifecycleScope.launch {
                    try {
                        scenarioRepository.addScenario(name, template)
                        loadScenarios()
                        dialog.dismiss()
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(
                            this@ScenarioListActivity,
                            R.string.scenario_limit_reached,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showThemePickerDialog() {
        lifecycleScope.launch {
            val current = themeRepository.getPalette()
            val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
            val root = ThemedDialogs.paddedRoot(this@ScenarioListActivity, pad)
            root.addView(
                TextView(this@ScenarioListActivity).apply {
                    text = getString(R.string.theme_picker_title)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
                    setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            this@ScenarioListActivity,
                            com.google.android.material.R.attr.colorOnSurface,
                            0,
                        ),
                    )
                    setPadding(
                        0,
                        0,
                        0,
                        resources.getDimensionPixelSize(R.dimen.theme_picker_title_bottom_gap),
                    )
                },
            )
            lateinit var dialog: AlertDialog
            root.addView(
                ThemeChoiceButtons.buildSelector(
                    context = this@ScenarioListActivity,
                    selectedId = current.id,
                    onSelected = { palette ->
                        lifecycleScope.launch {
                            themeRepository.setPalette(palette)
                            dialog.dismiss()
                            recreate()
                        }
                    },
                ),
            )
            dialog = ThemedDialogs.builder(this@ScenarioListActivity)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.applyThemedSurface(this@ScenarioListActivity)
            dialog.show()
        }
    }

    private fun showScenarioActionsDialog(scenario: Scenario) {
        AlertDialog.Builder(this)
            .setTitle(scenario.name)
            .setItems(
                arrayOf(
                    getString(R.string.scenario_rename),
                    getString(R.string.scenario_delete),
                ),
            ) { _, which ->
                when (which) {
                    0 -> showRenameDialog(scenario)
                    1 -> confirmDeleteScenario(scenario)
                }
            }
            .show()
    }

    private fun showRenameDialog(scenario: Scenario) {
        val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val nameInput = EditText(this).apply {
            setText(scenario.name)
            setSingleLine()
            setSelection(scenario.name.length)
        }
        root.addView(nameInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.scenario_rename)
            .setView(root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text?.toString().orEmpty()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.scenario_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    scenarioRepository.renameScenario(scenario.id, name)
                    loadScenarios()
                    refreshWidgets()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            nameInput.requestFocus()
            nameInput.selectAll()
        }
        dialog.show()
    }

    private fun confirmDeleteScenario(scenario: Scenario) {
        AlertDialog.Builder(this)
            .setTitle(R.string.scenario_delete)
            .setMessage(getString(R.string.scenario_delete_confirm, scenario.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    scenarioRepository.deleteScenario(scenario.id)
                    loadScenarios()
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
}
