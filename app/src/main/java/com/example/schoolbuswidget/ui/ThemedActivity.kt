package com.example.schoolbuswidget.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.schoolbuswidget.data.ThemePreferenceRepository
import kotlinx.coroutines.runBlocking

abstract class ThemedActivity : AppCompatActivity() {

    private var appliedPaletteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val palette = runBlocking { ThemePreferenceRepository(this@ThemedActivity).getPalette() }
        setTheme(palette.themeRes)
        appliedPaletteId = palette.id
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val currentId = runBlocking { ThemePreferenceRepository(this@ThemedActivity).getPalette() }.id
        if (appliedPaletteId != null && appliedPaletteId != currentId) {
            recreate()
        }
    }
}
