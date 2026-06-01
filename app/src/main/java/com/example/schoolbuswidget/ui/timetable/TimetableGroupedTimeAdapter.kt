package com.example.schoolbuswidget.ui.timetable

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.R
import com.google.android.material.button.MaterialButton
import java.time.LocalTime

class TimetableGroupedTimeAdapter(
    private val times: MutableList<LocalTime>,
    private val onDelete: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ListItem>()
    private val expandedHours = mutableSetOf<Int>()

    init {
        rebuildItems()
    }

    fun rebuildItems() {
        items.clear()
        times.withIndex()
            .sortedBy { it.value }
            .groupBy { it.value.hour }
            .toSortedMap()
            .forEach { (hour, indexed) ->
                val expanded = hour in expandedHours
                items.add(ListItem.Header(hour, indexed.size, expanded))
                if (expanded) {
                    indexed.forEach { (index, time) ->
                        items.add(ListItem.TimeRow(index, time))
                    }
                }
            }
        notifyDataSetChanged()
    }

    private fun toggleHour(hour: Int) {
        if (hour in expandedHours) {
            expandedHours.remove(hour)
        } else {
            expandedHours.add(hour)
        }
        rebuildItems()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.Header -> VIEW_HEADER
        is ListItem.TimeRow -> VIEW_TIME
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_HEADER -> HeaderHolder(
                inflater.inflate(R.layout.item_timetable_hour_header, parent, false),
            )
            else -> TimeHolder(
                inflater.inflate(R.layout.item_timetable_time, parent, false),
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> {
                val h = holder as HeaderHolder
                h.text.text = h.text.context.getString(
                    R.string.timetable_hour_section,
                    item.hour,
                    item.count,
                )
                h.expandIcon.setImageResource(
                    if (item.expanded) R.drawable.ic_expand_less_24 else R.drawable.ic_expand_more_24,
                )
                h.itemView.contentDescription = h.itemView.context.getString(
                    if (item.expanded) R.string.timetable_hour_collapse else R.string.timetable_hour_expand,
                    item.hour,
                )
                h.itemView.setOnClickListener { toggleHour(item.hour) }
            }
            is ListItem.TimeRow -> {
                val h = holder as TimeHolder
                h.timeView.text = item.time.toString()
                h.deleteButton.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val row = items[pos] as? ListItem.TimeRow ?: return@setOnClickListener
                        onDelete(row.indexInList)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private sealed class ListItem {
        data class Header(val hour: Int, val count: Int, val expanded: Boolean) : ListItem()
        data class TimeRow(val indexInList: Int, val time: LocalTime) : ListItem()
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.textHourHeader)
        val expandIcon: ImageView = view.findViewById(R.id.imageExpandToggle)
    }

    private class TimeHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeView: TextView = view.findViewById(R.id.textTime)
        val deleteButton: MaterialButton = view.findViewById(R.id.buttonDelete)
    }

    companion object {
        private const val VIEW_HEADER = 0
        private const val VIEW_TIME = 1
    }
}
