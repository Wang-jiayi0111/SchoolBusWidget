package com.example.schoolbuswidget.ui.timetable

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
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
import com.example.schoolbuswidget.data.mlkit.MlKitChineseTextRecognizer
import com.example.schoolbuswidget.domain.CampusLocation
import com.example.schoolbuswidget.domain.DepartureTime
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.domain.TimetableImportParser
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

class TimetableManageActivity : AppCompatActivity() {

    private val times = mutableListOf<LocalTime>()
    private lateinit var adapter: TimetableTimeAdapter
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

        adapter = TimetableTimeAdapter(times) { index ->
            if (index in times.indices) {
                times.removeAt(index)
                adapter.notifyDataSetChanged()
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
            adapter.notifyDataSetChanged()
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
                adapter.notifyDataSetChanged()
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

    private fun onImagePicked(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmapMaxSide(uri, 1600) }
            if (bitmap == null) {
                Toast.makeText(this@TimetableManageActivity, R.string.timetable_image_load_fail, Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val text = MlKitChineseTextRecognizer.recognizeText(inputImage)
                val parsed = TimetableImportParser.extractTimesFromText(text)
                showImportMergeDialog(parsed)
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
                adapter.notifyDataSetChanged()
            }
            .setNeutralButton(R.string.timetable_import_merge) { _, _ ->
                val merged = (times + parsed).toMutableSet().toMutableList()
                merged.sort()
                times.clear()
                times.addAll(merged)
                adapter.notifyDataSetChanged()
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
