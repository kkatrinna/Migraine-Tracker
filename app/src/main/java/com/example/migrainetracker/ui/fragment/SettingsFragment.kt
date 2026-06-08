package com.example.migrainetracker.ui.fragment

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.ui.MainActivity
import com.example.migrainetracker.utils.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: android.view.View? = null
    private val binding get() = _binding!!

    private lateinit var editName: TextInputEditText
    private lateinit var editBirthDate: TextInputEditText
    private lateinit var radioGroupGender: RadioGroup
    private lateinit var editWeight: TextInputEditText
    private lateinit var editHeight: TextInputEditText
    private lateinit var btnSaveProfile: MaterialButton
    private lateinit var btnThemeLight: MaterialButton
    private lateinit var btnThemeDark: MaterialButton
    private lateinit var btnExportAll: MaterialButton
    private lateinit var btnClearData: MaterialButton

    companion object {
        private const val PREFS_NAME = "user_prefs"
        private const val KEY_NAME = "user_name"
        private const val KEY_BIRTH_DATE = "user_birth_date"
        private const val KEY_GENDER = "user_gender"
        private const val KEY_WEIGHT = "user_weight"
        private const val KEY_HEIGHT = "user_height"
    }

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
        _binding = inflater.inflate(R.layout.fragment_settings, container, false)
        return binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        loadUserProfile()
        setupDatePicker()
        setupThemeButtons()
        setupExportButton()
        setupClearDataButton()
        checkDataAndUpdateExportButton()
    }

    private fun initViews() {
        editName = binding.findViewById(R.id.edit_name)
        editBirthDate = binding.findViewById(R.id.edit_birth_date)
        radioGroupGender = binding.findViewById(R.id.radio_group_gender)
        editWeight = binding.findViewById(R.id.edit_weight)
        editHeight = binding.findViewById(R.id.edit_height)
        btnSaveProfile = binding.findViewById(R.id.btn_save_profile)
        btnThemeLight = binding.findViewById(R.id.btn_theme_light)
        btnThemeDark = binding.findViewById(R.id.btn_theme_dark)
        btnExportAll = binding.findViewById(R.id.btn_export_all)
        btnClearData = binding.findViewById(R.id.btn_clear_data)

        btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }
    }

    private fun checkDataAndUpdateExportButton() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())

                val migraineCount = db.migraineRecordDao().getAllRecords().size
                val menstruationCount = db.menstruationDayDao().getAllMenstruationDays().size
                val pressureCount = db.pressureRecordDao().getAllRecords().size
                val pulseCount = db.pulseRecordDao().getAllRecords().size

                val hasData = migraineCount > 0 || menstruationCount > 0 || pressureCount > 0 || pulseCount > 0

                btnExportAll.isEnabled = hasData
                btnExportAll.alpha = if (hasData) 1.0f else 0.5f

                if (!hasData) {
                    btnExportAll.text = "Экспорт данных (нет данных)"
                } else {
                    btnExportAll.text = "Экспорт всех данных"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupDatePicker() {
        editBirthDate.setOnClickListener {
            showDatePickerDialog()
        }
        editBirthDate.isFocusable = false
        editBirthDate.isClickable = true
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val currentDate = editBirthDate.text.toString()

        var year = calendar.get(Calendar.YEAR)
        var month = calendar.get(Calendar.MONTH)
        var day = calendar.get(Calendar.DAY_OF_MONTH)

        if (currentDate.isNotEmpty() && currentDate.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) {
            try {
                val parts = currentDate.split(".")
                day = parts[0].toInt()
                month = parts[1].toInt() - 1
                year = parts[2].toInt()
            } catch (e: Exception) { }
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format("%02d.%02d.%04d", selectedDay, selectedMonth + 1, selectedYear)
                editBirthDate.setText(formattedDate)
            },
            year, month, day
        )

        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun loadUserProfile() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        editName.setText(prefs.getString(KEY_NAME, ""))
        editBirthDate.setText(prefs.getString(KEY_BIRTH_DATE, ""))

        val gender = prefs.getString(KEY_GENDER, "")
        when (gender) {
            "female" -> radioGroupGender.check(R.id.radio_female)
            "male" -> radioGroupGender.check(R.id.radio_male)
            else -> radioGroupGender.clearCheck()
        }

        editWeight.setText(prefs.getString(KEY_WEIGHT, ""))
        editHeight.setText(prefs.getString(KEY_HEIGHT, ""))
    }

    private fun saveUserProfile() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val oldGender = prefs.getString(KEY_GENDER, "")
        val newGender = when (radioGroupGender.checkedRadioButtonId) {
            R.id.radio_female -> "female"
            R.id.radio_male -> "male"
            else -> ""
        }

        val name = editName.text.toString().trim()
        val birthDate = editBirthDate.text.toString().trim()
        val weight = editWeight.text.toString().trim()
        val height = editHeight.text.toString().trim()

        prefs.edit {
            putString(KEY_NAME, name)
            putString(KEY_BIRTH_DATE, birthDate)
            putString(KEY_GENDER, newGender)
            putString(KEY_WEIGHT, weight)
            putString(KEY_HEIGHT, height)
        }

        if (oldGender != newGender) {
           (requireActivity() as? MainActivity)?.onResume()

        }
    }

    fun getUserName(): String {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAME, "Пользователь") ?: "Пользователь"
    }

    fun getUserBirthDate(): String? {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BIRTH_DATE, null)
    }

    fun getUserAge(): Int? {
        val birthDateStr = getUserBirthDate() ?: return null
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val birthDate = LocalDate.parse(birthDateStr, formatter)
            val today = LocalDate.now()
            today.year - birthDate.year
        } catch (e: Exception) { null }
    }

    fun getUserGender(): String? {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gender = prefs.getString(KEY_GENDER, null)
        return when (gender) {
            "female" -> "Женский"
            "male" -> "Мужской"
            else -> null
        }
    }

    fun getUserWeight(): Float? {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WEIGHT, "")?.toFloatOrNull()
    }

    fun getUserHeight(): Int? {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_HEIGHT, "")?.toIntOrNull()
    }

    private fun setupThemeButtons() {
        val currentTheme = ThemeManager.getCurrentTheme(requireContext())

        when (currentTheme) {
            ThemeManager.THEME_LIGHT -> btnThemeLight.isChecked = true
            ThemeManager.THEME_DARK -> btnThemeDark.isChecked = true
            else -> { }
        }

        btnThemeLight.setOnClickListener {
            ThemeManager.saveTheme(requireContext(), ThemeManager.THEME_LIGHT)
            requireActivity().recreate()
        }

        btnThemeDark.setOnClickListener {
            ThemeManager.saveTheme(requireContext(), ThemeManager.THEME_DARK)
            requireActivity().recreate()
        }
    }

    private fun setupExportButton() {
        btnExportAll.setOnClickListener {
            if (btnExportAll.isEnabled) {
                checkPermissionAndExport()
            } else {
                Toast.makeText(requireContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
            }
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

                val hasAnyRecords = migraineRecords.isNotEmpty() ||
                        menstruationDays.isNotEmpty() ||
                        pressureRecords.isNotEmpty() ||
                        pulseRecords.isNotEmpty()

                if (!hasAnyRecords) {
                    return@launch
                }

                val exportDir = getExportDirectory()
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                var exportedFilesCount = 0

                exportUserProfile(exportDir, timestamp)
                exportedFilesCount++

                if (migraineRecords.isNotEmpty() || menstruationDays.isNotEmpty()) {
                    exportMigraineCalendar(migraineRecords, menstruationDays, exportDir, timestamp)
                    exportedFilesCount++
                }

                if (pressureRecords.isNotEmpty()) {
                    exportPressureData(pressureRecords, exportDir, timestamp)
                    exportedFilesCount++
                }

                if (pulseRecords.isNotEmpty()) {
                    exportPulseData(pulseRecords, exportDir, timestamp)
                    exportedFilesCount++
                }

                val message = "Экспортировано файлов: $exportedFilesCount\nПапка: ${exportDir.absolutePath}"

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("✅ Экспорт завершен")
                    .setMessage(message)
                    .setPositiveButton("📂 Показать файлы") { _, _ ->
                        openDownloadsFolder(exportDir)
                    }
                    .setNegativeButton("Закрыть", null)
                    .setNeutralButton("📤 Поделиться последними") { _, _ ->
                        shareLatestFiles(exportDir)
                    }
                    .show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun openDownloadsFolder(directory: File) {
        try {
            if (!directory.exists()) {
                Toast.makeText(requireContext(), "Папка не найдена", Toast.LENGTH_SHORT).show()
                return
            }

            val allFiles = directory.listFiles()
            if (allFiles == null || allFiles.isEmpty()) {
                Toast.makeText(requireContext(), "Нет файлов для просмотра", Toast.LENGTH_SHORT).show()
                return
            }

            val sortedFiles = allFiles.filter { it.extension == "csv" || it.extension == "txt" }
                .sortedByDescending { it.lastModified() }

            if (sortedFiles.isEmpty()) {
                Toast.makeText(requireContext(), "Нет файлов для просмотра", Toast.LENGTH_SHORT).show()
                return
            }

            val latestFiles = if (sortedFiles.size > 5) sortedFiles.take(5) else sortedFiles

            val fileItems = latestFiles.map { file ->
                val sizeKB = file.length() / 1024
                val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                "${file.name} (${sizeKB} KB, $date)"
            }.toTypedArray()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("📁 Последние экспортированные файлы")
                .setItems(fileItems) { _, which ->
                    openCsvFile(latestFiles[which])
                }
                .setPositiveButton("📤 Поделиться последними") { _, _ ->
                    shareLatestFiles(directory)
                }
                .setNegativeButton("Закрыть", null)
                .show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLatestFiles(directory: File) {
        try {
            val allFiles = directory.listFiles()
            if (allFiles == null || allFiles.isEmpty()) {
                Toast.makeText(requireContext(), "Нет файлов для отправки", Toast.LENGTH_SHORT).show()
                return
            }

            val latestFiles = allFiles
                .filter { it.extension == "csv" || it.extension == "txt" }
                .sortedByDescending { it.lastModified() }
                .take(5)

            if (latestFiles.isEmpty()) {
                Toast.makeText(requireContext(), "Нет файлов для отправки", Toast.LENGTH_SHORT).show()
                return
            }

            val uris = latestFiles.map { file ->
                FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
            }

            val shareIntent = Intent().apply {
                action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                type = "*/*"
                if (uris.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Поделиться последними файлами"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка при отправке файлов", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCsvFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(Intent.createChooser(intent, "Открыть CSV файл"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Не удалось открыть файл. Установите приложение для работы с CSV (Excel, Google Sheets)", Toast.LENGTH_LONG).show()
        }
    }

    private fun getExportDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, "MigraineTracker")
    }

    private fun exportUserProfile(exportDir: File, timestamp: String) {
        val file = File(exportDir, "user_profile_$timestamp.txt")
        FileWriter(file).use { writer ->
            writer.append("ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ\n")
            writer.append("=".repeat(40) + "\n")
            writer.append("Создан: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
            writer.append("Имя: ${getUserName()}\n")
            writer.append("Дата рождения: ${getUserBirthDate() ?: "—"}\n")
            writer.append("Возраст: ${getUserAge() ?: "—"} лет\n")
            writer.append("Пол: ${getUserGender() ?: "—"}\n")
            writer.append("Вес: ${getUserWeight() ?: "—"} кг\n")
            writer.append("Рост: ${getUserHeight() ?: "—"} см\n")

            val weight = getUserWeight()
            val height = getUserHeight()
            if (weight != null && height != null && height > 0) {
                val bmi = weight / ((height / 100.0) * (height / 100.0))
                writer.append("ИМТ: ${String.format("%.1f", bmi)}\n")
                val bmiStatus = when {
                    bmi < 18.5 -> "Недостаточный вес"
                    bmi < 25 -> "Нормальный вес"
                    bmi < 30 -> "Избыточный вес"
                    else -> "Ожирение"
                }
                writer.append("Статус ИМТ: $bmiStatus\n")
            }
        }
    }

    private fun exportMigraineCalendar(
        records: List<com.example.migrainetracker.data.entity.MigraineRecord>,
        menstruationDays: List<com.example.migrainetracker.data.entity.MenstruationDay>,
        exportDir: File,
        timestamp: String
    ) {
        val file = File(exportDir, "migraine_calendar_$timestamp.csv")
        FileWriter(file).use { writer ->
            writer.append("Календарь мигрени\n")
            writer.append("Создан: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            writer.append("Дата,Время,Интенсивность,Лекарство,Тошнота,Светобоязнь,Аура,Заметки\n")
            for (record in records.sortedBy { it.date }) {
                writer.append("${record.date},${record.time},${record.intensity},${record.medicationName ?: ""},")
                writer.append("${if (record.nausea) "Да" else "Нет"},${if (record.photophobia) "Да" else "Нет"},")
                writer.append("${if (record.aura) "Да" else "Нет"},${record.notes ?: ""}\n")
            }
        }
    }

    private fun exportPressureData(
        records: List<com.example.migrainetracker.data.entity.PressureRecord>,
        exportDir: File,
        timestamp: String
    ) {
        val file = File(exportDir, "pressure_export_$timestamp.csv")
        FileWriter(file).use { writer ->
            writer.append("Дата,Время,Систолическое,Диастолическое,Пульс,Статус\n")
            for (record in records) {
                val status = getPressureStatus(record.systolic, record.diastolic)
                writer.append("${record.date},${record.time},${record.systolic},${record.diastolic},${record.pulse},$status\n")
            }
        }
    }

    private fun exportPulseData(
        records: List<com.example.migrainetracker.data.entity.PulseRecord>,
        exportDir: File,
        timestamp: String
    ) {
        val file = File(exportDir, "pulse_export_$timestamp.csv")
        FileWriter(file).use { writer ->
            writer.append("Дата,Время,Пульс,Статус\n")
            for (record in records) {
                val status = getPulseStatus(record.pulse)
                writer.append("${record.date},${record.time},${record.pulse},$status\n")
            }
        }
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
            in 0..40 -> "Критически низкий"
            in 41..59 -> "Пониженный"
            in 60..90 -> "Нормальный"
            in 91..135 -> "Тахикардия легкая"
            in 136..185 -> "Тахикардия средняя"
            else -> "Тахикардия тяжелая"
        }
    }

    private fun setupClearDataButton() {
        btnClearData.setOnClickListener {
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

                btnExportAll.isEnabled = false
                btnExportAll.alpha = 0.5f
                btnExportAll.text = "Экспорт данных (нет данных)"

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка при удалении данных: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}