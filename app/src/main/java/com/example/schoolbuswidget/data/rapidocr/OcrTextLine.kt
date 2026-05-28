package com.example.schoolbuswidget.data.rapidocr

/** One OCR text fragment with center in bitmap pixel coordinates (matches Python extract script). */
data class OcrTextLine(
    val text: String,
    val centerX: Float,
    val centerY: Float,
    val score: Float,
)
