package com.example.schoolbuswidget.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.domain.DepartureHourGrouper
import java.time.LocalTime

class DepartureHourGroupAdapter : RecyclerView.Adapter<DepartureHourGroupAdapter.Holder>() {

    private val groups = mutableListOf<DepartureHourGrouper.HourGroup>()

    fun submit(times: List<LocalTime>) {
        groups.clear()
        groups.addAll(DepartureHourGrouper.group(times))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_departure_hour_group, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val group = groups[position]
        holder.hourView.text = "%02d".format(group.hour)
        holder.timesView.text = group.times.joinToString("  ") { it.toString() }
    }

    override fun getItemCount(): Int = groups.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val hourView: TextView = view.findViewById(R.id.textHour)
        val timesView: TextView = view.findViewById(R.id.textTimes)
    }
}
