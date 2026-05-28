package com.example.schoolbuswidget.data.rapidocr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.schoolbuswidget.BuildConfig
import java.io.File
import java.io.FileOutputStream

/**
 * Debug-only: save OCR step images and log paths (tag [TAG]).
 *
 * - App-private: Android/data/.../files/ocr_debug/ (Device Explorer, same app)
 * - Public copy: Download/SchoolBusOcr/ (phone file manager)
 */
object TimetableOcrDebugDump {

    const val TAG = "SchoolBusOcr"

    @Volatile
    var lastDebugDir: String? = null

    fun savePng(context: Context, bitmap: Bitmap, name: String) {
        if (!BuildConfig.DEBUG) return
        if (bitmap.isRecycled) {
            Log.w(TAG, "skip $name: bitmap recycled")
            return
        }
        val dir = context.getExternalFilesDir("ocr_debug") ?: context.cacheDir
        dir.mkdirs()
        val file = File(dir, name)
        try {
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    Log.w(TAG, "compress failed: ${file.absolutePath}")
                    return
                }
            }
            lastDebugDir = dir.absolutePath
            Log.i(TAG, "saved ${file.name} -> ${file.absolutePath} (${file.length()} bytes)")
            copyToDownloads(context, file, name)
        } catch (e: Exception) {
            Log.e(TAG, "save failed: ${file.absolutePath}", e)
        }
    }

    private fun copyToDownloads(context: Context, source: File, name: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/SchoolBusOcr",
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                }
                Log.i(TAG, "also in Downloads/SchoolBusOcr/$name")
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS,
                )
                val pubDir = File(downloads, "SchoolBusOcr").apply { mkdirs() }
                source.copyTo(File(pubDir, name), overwrite = true)
                Log.i(TAG, "also in ${File(pubDir, name).absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Downloads copy skipped for $name", e)
        }
    }

    fun logStart(campus: String) {
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, "image import started, campus=$campus (north saves ocr_*.png)")
    }
}
