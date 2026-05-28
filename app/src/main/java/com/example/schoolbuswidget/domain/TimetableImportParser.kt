package com.example.schoolbuswidget.domain

import java.time.LocalTime
import java.util.regex.Pattern

object TimetableImportParser {

    /** H:mm with ASCII / fullwidth colon and common OCR separators (ratio dot, middle dot, fullwidth dot). */
    private val timeWithSeparator = Pattern.compile(
        "([0-9OoIl|０-９]{1,2})\\s*[:：∶.．·｡]\\s*([0-9OoIl|SsZzBb０-９]{1,2})",
    )

    /**
     * e.g. "8 30" or "08  15" when minutes are two digits.
     * Must not start right after a digit / colon / dot (avoids "7.20  12…" → 20:12).
     */
    private val spacedHourMinute = Pattern.compile(
        "(?<![0-9:：.．０-９])([01]?[0-9]|2[0-3])\\s{1,4}([0-5][0-9])(?![0-9０-９])",
    )

    fun extractTimesFromText(raw: String): List<LocalTime> {
        val normalized = normalizeForOcrText(raw)
        val out = linkedSetOf<LocalTime>()
        collectFromPattern(timeWithSeparator, normalized, out, hourGroup = 1, minuteGroup = 2)
        collectFromPattern(spacedHourMinute, normalized, out, hourGroup = 1, minuteGroup = 2)
        return out.sorted()
    }

    private fun collectFromPattern(
        pattern: Pattern,
        text: String,
        out: MutableSet<LocalTime>,
        hourGroup: Int,
        minuteGroup: Int,
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val h = ocrDigitsToInt(matcher.group(hourGroup)) ?: continue
            val m = ocrDigitsToInt(matcher.group(minuteGroup)) ?: continue
            if (h !in 0..23 || m !in 0..59) continue
            out.add(LocalTime.of(h, m))
        }
    }

    internal fun normalizeForOcrText(input: String): String {
        var s = normalizeFullWidthDigits(input)
        s = normalizeDigitLookalikesBetweenNumbers(s)
        return s
    }

    private fun normalizeFullWidthDigits(input: String): String {
        val from = "０１２３４５６７８９：．"
        val to = "0123456789:."
        return input.map { c ->
            val i = from.indexOf(c)
            if (i >= 0) to[i] else c
        }.joinToString("")
    }

    /**
     * Fix common OCR confusions when the character sits between digits or next to a time separator.
     */
    private fun normalizeDigitLookalikesBetweenNumbers(s: String): String {
        var t = s
        val digitNeighbor = "[0-9]"
        val sep = "[:：∶.．·]"
        val rules = listOf(
            "(?<=$digitNeighbor)[Oo](?=$digitNeighbor)" to "0",
            "(?<=$sep)[Oo](?=$digitNeighbor)" to "0",
            "(?<=$digitNeighbor)[Oo](?=$sep)" to "0",
            "(?<=$digitNeighbor)[Il|｜](?=$digitNeighbor)" to "1",
            "(?<=$sep)[Il|｜](?=$digitNeighbor)" to "1",
            "(?<=$digitNeighbor)[Il|｜](?=$sep)" to "1",
            "(?<=$digitNeighbor)[Ss](?=$digitNeighbor)" to "5",
            "(?<=$digitNeighbor)[Zz](?=$digitNeighbor)" to "2",
            "(?<=$digitNeighbor)[Bb](?=$digitNeighbor)" to "8",
        )
        for ((regex, repl) in rules) {
            t = t.replace(Regex(regex), repl)
        }
        return t
    }

    private fun ocrDigitsToInt(fragment: String?): Int? {
        if (fragment.isNullOrBlank()) return null
        val cleaned = fragment.map { c ->
            when (c) {
                'O', 'o' -> '0'
                'l', 'I', 'i', '|', '｜' -> '1'
                'S', 's' -> '5'
                'Z', 'z' -> '2'
                'B', 'b' -> '8'
                in '0'..'9' -> c
                else -> return null
            }
        }.joinToString("")
        return cleaned.toIntOrNull()
    }
}
