package com.example.schoolbuswidget.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import java.time.LocalTime

object AlignedHourTimesGrid {

    const val TIMES_PER_ROW = 4

    data class Style(
        val textSizeSp: Float = 14f,
        val rowStartPaddingPx: Int = 0,
        val rowBottomMarginPx: Int = 0,
        val textSelectable: Boolean = false,
    )

    fun bind(container: LinearLayout, times: List<LocalTime>, style: Style = Style()) {
        container.removeAllViews()
        appendTo(container, times, style)
    }

    fun appendTo(container: LinearLayout, times: List<LocalTime>, style: Style = Style()) {
        if (times.isEmpty()) return
        val rowGapPx = defaultRowGapPx(container.context)
        times.chunked(TIMES_PER_ROW).forEach { rowTimes ->
            container.addView(buildRow(container.context, rowTimes, style, rowGapPx))
        }
    }

    private fun buildRow(
        context: Context,
        rowTimes: List<LocalTime>,
        style: Style,
        defaultRowGapPx: Int,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = style.rowBottomMarginPx.takeIf { it > 0 } ?: defaultRowGapPx
            }
            setPadding(style.rowStartPaddingPx, 0, 0, 0)
            repeat(TIMES_PER_ROW) { column ->
                if (column > 0) {
                    addView(createDividerCell(context, style))
                }
                addView(createTimeCell(context, rowTimes.getOrNull(column), style))
            }
        }

    private fun createTimeCell(
        context: Context,
        time: LocalTime?,
        style: Style,
    ): TextView =
        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = time?.toString().orEmpty()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            }
            if (style.textSelectable) {
                setTextIsSelectable(true)
            }
        }

    private fun createDividerCell(context: Context, style: Style): TextView {
        val dividerColor = MaterialColors.getColor(context, R.attr.colorOutlineVariant, 0)
        val dividerWidthPx = dividerWidthPx(context)
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dividerWidthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            text = "|"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor(dividerColor)
            alpha = 0.45f
            maxLines = 1
            includeFontPadding = false
        }
    }

    private fun dividerWidthPx(context: Context): Int =
        (6 * context.resources.displayMetrics.density).toInt()

    private fun defaultRowGapPx(context: Context): Int =
        (2 * context.resources.displayMetrics.density).toInt()
}
