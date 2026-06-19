package com.example.schoolbuswidget.domain

import com.example.schoolbuswidget.data.rapidocr.OcrTextLine
import java.time.LocalTime

/**
 * Structured parser for the SZTU north-campus "高峰专线655" timetable poster layout
 * (same assumptions as [tools.extract_north_timetable]).
 *
 * Expects OCR lines from a **cropped schedule region** bitmap (not the full marketing poster).
 */
object NorthPeak655PosterParser {

    private val layout = Peak655ScheduleParser.Layout(
        hourRange = 6..22,
        startHourLeftMarker = "06",
        refImgW = 1210f,
        refImgH = 1108f,
        refHourXMax = 108f,
        refWorkXMin = 120f,
        refMicroY = 14f,
        refMergeGap = 36f,
        splitFallbackFraction = 0.52f,
        hour08EveryThreeWorkday = true,
        emptyHour06Rule = true,
    )

    data class Result(
        val workday: List<LocalTime>,
        val holiday: List<LocalTime>,
    )

    fun tryParse(
        lines: List<OcrTextLine>,
        imgWidth: Int,
        imgHeight: Int,
        upscaleApplied: Float = 1f,
    ): Result? {
        val parsed = Peak655ScheduleParser.tryParse(lines, imgWidth, imgHeight, upscaleApplied, layout)
            ?: return null
        return Result(parsed.workday, parsed.holiday)
    }
}
