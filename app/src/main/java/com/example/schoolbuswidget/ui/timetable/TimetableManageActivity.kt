package com.example.schoolbuswidget.ui.timetable

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
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
import com.example.schoolbuswidget.domain.SouthPeak655PosterParser
import com.example.schoolbuswidget.domain.ServiceDayType
import com.example.schoolbuswidget.domain.TimetableImportParser
import com.example.schoolbuswidget.ui.AlignedHourTimesGrid
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
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
    private var imageOcrLoadingDialog: AlertDialog? = null

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
        toolbar.inflateMenu(R.menu.menu_timetable_manage)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_reset) {
                confirmResetToDefault()
                true
            } else {
                false
            }
        }

        chipNorth = findViewById(R.id.chipNorth)
        chipSouth = findViewById(R.id.chipSouth)
        chipWorkday = findViewById(R.id.chipWorkday)
        chipHoliday = findViewById(R.id.chipHoliday)
        textCustomSource = findViewById(R.id.textCustomSource)

        val chipGroupCampus = findViewById<ChipGroup>(R.id.chipGroupCampus)
        val chipGroupDayType = findViewById<ChipGroup>(R.id.chipGroupDayType)

        adapter = TimetableGroupedTimeAdapter(
            times,
            onDelete = { index ->
                if (index in times.indices) {
                    times.removeAt(index)
                    adapter.rebuildItems()
                }
            },
            onHourLongPress = { hour -> showHourActionsDialog(hour) },
        )
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

    private fun showHourActionsDialog(hour: Int) {
        val count = times.count { it.hour == hour }
        val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        val btnMargin = (8 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        lateinit var dialog: AlertDialog
        val addButton = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            text = getString(R.string.timetable_hour_batch_add)
            setOnClickListener {
                dialog.dismiss()
                showBatchAddHourDialog(hour)
            }
        }
        root.addView(addButton)

        if (count > 0) {
            root.addView(
                MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = btnMargin
                    }
                    text = getString(R.string.timetable_hour_delete_all, count)
                    setOnClickListener {
                        dialog.dismiss()
                        confirmDeleteHour(hour)
                    }
                },
            )
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.timetable_hour_actions_title, hour))
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun showBatchAddHourDialog(hour: Int) {
        val input = EditText(this).apply {
            setHint(R.string.timetable_hour_batch_add_hint)
            minLines = 2
            setHorizontallyScrolling(false)
        }
        val padding = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        input.setPadding(padding, padding, padding, padding)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.timetable_hour_actions_title, hour))
            .setView(input)
            .setPositiveButton(R.string.timetable_import_parse) { _, _ ->
                val parsed = TimetableImportParser.extractTimesForHour(hour, input.text.toString())
                if (parsed.isEmpty()) {
                    Toast.makeText(this, R.string.timetable_hour_batch_add_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                var added = 0
                parsed.forEach { time ->
                    if (time !in times) {
                        times.add(time)
                        added++
                    }
                }
                times.sort()
                adapter.ensureHourExpanded(hour)
                adapter.rebuildItems()
                Toast.makeText(
                    this,
                    getString(R.string.timetable_hour_batch_add_added, added),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteHour(hour: Int) {
        val count = times.count { it.hour == hour }
        if (count == 0) return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.timetable_hour_delete_confirm, hour, count))
            .setPositiveButton(R.string.timetable_delete_time) { _, _ ->
                times.removeAll { it.hour == hour }
                adapter.rebuildItems()
                Toast.makeText(this, R.string.timetable_hour_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
            showImageOcrLoading()
            var bitmap: Bitmap? = null
            try {
                bitmap = withContext(Dispatchers.IO) { decodeBitmapMaxSide(uri, 2048) }
                if (bitmap == null) {
                    Toast.makeText(this@TimetableManageActivity, R.string.timetable_image_load_fail, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val campus = selectedLocation()
                val dayType = selectedDayType()
                val outcome = withContext(Dispatchers.Default) {
                    ocrBitmapToOutcome(bitmap, campus, dayType)
                }
                when (outcome) {
                    is OcrImportOutcome.PosterDual ->
                        showPosterDualImageImportDialog(outcome.campus, outcome.workday, outcome.holiday)
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
                hideImageOcrLoading()
                bitmap?.let { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    private fun showImageOcrLoading() {
        hideImageOcrLoading()
        val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(
            CircularProgressIndicator(this).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = pad }
            },
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.timetable_image_ocr_loading)
            },
        )
        imageOcrLoadingDialog = AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(false)
            .create()
        imageOcrLoadingDialog?.show()
    }

    private fun hideImageOcrLoading() {
        imageOcrLoadingDialog?.dismiss()
        imageOcrLoadingDialog = null
    }

    override fun onDestroy() {
        hideImageOcrLoading()
        super.onDestroy()
    }

    private sealed class OcrImportOutcome {
        data class PosterDual(
            val campus: CampusLocation,
            val workday: List<LocalTime>,
            val holiday: List<LocalTime>,
        ) : OcrImportOutcome()

        data class Single(val times: List<LocalTime>) : OcrImportOutcome()
    }

    /**
     * 655 poster: when both columns parse well, return [PosterDual] so the UI can save both without
     * switching the day-type chip. Otherwise fall back to flat OCR for the selected column.
     */
    private fun ocrBitmapToOutcome(
        decoded: Bitmap,
        campus: CampusLocation,
        dayType: ServiceDayType,
    ): OcrImportOutcome {
        when (campus) {
            CampusLocation.NORTH -> {
                tryStructuredPosterImport(
                    decoded = decoded,
                    campus = campus,
                    dayType = dayType,
                    crop = { OcrBitmapPreprocessor.cropNorthScheduleRegion(it) },
                    parse = { lines, w, h, scale ->
                        NorthPeak655PosterParser.tryParse(lines, w, h, scale)?.let {
                            PosterParseResult(it.workday, it.holiday)
                        }
                    },
                )?.let { return it }
            }
            CampusLocation.SOUTH -> {
                tryStructuredPosterImport(
                    decoded = decoded,
                    campus = campus,
                    dayType = dayType,
                    crop = { OcrBitmapPreprocessor.cropSouthScheduleRegion(it) },
                    parse = { lines, w, h, scale ->
                        SouthPeak655PosterParser.tryParse(lines, w, h, scale)?.let {
                            PosterParseResult(it.workday, it.holiday)
                        }
                    },
                )?.let { return it }
            }
        }
        val prep = OcrBitmapPreprocessor.preprocessForTimetableOcr(decoded)
        try {
            val flat = TimetableImportParser.extractTimesFromText(
                RapidOcrRecognizer.recognizeText(prep, this),
            )
            return OcrImportOutcome.Single(flat)
        } finally {
            if (!prep.isRecycled && prep !== decoded) prep.recycle()
        }
    }

    private data class PosterParseResult(
        val workday: List<LocalTime>,
        val holiday: List<LocalTime>,
    )

    private fun tryStructuredPosterImport(
        decoded: Bitmap,
        campus: CampusLocation,
        dayType: ServiceDayType,
        crop: (Bitmap) -> Bitmap,
        parse: (lines: List<com.example.schoolbuswidget.data.rapidocr.OcrTextLine>, Int, Int, Float) ->
            PosterParseResult?,
    ): OcrImportOutcome? {
        val cropped = crop(decoded)
        val prep = OcrBitmapPreprocessor.preprocessForPosterScheduleOcr(cropped)
        if (cropped !== decoded && !cropped.isRecycled) cropped.recycle()
        val (scaled, appliedScale) = OcrBitmapPreprocessor.upscaleForScheduleOcr(prep)
        if (scaled !== prep && !prep.isRecycled) prep.recycle()
        try {
            val lines = RapidOcrRecognizer.recognizeLines(scaled, this)
            val structured = parse(lines, scaled.width, scaled.height, appliedScale) ?: return null
            val wdOk = structured.workday.size >= MIN_STRUCTURED_WORKDAY_DEPARTURES
            val holOk = structured.holiday.size >= MIN_STRUCTURED_HOLIDAY_DEPARTURES
            if (wdOk || holOk) {
                return OcrImportOutcome.PosterDual(campus, structured.workday, structured.holiday)
            }
            val column = when (dayType) {
                ServiceDayType.WORKDAY -> structured.workday
                ServiceDayType.HOLIDAY -> structured.holiday
            }
            if (column.size >= MIN_STRUCTURED_WORKDAY_DEPARTURES) {
                return OcrImportOutcome.Single(column)
            }
            return null
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    private fun showPosterDualImageImportDialog(
        campus: CampusLocation,
        workday: List<LocalTime>,
        holiday: List<LocalTime>,
    ) {
        val titleRes = when (campus) {
            CampusLocation.NORTH -> R.string.timetable_import_north_dual_title
            CampusLocation.SOUTH -> R.string.timetable_import_south_dual_title
        }
        val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        val btnMargin = (8 * resources.displayMetrics.density).toInt()
        val maxPreviewHeight = resources.getDimensionPixelSize(R.dimen.dialog_import_preview_max_height)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        val chipGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(0, 0, 0, btnMargin)
        }
        val chipWorkday = Chip(this).apply {
            id = View.generateViewId()
            isCheckable = true
            text = getString(
                R.string.timetable_import_preview_column_header,
                getString(R.string.label_day_workday),
                workday.size,
            )
            isEnabled = workday.isNotEmpty()
            isChecked = workday.isNotEmpty() || holiday.isEmpty()
        }
        val chipHoliday = Chip(this).apply {
            id = View.generateViewId()
            isCheckable = true
            text = getString(
                R.string.timetable_import_preview_column_header,
                getString(R.string.label_day_holiday),
                holiday.size,
            )
            isEnabled = holiday.isNotEmpty()
            isChecked = holiday.isNotEmpty() && workday.isEmpty()
        }
        chipGroup.addView(chipWorkday)
        chipGroup.addView(chipHoliday)
        root.addView(chipGroup)

        val previewHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val previewScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxPreviewHeight,
            )
            addView(previewHost)
        }
        root.addView(previewScroll)

        fun fillPreview(times: List<LocalTime>) {
            previewHost.removeAllViews()
            ImportPreviewLayout(previewHost).appendImportPreviewTimes(times)
        }

        lateinit var dialog: AlertDialog

        val saveCurrentBtn = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = btnMargin
            }
            setOnClickListener {
                dialog.dismiss()
                if (chipWorkday.isChecked) {
                    savePosterOneColumn(
                        campus,
                        ServiceDayType.WORKDAY,
                        workday,
                        R.string.timetable_import_poster_saved_workday,
                    )
                } else {
                    savePosterOneColumn(
                        campus,
                        ServiceDayType.HOLIDAY,
                        holiday,
                        R.string.timetable_import_poster_saved_holiday,
                    )
                }
            }
        }
        root.addView(saveCurrentBtn)

        fun refreshForSelection(workdaySelected: Boolean) {
            if (workdaySelected) {
                fillPreview(workday)
                saveCurrentBtn.text = getString(R.string.timetable_import_poster_save_workday)
                saveCurrentBtn.isEnabled = workday.isNotEmpty()
            } else {
                fillPreview(holiday)
                saveCurrentBtn.text = getString(R.string.timetable_import_poster_save_holiday)
                saveCurrentBtn.isEnabled = holiday.isNotEmpty()
            }
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                chipWorkday.id -> refreshForSelection(workdaySelected = true)
                chipHoliday.id -> refreshForSelection(workdaySelected = false)
            }
        }
        refreshForSelection(workdaySelected = chipWorkday.isChecked)

        dialog = AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(root)
            .setNegativeButton(R.string.timetable_import_poster_dual_save_both) { _, _ ->
                savePosterBothColumns(
                    campus,
                    workday,
                    holiday,
                    R.string.timetable_import_poster_saved_both,
                )
            }
            .setPositiveButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled =
                workday.isNotEmpty() && holiday.isNotEmpty()
        }

        root.setPadding(pad, pad, pad, pad)
        dialog.show()
    }

    private class ImportPreviewLayout(private val container: LinearLayout) {
        private val context get() = container.context

        fun appendImportPreviewColumn(title: String, times: List<LocalTime>) {
            container.addView(
                TextView(context).apply {
                    text = title
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                },
            )
            appendImportPreviewTimes(times)
        }

        fun appendImportPreviewTimes(times: List<LocalTime>) {
            if (times.isEmpty()) {
                container.addView(
                    TextView(context).apply {
                        text = context.getString(R.string.timetable_import_preview_none)
                        textSize = 14f
                        setPadding(0, blockGapPx(), 0, 0)
                    },
                )
                return
            }
            times.distinct().sorted()
                .groupBy { it.hour }
                .toSortedMap()
                .forEach { (hour, hourTimes) ->
                    container.addView(
                        TextView(context).apply {
                            text = context.getString(
                                R.string.timetable_hour_section,
                                hour,
                                hourTimes.size,
                            )
                            textSize = 14f
                            setTypeface(typeface, Typeface.BOLD)
                            setPadding(0, blockGapPx(), 0, 0)
                        },
                    )
                    AlignedHourTimesGrid.appendTo(
                        container,
                        hourTimes,
                        AlignedHourTimesGrid.Style(
                            rowStartPaddingPx = timeIndentPx(),
                            rowBottomMarginPx = timeLineGapPx(),
                            textSelectable = true,
                        ),
                    )
                }
        }

        fun appendImportPreviewSectionSpacer() {
            container.addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        sectionGapPx(),
                    )
                },
            )
        }

        fun appendImportPreviewFooter(text: CharSequence) {
            container.addView(
                TextView(context).apply {
                    this.text = text
                    textSize = 14f
                    setPadding(0, sectionGapPx(), 0, 0)
                },
            )
        }

        private fun sectionGapPx(): Int =
            context.resources.getDimensionPixelSize(R.dimen.dialog_import_preview_section_gap)

        private fun blockGapPx(): Int =
            context.resources.getDimensionPixelSize(R.dimen.dialog_import_preview_block_gap)

        private fun timeIndentPx(): Int = (12 * context.resources.displayMetrics.density).toInt()

        private fun timeLineGapPx(): Int = (2 * context.resources.displayMetrics.density).toInt()
    }

    private fun buildImportPreviewScrollView(
        block: ImportPreviewLayout.() -> Unit,
    ): ScrollView {
        val pad = resources.getDimensionPixelSize(R.dimen.dialog_edit_padding)
        val maxHeight = resources.getDimensionPixelSize(R.dimen.dialog_import_preview_max_height)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        ImportPreviewLayout(container).block()
        return ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxHeight,
            )
            addView(container)
        }
    }

    private fun savePosterBothColumns(
        campus: CampusLocation,
        workday: List<LocalTime>,
        holiday: List<LocalTime>,
        savedToastRes: Int,
    ) {
        lifecycleScope.launch {
            repository.saveDepartures(
                campus,
                ServiceDayType.WORKDAY,
                workday.distinct().sorted().map { DepartureTime(it) },
            )
            repository.saveDepartures(
                campus,
                ServiceDayType.HOLIDAY,
                holiday.distinct().sorted().map { DepartureTime(it) },
            )
            loadTimetableForSelection()
            Toast.makeText(this@TimetableManageActivity, savedToastRes, Toast.LENGTH_SHORT).show()
            refreshHomeWidgets()
        }
    }

    private fun savePosterOneColumn(
        campus: CampusLocation,
        dayType: ServiceDayType,
        times: List<LocalTime>,
        savedToastRes: Int,
    ) {
        lifecycleScope.launch {
            repository.saveDepartures(
                campus,
                dayType,
                times.distinct().sorted().map { DepartureTime(it) },
            )
            loadTimetableForSelection()
            Toast.makeText(this@TimetableManageActivity, savedToastRes, Toast.LENGTH_SHORT).show()
            refreshHomeWidgets()
        }
    }

    companion object {
        /** If structured poster parse yields at least this many times, trust it over flat OCR. */
        private const val MIN_STRUCTURED_WORKDAY_DEPARTURES = 12
        private const val MIN_STRUCTURED_HOLIDAY_DEPARTURES = 8
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
        val previewScroll = buildImportPreviewScrollView {
            appendImportPreviewColumn(
                getString(R.string.main_departure_list_summary, parsed.size),
                parsed,
            )
            appendImportPreviewFooter(
                getString(R.string.timetable_import_result_message, parsed.size),
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.timetable_import_result_title)
            .setView(previewScroll)
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
