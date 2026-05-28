package com.example.schoolbuswidget.domain

import com.example.schoolbuswidget.data.rapidocr.OcrTextLine
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.max

/**
 * Structured parser for the SZTU north-campus "高峰专线655" timetable poster layout
 * (same assumptions as [tools.extract_north_timetable]).
 *
 * Expects OCR lines from a **cropped schedule region** bitmap (not the full marketing poster).
 */
object NorthPeak655PosterParser {

    private val refImgW = 1536f
    private val refImgH = 1232f
    private val refHourXMax = 108f
    private val refWorkXMin = 120f
    private val refMicroY = 14f
    private val refMergeGap = 36f

    data class Result(
        val workday: List<LocalTime>,
        val holiday: List<LocalTime>,
    )

    /**
     * @param upscaleApplied factor applied to the cropped bitmap before OCR (e.g. 1.5f); use 1f if none.
     */
    fun tryParse(
        lines: List<OcrTextLine>,
        imgWidth: Int,
        imgHeight: Int,
        upscaleApplied: Float = 1f,
    ): Result? {
        if (lines.isEmpty() || imgWidth < 200 || imgHeight < 200) return null
        val imgW = imgWidth.toFloat()
        val imgH = imgHeight.toFloat()
        val hourXMax = imgW * (refHourXMax / refImgW)
        val workXMin = imgW * (refWorkXMin / refImgW)
        val microYTol = imgH * (refMicroY / refImgH)
        val mergeGap = refMergeGap * max(upscaleApplied, 1f) / 1.5f * (imgH / refImgH)

        val rows = lines.map { Row(it.text, it.centerX, it.centerY, it.score) }
        val splitX = estimateSplitX(rows, imgW, imgW * 0.52f)
        val filtered = scheduleRowFilter(rows, imgW, imgH)
        val micro = clusterRows(filtered, microYTol)
        val macro = mergeFragmentsToHourRows(micro, mergeGap).sortedBy { medianY(it) }

        val startIdx = macro.withIndex().firstOrNull { (i, band) ->
            val left = band.filter { it.x < hourXMax }.sortedBy { it.x }.joinToString("") { it.text }
            val joined = band.joinToString("") { it.text }
            left.contains("06") || (joined.contains("暂无") && i < 5)
        }?.index ?: return null

        val tableRows = macro.drop(startIdx).take(17)
        if (tableRows.size < 10) return null

        val hoursSeq = (6..22).map { "%02d".format(it) }
        val workday = linkedMapOf<String, MutableList<Int>>()
        val holiday = linkedMapOf<String, MutableList<Int>>()

        hoursSeq.forEach { h ->
            workday[h] = mutableListOf()
            holiday[h] = mutableListOf()
        }

        for ((hi, band) in tableRows.withIndex()) {
            if (hi >= hoursSeq.size) break
            val hour = hoursSeq[hi]
            val (wboxes, hboxes) = splitWorkHoliday(band, splitX, workXMin)
            val wt = wboxes.sortedBy { it.x }.map { it.text }
            val ht = hboxes.sortedBy { it.x }.map { it.text }
            val wJoinDigits = joinMinuteDigitsOnly(wt)
            val hJoinDigits = joinMinuteDigitsOnly(ht)
            val wAll = wt.joinToString("")
            val hAll = ht.joinToString("")

            when (hour) {
                "06" -> {
                    workday[hour] = if (wAll.contains("暂无")) mutableListOf() else normalizeMinutes(extractTwoDigitMinutes(wJoinDigits)).toMutableList()
                    holiday[hour] = if (hAll.contains("暂无")) mutableListOf() else normalizeMinutes(extractTwoDigitMinutes(hJoinDigits)).toMutableList()
                }
                "08" -> {
                    workday[hour] = normalizeMinutes(fixKnownNoise(hour, "workday", mergeHour08Workday(wt))).toMutableList()
                    holiday[hour] = normalizeMinutes(fixKnownNoise(hour, "holiday", extractTwoDigitMinutes(hJoinDigits))).toMutableList()
                }
                else -> {
                    workday[hour] = normalizeMinutes(fixKnownNoise(hour, "workday", extractTwoDigitMinutes(wJoinDigits))).toMutableList()
                    holiday[hour] = normalizeMinutes(fixKnownNoise(hour, "holiday", extractTwoDigitMinutes(hJoinDigits))).toMutableList()
                }
            }
        }

        fun flatten(map: Map<String, List<Int>>): List<LocalTime> {
            val out = sortedSetOf<LocalTime>()
            for ((h, mins) in map) {
                val hi = h.toIntOrNull() ?: continue
                for (m in mins) {
                    if (m in 0..59) out.add(LocalTime.of(hi, m))
                }
            }
            return out.toList()
        }

        return Result(
            workday = flatten(workday),
            holiday = flatten(holiday),
        )
    }

    private data class Row(val text: String, val x: Float, val y: Float, val score: Float)

    private fun scheduleRowFilter(rows: List<Row>, imgW: Float, imgH: Float): List<Row> {
        val letterRun = Regex("[A-Za-z]{8}")
        return rows.filter { r ->
            if (r.x > imgW * 0.92f) return@filter false
            if (r.text.length > 40 && letterRun.containsMatchIn(r.text)) return@filter false
            if (r.y < imgH * 0.02f) return@filter false
            true
        }
    }

    private fun clusterRows(items: List<Row>, yTol: Float): List<List<Row>> {
        val sorted = items.sortedBy { it.y }
        val bands = mutableListOf<MutableList<Row>>()
        for (it in sorted) {
            if (bands.isEmpty()) {
                bands.add(mutableListOf(it))
                continue
            }
            val last = bands.last().last()
            if (abs(it.y - last.y) <= yTol) {
                bands.last().add(it)
            } else {
                bands.add(mutableListOf(it))
            }
        }
        return bands
    }

    private fun medianY(band: List<Row>): Float {
        val ys = band.map { it.y }
        return ys.sum() / ys.size
    }

    private fun mergeFragmentsToHourRows(microBands: List<List<Row>>, maxInnerGap: Float): List<List<Row>> {
        if (microBands.isEmpty()) return emptyList()
        val sortedBands = microBands.sortedBy { medianY(it) }
        val macro = mutableListOf<MutableList<Row>>()
        var buf = sortedBands.first().toMutableList()
        var prevY = medianY(buf)
        for (band in sortedBands.drop(1)) {
            val y = medianY(band)
            if (y - prevY <= maxInnerGap) {
                buf.addAll(band)
                prevY = medianY(buf)
            } else {
                macro.add(buf)
                buf = band.toMutableList()
                prevY = y
            }
        }
        macro.add(buf)
        return macro
    }

    private fun estimateSplitX(rows: List<Row>, imgWidth: Float, fallback: Float): Float {
        val xs = mutableListOf<Float>()
        for (r in rows) {
            val digits = r.text.filter { it.isDigit() }
            if (digits.length >= 10) xs.add(r.x)
        }
        if (xs.size < 3) return fallback
        xs.sort()
        var bestGap = 0f
        var best = fallback
        for (i in 0 until xs.size - 1) {
            val gap = xs[i + 1] - xs[i]
            if (gap > bestGap) {
                bestGap = gap
                best = (xs[i] + xs[i + 1]) / 2f
            }
        }
        return best
    }

    private fun extractTwoDigitMinutes(s: String): List<Int> {
        val cleaned = s.replace("暂无", "")
        val digitsOnly = cleaned.filter { it.isDigit() }
        val pairs = mutableListOf<Int>()
        var i = 0
        while (i + 2 <= digitsOnly.length) {
            val v = digitsOnly.substring(i, i + 2).toIntOrNull() ?: 0
            if (v <= 59) pairs.add(v)
            i += 2
        }
        return pairs
    }

    private fun parseEveryThreeMinutes(parts: List<String>): Pair<List<Int>, Boolean> {
        val joined = parts.joinToString("")
        val lower = joined.lowercase()
        val cn = joined.contains("每") && joined.contains("3") && joined.contains("分钟")
        val en = Regex("every\\s*3\\s*min", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        if (cn || en) return (0..30 step 3).toList() to true
        return emptyList<Int>() to false
    }

    private fun mergeHour08Workday(parts: List<String>): List<Int> {
        val (expanded, _) = parseEveryThreeMinutes(parts)
        val digitParts = parts.filter { p ->
            val pl = p.lowercase()
            when {
                p.contains("每") && p.contains("分钟") -> false
                Regex("every\\s*3\\s*min", RegexOption.IGNORE_CASE).containsMatchIn(pl) -> false
                else -> true
            }
        }
        val raw = digitParts.flatMap { extractTwoDigitMinutes(it) }.toMutableList()
        var out = (expanded + raw).toSortedSet()
        if (expanded.isNotEmpty() && (out.maxOrNull() ?: 0) <= 30 && !out.contains(40)) {
            out = (out + listOf(40, 50)).toSortedSet()
        }
        return out.filter { it in 0..59 }.sorted()
    }

    private fun isScheduleNoiseBox(text: String): Boolean {
        val t = text.trim()
        if (t.length > 22 && Regex("[A-Za-z]{12}").containsMatchIn(t)) return true
        if (Regex("\\bminutes?\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) && t.length > 12) return true
        if (t.contains("从") && (t.contains("站") || t.contains("行驶"))) return true
        if (t.contains("意为") || t.contains("Meaning")) return true
        return false
    }

    private fun isMinuteDigitCell(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.contains("暂无")) return false
        if (Regex("[A-Za-z]").containsMatchIn(t)) return false
        if (Regex("每|分钟|意为").containsMatchIn(t)) return false
        return Regex("^[\\d\\s.]+$").matches(t)
    }

    private fun joinMinuteDigitsOnly(texts: List<String>): String =
        texts.filter { isMinuteDigitCell(it) }.joinToString("")

    private fun splitWorkHoliday(
        band: List<Row>,
        splitX: Float,
        workXMin: Float,
    ): Pair<List<Row>, List<Row>> {
        val work = mutableListOf<Row>()
        val hol = mutableListOf<Row>()
        for (b in band) {
            if (isScheduleNoiseBox(b.text)) continue
            if (b.x < workXMin) continue
            when {
                b.x >= splitX -> hol.add(b)
                b.x < splitX -> work.add(b)
            }
        }
        return work to hol
    }

    private fun fixKnownNoise(hour: String, col: String, minutes: List<Int>): List<Int> {
        if (hour == "09" && col == "workday") {
            val s = minutes.toMutableSet()
            if (5 in s && 55 in s && 57 !in s) {
                s.remove(5)
                s.add(57)
            }
            return s.sorted()
        }
        return minutes
    }

    private fun normalizeMinutes(raw: List<Int>): List<Int> =
        raw.filter { it in 0..59 }.distinct().sorted()
}
