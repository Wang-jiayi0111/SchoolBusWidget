package com.example.schoolbuswidget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.schoolbuswidget.ui.ThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.data.ScenarioRepository
import com.example.schoolbuswidget.data.WidgetPreferenceRepository
import com.example.schoolbuswidget.domain.Scenario
import com.example.schoolbuswidget.domain.ScenarioTemplate
import com.example.schoolbuswidget.ui.scenario.ScenarioListAdapter
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class WidgetConfigureActivity : ThemedActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_widget_configure)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val adapter = ScenarioListAdapter(
            subtitleFor = { templateSubtitle(it.template) },
            onClick = { scenario -> finishConfiguration(scenario) },
            onLongClick = { },
        )
        findViewById<RecyclerView>(R.id.recyclerScenarios).apply {
            layoutManager = LinearLayoutManager(this@WidgetConfigureActivity)
            this.adapter = adapter
        }

        lifecycleScope.launch {
            val scenarios = ScenarioRepository(this@WidgetConfigureActivity).listScenarios()
            adapter.submitList(scenarios)
            if (scenarios.isEmpty()) {
                Toast.makeText(this@WidgetConfigureActivity, R.string.scenario_list_empty, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun templateSubtitle(template: ScenarioTemplate): String {
        return when (template) {
            ScenarioTemplate.SIMPLE -> getString(R.string.scenario_template_simple_subtitle)
            ScenarioTemplate.MULTI_SCHEDULE -> getString(R.string.scenario_template_multi_schedule_subtitle)
            ScenarioTemplate.MULTI_PROFILE -> getString(R.string.scenario_template_multi_profile_subtitle)
        }
    }

    private fun finishConfiguration(scenario: Scenario) {
        lifecycleScope.launch {
            WidgetPreferenceRepository(this@WidgetConfigureActivity).setScenarioId(appWidgetId, scenario.id)
            val manager = AppWidgetManager.getInstance(this@WidgetConfigureActivity)
            SchoolBusAppWidgetProvider().onUpdate(
                this@WidgetConfigureActivity,
                manager,
                intArrayOf(appWidgetId),
            )
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}
