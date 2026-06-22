package com.example.schoolbuswidget.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.schoolbuswidget.R
import com.google.android.material.button.MaterialButton

object ScheduleChoiceButtons {

    enum class Palette {
        Standard,
        SoftSelected,
    }

    fun createButton(
        context: Context,
        text: CharSequence,
        checked: Boolean = false,
        viewId: Int = View.NO_ID,
        palette: Palette = Palette.Standard,
        styleRes: Int = R.style.Widget_SchoolBus_ScheduleChoiceButton,
    ): MaterialButton {
        val (bgRes, textRes) = when (palette) {
            Palette.Standard -> R.color.chip_schedule_bg to R.color.chip_schedule_text
            Palette.SoftSelected -> R.color.chip_schedule_bg_soft to R.color.chip_schedule_text_soft
        }
        val bgColors = ContextCompat.getColorStateList(context, bgRes)
        val textColors = ContextCompat.getColorStateList(context, textRes)
        return MaterialButton(ContextThemeWrapper(context, styleRes)).apply {
            id = if (viewId != View.NO_ID) viewId else View.generateViewId()
            this.text = text
            isCheckable = true
            isChecked = checked
            isAllCaps = false
            if (styleRes == R.style.Widget_SchoolBus_ScheduleChoiceButton) {
                backgroundTintList = bgColors
                setTextColor(textColors)
            }
        }
    }

    private fun applyWrappedLabelStyle(button: MaterialButton) {
        button.isSingleLine = false
        button.maxLines = 2
        button.textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun shouldStackLabels(items: List<Pair<String, CharSequence>>): Boolean {
        return items.any { it.second.length > 8 }
    }

    fun createEqualWidthRow(context: Context, vararg buttons: MaterialButton): LinearLayout {
        val spacing = context.resources.getDimensionPixelSize(R.dimen.chip_schedule_spacing)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEachIndexed { index, button ->
                val params = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    if (index < buttons.lastIndex) {
                        marginEnd = spacing
                    }
                }
                addView(button, params)
            }
        }
    }

    fun createSeparatedChoiceRows(
        context: Context,
        buttons: List<MaterialButton>,
        columnsPerRow: Int,
    ): LinearLayout {
        val spacing = context.resources.getDimensionPixelSize(R.dimen.chip_schedule_spacing)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            buttons.chunked(columnsPerRow).forEach { rowButtons ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                rowButtons.forEachIndexed { index, button ->
                    val params = if (columnsPerRow == 1) {
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            bottomMargin = spacing
                        }
                    } else {
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        ).apply {
                            if (index < rowButtons.lastIndex) {
                                marginEnd = spacing
                            }
                            bottomMargin = spacing
                        }
                    }
                    row.addView(button, params)
                }
                addView(row)
            }
        }
    }

    fun buildExclusiveSelector(
        context: Context,
        items: List<Pair<String, CharSequence>>,
        selectedId: String?,
        onSelected: (String) -> Unit,
        columnsPerRow: Int = 3,
        compactButtonWidthPx: Int? = null,
    ): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        if (items.isEmpty()) return container

        val stacked = shouldStackLabels(items)
        val buttonStyle = if (compactButtonWidthPx != null) {
            R.style.Widget_SchoolBus_ThemeChoiceButton
        } else {
            R.style.Widget_SchoolBus_ScheduleChoiceButton
        }
        val buttons = items.map { (id, label) ->
            createButton(context, label, checked = id == selectedId, styleRes = buttonStyle).apply {
                tag = id
                if (stacked) applyWrappedLabelStyle(this)
            }
        }
        val rows = when {
            compactButtonWidthPx != null ->
                createFixedWidthChoiceRows(context, buttons, columnsPerRow, compactButtonWidthPx)
            items.size == 2 && !stacked ->
                createEqualWidthRow(context, *buttons.toTypedArray())
            stacked ->
                createSeparatedChoiceRows(context, buttons, columnsPerRow = 1)
            else ->
                createSeparatedChoiceRows(context, buttons, columnsPerRow)
        }
        container.addView(rows)
        wireExclusive(*buttons.toTypedArray()) {
            val selected = buttons.firstOrNull { it.isChecked }?.tag as? String ?: return@wireExclusive
            onSelected(selected)
        }
        return container
    }

    private fun createFixedWidthChoiceRows(
        context: Context,
        buttons: List<MaterialButton>,
        columnsPerRow: Int,
        buttonWidthPx: Int,
    ): LinearLayout {
        val spacing = context.resources.getDimensionPixelSize(R.dimen.chip_schedule_spacing)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            buttons.chunked(columnsPerRow).forEach { rowButtons ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                rowButtons.forEachIndexed { index, button ->
                    val params = LinearLayout.LayoutParams(
                        buttonWidthPx,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (index < rowButtons.lastIndex) {
                            marginEnd = spacing
                        }
                        bottomMargin = spacing
                    }
                    row.addView(button, params)
                }
                addView(row)
            }
        }
    }

    fun updateChecked(container: ViewGroup, selectedId: String?) {
        walkButtons(container) { button ->
            button.isChecked = button.tag == selectedId
        }
    }

    private fun walkButtons(view: View, action: (MaterialButton) -> Unit) {
        when (view) {
            is MaterialButton -> action(view)
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    walkButtons(view.getChildAt(index), action)
                }
            }
        }
    }
    fun wireExclusive(vararg buttons: MaterialButton, onChanged: (() -> Unit)? = null) {
        buttons.forEach { button ->
            button.setOnClickListener { clicked ->
                if (!clicked.isEnabled) return@setOnClickListener
                buttons.forEach { other ->
                    other.isChecked = other === clicked
                }
                onChanged?.invoke()
            }
        }
    }
}
