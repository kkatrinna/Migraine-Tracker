package com.example.migrainetracker.ui.fragment

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.MedicineReminder
import com.example.migrainetracker.data.entity.MigraineRecord
import com.example.migrainetracker.data.entity.MigraineRecordTrigger
import com.example.migrainetracker.data.entity.Trigger
import com.example.migrainetracker.data.repository.TrackerRepository
import com.example.migrainetracker.ui.adapters.RemindersAdapter
import com.example.migrainetracker.ui.adapters.TriggerCheckboxAdapter
import com.example.migrainetracker.utils.ReminderReceiver
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainFragment : Fragment() {

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var repository: TrackerRepository
    private lateinit var db: AppDatabase
    private var currentYearMonth = YearMonth.now()
    private var migraineDays = mutableMapOf<LocalDate, MutableList<MigraineRecord>>()
    private var menstruationDays = mutableSetOf<LocalDate>()
    private var allTriggers = listOf<Trigger>()

    private lateinit var textMonthTitle: TextView
    private lateinit var recyclerCalendar: RecyclerView
    private lateinit var textMigraineDaysCount: TextView
    private lateinit var textAvgIntensity: TextView
    private lateinit var textMaxIntensity: TextView
    private lateinit var textTopTriggers: TextView
    private lateinit var layoutTopTriggers: LinearLayout

    private lateinit var remindersAdapter: RemindersAdapter
    private lateinit var recyclerReminders: RecyclerView
    private lateinit var textEmptyReminders: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())
        repository = TrackerRepository(db)

        textMonthTitle = view.findViewById(R.id.text_month_title)
        recyclerCalendar = view.findViewById(R.id.recycler_calendar)
        textMigraineDaysCount = view.findViewById(R.id.text_migraine_days_count)
        textAvgIntensity = view.findViewById(R.id.text_avg_intensity)
        textMaxIntensity = view.findViewById(R.id.text_max_intensity)
        textTopTriggers = view.findViewById(R.id.text_top_triggers)
        layoutTopTriggers = view.findViewById(R.id.layout_top_triggers)

        setupCalendar()
        setupButtons(view)
        applyThemeColors()

        view.findViewById<Button>(R.id.btn_reminders)?.setOnClickListener {
            showRemindersDialog()
        }

        requestExactAlarmPermission()

        lifecycleScope.launch {
            ensureTriggersExist()
            loadTriggers()
            loadData()
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeColors()
        loadData()
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Разрешение на будильники")
                    .setMessage("Для точных напоминаний о приёме лекарств необходимо разрешить планирование будильников.")
                    .setPositiveButton("Перейти в настройки") { _, _ ->
                        startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Напомнить позже", null)
                    .show()
            }
        }
    }

    private suspend fun ensureTriggersExist() {
        val existingTriggers = db.triggerDao().getAllTriggers()
        if (existingTriggers.isNotEmpty()) {
            allTriggers = existingTriggers
            return
        }

        val presetTriggers = listOf(
            "Стресс", "Недосып", "Яркий свет", "Громкий звук",
            "Погода", "Голод", "Кофеин", "Алкоголь", "Гормоны", "Другое"
        )
        for (name in presetTriggers) {
            db.triggerDao().insertTrigger(Trigger(name = name))
        }

        allTriggers = db.triggerDao().getAllTriggers()
    }

    private fun applyThemeColors() {
        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = if (isNightMode) {
            ContextCompat.getColor(requireContext(), android.R.color.black)
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.white)
        }
        view?.setBackgroundColor(backgroundColor)
        textMonthTitle.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
        if (::calendarAdapter.isInitialized) {
            calendarAdapter.notifyDataSetChanged()
        }
    }

    private suspend fun loadTriggers() {
        allTriggers = db.triggerDao().getAllTriggers()

        if (allTriggers.isEmpty()) {
            val presetTriggers = listOf(
                "Стресс", "Недосып", "Яркий свет", "Громкий звук",
                "Погода", "Голод", "Кофеин", "Алкоголь", "Гормоны", "Другое"
            )
            for (name in presetTriggers) {
                db.triggerDao().insertTrigger(Trigger(name = name))
            }
            allTriggers = db.triggerDao().getAllTriggers()
        }
    }

    private fun setupCalendar() {
        calendarAdapter = CalendarAdapter(
            onDayClick = { date -> showAddMigraineDialog(date) },
            onDayLongClick = { date -> showDayDetailsDialog(date) }
        )
        recyclerCalendar.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = calendarAdapter
        }
    }

    private fun loadTopTriggers() {
        lifecycleScope.launch {
            try {
                val topTriggers = db.triggerDao().getUsedTopTriggers()
                if (::textTopTriggers.isInitialized) {
                    updateTopTriggersUI(topTriggers)
                }
            } catch (e: Exception) {
                if (::textTopTriggers.isInitialized) {
                    textTopTriggers.text = "Ошибка загрузки"
                }
            }
        }
    }

    private fun updateTopTriggersUI(topTriggers: List<com.example.migrainetracker.data.entity.TopTrigger>) {
        if (topTriggers.isEmpty()) {
            textTopTriggers.text = "Нет отмеченных триггеров"
            textTopTriggers.visibility = View.VISIBLE
            layoutTopTriggers.visibility = View.GONE
            return
        }

        textTopTriggers.visibility = View.GONE
        layoutTopTriggers.visibility = View.VISIBLE
        layoutTopTriggers.removeAllViews()

        for ((index, trigger) in topTriggers.withIndex()) {
            val triggerView = layoutInflater.inflate(R.layout.item_top_trigger, layoutTopTriggers, false)

            val textNumber = triggerView.findViewById<TextView>(R.id.text_number)
            val textTriggerName = triggerView.findViewById<TextView>(R.id.text_trigger_name)
            val textTriggerCount = triggerView.findViewById<TextView>(R.id.text_trigger_count)
            val progressBar = triggerView.findViewById<ProgressBar>(R.id.progress_bar_trigger)

            textNumber.text = "${index + 1}"
            textTriggerName.text = trigger.name
            textTriggerCount.text = "${trigger.count} ${getCountWord(trigger.count)}"

            val maxCount = topTriggers.firstOrNull()?.count ?: 1
            val progress = (trigger.count.toFloat() / maxCount * 100).toInt()
            progressBar.progress = progress

            layoutTopTriggers.addView(triggerView)
        }
    }

    private fun getCountWord(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "раз"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "раза"
            else -> "раз"
        }
    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.button_previous_month).setOnClickListener {
            currentYearMonth = currentYearMonth.minusMonths(1)
            updateMonthTitle()
            loadData()
        }
        view.findViewById<Button>(R.id.button_next_month).setOnClickListener {
            currentYearMonth = currentYearMonth.plusMonths(1)
            updateMonthTitle()
            loadData()
        }
        updateMonthTitle()
    }

    private fun updateMonthTitle() {
        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy")
        textMonthTitle.text = currentYearMonth.format(formatter)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val startDate = currentYearMonth.atDay(1)
            val endDate = currentYearMonth.atEndOfMonth()

            val migraineRecords = repository.getMigraineRecordsForDateRange(startDate, endDate)
            migraineDays.clear()
            for (record in migraineRecords) {
                if (!migraineDays.containsKey(record.date)) {
                    migraineDays[record.date] = mutableListOf()
                }
                migraineDays[record.date]?.add(record)
            }

            val menstruationList = repository.getMenstruationDaysForMonth(startDate, endDate)
            menstruationDays.clear()
            for (day in menstruationList) {
                if (day.isMenstruating) {
                    menstruationDays.add(day.date)
                }
            }

            updateCalendar()
            updateStatistics()
            loadTopTriggers()
        }
    }

    private fun updateCalendar() {
        val firstDayOfMonth = currentYearMonth.atDay(1)
        val daysInMonth = currentYearMonth.lengthOfMonth()
        val days = mutableListOf<CalendarDay>()

        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value
        for (i in 1 until firstDayOfWeek) {
            days.add(CalendarDay(null, 0, false))
        }

        for (day in 1..daysInMonth) {
            val date = currentYearMonth.atDay(day)
            val maxIntensity = migraineDays[date]?.maxOfOrNull { it.intensity } ?: 0
            val isMenstruating = menstruationDays.contains(date)
            days.add(CalendarDay(date, maxIntensity, isMenstruating))
        }
        calendarAdapter.submitList(days)
    }

    private fun updateStatistics() {
        val allRecords = migraineDays.values.flatten()
        val daysWithMigraine = allRecords.map { it.date }.distinct().size
        val avgIntensity = if (allRecords.isNotEmpty()) {
            allRecords.map { it.intensity }.average()
        } else {
            0.0
        }
        val maxIntensity = allRecords.maxOfOrNull { it.intensity } ?: 0

        textMigraineDaysCount.text = daysWithMigraine.toString()
        textAvgIntensity.text = String.format("%.1f/10", avgIntensity)
        textMaxIntensity.text = "$maxIntensity/10"
    }

    private fun showAddMigraineDialog(date: LocalDate, recordToEdit: MigraineRecord? = null) {
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Загрузка...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            if (allTriggers.isEmpty()) {
                allTriggers = db.triggerDao().getAllTriggers()
            }
            progressDialog.dismiss()
            showAddMigraineDialogInternal(date, recordToEdit)
        }
    }

    private fun showAddMigraineDialogInternal(date: LocalDate, recordToEdit: MigraineRecord? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_migraine, null)

        val editTime = dialogView.findViewById<EditText>(R.id.edit_time)
        val editEndTime = dialogView.findViewById<EditText>(R.id.edit_end_time)
        val buttonAddDuration = dialogView.findViewById<Button>(R.id.button_add_duration)
        val textDuration = dialogView.findViewById<TextView>(R.id.text_duration)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekBar_intensity)
        val textIntensity = dialogView.findViewById<TextView>(R.id.text_intensity_value)
        val editMedication = dialogView.findViewById<EditText>(R.id.edit_medication)
        val spinnerMedication = dialogView.findViewById<Spinner>(R.id.spinner_medication)
        val checkNausea = dialogView.findViewById<CheckBox>(R.id.check_nausea)
        val checkPhotophobia = dialogView.findViewById<CheckBox>(R.id.check_photophobia)
        val checkAura = dialogView.findViewById<CheckBox>(R.id.check_aura)
        val editNotes = dialogView.findViewById<EditText>(R.id.edit_notes)
        val recyclerTriggers = dialogView.findViewById<RecyclerView>(R.id.recycler_triggers)
        val buttonCurrentTime = dialogView.findViewById<Button>(R.id.button_current_time)

        editTime.addTextChangedListener(com.example.migrainetracker.utils.TimeTextWatcher(editTime))
        editEndTime.addTextChangedListener(com.example.migrainetracker.utils.TimeTextWatcher(editEndTime))

        val medications = listOf("Без лекарства", "Налгезин 500мг", "Налгезин 275мг",
            "Суматриптан 50мг", "Суматриптан 100мг", "Эксенза", "Диалрапид 100", "Своё")

        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, medications)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMedication.adapter = spinnerAdapter
        spinnerMedication.setSelection(0)

        fun updateDuration() {
            val startTimeStr = editTime.text.toString()
            val endTimeStr = editEndTime.text.toString()

            if (startTimeStr.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")) &&
                endTimeStr.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$"))) {
                val start = LocalTime.parse(startTimeStr)
                val end = LocalTime.parse(endTimeStr)
                val durationMinutes = if (end.isAfter(start)) {
                    java.time.Duration.between(start, end).toMinutes()
                } else {
                    java.time.Duration.between(start, end.plusHours(24)).toMinutes()
                }
                val hours = durationMinutes / 60
                val minutes = durationMinutes % 60
                textDuration.text = "Длительность: ${hours}ч ${minutes}мин"
            } else {
                textDuration.text = "Длительность: не указана"
            }
        }

        editTime.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateDuration() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editEndTime.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateDuration() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        buttonAddDuration.setOnClickListener {
            val currentEnd = editEndTime.text.toString()
            val newTime = if (currentEnd.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$"))) {
                val time = LocalTime.parse(currentEnd)
                val newTime = time.plusHours(1)
                String.format("%02d:%02d", newTime.hour, newTime.minute)
            } else {
                val now = LocalTime.now()
                String.format("%02d:%02d", now.hour, now.minute)
            }
            editEndTime.setText(newTime)
        }

        val selectedTriggerIds = mutableSetOf<Int>()

        if (recordToEdit != null) {
            runBlocking {
                val ids = db.migraineRecordTriggerDao().getTriggerIdsForRecord(recordToEdit.id)
                selectedTriggerIds.addAll(ids)
            }
        }

        val triggerAdapter = TriggerCheckboxAdapter(allTriggers, selectedTriggerIds)
        recyclerTriggers.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerTriggers.adapter = triggerAdapter

        if (recordToEdit != null) {
            editTime.setText(recordToEdit.time.format(DateTimeFormatter.ofPattern("HH:mm")))
            recordToEdit.endTime?.let { editEndTime.setText(it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            seekBar.progress = recordToEdit.intensity
            textIntensity.text = "${recordToEdit.intensity}/10"
            checkNausea.isChecked = recordToEdit.nausea
            checkPhotophobia.isChecked = recordToEdit.photophobia
            checkAura.isChecked = recordToEdit.aura
            editNotes.setText(recordToEdit.notes ?: "")
            if (!recordToEdit.medicationName.isNullOrEmpty()) {
                val index = medications.indexOf(recordToEdit.medicationName)
                if (index != -1) {
                    spinnerMedication.setSelection(index)
                } else {
                    editMedication.setText(recordToEdit.medicationName)
                }
            }
            updateDuration()
        } else {
            val now = LocalTime.now()
            editTime.setText(String.format("%02d:%02d", now.hour, now.minute))
            spinnerMedication.setSelection(0)
            editMedication.setText("")
        }

        spinnerMedication.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    editMedication.setText("")
                    editMedication.isEnabled = false
                    editMedication.hint = "Выбрано 'Без лекарства'"
                } else {
                    editMedication.isEnabled = true
                    editMedication.hint = "Название таблетки"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        buttonCurrentTime.setOnClickListener {
            val now = LocalTime.now()
            editTime.setText(String.format("%02d:%02d", now.hour, now.minute))
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                textIntensity.text = "$progress/10"
                textIntensity.setTextColor(getIntensityColor(progress))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val title = if (recordToEdit != null) "Редактировать запись" else "Добавить запись"

        AlertDialog.Builder(requireContext())
            .setTitle("$title - ${date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val timeStr = editTime.text.toString()
                val endTimeStr = editEndTime.text.toString()

                val timePattern = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
                if (!timePattern.matches(timeStr)) {
                    Toast.makeText(requireContext(), "Введите время в формате ЧЧ:ММ (например 14:30)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val parts = timeStr.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                val time = LocalTime.of(hour, minute)

                val endTime = if (endTimeStr.isNotBlank() && timePattern.matches(endTimeStr)) {
                    val endParts = endTimeStr.split(":")
                    LocalTime.of(endParts[0].toInt(), endParts[1].toInt())
                } else null

                val intensity = seekBar.progress
                val selectedMedication = spinnerMedication.selectedItem.toString()
                val customMedication = editMedication.text.toString()

                val medicationName = when {
                    selectedMedication == "Без лекарства" -> null
                    customMedication.isNotBlank() -> customMedication
                    selectedMedication.isNotBlank() -> selectedMedication
                    else -> null
                }

                val record = MigraineRecord(
                    id = recordToEdit?.id ?: 0,
                    date = date,
                    time = time,
                    endTime = endTime,
                    intensity = intensity,
                    medicationName = medicationName,
                    medicationTime = if (medicationName != null) time else null,
                    nausea = checkNausea.isChecked,
                    photophobia = checkPhotophobia.isChecked,
                    aura = checkAura.isChecked,
                    notes = editNotes.text.toString().ifBlank { null }
                )

                lifecycleScope.launch {
                    try {
                        if (recordToEdit != null) {
                            repository.updateMigraineRecord(record)
                            val existingId = recordToEdit.id
                            db.migraineRecordTriggerDao().deleteByRecordId(existingId)
                            for (triggerId in selectedTriggerIds) {
                                db.migraineRecordTriggerDao().insert(
                                    MigraineRecordTrigger(recordId = existingId, triggerId = triggerId)
                                )
                            }
                        } else {
                            val newId = repository.addMigraineRecord(record)
                            for (triggerId in selectedTriggerIds) {
                                db.migraineRecordTriggerDao().insert(
                                    MigraineRecordTrigger(recordId = newId.toInt(), triggerId = triggerId)
                                )
                            }
                        }
                        loadData()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDayDetailsDialog(date: LocalDate) {
        lifecycleScope.launch {
            val records = migraineDays[date] ?: emptyList()
            val isMenstruating = menstruationDays.contains(date)

            val recordsWithTriggers = records.map { record ->
                val triggerIds = db.migraineRecordTriggerDao().getTriggerIdsForRecord(record.id)
                val triggerNames = triggerIds.mapNotNull { id ->
                    allTriggers.find { it.id == id }?.name
                }
                record to triggerNames
            }

            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Детали дня - ${date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))}")

            val view = layoutInflater.inflate(R.layout.dialog_day_details, null)
            val textRecordsList = view.findViewById<TextView>(R.id.text_records_list)

            val message = buildString {
                if (isMenstruating) {
                    append(" День месячных\n\n")
                }
                if (recordsWithTriggers.isEmpty()) {
                    append("Нет записей о мигрени")
                } else {
                    append(" Записи о мигрени:\n")
                    for ((index, pair) in recordsWithTriggers.withIndex()) {
                        val record = pair.first
                        val triggers = pair.second
                        append("${index + 1}. ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))} - ")
                        append("Боль: ${record.intensity}/10")
                        if (record.medicationName != null) {
                            append("  ${record.medicationName}")
                        }
                        if (triggers.isNotEmpty()) {
                            append("\n    ⚡ Триггеры: ${triggers.joinToString(", ")}")
                        }
                        append("\n")
                    }
                }
            }
            textRecordsList.text = message

            builder.setView(view)
            builder.setPositiveButton("Закрыть", null)
            builder.setNeutralButton("Добавить запись") { _, _ ->
                showAddMigraineDialog(date)
            }

            if (records.isNotEmpty()) {
                builder.setNegativeButton("Управление записями") { _, _ ->
                    showRecordManagementDialog(date, records)
                }
            }

            builder.show()
        }
    }

    private fun showRecordManagementDialog(date: LocalDate, records: List<MigraineRecord>) {
        val items = records.mapIndexed { index, record ->
            "${index + 1}. ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${record.intensity}/10" +
                    if (record.medicationName != null) " (${record.medicationName})" else ""
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите запись")
            .setItems(items) { _, which ->
                val selectedRecord = records[which]
                showRecordActionDialog(date, selectedRecord)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRecordActionDialog(date: LocalDate, record: MigraineRecord) {
        val actions = arrayOf(" Редактировать", " Удалить")

        AlertDialog.Builder(requireContext())
            .setTitle("Действие с записью")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showAddMigraineDialog(date, record)
                    1 -> showDeleteRecordConfirmDialog(record)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteRecordConfirmDialog(record: MigraineRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить запись")
            .setMessage("Удалить запись о мигрени от ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))} с интенсивностью ${record.intensity}/10?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    db.migraineRecordTriggerDao().deleteByRecordId(record.id)
                    repository.deleteMigraineRecord(record)
                    loadData()
                }
            }
            .setNegativeButton("Отмена", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun getIntensityColor(intensity: Int): Int {
        return when (intensity) {
            0 -> Color.parseColor("#4CAF50")
            in 1..3 -> Color.parseColor("#8BC34A")
            in 4..6 -> Color.parseColor("#FFC107")
            in 7..8 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
    }

    private fun getIntensityColorWithAlpha(intensity: Int, alpha: Int): Int {
        val color = getIntensityColor(intensity)
        return ColorUtils.setAlphaComponent(color, alpha)
    }

    private fun isDarkColor(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val brightness = (0.299 * red + 0.587 * green + 0.114 * blue)
        return brightness < 128
    }

    private fun showRemindersDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reminders, null)
        recyclerReminders = dialogView.findViewById(R.id.recycler_reminders)
        val btnAddReminder = dialogView.findViewById<Button>(R.id.btn_add_reminder)
        textEmptyReminders = dialogView.findViewById(R.id.text_empty_reminders)

        recyclerReminders.layoutManager = LinearLayoutManager(requireContext())
        remindersAdapter = RemindersAdapter(
            reminders = emptyList(),
            onDeleteClick = { reminder -> deleteReminder(reminder) },
            onToggleClick = { reminder, isEnabled -> toggleReminder(reminder, isEnabled) }
        )
        recyclerReminders.adapter = remindersAdapter

        loadRemindersData()

        btnAddReminder.setOnClickListener {
            showAddReminderDialog()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Напоминания о лекарствах")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun loadRemindersData() {
        lifecycleScope.launch {
            try {
                val reminders = db.medicineReminderDao().getAllRemindersList()
                if (reminders.isEmpty()) {
                    textEmptyReminders.visibility = View.VISIBLE
                    recyclerReminders.visibility = View.GONE
                } else {
                    textEmptyReminders.visibility = View.GONE
                    recyclerReminders.visibility = View.VISIBLE
                    remindersAdapter.updateList(reminders)
                }
            } catch (e: Exception) {
                textEmptyReminders.visibility = View.VISIBLE
                textEmptyReminders.text = "Ошибка загрузки: ${e.message}"
                recyclerReminders.visibility = View.GONE
            }
        }
    }

    private fun refreshRemindersList() {
        lifecycleScope.launch {
            try {
                val reminders = db.medicineReminderDao().getAllRemindersList()
                remindersAdapter.updateList(reminders)
                if (reminders.isEmpty()) {
                    textEmptyReminders.visibility = View.VISIBLE
                    recyclerReminders.visibility = View.GONE
                } else {
                    textEmptyReminders.visibility = View.GONE
                    recyclerReminders.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deleteReminder(reminder: MedicineReminder) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить напоминание")
            .setMessage("Удалить напоминание для \"${reminder.medicineName}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    cancelReminder(reminder.id)
                    db.medicineReminderDao().deleteReminder(reminder)
                    refreshRemindersList()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toggleReminder(reminder: MedicineReminder, isEnabled: Boolean) {
        lifecycleScope.launch {
            val updatedReminder = reminder.copy(isEnabled = isEnabled)
            db.medicineReminderDao().updateReminder(updatedReminder)

            if (isEnabled) {
                scheduleReminder(updatedReminder)
            } else {
                cancelReminder(reminder.id)
            }
            refreshRemindersList()
        }
    }

    private fun cancelReminder(reminderId: Int) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun showAddReminderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_reminder, null)
        val editMedicineName = dialogView.findViewById<TextInputEditText>(R.id.edit_medicine_name)
        val editReminderTime = dialogView.findViewById<TextInputEditText>(R.id.edit_reminder_time)
        val checkRepeat = dialogView.findViewById<CheckBox>(R.id.check_repeat)
        val spinnerRepeat = dialogView.findViewById<Spinner>(R.id.spinner_repeat)

        val btnTime9 = dialogView.findViewById<MaterialButton>(R.id.btn_time_9)
        val btnTime12 = dialogView.findViewById<MaterialButton>(R.id.btn_time_12)
        val btnTime15 = dialogView.findViewById<MaterialButton>(R.id.btn_time_15)
        val btnTime18 = dialogView.findViewById<MaterialButton>(R.id.btn_time_18)
        val btnTime21 = dialogView.findViewById<MaterialButton>(R.id.btn_time_21)

        editReminderTime.addTextChangedListener(com.example.migrainetracker.utils.TimeTextWatcher(editReminderTime))
        editReminderTime.setText("09:00")

        btnTime9.setOnClickListener { editReminderTime.setText("09:00") }
        btnTime12.setOnClickListener { editReminderTime.setText("12:00") }
        btnTime15.setOnClickListener { editReminderTime.setText("15:00") }
        btnTime18.setOnClickListener { editReminderTime.setText("18:00") }
        btnTime21.setOnClickListener { editReminderTime.setText("21:00") }

        val repeatIntervals = listOf("Каждые 24 часа (ежедневно)", "Каждые 12 часов", "Каждые 8 часов", "Каждые 6 часов", "Каждые 4 часа", "Каждые 2 часа", "Каждый час")
        val repeatValues = listOf(24, 12, 8, 6, 4, 2, 1)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, repeatIntervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRepeat.adapter = adapter
        spinnerRepeat.isEnabled = false

        checkRepeat.setOnCheckedChangeListener { _, isChecked ->
            spinnerRepeat.isEnabled = isChecked
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Разрешение на уведомления")
                    .setMessage("Для получения напоминаний необходимо разрешить уведомления.")
                    .setPositiveButton("Разрешить") { _, _ ->
                        requestPermissions(
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            1002
                        )
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Разрешение на будильники")
                    .setMessage("Для точных напоминаний о лекарствах необходимо разрешить планирование будильников в настройках.")
                    .setPositiveButton("Открыть настройки") { _, _ ->
                        startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
                return
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Новое напоминание")
            .setView(dialogView)
            .setPositiveButton("Добавить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val medicineName = editMedicineName.text.toString()
                if (medicineName.isBlank()) {
                    editMedicineName.error = "Введите название лекарства"
                    return@setOnClickListener
                }

                val timeStr = editReminderTime.text.toString()
                val timePattern = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
                if (!timePattern.matches(timeStr)) {
                    editReminderTime.error = "Введите время в формате ЧЧ:ММ"
                    return@setOnClickListener
                }

                val parts = timeStr.split(":")
                val hour = parts[0].toIntOrNull() ?: 0
                val minute = parts[1].toIntOrNull() ?: 0
                val reminderTime = LocalTime.of(hour, minute)
                val repeatInterval = if (checkRepeat.isChecked) repeatValues[spinnerRepeat.selectedItemPosition] else 0

                if (repeatInterval == 0) {
                    val now = LocalTime.now()
                    if (reminderTime.isBefore(now)) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Время прошло")
                            .setMessage("Вы выбрали время, которое уже прошло сегодня. Напоминание установится на завтра.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

                lifecycleScope.launch {
                    try {
                        val reminder = MedicineReminder(
                            medicineName = medicineName,
                            reminderTime = reminderTime,
                            isEnabled = true,
                            repeatInterval = repeatInterval
                        )
                        val id = db.medicineReminderDao().insertReminder(reminder)
                        val reminderWithId = reminder.copy(id = id.toInt())
                        scheduleReminder(reminderWithId)

                        refreshRemindersList()
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun scheduleReminder(reminder: MedicineReminder) {
        if (!reminder.isEnabled) return

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), ReminderReceiver::class.java).apply {
            putExtra("medicine_name", reminder.medicineName)
            putExtra("reminder_id", reminder.id)
            putExtra("repeat_interval", reminder.repeatInterval)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, reminder.reminderTime.hour)
            set(java.util.Calendar.MINUTE, reminder.reminderTime.minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (reminder.repeatInterval > 0) {
                val intervalMillis = (reminder.repeatInterval * 60 * 60 * 1000).toLong()
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    intervalMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("Reminder", "Ошибка установки будильника", e)
        }
    }

    inner class CalendarAdapter(
        private val onDayClick: (LocalDate) -> Unit,
        private val onDayLongClick: (LocalDate) -> Unit
    ) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

        private var days = listOf<CalendarDay>()

        fun submitList(newDays: List<CalendarDay>) {
            days = newDays
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_calendar_day, parent, false)
            return DayViewHolder(view)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            holder.bind(days[position])
        }

        override fun getItemCount() = days.size

        inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textDay: TextView = itemView.findViewById(R.id.text_day)
            private val imagePain: ImageView = itemView.findViewById(R.id.image_pain)
            private val imageMenstruation: ImageView = itemView.findViewById(R.id.image_menstruation)

            fun bind(day: CalendarDay) {
                if (day.date != null) {
                    textDay.text = day.date.dayOfMonth.toString()
                    textDay.isEnabled = true
                    textDay.visibility = View.VISIBLE

                    val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

                    when (day.intensity) {
                        0 -> {
                            imagePain.setImageResource(R.drawable.ic_no_migraine)
                            imagePain.visibility = View.VISIBLE
                        }
                        in 1..3 -> {
                            imagePain.setImageResource(R.drawable.ic_migraine_mild)
                            imagePain.visibility = View.VISIBLE
                        }
                        in 4..6 -> {
                            imagePain.setImageResource(R.drawable.ic_migraine_moderate)
                            imagePain.visibility = View.VISIBLE
                        }
                        else -> {
                            imagePain.setImageResource(R.drawable.ic_migraine_severe)
                            imagePain.visibility = View.VISIBLE
                        }
                    }

                    imageMenstruation.visibility = if (day.isMenstruating) View.VISIBLE else View.GONE

                    if (day.intensity > 0) {
                        val backgroundColor = getIntensityColorWithAlpha(day.intensity, 100)
                        textDay.setBackgroundColor(backgroundColor)
                        val intensityColor = getIntensityColor(day.intensity)
                        textDay.setTextColor(if (isDarkColor(intensityColor)) Color.WHITE else Color.BLACK)
                    } else {
                        textDay.background = null
                        textDay.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
                    }

                    itemView.setOnClickListener { onDayClick(day.date) }
                    itemView.setOnLongClickListener {
                        onDayLongClick(day.date)
                        true
                    }
                } else {
                    textDay.text = ""
                    textDay.isEnabled = false
                    textDay.visibility = View.INVISIBLE
                    imagePain.visibility = View.INVISIBLE
                    imageMenstruation.visibility = View.INVISIBLE
                    itemView.setOnClickListener(null)
                }
            }
        }
    }

    data class CalendarDay(
        val date: LocalDate?,
        val intensity: Int,
        val isMenstruating: Boolean
    )
}