package com.example.schoolbuswidget.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.domain.TimetableSchedule

data class ScheduleListItem(
    val schedule: TimetableSchedule,
    val ruleSummary: String,
    val timeCount: Int,
)

class ScheduleListAdapter(
    private val onClick: (TimetableSchedule) -> Unit,
    private val onLongClick: (TimetableSchedule) -> Unit,
) : ListAdapter<ScheduleListItem, ScheduleListAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.textScheduleName)
        private val ruleText: TextView = itemView.findViewById(R.id.textScheduleRule)
        private val countText: TextView = itemView.findViewById(R.id.textScheduleTimesCount)

        fun bind(item: ScheduleListItem) {
            nameText.text = item.schedule.name
            ruleText.text = item.ruleSummary
            countText.text = itemView.context.getString(R.string.schedule_time_count, item.timeCount)
            itemView.setOnClickListener { onClick(item.schedule) }
            itemView.setOnLongClickListener {
                onLongClick(item.schedule)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ScheduleListItem>() {
        override fun areItemsTheSame(oldItem: ScheduleListItem, newItem: ScheduleListItem): Boolean =
            oldItem.schedule.id == newItem.schedule.id

        override fun areContentsTheSame(oldItem: ScheduleListItem, newItem: ScheduleListItem): Boolean =
            oldItem == newItem
    }
}
