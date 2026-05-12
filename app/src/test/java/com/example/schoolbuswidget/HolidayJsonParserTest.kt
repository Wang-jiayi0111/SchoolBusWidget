package com.example.schoolbuswidget

import com.example.schoolbuswidget.data.holiday.HolidayJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HolidayJsonParserTest {

    @Test
    fun parses_flat_month_day_keys() {
        val json = """{"01-01":{"holiday":true},"02-08":{"holiday":false}}"""
        val map = HolidayJsonParser.parseYearHolidayFlags(json)
        assertNotNull(map)
        assertEquals(true, map!!["01-01"])
        assertEquals(false, map["02-08"])
    }

    @Test
    fun parses_nested_holiday_object() {
        val json = """{"code":0,"holiday":{"01-01":{"holiday":true}}}"""
        val map = HolidayJsonParser.parseYearHolidayFlags(json)
        assertNotNull(map)
        assertEquals(true, map!!["01-01"])
    }

    @Test
    fun returns_null_for_invalid_json() {
        assertNull(HolidayJsonParser.parseYearHolidayFlags("not json"))
    }
}
