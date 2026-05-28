package com.example.schoolbuswidget.data.rapidocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Light preprocessing to help line OCR on timetable photos: grayscale + mild contrast lift.
 * Heavier pipelines (CLAHE / denoise) need OpenCV; this stays dependency-free.
 */
object OcrBitmapPreprocessor {

    /** Default crop for north-campus 655 timetable poster (same as tools/extract_north_timetable.py). */
    fun cropNorthScheduleRegion(source: Bitmap): Bitmap {
        if (source.isRecycled) throw IllegalArgumentException("bitmap recycled")
        val l = 0.01f
        val t = 0.27f
        val r = 0.64f
        val b = 0.72f
        val x1 = (source.width * l).toInt().coerceIn(0, max(0, source.width - 1))
        val y1 = (source.height * t).toInt().coerceIn(0, max(0, source.height - 1))
        val x2 = (source.width * r).toInt().coerceIn(x1 + 1, source.width)
        val y2 = (source.height * b).toInt().coerceIn(y1 + 1, source.height)
        return Bitmap.createBitmap(source, x1, y1, x2 - x1, y2 - y1)
    }

    /**
     * ~1.5× upscale like the Python extractor, capped by [maxLongSide].
     * @return scaled bitmap and the effective scale factor vs [source] (for structured parser tuning).
     */
    fun upscaleForScheduleOcr(source: Bitmap, scale: Float = 1.5f, maxLongSide: Int = 2048): Pair<Bitmap, Float> {
        if (source.isRecycled) throw IllegalArgumentException("bitmap recycled")
        var outW = (source.width * scale).roundToInt().coerceAtLeast(1)
        var outH = (source.height * scale).roundToInt().coerceAtLeast(1)
        val longSide = max(outW, outH)
        val applied: Float = if (longSide > maxLongSide) {
            val r = maxLongSide.toFloat() / longSide
            outW = (outW * r).roundToInt().coerceAtLeast(1)
            outH = (outH * r).roundToInt().coerceAtLeast(1)
            scale * r
        } else {
            scale
        }
        if (outW == source.width && outH == source.height) {
            return source to 1f
        }
        return Bitmap.createScaledBitmap(source, outW, outH, true) to applied
    }

    fun preprocessForTimetableOcr(source: Bitmap): Bitmap {
        if (source.isRecycled) throw IllegalArgumentException("bitmap recycled")
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.35f
        val shift = -128f * contrast + 128f
        val scale = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, shift,
                0f, contrast, 0f, 0f, shift,
                0f, 0f, contrast, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        scale.postConcat(gray)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(scale)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }
}
