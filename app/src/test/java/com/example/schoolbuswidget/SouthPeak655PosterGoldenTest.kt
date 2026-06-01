package com.example.schoolbuswidget

import com.example.schoolbuswidget.domain.SouthPeak655PosterParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalTime

/**
 * Strict check: parsed south timetable must match [tools/south_timetable_golden.json].
 *
 * Run:
 *   .\gradlew.bat :app:testDebugUnitTest --tests "com.example.schoolbuswidget.SouthPeak655PosterGoldenTest"
 */
class SouthPeak655PosterGoldenTest {

    @Test
    fun pcOcrParse_matchesPosterGolden() {
        val ocrFile = resolveFixture("tools/south_ocr_raw.json") ?: return
        val golden = loadGolden() ?: return
        val meta = File(ocrFile.parentFile, "south_timetable_improved.json")
        val (w, h) = imageSizeFromMeta(meta) ?: (1806 to 1404)

        val parsed = SouthPeak655PosterParser.tryParse(
            loadOcrLines(ocrFile),
            w,
            h,
            upscaleApplied = 1.5f,
        )
        val result = requireNotNull(parsed) { "Parser returned null" }
        assertMatchesGolden(golden, result, label = "PC OCR (south_ocr_raw.json)")
    }

    private data class Golden(
        val workday: Map<String, List<String>>,
        val holiday: Map<String, List<String>>,
    )

    private fun loadGolden(): Golden? {
        val file = resolveFixture("tools/south_timetable_golden.json") ?: return null
        val root = JSONObject(file.readText())
        return Golden(
            workday = jsonColumnToMap(root.getJSONObject("workday")),
            holiday = jsonColumnToMap(root.getJSONObject("holiday")),
        )
    }

    private fun jsonColumnToMap(obj: JSONObject): Map<String, List<String>> {
        val out = linkedMapOf<String, List<String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val hour = keys.next()
            val arr = obj.getJSONArray(hour)
            val mins = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                mins.add(arr.getString(i))
            }
            out[hour] = mins
        }
        return out
    }

    private fun assertMatchesGolden(
        golden: Golden,
        result: SouthPeak655PosterParser.Result,
        label: String,
    ) {
        val gotWd = resultToHourMap(result.workday)
        val gotHol = resultToHourMap(result.holiday)
        val diffs = mutableListOf<String>()

        for (hour in (7..22).map { "%02d".format(it) }) {
            diffColumn(diffs, label, "workday", hour, golden.workday[hour].orEmpty(), gotWd[hour].orEmpty())
            diffColumn(diffs, label, "holiday", hour, golden.holiday[hour].orEmpty(), gotHol[hour].orEmpty())
        }

        if (diffs.isNotEmpty()) {
            fail(
                "Not identical to poster golden ($label):\n" +
                    diffs.joinToString("\n") +
                    "\n\nGolden file: tools/south_timetable_golden.json",
            )
        }
    }

    private fun diffColumn(
        diffs: MutableList<String>,
        label: String,
        column: String,
        hour: String,
        expected: List<String>,
        actual: List<String>,
    ) {
        if (expected != actual) {
            diffs.add(
                "[$label] $column $hour:00\n" +
                    "  poster: ${expected.joinToString(",")}\n" +
                    "  parsed: ${actual.joinToString(",")}",
            )
        }
    }

    private fun resultToHourMap(times: List<LocalTime>): Map<String, List<String>> {
        val buckets = (7..22).associate { "%02d".format(it) to mutableListOf<String>() }.toMutableMap()
        for (t in times) {
            buckets.getOrPut("%02d".format(t.hour)) { mutableListOf() }
                .add("%02d".format(t.minute))
        }
        return buckets.mapValues { (_, v) -> v.sorted() }
    }

    private fun imageSizeFromMeta(metaFile: File): Pair<Int, Int>? {
        if (!metaFile.isFile) return null
        val size = JSONObject(metaFile.readText())
            .optJSONObject("image_size_after_preprocess") ?: return null
        val w = size.optInt("width", -1)
        val h = size.optInt("height", -1)
        return if (w > 0 && h > 0) w to h else null
    }

    private fun loadOcrLines(jsonFile: File): List<com.example.schoolbuswidget.data.rapidocr.OcrTextLine> {
        val arr = JSONArray(jsonFile.readText())
        val lines = ArrayList<com.example.schoolbuswidget.data.rapidocr.OcrTextLine>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            lines.add(
                com.example.schoolbuswidget.data.rapidocr.OcrTextLine(
                    text = o.getString("text"),
                    centerX = o.getDouble("x").toFloat(),
                    centerY = o.getDouble("y").toFloat(),
                    score = o.optDouble("score", 1.0).toFloat(),
                ),
            )
        }
        return lines
    }

    private fun resolveFixture(relativePath: String): File? {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return listOf(
            cwd.resolve(relativePath),
            cwd.resolve("app").resolve(relativePath),
            File(relativePath),
        ).firstOrNull { it.isFile }
    }
}
