package com.example.schoolbuswidget.data.rapidocr

import android.content.Context
import android.graphics.Bitmap
import com.benjaminwan.ocrlibrary.OcrEngine
import com.benjaminwan.ocrlibrary.Point
import com.benjaminwan.ocrlibrary.TextBlock
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min

object RapidOcrRecognizer {

    private val lock = Any()

    @Volatile
    private var engine: OcrEngine? = null

    private fun getEngine(context: Context): OcrEngine {
        engine?.let { return it }
        synchronized(lock) {
            engine?.let { return it }
            val e = OcrEngine(context.applicationContext)
            engine = e
            return e
        }
    }

    fun recognizeText(bitmap: Bitmap, context: Context): String {
        if (bitmap.isRecycled) {
            throw IllegalArgumentException("bitmap recycled")
        }
        val boxImg = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            synchronized(lock) {
                val eng = getEngine(context)
                eng.doAngle = true
                eng.mostAngle = true
                val maxDim = max(bitmap.width, bitmap.height)
                val maxSideLen = min(2048, maxDim)
                val result = eng.detect(bitmap, boxImg, maxSideLen)
                return result.strRes
            }
        } finally {
            if (!boxImg.isRecycled) {
                boxImg.recycle()
            }
        }
    }

    /**
     * OCR with per-line centers (for structured timetable parsing).
     */
    fun recognizeLines(bitmap: Bitmap, context: Context): List<OcrTextLine> {
        if (bitmap.isRecycled) {
            throw IllegalArgumentException("bitmap recycled")
        }
        val boxImg = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            synchronized(lock) {
                val eng = getEngine(context)
                eng.doAngle = true
                eng.mostAngle = true
                val maxDim = max(bitmap.width, bitmap.height)
                val maxSideLen = min(2048, maxDim)
                val result = eng.detect(bitmap, boxImg, maxSideLen)
                @Suppress("UNCHECKED_CAST")
                val blocks = result.textBlocks as? ArrayList<TextBlock> ?: return emptyList()
                val out = ArrayList<OcrTextLine>(blocks.size)
                for (b in blocks) {
                    val (cx, cy) = centerOf(b)
                    out.add(OcrTextLine(text = b.text, centerX = cx, centerY = cy, score = b.boxScore))
                }
                return out
            }
        } finally {
            if (!boxImg.isRecycled) {
                boxImg.recycle()
            }
        }
    }

    private fun centerOf(block: TextBlock): Pair<Float, Float> {
        @Suppress("UNCHECKED_CAST")
        val pts = block.boxPoint as? ArrayList<*> ?: return 0f to 0f
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (p in pts) {
            when (p) {
                is Point -> {
                    sx += p.x
                    sy += p.y
                    n++
                }
            }
        }
        return if (n == 0) {
            0f to 0f
        } else {
            (sx / n).toFloat() to (sy / n).toFloat()
        }
    }
}
