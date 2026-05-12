package com.example.schoolbuswidget.domain

import java.time.LocalTime
import java.util.regex.Pattern

object TimetableImportParser {

    private val timePattern = Pattern.compile(
        "(\\d{1,2})\\s*[:：]\\s*(\\d{2})",
    )

    fun extractTimesFromText(raw: String): List<LocalTime> {
        val normalized = normalizeFullWidthDigits(raw)
        val matcher = timePattern.matcher(normalized)
        val out = linkedSetOf<LocalTime>()
        while (matcher.find()) {
            val h = matcher.group(1)?.toIntOrNull() ?: continue
            val m = matcher.group(2)?.toIntOrNull() ?: continue
            if (h !in 0..23 || m !in 0..59) continue
            out.add(LocalTime.of(h, m))
        }
        return out.sorted()
    }

    private fun normalizeFullWidthDigits(input: String): String {
        val from = "０１２３４５６７８９："
        val to = "0123456789:"
        return input.map { c ->
            val i = from.indexOf(c)
            if (i >= 0) to[i] else c
        }.joinToString("")
    }
}
