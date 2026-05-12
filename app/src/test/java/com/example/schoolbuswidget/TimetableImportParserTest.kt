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
}
