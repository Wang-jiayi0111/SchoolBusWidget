package com.example.schoolbuswidget.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.example.schoolbuswidget.R
import com.google.android.material.button.MaterialButton

object ThemeChoiceButtons {

    fun buildSelector(
        context: Context,
        selectedId: String?,
        onSelected: (ThemePalette) -> Unit,
    ): LinearLayout {
        val spacing = context.resources.getDimensionPixelSize(R.dimen.chip_schedule_spacing)
        val buttonWidth = context.resources.getDimensionPixelSize(R.dimen.theme_choice_button_width)
        val buttonHeight = context.resources.getDimensionPixelSize(R.dimen.theme_choice_button_height)
        val columnsPerRow = 3
        val buttons = ThemePalette.entries.map { palette ->
            createSwatchButton(
                context = context,
                palette = palette,
                checked = palette.id == selectedId,
                widthPx = buttonWidth,
                heightPx = buttonHeight,
            )
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        buttons.chunked(columnsPerRow).forEach { rowButtons ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            rowButtons.forEachIndexed { index, button ->
                val params = LinearLayout.LayoutParams(buttonWidth, buttonHeight).apply {
                    if (index < rowButtons.lastIndex) marginEnd = spacing
                    bottomMargin = spacing
                }
                row.addView(button, params)
            }
            container.addView(row)
        }
        wireExclusive(buttons) { selected ->
            onSelected(selected)
        }
        return container
    }

    private fun createSwatchButton(
        context: Context,
        palette: ThemePalette,
        checked: Boolean,
        widthPx: Int,
        heightPx: Int,
    ): MaterialButton {
        val primary = ContextCompat.getColor(context, palette.primaryColorRes)
        val onPrimary = ContextCompat.getColor(context, R.color.white)
        return MaterialButton(ContextThemeWrapper(context, R.style.Widget_SchoolBus_ThemeChoiceButton)).apply {
            tag = palette
            text = context.getString(palette.labelRes)
            isCheckable = true
            isChecked = checked
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(primary)
            setTextColor(onPrimary)
            applySelectionRing(checked)
            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx)
        }
    }

    private fun MaterialButton.applySelectionRing(selected: Boolean) {
        val strokePx = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
        if (selected) {
            strokeWidth = strokePx
            strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.white),
            )
            elevation = 6f
        } else {
            strokeWidth = 0
            elevation = 0f
        }
    }

    private fun wireExclusive(
        buttons: List<MaterialButton>,
        onSelected: (ThemePalette) -> Unit,
    ) {
        buttons.forEach { button ->
            button.setOnClickListener { clicked ->
                buttons.forEach { other ->
                    val selected = other === clicked
                    other.isChecked = selected
                    other.applySelectionRing(selected)
                }
                val palette = clicked.tag as? ThemePalette ?: return@setOnClickListener
                onSelected(palette)
            }
        }
    }
}
