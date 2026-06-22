package com.example.schoolbuswidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.schoolbuswidget.ui.theme.ThemePalette
import kotlinx.coroutines.flow.first

class ThemePreferenceRepository(
    private val context: Context,
) {
    suspend fun getPalette(): ThemePalette {
        val id = context.timetableDataStore.data.first()[paletteKey]
        return ThemePalette.fromId(id)
    }

    suspend fun setPalette(palette: ThemePalette) {
        context.timetableDataStore.edit { prefs ->
            prefs[paletteKey] = palette.id
        }
    }

    private val paletteKey = stringPreferencesKey("app_theme_palette")
}
