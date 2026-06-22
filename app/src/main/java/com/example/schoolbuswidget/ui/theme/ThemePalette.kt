package com.example.schoolbuswidget.ui.theme

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.example.schoolbuswidget.R

enum class ThemePalette(
    val id: String,
    @StringRes val labelRes: Int,
    val themeRes: Int,
    @ColorRes val primaryColorRes: Int,
) {
    ORANGE("orange", R.string.theme_palette_orange, R.style.Theme_SchoolBusWidget_Orange, R.color.palette_orange_primary),
    BLUE("blue", R.string.theme_palette_blue, R.style.Theme_SchoolBusWidget_Blue, R.color.palette_blue_primary),
    GREEN("green", R.string.theme_palette_green, R.style.Theme_SchoolBusWidget_Green, R.color.palette_green_primary),
    PURPLE("purple", R.string.theme_palette_purple, R.style.Theme_SchoolBusWidget_Purple, R.color.palette_purple_primary),
    TEAL("teal", R.string.theme_palette_teal, R.style.Theme_SchoolBusWidget_Teal, R.color.palette_teal_primary),
    ;

    companion object {
        fun fromId(id: String?): ThemePalette =
            entries.firstOrNull { it.id == id } ?: ORANGE
    }
}
