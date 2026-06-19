package com.example.schoolbuswidget.domain

import com.example.schoolbuswidget.data.rapidocr.OcrTextLine
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.max

/**
 * Shared structured parser for SZTU peak-line 655 timetable posters (north / south layouts).
 */
internal object Peak655ScheduleParser {

    data class Layout(
        val hourRange: IntRange,
        val startHourLeftMarker: String,
        val refImgW: Float,
        val refImgH: Float,
        val refHourXMax: Float,
        val refWorkXMin: Float,
        val refMicroY: Float,
        val refMergeGap: Float,
        val splitFallbackFraction: Float,
        /** North 08:00 workday expands \"每3分钟一班车\" to 00–30 step 3. */
        val hour08EveryThreeWorkday: Boolean,
        /** North row 06 may be \"暂无\" with no departures. */
        val emptyHour06Rule: Boolean,
    )

    data class Result(
        val workday: List<LocalTime>,
        val holiday: List<LocalTime>,
    )

    fun tryParse(
        lines: List<OcrTextLine>,
        imgWidth: Int,
        imgHeight: Int,
        upscaleApplied: Float,
        layout: Layout,
    ): Result? {
        if (lines.isEmpty() || imgWidth < 200 || imgHeight < 200) return null
        val imgW = imgWidth.toFloat()
        val imgH = imgHeight.toFloat()
        val hourXMax = imgW * (layout.refHourXMax / layout.refImgW)
        val workXMin = imgW * (layout.refWorkXMin / layout.refImgW)
        val microYTol = imgH * (layout.refMicroY / layout.refImgH)
        val mergeGap = layout.refMergeGap * max(upscaleApplied, 1f) / 1.5f * (imgH / layout.refImgH)
        val rowYTol = max(microYTol * 3f, imgH * (48f / layout.refImgH))

        val rows = lines.map { Row(it.text, it.centerX, it.centerY, it.score) }
        val splitX = estimateSplitX(rows, imgW, imgW * layout.splitFallbackFraction)
        val filtered = scheduleRowFilter(rows, imgW, imgH)
        val micro = clusterRows(filtered, microYTol)
        val macro = mergeFragmentsToHourRows(micro, mergeGap).sortedBy { medianY(it) }

        val startIdx = macro.withIndex().firstOrNull { (i, band) ->
            val left = band.filter { it.x < hourXMax }.sortedBy { it.x }.joinToString("") { it.text }
            val joined = band.joinToString("") { it.text }
            when {
                left.contains(layout.startHourLeftMarker) -> true
                layout.emptyHour06Rule && left.contains("06") -> true
                layout.emptyHour06Rule && joined.contains("暂无") && i < 5 -> true
                else -> false
            }
        }?.index ?: return null

        val tableRows = macro.drop(startIdx).take(layout.hourRange.count())
        if (tableRows.size < layout.hourRange.count() / 2) return null

        val hoursSeq = layout.hourRange.map { "%02d".format(it) }
        val workday = linkedMapOf<String, MutableList<Int>>()
        val holiday = linkedMapOf<String, MutableList<Int>>()

        hoursSeq.forEach { h ->
            workday[h] = mutableListOf()
            holiday[h] = mutableListOf()
        }

        for ((hi, band) in tableRows.withIndex()) {
            if (hi >= hoursSeq.size) break
            val hour = resolveHour(
                detected = detectHourFromBand(band, hourXMax),
                sequential = hoursSeq[hi],
                hourRange = layout.hourRange,
            )
            val expanded = expandBandAroundAnchor(filtered, band, splitX, hourXMax, microYTol, rowYTol)
            val (wboxes, hboxes) = splitWorkHoliday(expanded, splitX, workXMin)
            val wt = wboxes.sortedBy { it.x }.map { it.text }
            val ht = hboxes.sortedBy { it.x }.map { it.text }
            val wAll = wt.joinToString("")
            val hAll = ht.joinToString("")

            when {
                layout.emptyHour06Rule && hour == "06" -> {
                    workday[hour] = if (wAll.contains("暂无")) mutableListOf() else normalizeMinutes(extractMinutesFromCells(wt)).toMutableList()
                    holiday[hour] = if (hAll.contains("暂无")) mutableListOf() else normalizeMinutes(extractMinutesFromCells(ht)).toMutableList()
                }
                layout.hour08EveryThreeWorkday && hour == "08" -> {
                    workday[hour] = normalizeMinutes(fixKnownNoise(hour, "workday", mergeHour08Workday(wt))).toMutableList()
                    holiday[hour] = normalizeMinutes(fixKnownNoise(hour, "holiday", extractMinutesFromCells(ht))).toMutableList()
                }
                else -> {
                    workday[hour] = normalizeMinutes(fixKnownNoise(hour, "workday", extractMinutesFromCells(wt))).toMutableList()
                    holiday[hour] = normalizeMinutes(fixKnownNoise(hour, "holiday", extractMinutesFromCells(ht))).toMutableList()
                }
            }
        }

        return Result(
            workday = flatten(workday),
            holiday = flatten(holiday),
        )
    }

    private fun flatten(map: Map<String, List<Int>>): List<LocalTime> {
        val out = sortedSetOf<LocalTime>()
        for ((h, mins) in map) {
            val hi = h.toIntOrNull() ?: continue
            for (m in mins) {
                if (m in 0..59) out.add(LocalTime.of(hi, m))
            }
        }
        return out.toList()
    }

    private data class Row(val text: String, val x: Float, val y: Float, val score: Float)

    private fun scheduleRowFilter(rows: List<Row>, imgW: Float, imgH: Float): List<Row> {
        val letterRun = Regex("[A-Za-z]{8}")
        return rows.filter { r ->
            // App OCR may place the last holiday minute near ~0.95× width; 0.92 was too aggressive.
            if (r.x > imgW * 0.98f) return@filter false
            if (r.text.length > 40 && letterRun.containsMatchIn(r.text)) return@filter false
            if (r.y < imgH * 0.02f) return@filter false
            true
        }
    }

    private val hourToken = Regex("(0[6-9]|1[0-9]|2[0-2])")

    private fun detectHourFromBand(band: List<Row>, hourXMax: Float): String? {
        val left = band.filter { it.x < hourXMax }.sortedBy { it.x }.joinToString("") { it.text }
        return hourToken.find(left.replace("暂无", ""))?.value
    }

    private fun resolveHour(detected: String?, sequential: String, hourRange: IntRange): String {
        if (detected == null) return sequential
        val d = detected.toIntOrNull() ?: return sequential
        val s = sequential.toIntOrNull() ?: return sequential
        if (d !in hourRange) return sequential
        if (abs(d - s) <= 1) return detected
        return sequential
    }

    /** Re-attach holiday minute boxes that drift slightly below the hour marker on the same row. */
    private fun expandBandAroundAnchor(
        filtered: List<Row>,
        band: List<Row>,
        splitX: Float,
        hourXMax: Float,
        microYTol: Float,
        rowYTol: Float,
    ): List<Row> {
        val anchorY = band.filter { it.x < hourXMax }.minByOrNull { it.x }?.y ?: medianY(band)
        val yMin = anchorY - microYTol * 0.5f
        val yMax = anchorY + rowYTol
        val seen = band.map { it.text to it.x to it.y }.toSet()
        val extras = filtered.filter { r ->
            val key = r.text to r.x to r.y
            key !in seen &&
                r.x >= splitX &&
                r.y >= yMin &&
                r.y <= yMax &&
                !isScheduleNoiseBox(r.text)
        }
        return band + extras
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
        if (digitsOnly.isEmpty()) return emptyList()
        val fromStart = parseTwoDigitPairs(digitsOnly, 0)
        if (digitsOnly.length % 2 == 1 && digitsOnly.length >= 3) {
            val fromOffset = parseTwoDigitPairs(digitsOnly, 1)
            if (fromOffset.size >= fromStart.size) return fromOffset
        }
        return fromStart
    }

    private fun parseTwoDigitPairs(digitsOnly: String, offset: Int): List<Int> {
        val pairs = mutableListOf<Int>()
        var i = offset
        while (i + 2 <= digitsOnly.length) {
            val v = digitsOnly.substring(i, i + 2).toIntOrNull() ?: 0
            if (v <= 59) pairs.add(v)
            i += 2
        }
        return pairs
    }

    private fun extractMinutesFromCells(texts: List<String>): List<Int> =
        texts.filter { isMinuteDigitCell(it) }.flatMap { extractTwoDigitMinutes(it) }

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
