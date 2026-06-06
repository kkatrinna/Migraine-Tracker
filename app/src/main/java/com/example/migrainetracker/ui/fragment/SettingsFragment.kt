package com.example.migrainetracker.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.databinding.FragmentSettingsBinding
import com.example.migrainetracker.utils.ThemeManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            exportAllData()
        } else {
            Toast.makeText(requireContext(), "Нет разрешения на запись файлов", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupThemeRadioGroup()
        setupExportButton()
        setupClearDataButton()
        setupAboutButton()
    }

    private fun setupThemeRadioGroup() {
        val currentTheme = ThemeManager.getCurrentTheme(requireContext())

        when (currentTheme) {
            ThemeManager.THEME_LIGHT -> binding.radioLight.isChecked = true
            ThemeManager.THEME_DARK -> binding.radioDark.isChecked = true
            ThemeManager.THEME_SYSTEM -> binding.radioSystem.isChecked = true
        }

        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radio_light -> ThemeManager.THEME_LIGHT
                R.id.radio_dark -> ThemeManager.THEME_DARK
                else -> ThemeManager.THEME_SYSTEM
            }

            ThemeManager.saveTheme(requireContext(), newTheme)
            requireActivity().recreate()
        }
    }

    private fun setupExportButton() {
        binding.buttonExportData.setOnClickListener {
            checkPermissionAndExport()
        }
    }

    private fun checkPermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            exportAllData()
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                exportAllData()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun exportAllData() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())

                val migraineRecords = db.migraineRecordDao().getAllRecords()
                val menstruationDays = db.menstruationDayDao().getAllMenstruationDays()
                val pressureRecords = db.pressureRecordDao().getAllRecords()
                val pulseRecords = db.pulseRecordDao().getAllRecords()

                val exportDir = getExportDirectory()
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

                exportMigraineCalendar(migraineRecords, menstruationDays, exportDir, timestamp)

                exportPressureData(pressureRecords, exportDir, timestamp)

                exportPulseData(pulseRecords, exportDir, timestamp)

                Toast.makeText(requireContext(), "Данные экспортированы в папку Downloads/MigraineTracker", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun getExportDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, "MigraineTracker")
    }

    private fun exportMigraineCalendar(
        records: List<com.example.migrainetracker.data.entity.MigraineRecord>,
        menstruationDays: List<com.example.migrainetracker.data.entity.MenstruationDay>,
        exportDir: File,
        timestamp: String
    ) {
        val file = File(exportDir, "migraine_calendar_$timestamp.csv")
        val writer = FileWriter(file)

        val maxIntensityByDate = records.groupBy { it.date }
            .mapValues { it.value.maxOf { r -> r.intensity } }

        val medicationsByDate = records.groupBy { it.date }
            .mapValues { it.value.mapNotNull { r -> r.medicationName }.distinct() }

        val menstruationMap = menstruationDays.associate { it.date to it.isMenstruating }

        val allDates = (records.map { it.date } + menstruationDays.map { it.date }).distinct()
        val startDate = if (allDates.isNotEmpty()) allDates.minOrNull() else LocalDate.now()
        val endDate = if (allDates.isNotEmpty()) allDates.maxOrNull() else LocalDate.now()

        writer.append("Календарь мигрени\n")
        writer.append("Создан: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

        var currentMonth = startDate?.let { YearMonth.from(it) }
        val endMonth = endDate?.let { YearMonth.from(it) }

        while (currentMonth != null && !currentMonth.isAfter(endMonth)) {
            val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))
            writer.append("${currentMonth.format(monthFormatter)}\n")

            writer.append("Пн,Вт,Ср,Чт,Пт,Сб,Вс\n")

            val firstDayOfMonth = currentMonth.atDay(1)
            val daysInMonth = currentMonth.lengthOfMonth()

            val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value

            val calendarRows = mutableListOf<MutableList<String>>()
            var currentRow = mutableListOf<String>()

            for (i in 1 until firstDayOfWeek) {
                currentRow.add("")
            }

            for (day in 1..daysInMonth) {
                val date = currentMonth.atDay(day)
                val intensity = maxIntensityByDate[date] ?: 0
                val isMenstruating = menstruationMap[date] ?: false
                val medications = medicationsByDate[date] ?: emptyList()

                var cell = "$day"
                if (intensity > 0) {
                    cell += " [${intensity}]"
                }
                if (isMenstruating) {
                    cell += " 🔴"
                }
                if (medications.isNotEmpty()) {
                    cell += " 💊${medications.joinToString(",")}"
                }

                currentRow.add(cell)

                if (date.dayOfWeek.value == 7 || day == daysInMonth) {
                    while (currentRow.size < 7) {
                        currentRow.add("")
                    }
                    calendarRows.add(currentRow)
                    currentRow = mutableListOf()
                }
            }

            for (row in calendarRows) {
                writer.append(row.joinToString(","))
                writer.append("\n")
            }

            writer.append("\n")
            currentMonth = currentMonth.plusMonths(1)
        }

        writer.append("\nЛегенда:\n")
        writer.append("[N] - интенсивность боли от 0 до 10\n")
        writer.append("🔴 - день месячных\n")
        writer.append("💊лекарство - принятое лекарство\n\n")

        writer.append("\n\nДетальные записи:\n")
        writer.append("Дата,Время,Интенсивность,Лекарство\n")

        for (record in records.sortedBy { it.date }) {
            writer.append("${record.date},${record.time},${record.intensity},${record.medicationName ?: ""}\n")
        }

        writer.flush()
        writer.close()
    }

    private fun exportPressureData(
        records: List<com.example.migrainetracker.data.entity.PressureRecord>,
        exportDir: File,
        timestamp: String
    ) {
        val file = File(exportDir, "pressure_export_$timestamp.csv")
        val writer = FileWriter(file)

        writer.append("Список измерений давления\n")
        writer.append("Создан: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
        writer.append("Дата,Время,Верхнее давление (мм рт.ст.),Нижнее давление (мм рт.ст.),Статус\n")

        if (records.isEmpty()) {
            writer.append("Нет записей о давлении\n")
        } else {
            for (record in records.sortedByDescending { it.date }) {
                val status = getPressureStatus(record.systolic, record.diastolic)
                writer.append("${record.date},${record.time},${record.systolic},${record.diastolic},$status\n")
            }
        }

        writer.flush()
        writer.close()
    }

    private fun exportPulseData(
        records: List<com.example.migrainetracker.data.entity.PulseRecord>,
        exportDir: File,
        timestamp: String
    ) {
        val file = File(exportDir, "pulse_export_$timestamp.csv")
        val writer = FileWriter(file)

        writer.append("Список измерений пульса\n")
        writer.append("Создан: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
        writer.append("Дата,Время,Пульс (уд/мин),Статус\n")

        if (records.isEmpty()) {
            writer.append("Нет записей о пульсе\n")
        } else {
            for (record in records.sortedByDescending { it.date }) {
                val status = getPulseStatus(record.pulse)
                writer.append("${record.date},${record.time},${record.pulse},$status\n")
            }
        }

        writer.flush()
        writer.close()
    }

    private fun getPressureStatus(systolic: Int, diastolic: Int): String {
        return when {
            systolic < 90 && diastolic < 60 -> "Пониженное"
            systolic in 90..119 && diastolic in 60..79 -> "Нормальное"
            systolic in 120..129 && diastolic < 80 -> "Повышенное"
            systolic in 130..139 || diastolic in 80..89 -> "Гипертензия 1 степени"
            systolic in 140..179 || diastolic in 90..119 -> "Гипертензия 2 степени"
            systolic >= 180 || diastolic >= 120 -> "Гипертонический криз"
            else -> "Не определено"
        }
    }

    private fun getPulseStatus(pulse: Int): String {
        return when (pulse) {
            in 0..40 -> "Очень низкий"
            in 41..59 -> "Низкий"
            in 60..79 -> "Нормальный"
            in 80..99 -> "Учащенный"
            in 100..119 -> "Тахикардия легкая"
            in 120..139 -> "Тахикардия средняя"
            else -> "Тахикардия тяжелая"
        }
    }

    private fun setupClearDataButton() {
        binding.buttonClearData.setOnClickListener {
            showClearDataConfirmDialog()
        }
    }

    private fun showClearDataConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистить все данные")
            .setMessage("Вы уверены, что хотите удалить ВСЕ данные?\n\n" +
                    "Будут удалены:\n" +
                    "• Все записи о мигрени\n" +
                    "• Все записи о давлении\n" +
                    "• Все записи о пульсе\n" +
                    "• Все отметки о месячных\n\n" +
                    "Это действие нельзя отменить!")
            .setPositiveButton("Удалить") { _, _ ->
                clearAllData()
            }
            .setNegativeButton("Отмена", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun clearAllData() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())

                db.migraineRecordDao().deleteAll()
                db.pressureRecordDao().deleteAll()
                db.pulseRecordDao().deleteAll()
                db.menstruationDayDao().deleteAll()

                Toast.makeText(requireContext(), "Все данные успешно удалены", Toast.LENGTH_LONG).show()
                requireActivity().recreate()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка при удалении данных: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupAboutButton() {
        binding.buttonAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("О приложении")
            .setMessage("""
                Migraine Tracker
                
                Приложение для отслеживания:
                • Мигрени
                • Давления и пульса
                • Менструального цикла
                
                Разработано с ❤️ для здоровья
            """.trimIndent())
            .setPositiveButton("Закрыть", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}