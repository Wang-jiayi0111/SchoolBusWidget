package com.example.schoolbuswidget

import com.example.schoolbuswidget.data.rapidocr.OcrTextLine
import com.example.schoolbuswidget.domain.NorthPeak655PosterParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalTime
import javax.imageio.ImageIO

/**
 * Strict check: parsed timetable must match [tools/north_timetable_golden.json] exactly
 * (poster ground truth, hour 06-22, every minute).
 *
 * Run:
 *   .\gradlew.bat :app:testDebugUnitTest --tests "com.example.schoolbuswidget.NorthPeak655PosterGoldenTest"
 */
class NorthPeak655PosterGoldenTest {

    @Test
    fun pcOcrParse_matchesPosterGolden() {
        val ocrFile = resolveFixture("tools/ocr_improved_raw.json") ?: return
        val golden = loadGolden() ?: return
        val meta = File(ocrFile.parentFile, "north_timetable_improved.json")
        val (w, h) = imageSizeFromMeta(meta) ?: (1210 to 1108)

        val parsed = NorthPeak655PosterParser.tryParse(
            loadOcrLines(ocrFile),
            w,
            h,
            upscaleApplied = 1.5f,
        )
        val result = requireNotNull(parsed) { "Parser returned null" }
        assertMatchesGolden(golden, result, label = "PC OCR (ocr_improved_raw.json)")
    }

    @Test
    fun appOcrParse_matchesPosterGolden() {
        val ocrFile = resolveFixture("tools/ocr_from_app_scaled.json") ?: return
        val imageFile = resolveFixture("ocr_scaled.png") ?: return
        val golden = loadGolden() ?: return

        val img = ImageIO.read(imageFile) ?: return
        val parsed = NorthPeak655PosterParser.tryParse(
            loadOcrLines(ocrFile),
            img.width,
            img.height,
            upscaleApplied = 1.5f,
        )
        val result = requireNotNull(parsed) { "Parser returned null" }
        assertMatchesGolden(golden, result, label = "App OCR (ocr_from_app_scaled.json)")
    }

    private data class Golden(
        val workday: Map<String, List<String>>,
        val holiday: Map<String, List<String>>,
    )

    private fun loadGolden(): Golden? {
        val file = resolveFixture("tools/north_timetable_golden.json") ?: return null
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
        result: NorthPeak655PosterParser.Result,
        label: String,
    ) {
        val gotWd = resultToHourMap(result.workday)
        val gotHol = resultToHourMap(result.holiday)
        val diffs = mutableListOf<String>()

        for (hour in (6..22).map { "%02d".format(it) }) {
            diffColumn(diffs, label, "workday", hour, golden.workday[hour].orEmpty(), gotWd[hour].orEmpty())
            diffColumn(diffs, label, "holiday", hour, golden.holiday[hour].orEmpty(), gotHol[hour].orEmpty())
        }

        if (diffs.isNotEmpty()) {
            fail(
                "Not identical to poster golden ($label):\n" +
                    diffs.joinToString("\n") +
                    "\n\nGolden file: tools/north_timetable_golden.json",
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
        val buckets = (6..22).associate { "%02d".format(it) to mutableListOf<String>() }.toMutableMap()
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

    private fun loadOcrLines(jsonFile: File): List<OcrTextLine> {
        val arr = JSONArray(jsonFile.readText())
        val lines = ArrayList<OcrTextLine>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            lines.add(
                OcrTextLine(
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
