package com.example.migrainetracker.ui.fragment

import android.app.TimePickerDialog
import android.content.res.Configuration
import android.graphics.Color
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
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.MigraineRecord
import com.example.migrainetracker.data.repository.TrackerRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainFragment : Fragment() {

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var repository: TrackerRepository
    private var currentYearMonth = YearMonth.now()
    private var migraineDays = mutableMapOf<LocalDate, MutableList<MigraineRecord>>()
    private var menstruationDays = mutableSetOf<LocalDate>()

    private lateinit var textMonthTitle: TextView
    private lateinit var recyclerCalendar: RecyclerView
    private lateinit var textMigraineDaysCount: TextView
    private lateinit var textAvgIntensity: TextView
    private lateinit var textMaxIntensity: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getInstance(requireContext())
        repository = TrackerRepository(db)

        textMonthTitle = view.findViewById(R.id.text_month_title)
        recyclerCalendar = view.findViewById(R.id.recycler_calendar)
        textMigraineDaysCount = view.findViewById(R.id.text_migraine_days_count)
        textAvgIntensity = view.findViewById(R.id.text_avg_intensity)
        textMaxIntensity = view.findViewById(R.id.text_max_intensity)

        setupCalendar()
        setupButtons(view)
        loadData()
        applyThemeColors()
    }

    override fun onResume() {
        super.onResume()
        applyThemeColors()
        loadData()
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

    private fun setupCalendar() {
        calendarAdapter = CalendarAdapter(
            onDayClick = { date ->
                showAddMigraineDialog(date)
            },
            onDayLongClick = { date ->
                showDayDetailsDialog(date)
            }
        )
        recyclerCalendar.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = calendarAdapter
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_migraine, null)

        val editTime = dialogView.findViewById<EditText>(R.id.edit_time)
        val buttonCurrentTime = dialogView.findViewById<Button>(R.id.button_current_time)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekBar_intensity)
        val textIntensity = dialogView.findViewById<TextView>(R.id.text_intensity_value)
        val editMedication = dialogView.findViewById<EditText>(R.id.edit_medication)
        val spinnerMedication = dialogView.findViewById<Spinner>(R.id.spinner_medication)

        editTime.addTextChangedListener(com.example.migrainetracker.utils.TimeTextWatcher(editTime))

        val medications = listOf("Выбрать из списка", "Налгезин 500мг", "Налгезин 275мг",
            "Суматриптан 50мг", "Суматриптан 100мг", "Эксенза", "Диалрапид 100")

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, medications)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMedication.adapter = adapter

        if (recordToEdit != null) {
            editTime.setText(recordToEdit.time.format(DateTimeFormatter.ofPattern("HH:mm")))
            seekBar.progress = recordToEdit.intensity
            textIntensity.text = "${recordToEdit.intensity}/10"
            if (!recordToEdit.medicationName.isNullOrEmpty()) {
                val index = medications.indexOf(recordToEdit.medicationName)
                if (index != -1) {
                    spinnerMedication.setSelection(index)
                } else {
                    editMedication.setText(recordToEdit.medicationName)
                }
            }
        } else {
            val now = LocalTime.now()
            editTime.setText(String.format("%02d:%02d", now.hour, now.minute))
        }

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

                val timePattern = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
                if (!timePattern.matches(timeStr)) {
                    Toast.makeText(requireContext(), "Введите время в формате ЧЧ:ММ (например 14:30)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val parts = timeStr.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()

                val time = LocalTime.of(hour, minute)
                val intensity = seekBar.progress
                val selectedMedication = spinnerMedication.selectedItem.toString()
                val customMedication = editMedication.text.toString()

                val medicationName = if (customMedication.isNotBlank()) customMedication
                else if (selectedMedication != "Выбрать из списка") selectedMedication
                else null

                val record = MigraineRecord(
                    id = recordToEdit?.id ?: 0,
                    date = date,
                    time = time,
                    intensity = intensity,
                    medicationName = medicationName,
                    medicationTime = if (medicationName != null) time else null
                )

                lifecycleScope.launch {
                    if (recordToEdit != null) {
                        repository.updateMigraineRecord(record)
                    } else {
                        repository.addMigraineRecord(record)
                    }
                    loadData()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDayDetailsDialog(date: LocalDate) {
        lifecycleScope.launch {
            val records = migraineDays[date] ?: emptyList()
            val isMenstruating = menstruationDays.contains(date)

            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Детали дня - ${date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))}")

            val view = layoutInflater.inflate(R.layout.dialog_day_details, null)
            val textRecordsList = view.findViewById<TextView>(R.id.text_records_list)

            val message = buildString {
                if (isMenstruating) {
                    append("🔴 День месячных\n\n")
                }
                if (records.isEmpty()) {
                    append("Нет записей о мигрени")
                } else {
                    append(" Записи о мигрени:\n")
                    for ((index, record) in records.withIndex()) {
                        append("${index + 1}. ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))} - ")
                        append("Боль: ${record.intensity}/10")
                        if (record.medicationName != null) {
                            append(" 💊 ${record.medicationName}")
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
                // Меню с выбором: редактировать или удалить
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
            .setTitle("Выберите действие")
            .setItems(items) { _, which ->
                val selectedRecord = records[which]
                showRecordActionDialog(date, selectedRecord)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRecordActionDialog(date: LocalDate, record: MigraineRecord) {
        val actions = arrayOf("Редактировать", "Удалить")

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
                    repository.deleteMigraineRecord(record)
                    loadData()
                }
            }
            .setNegativeButton("Отмена", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun showEditSelectionDialog(date: LocalDate, records: List<MigraineRecord>) {
        val items = records.mapIndexed { index, record ->
            "${index + 1}. ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${record.intensity}/10"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите запись для редактирования")
            .setItems(items) { _, which ->
                showAddMigraineDialog(date, records[which])
            }
            .setNegativeButton("Отмена", null)
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