package com.example.schoolbuswidget

import com.example.schoolbuswidget.domain.TimetableImportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimetableImportParserTest {

    @Test
    fun parses_colon_and_fullwidth_colon() {
        val text = "早班 7:20  午班 12：45"
        val times = TimetableImportParser.extractTimesFromText(text)
        assertEquals(listOf(LocalTime.of(7, 20), LocalTime.of(12, 45)), times)
    }

    @Test
    fun parses_fullwidth_digits() {
        val text = "０８：３０"
        val times = TimetableImportParser.extractTimesFromText(text)
        assertEquals(listOf(LocalTime.of(8, 30)), times)
    }

    @Test
    fun deduplicates_and_sorts() {
        val text = "10:00 9:00 10:00"
        val times = TimetableImportParser.extractTimesFromText(text)
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(10, 0)), times)
    }

    @Test
    fun skips_invalid_hour() {
        val text = "25:00 8:00"
        val times = TimetableImportParser.extractTimesFromText(text)
        assertEquals(listOf(LocalTime.of(8, 0)), times)
    }

    @Test
    fun empty_on_no_match() {
        assertTrue(TimetableImportParser.extractTimesFromText("无时间").isEmpty())
    }

    @Test
    fun parses_dot_separator_and_ratio_colon() {
        val text = "7.20  12∶45"
        val times = TimetableImportParser.extractTimesFromText(text)
        assertEquals(listOf(LocalTime.of(7, 20), LocalTime.of(12, 45)), times)
    }

    @Test
    fun parses_spaced_hour_minute() {
        val text = "早班 8  30  末班 18 05"
        val times = TimetableImportParser.extractTimesFromText(text)
        assertEquals(listOf(LocalTime.of(8, 30), LocalTime.of(18, 5)), times)
    }

    @Test
    fun parses_ocr_lookalikes_in_time_tokens() {
        val text = "12:0O  9:3O"
        val times = TimetableImportParser.extractTimesFromText(text)
        assertEquals(listOf(LocalTime.of(9, 30), LocalTime.of(12, 0)), times)
    }

    @Test
    fun does_not_treat_year_as_time() {
        val times = TimetableImportParser.extractTimesFromText("更新于2024年1月")
        assertTrue(times.isEmpty())
    }
}
