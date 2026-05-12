package com.example.schoolbuswidget.ui.timetable

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.R
import com.google.android.material.button.MaterialButton
import java.time.LocalTime

class TimetableTimeAdapter(
    private val times: MutableList<LocalTime>,
    private val onDelete: (Int) -> Unit,
) : RecyclerView.Adapter<TimetableTimeAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable_time, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val time = times[position]
        holder.timeView.text = time.toString()
        holder.deleteButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDelete(pos)
        }
    }

    override fun getItemCount(): Int = times.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val timeView: TextView = view.findViewById(R.id.textTime)
        val deleteButton: MaterialButton = view.findViewById(R.id.buttonDelete)
    }
}
