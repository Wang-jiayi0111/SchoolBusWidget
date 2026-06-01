package com.example.schoolbuswidget.domain

import com.example.schoolbuswidget.data.rapidocr.OcrTextLine
import java.time.LocalTime

/**
 * Structured parser for the SZTU south-campus "高峰专线655" timetable poster
 * (same assumptions as [tools.extract_south_timetable]).
 */
object SouthPeak655PosterParser {

    private val layout = Peak655ScheduleParser.Layout(
        hourRange = 7..22,
        startHourLeftMarker = "07",
        refImgW = 1902f,
        refImgH = 1650f,
        refHourXMax = 127f,
        refWorkXMin = 141f,
        refMicroY = 14f,
        refMergeGap = 36f,
        splitFallbackFraction = 0.66f,
        hour08EveryThreeWorkday = false,
        emptyHour06Rule = false,
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
