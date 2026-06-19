package com.example.schoolbuswidget.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.TextPaint
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
        val columnWidthPx = timeColumnWidthPx(container.context, style.textSizeSp)
        times.chunked(TIMES_PER_ROW).forEach { rowTimes ->
            container.addView(buildRow(container.context, rowTimes, style, rowGapPx, columnWidthPx))
        }
    }

    private fun buildRow(
        context: Context,
        rowTimes: List<LocalTime>,
        style: Style,
        defaultRowGapPx: Int,
        columnWidthPx: Int,
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
                addView(createTimeCell(context, rowTimes.getOrNull(column), style, columnWidthPx))
            }
        }

    private fun createTimeCell(
        context: Context,
        time: LocalTime?,
        style: Style,
        columnWidthPx: Int,
    ): TextView =
        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(columnWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
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

    private fun timeColumnWidthPx(context: Context, textSizeSp: Float): Int {
        val paint = TextPaint().apply {
            typeface = Typeface.MONOSPACE
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics,
            )
        }
        val horizontalPaddingPx = (4 * context.resources.displayMetrics.density).toInt()
        return paint.measureText("88:88").toInt() + horizontalPaddingPx * 2
    }

    private fun defaultRowGapPx(context: Context): Int =
        (2 * context.resources.displayMetrics.density).toInt()
}
