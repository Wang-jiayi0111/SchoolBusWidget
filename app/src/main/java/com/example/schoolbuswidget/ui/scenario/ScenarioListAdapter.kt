package com.example.schoolbuswidget.ui.scenario

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.domain.Scenario

class ScenarioListAdapter(
    private val subtitleFor: (Scenario) -> String,
    private val onClick: (Scenario) -> Unit,
    private val onLongClick: (Scenario) -> Unit,
) : ListAdapter<Scenario, ScenarioListAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scenario, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.textScenarioName)
        private val subtitleText: TextView = itemView.findViewById(R.id.textScenarioSubtitle)

        fun bind(scenario: Scenario) {
            nameText.text = scenario.name
            subtitleText.text = subtitleFor(scenario)
            itemView.setOnClickListener { onClick(scenario) }
            itemView.setOnLongClickListener {
                onLongClick(scenario)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Scenario>() {
        override fun areItemsTheSame(oldItem: Scenario, newItem: Scenario): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Scenario, newItem: Scenario): Boolean = oldItem == newItem
    }
}
