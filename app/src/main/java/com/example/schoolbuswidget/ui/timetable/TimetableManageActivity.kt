package com.example.schoolbuswidget.ui.timetable

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schoolbuswidget.R
import com.example.schoolbuswidget.data.TimetableDataStoreRepository
import com.example.schoolbuswidget.data.rapidocr.OcrBitmapPreprocessor
import com.example.schoolbuswidget.data.rapidocr.RapidOcrRecognizer
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.NorthPeak655PosterParser
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.BuildConfig
import com.example.schoolbuswidget.domain.TimetableImportParser
import com.example.schoolbuswidget.data.rapidocr.TimetableOcrDebugDump
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
class TimetableManageActivity : AppCompatActivity() {

    private val times = mutableListOf<LocalTime>()
    private lateinit var adapter: TimetableGroupedTimeAdapter
    private val repository by lazy { TimetableDataStoreRepository(this) }

    private lateinit var chipNorth: Chip
    private lateinit var chipSouth: Chip
    private lateinit var chipWorkday: Chip
    private lateinit var chipHoliday: Chip
    private lateinit var textCustomSource: TextView

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timetable_manage)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        chipNorth = findViewById(R.id.chipNorth)
        chipSouth = findViewById(R.id.chipSouth)
        chipWorkday = findViewById(R.id.chipWorkday)
        chipHoliday = findViewById(R.id.chipHoliday)
        textCustomSource = findViewById(R.id.textCustomSource)

        val chipGroupCampus = findViewById<ChipGroup>(R.id.chipGroupCampus)
        val chipGroupDayType = findViewById<ChipGroup>(R.id.chipGroupDayType)

        adapter = TimetableGroupedTimeAdapter(times) { index ->
            if (index in times.indices) {
                times.removeAt(index)
                adapter.rebuildItems()
            }
        }
        val recycler = findViewById<RecyclerView>(R.id.recyclerTimes)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        chipGroupCampus.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) loadTimetableForSelection()
        }
        chipGroupDayType.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) loadTimetableForSelection()
        }

        findViewById<MaterialButton>(R.id.buttonAddTime).setOnClickListener { showAddTimePicker() }
        findViewById<MaterialButton>(R.id.buttonSave).setOnClickListener { saveCurrent() }
        findViewById<MaterialButton>(R.id.buttonResetDefault).setOnClickListener { confirmResetToDefault() }
        findViewById<MaterialButton>(R.id.buttonImportText).setOnClickListener { showImportTextDialog() }
        findViewById<MaterialButton>(R.id.buttonImportImage).setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }

        loadTimetableForSelection()
    }

    private fun selectedLocation(): CampusLocation {
        return if (chipNorth.isChecked) CampusLocation.NORTH else CampusLocation.SOUTH
    }

    private fun selectedDayType(): ServiceDayType {
        return if (chipWorkday.isChecked) ServiceDayType.WORKDAY else ServiceDayType.HOLIDAY
    }

    private fun loadTimetableForSelection() {
        lifecycleScope.launch {
            val list = repository.getDepartures(selectedLocation(), selectedDayType())
                .map { it.time }
            times.clear()
            times.addAll(list)
            adapter.rebuildItems()
            val custom = repository.hasCustomDepartures(selectedLocation(), selectedDayType())
            textCustomSource.text = getString(
                if (custom) R.string.timetable_source_custom else R.string.timetable_source_builtin,
            )
        }
    }

    private fun showAddTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(8)
            .setMinute(0)
            .setTitleText(R.string.timetable_add_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            val t = LocalTime.of(picker.hour, picker.minute)
            if (t !in times) {
                times.add(t)
                times.sort()
                adapter.rebuildItems()
            }
        }
        picker.show(supportFragmentManager, "add_time")
    }

    private fun saveCurrent() {
        lifecycleScope.launch {
            val deps = times.distinct().sorted().map { DepartureTime(it) }
            repository.saveDepartures(selectedLocation(), selectedDayType(), deps)
            textCustomSource.text = getString(R.string.timetable_source_custom)
            Toast.makeText(this@TimetableManageActivity, R.string.timetable_saved, Toast.LENGTH_SHORT).show()
            refreshHomeWidgets()
        }
    }

    private fun confirmResetToDefault() {
        AlertDialog.Builder(this)
            .setTitle(R.string.timetable_reset_default)
            .setMessage(R.string.timetable_reset_confirm)
            .setPositiveButton(R.string.timetable_reset_confirm_yes) { _, _ ->
                lifecycleScope.launch {
                    repository.clearSavedDepartures(selectedLocation(), selectedDayType())
                    loadTimetableForSelection()
                    Toast.makeText(
                        this@TimetableManageActivity,
                        R.string.timetable_reset_done,
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshHomeWidgets()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showImportTextDialog() {
        if (selectedLocation() == CampusLocation.NORTH) {
            showNorthImportTextDualDialog()
            return
        }
        val input = EditText(this).apply {
            setHint(R.string.timetable_import_text_hint)
            minLines = 5
            setHorizontallyScrolling(false)
        }
        val padding = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        input.setPadding(padding, padding, padding, padding)
        AlertDialog.Builder(this)
            .setTitle(R.string.timetable_import_text)
            .setView(input)
            .setPositiveButton(R.string.timetable_import_parse) { _, _ ->
                val parsed = TimetableImportParser.extractTimesFromText(input.text.toString())
                showImportMergeDialog(parsed)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNorthImportTextDualDialog() {
        val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val labelWd = TextView(this).apply { text = getString(R.string.label_day_workday) }
        val editWd = EditText(this).apply {
            minLines = 4
            setHorizontallyScrolling(false)
            setHint(R.string.timetable_import_text_workday_hint)
        }
        val labelHol = TextView(this).apply {
            text = getString(R.string.label_day_holiday)
            setPadding(0, pad / 2, 0, 0)
        }
        val editHol = EditText(this).apply {
            minLines = 4
            setHorizontallyScrolling(false)
            setHint(R.string.timetable_import_text_holiday_hint)
        }
        container.addView(labelWd)
        container.addView(editWd)
        container.addView(labelHol)
        container.addView(editHol)
        scroll.addView(container)
        AlertDialog.Builder(this)
            .setTitle(R.string.timetable_import_text_north_dual_title)
            .setView(scroll)
            .setPositiveButton(R.string.timetable_import_text_north_dual_save) { _, _ ->
                val wd = TimetableImportParser.extractTimesFromText(editWd.text.toString())
                val hol = TimetableImportParser.extractTimesFromText(editHol.text.toString())
                if (wd.isEmpty() && hol.isEmpty()) {
                    Toast.makeText(this, R.string.timetable_import_text_north_dual_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    if (wd.isNotEmpty()) {
                        repository.saveDepartures(
                            CampusLocation.NORTH,
                            ServiceDayType.WORKDAY,
                            wd.distinct().sorted().map { DepartureTime(it) },
                        )
                    }
                    if (hol.isNotEmpty()) {
                        repository.saveDepartures(
                            CampusLocation.NORTH,
                            ServiceDayType.HOLIDAY,
                            hol.distinct().sorted().map { DepartureTime(it) },
                        )
                    }
                    loadTimetableForSelection()
                    Toast.makeText(
                        this@TimetableManageActivity,
                        R.string.timetable_import_north_text_saved,
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshHomeWidgets()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onImagePicked(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmapMaxSide(uri, 2048) }
            if (bitmap == null) {
                Toast.makeText(this@TimetableManageActivity, R.string.timetable_image_load_fail, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val campus = selectedLocation()
            val dayType = selectedDayType()
            TimetableOcrDebugDump.lastDebugDir = null
            TimetableOcrDebugDump.logStart(
                if (campus == CampusLocation.NORTH) "NORTH" else "SOUTH",
            )
            try {
                val outcome = withContext(Dispatchers.Default) {
                    ocrBitmapToOutcome(bitmap, campus, dayType)
                }
                if (BuildConfig.DEBUG) {
                    TimetableOcrDebugDump.lastDebugDir?.let { dir ->
                        Toast.makeText(
                            this@TimetableManageActivity,
                            "调试图:\n$dir\n或 下载/SchoolBusOcr/",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                when (outcome) {
                    is OcrImportOutcome.NorthDual ->
                        showNorthDualImageImportDialog(outcome.workday, outcome.holiday)
                    is OcrImportOutcome.Single ->
                        showImportMergeDialog(outcome.times)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@TimetableManageActivity,
                    getString(R.string.timetable_image_ocr_fail, e.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private sealed class OcrImportOutcome {
        data class NorthDual(val workday: List<LocalTime>, val holiday: List<LocalTime>) : OcrImportOutcome()
        data class Single(val times: List<LocalTime>) : OcrImportOutcome()
    }

    /**
     * North 655 poster: when both columns parse well, return [NorthDual] so the UI can save both without
     * switching the day-type chip. Otherwise same as before (single list for merge/replace on screen).
     */
    private fun ocrBitmapToOutcome(
        decoded: Bitmap,
        campus: CampusLocation,
        dayType: ServiceDayType,
    ): OcrImportOutcome {
        val prep = OcrBitmapPreprocessor.preprocessForTimetableOcr(decoded)
        try {
            if (campus == CampusLocation.NORTH) {
                TimetableOcrDebugDump.savePng(this, prep, "ocr_prep.png")
                val cropped = OcrBitmapPreprocessor.cropNorthScheduleRegion(prep)
                TimetableOcrDebugDump.savePng(this, cropped, "ocr_cropped.png")
                val (scaled, appliedScale) = OcrBitmapPreprocessor.upscaleForScheduleOcr(cropped)
                if (scaled !== cropped && !cropped.isRecycled) cropped.recycle()
                try {
                    TimetableOcrDebugDump.savePng(this, scaled, "ocr_scaled.png")
                    val lines = RapidOcrRecognizer.recognizeLines(scaled, this)
                    val structured = NorthPeak655PosterParser.tryParse(
                        lines,
                        scaled.width,
                        scaled.height,
                        appliedScale,
                    )
                    if (structured != null) {
                        val wdOk = structured.workday.size >= MIN_NORTH_STRUCTURED_DEPARTURES
                        val holOk = structured.holiday.size >= MIN_NORTH_HOLIDAY_DEPARTURES
                        if (wdOk && holOk) {
                            return OcrImportOutcome.NorthDual(structured.workday, structured.holiday)
                        }
                        val column = when (dayType) {
                            ServiceDayType.WORKDAY -> structured.workday
                            ServiceDayType.HOLIDAY -> structured.holiday
                        }
                        if (column.size >= MIN_NORTH_STRUCTURED_DEPARTURES) {
                            return OcrImportOutcome.Single(column)
                        }
                    }
                } finally {
                    if (!scaled.isRecycled) scaled.recycle()
                }
            }
            val flat = TimetableImportParser.extractTimesFromText(
                RapidOcrRecognizer.recognizeText(prep, this),
            )
            return OcrImportOutcome.Single(flat)
        } finally {
            if (!prep.isRecycled && prep !== decoded) prep.recycle()
        }
    }

    private fun showNorthDualImageImportDialog(workday: List<LocalTime>, holiday: List<LocalTime>) {
        val wdPrev = previewTimesForDialog(workday)
        val hdPrev = previewTimesForDialog(holiday)
        AlertDialog.Builder(this)
            .setTitle(R.string.timetable_import_north_dual_title)
            .setMessage(
                getString(
                    R.string.timetable_import_north_dual_message,
                    workday.size,
                    wdPrev,
                    holiday.size,
                    hdPrev,
                ),
            )
            .setPositiveButton(R.string.timetable_import_north_dual_save_both) { _, _ ->
                saveNorthBothColumns(workday, holiday)
            }
            .setNeutralButton(R.string.timetable_import_north_dual_save_one) { _, _ ->
                val only = when (selectedDayType()) {
                    ServiceDayType.WORKDAY -> workday
                    ServiceDayType.HOLIDAY -> holiday
                }
                saveNorthOneColumn(selectedDayType(), only)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun previewTimesForDialog(times: List<LocalTime>, maxShow: Int = 8): String {
        val head = times.take(maxShow).joinToString("、") { it.toString() }
        return if (times.size > maxShow) "$head…" else head
    }

    private fun saveNorthBothColumns(workday: List<LocalTime>, holiday: List<LocalTime>) {
        lifecycleScope.launch {
            repository.saveDepartures(
                CampusLocation.NORTH,
                ServiceDayType.WORKDAY,
                workday.distinct().sorted().map { DepartureTime(it) },
            )
            repository.saveDepartures(
                CampusLocation.NORTH,
                ServiceDayType.HOLIDAY,
                holiday.distinct().sorted().map { DepartureTime(it) },
            )
            loadTimetableForSelection()
            Toast.makeText(this@TimetableManageActivity, R.string.timetable_import_north_dual_saved_both, Toast.LENGTH_SHORT).show()
            refreshHomeWidgets()
        }
    }

    private fun saveNorthOneColumn(dayType: ServiceDayType, times: List<LocalTime>) {
        lifecycleScope.launch {
            repository.saveDepartures(
                CampusLocation.NORTH,
                dayType,
                times.distinct().sorted().map { DepartureTime(it) },
            )
            loadTimetableForSelection()
            Toast.makeText(this@TimetableManageActivity, R.string.timetable_import_north_dual_saved_one, Toast.LENGTH_SHORT).show()
            refreshHomeWidgets()
        }
    }

    companion object {
        /** If structured north parse yields at least this many times, trust it over flat OCR. */
        private const val MIN_NORTH_STRUCTURED_DEPARTURES = 12
        private const val MIN_NORTH_HOLIDAY_DEPARTURES = 8
    }

    private fun decodeBitmapMaxSide(uri: Uri, maxSide: Int): Bitmap? {
        return contentResolver.openInputStream(uri)?.use { stream ->
            val raw = BitmapFactory.decodeStream(stream) ?: return null
            val w = raw.width
            val h = raw.height
            if (w <= maxSide && h <= maxSide) return raw
            val scale = maxSide.toFloat() / maxOf(w, h)
            val nw = (w * scale).toInt().coerceAtLeast(1)
            val nh = (h * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(raw, nw, nh, true)
            if (scaled != raw && !raw.isRecycled) raw.recycle()
            scaled
        }
    }

    private fun showImportMergeDialog(parsed: List<LocalTime>) {
        if (parsed.isEmpty()) {
            Toast.makeText(this, R.string.timetable_import_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val preview = parsed.joinToString("、") { it.toString() }
        AlertDialog.Builder(this)
            .setTitle(R.string.timetable_import_result_title)
            .setMessage(getString(R.string.timetable_import_result_message, parsed.size, preview))
            .setPositiveButton(R.string.timetable_import_replace) { _, _ ->
                times.clear()
                times.addAll(parsed)
                times.sort()
                adapter.rebuildItems()
            }
            .setNeutralButton(R.string.timetable_import_merge) { _, _ ->
                val merged = (times + parsed).toMutableSet().toMutableList()
                merged.sort()
                times.clear()
                times.addAll(merged)
                adapter.rebuildItems()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshHomeWidgets() {
        sendBroadcast(
            Intent(this, SchoolBusAppWidgetProvider::class.java).apply {
                action = SchoolBusAppWidgetProvider.ACTION_REFRESH_ALL
            },
        )
    }
}
