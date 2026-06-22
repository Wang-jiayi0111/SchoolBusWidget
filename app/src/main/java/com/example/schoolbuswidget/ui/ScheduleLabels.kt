package com.example.schoolbuswidget.ui

import android.content.Context
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.domain.ScheduleRuleKind
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.domain.TimetableSchedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object ScheduleLabels {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    fun formatDisplayDate(date: LocalDate): String = date.format(displayDateFormatter)

    fun ruleSummary(context: Context, schedule: TimetableSchedule): String {
        return when (schedule.ruleKind) {
            ScheduleRuleKind.DAY_TYPE -> {
                val label = when (schedule.dayType) {
                    ServiceDayType.WORKDAY -> context.getString(R.string.label_day_workday)
                    ServiceDayType.HOLIDAY -> context.getString(R.string.label_day_holiday)
                    null -> context.getString(R.string.schedule_rule_day_type)
                }
                context.getString(R.string.schedule_rule_day_type_summary, label)
            }
            ScheduleRuleKind.WEEKLY -> {
                val days = (schedule.weekdays ?: emptySet())
                    .sorted()
                    .joinToString("、") { weekdayLabel(context, it) }
                context.getString(R.string.schedule_rule_weekly_summary, days.ifBlank { "—" })
            }
            ScheduleRuleKind.DATE_RANGE -> {
                val start = schedule.startDate?.let { formatDisplayDate(it) } ?: "—"
                val end = schedule.endDate?.let { formatDisplayDate(it) } ?: "—"
                context.getString(R.string.schedule_rule_date_range_summary, start, end)
            }
        }
    }

    fun activeSchedulesCaption(context: Context, names: List<String>): String {
        return if (names.isEmpty()) {
            context.getString(R.string.schedule_active_none)
        } else {
            context.getString(R.string.schedule_active_today, names.joinToString("、"))
        }
    }

    fun weekdayLabel(context: Context, isoDay: Int): String {
        return when (isoDay) {
            1 -> context.getString(R.string.weekday_mon)
            2 -> context.getString(R.string.weekday_tue)
            3 -> context.getString(R.string.weekday_wed)
            4 -> context.getString(R.string.weekday_thu)
            5 -> context.getString(R.string.weekday_fri)
            6 -> context.getString(R.string.weekday_sat)
            7 -> context.getString(R.string.weekday_sun)
            else -> "?"
        }
    }

    fun defaultScheduleName(
        context: Context,
        ruleKind: ScheduleRuleKind,
        dayType: ServiceDayType? = null,
        weekdays: Set<Int>? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): String {
        return when (ruleKind) {
            ScheduleRuleKind.DAY_TYPE -> when (dayType) {
                ServiceDayType.HOLIDAY -> context.getString(R.string.label_day_holiday)
                else -> context.getString(R.string.label_day_workday)
            }
            ScheduleRuleKind.WEEKLY -> (weekdays ?: emptySet())
                .sorted()
                .joinToString("、") { weekdayLabel(context, it) }
            ScheduleRuleKind.DATE_RANGE -> {
                val start = startDate?.let { formatDisplayDate(it) } ?: "—"
                val end = endDate?.let { formatDisplayDate(it) } ?: "—"
                context.getString(R.string.schedule_default_name_date_range, start, end)
            }
        }
    }

    fun resolveScheduleName(
        context: Context,
        customName: String,
        ruleKind: ScheduleRuleKind,
        dayType: ServiceDayType? = null,
        weekdays: Set<Int>? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): String {
        val trimmed = customName.trim()
        if (trimmed.isNotEmpty()) return trimmed
        return defaultScheduleName(context, ruleKind, dayType, weekdays, startDate, endDate)
    }
}
