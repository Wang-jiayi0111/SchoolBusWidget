package com.example.schoolbuswidget.ui

import android.content.Context
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.data.WidgetPreferenceRepository
import com.example.schoolbuswidget.domain.DayTypeResolutionSource
import com.example.schoolbuswidget.domain.ServiceDayType

object DayTypeLabels {

    fun location(context: Context, northSelected: Boolean): String {
        return context.getString(
            if (northSelected) R.string.label_campus_north else R.string.label_campus_south,
        )
    }

    fun serviceDayType(context: Context, dayType: ServiceDayType): String {
        return context.getString(
            if (dayType == ServiceDayType.WORKDAY) {
                R.string.label_day_workday
            } else {
                R.string.label_day_holiday
            },
        )
    }

    fun modeName(context: Context, mode: Int): String {
        return when (mode) {
            WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_WORKDAY -> context.getString(R.string.label_day_workday)
            WidgetPreferenceRepository.DAY_TYPE_MODE_MANUAL_HOLIDAY -> context.getString(R.string.label_day_holiday)
            else -> context.getString(R.string.label_day_auto)
        }
    }

    fun compactSelection(
        context: Context,
        northSelected: Boolean,
        mode: Int,
        resolvedDayType: ServiceDayType,
    ): String {
        val loc = location(context, northSelected)
        val second = if (mode == WidgetPreferenceRepository.DAY_TYPE_MODE_AUTO) {
            context.getString(
                R.string.label_auto_with_resolved,
                serviceDayType(context, resolvedDayType),
            )
        } else {
            modeName(context, mode)
        }
        return context.getString(R.string.widget_header_selection, loc, second)
    }

    fun selectionSummary(
        context: Context,
        northSelected: Boolean,
        mode: Int,
        resolvedDayType: ServiceDayType,
    ): String {
        val loc = location(context, northSelected)
        val second = if (mode == WidgetPreferenceRepository.DAY_TYPE_MODE_AUTO) {
            context.getString(
                R.string.label_auto_with_resolved,
                serviceDayType(context, resolvedDayType),
            )
        } else {
            modeName(context, mode)
        }
        return context.getString(R.string.format_selection_line, loc, second)
    }

    fun resolutionSourceCaption(context: Context, source: DayTypeResolutionSource): String {
        return when (source) {
            DayTypeResolutionSource.HOLIDAY_API -> context.getString(R.string.caption_source_holiday_api)
            DayTypeResolutionSource.HOLIDAY_CACHE -> context.getString(R.string.caption_source_holiday_cache)
            DayTypeResolutionSource.HOLIDAY_BUNDLED -> context.getString(R.string.caption_source_holiday_bundled)
            DayTypeResolutionSource.WEEK_RULE -> context.getString(R.string.caption_source_week_rule)
            DayTypeResolutionSource.MANUAL_OVERRIDE -> context.getString(R.string.caption_source_manual)
        }
    }
}
