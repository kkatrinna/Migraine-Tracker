package com.example.migrainetracker.ui.fragment

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.PressureRecord
import com.example.migrainetracker.data.entity.PulseRecord
import com.example.migrainetracker.data.repository.TrackerRepository
import com.example.migrainetracker.ui.adapters.PressureCardAdapter
import com.example.migrainetracker.ui.adapters.PulseCardAdapter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

class BloodPressureFragment : Fragment() {

    private lateinit var repository: TrackerRepository
    private lateinit var db: AppDatabase
    private lateinit var pressureAdapter: PressureCardAdapter
    private lateinit var pulseAdapter: PulseCardAdapter

    private lateinit var textPressureCount: TextView
    private lateinit var textAvgSystolic: TextView
    private lateinit var textAvgDiastolic: TextView
    private lateinit var textMinPressure: TextView
    private lateinit var textMaxPressure: TextView
    private lateinit var textPulseCount: TextView
    private lateinit var textAvgPulse: TextView
    private lateinit var textMinPulse: TextView
    private lateinit var textMaxPulse: TextView
    private lateinit var cardAddMeasurement: LinearLayout
    private lateinit var cardExportPressure: MaterialCardView
    private lateinit var cardExportPulse: MaterialCardView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_blood_pressure, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())
        repository = TrackerRepository(db)

        textPressureCount = view.findViewById(R.id.text_pressure_count)
        textAvgSystolic = view.findViewById(R.id.text_avg_systolic)
        textAvgDiastolic = view.findViewById(R.id.text_avg_diastolic)
        textMinPressure = view.findViewById(R.id.text_min_pressure)
        textMaxPressure = view.findViewById(R.id.text_max_pressure)
        textPulseCount = view.findViewById(R.id.text_pulse_count)
        textAvgPulse = view.findViewById(R.id.text_avg_pulse)
        textMinPulse = view.findViewById(R.id.text_min_pulse)
        textMaxPulse = view.findViewById(R.id.text_max_pulse)

        cardAddMeasurement = view.findViewById(R.id.card_add_measurement)
        cardExportPressure = view.findViewById(R.id.card_export_pressure)
        cardExportPulse = view.findViewById(R.id.card_export_pulse)

        setupRecyclerViews(view)
        setupButtons()
        loadData()
        createNotificationChannel()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupRecyclerViews(view: View) {
        pressureAdapter = PressureCardAdapter(
            onItemClick = { showPressureDetails(it) },
            onItemDelete = {
                lifecycleScope.launch {
                    repository.deletePressureRecord(it)
                    loadData()
                }
            }
        )

        view.findViewById<RecyclerView>(R.id.recycler_pressure).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pressureAdapter
        }

        pulseAdapter = PulseCardAdapter(
            onItemClick = { showPulseDetails(it) },
            onItemDelete = {
                lifecycleScope.launch {
                    repository.deletePulseRecord(it)
                    loadData()
                }
            }
        )

        view.findViewById<RecyclerView>(R.id.recycler_pulse).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pulseAdapter
        }
    }

    private fun setupButtons() {
        cardAddMeasurement.setOnClickListener {
            showAddMeasurementDialog()
        }

        cardExportPressure.setOnClickListener {
            exportPressureData()
        }

        cardExportPulse.setOnClickListener {
            exportPulseData()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "export_channel",
                "Экспорт данных",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о завершении экспорта данных"
            }
            val notificationManager = requireContext().getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showExportNotification(fileName: String, fileUri: Uri) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            requireContext(),
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(requireContext(), "export_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Экспорт завершен")
            .setContentText("Файл $fileName сохранен в папку Загрузки")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = requireContext().getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivityForResult(intent, 1001)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, 1001)
            }
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                1001
            )
        }
    }

    private fun exportPressureData() {
        if (!checkStoragePermission()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("📁 Доступ к файлам")
                .setMessage("Для экспорта данных необходимо разрешить доступ к файлам")
                .setPositiveButton("Разрешить") { _, _ ->
                    requestStoragePermission()
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }

        lifecycleScope.launch {
            val records = db.pressureRecordDao().getAllRecords()
            if (records.isEmpty()) {
                Toast.makeText(requireContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
                return@launch
            }
            exportToCSV(records, "pressure")
        }
    }

    private fun exportPulseData() {
        if (!checkStoragePermission()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("📁 Доступ к файлам")
                .setMessage("Для экспорта данных необходимо разрешить доступ к файлам")
                .setPositiveButton("Разрешить") { _, _ ->
                    requestStoragePermission()
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }

        lifecycleScope.launch {
            val records = db.pulseRecordDao().getAllRecords()
            if (records.isEmpty()) {
                Toast.makeText(requireContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
                return@launch
            }
            exportToCSV(records, "pulse")
        }
    }

    private fun exportToCSV(records: List<Any>, type: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = when (type) {
                "pressure" -> "pressure_records_$timestamp.csv"
                "pulse" -> "pulse_records_$timestamp.csv"
                else -> "records_$timestamp.csv"
            }

            val content = buildCSVContent(records, type)
            var fileUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MigraineTracker")
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                    fileUri = uri
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "MigraineTracker")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = File(appDir, fileName)
                FileWriter(file).use { writer ->
                    writer.write(content)
                }
                fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
            }

            fileUri?.let {
                showExportNotification(fileName, it)
                Toast.makeText(requireContext(), "Файл сохранен: $fileName", Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(requireContext(), "Ошибка сохранения файла", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun buildCSVContent(records: List<Any>, type: String): String {
        val sb = StringBuilder()

        when (type) {
            "pressure" -> {
                sb.append("Дата,Время,Систолическое,Диастолическое,Пульс,Статус\n")
                records.forEach { record ->
                    if (record is PressureRecord) {
                        val status = getPressureStatus(record.systolic, record.diastolic)
                        sb.append("${record.date},${record.time},${record.systolic},${record.diastolic},${record.pulse},\"$status\"\n")
                    }
                }

                // Добавляем статистику
                if (records.isNotEmpty()) {
                    val pressureRecords = records.filterIsInstance<PressureRecord>()
                    val avgSystolic = pressureRecords.map { it.systolic }.average()
                    val avgDiastolic = pressureRecords.map { it.diastolic }.average()
                    val minSystolic = pressureRecords.minByOrNull { it.systolic }?.systolic ?: 0
                    val maxSystolic = pressureRecords.maxByOrNull { it.systolic }?.systolic ?: 0
                    val minDiastolic = pressureRecords.minByOrNull { it.diastolic }?.diastolic ?: 0
                    val maxDiastolic = pressureRecords.maxByOrNull { it.diastolic }?.diastolic ?: 0

                    sb.append("\n\nСТАТИСТИКА\n")
                    sb.append("Всего измерений,${pressureRecords.size}\n")
                    sb.append("Среднее давление,$avgSystolic/$avgDiastolic\n")
                    sb.append("Минимальное давление,$minSystolic/$minDiastolic\n")
                    sb.append("Максимальное давление,$maxSystolic/$maxDiastolic\n")
                }
            }
            "pulse" -> {
                sb.append("Дата,Время,Пульс,Статус\n")
                records.forEach { record ->
                    if (record is PulseRecord) {
                        val status = getPulseStatus(record.pulse)
                        sb.append("${record.date},${record.time},${record.pulse},\"$status\"\n")
                    }
                }

                // Добавляем статистику
                if (records.isNotEmpty()) {
                    val pulseRecords = records.filterIsInstance<PulseRecord>()
                    val avgPulse = pulseRecords.map { it.pulse }.average()
                    val minPulse = pulseRecords.minByOrNull { it.pulse }?.pulse ?: 0
                    val maxPulse = pulseRecords.maxByOrNull { it.pulse }?.pulse ?: 0

                    sb.append("\n\nСТАТИСТИКА\n")
                    sb.append("Всего измерений,${pulseRecords.size}\n")
                    sb.append("Средний пульс,${String.format("%.0f", avgPulse)}\n")
                    sb.append("Минимальный пульс,$minPulse\n")
                    sb.append("Максимальный пульс,$maxPulse\n")
                }
            }
        }

        // Добавляем информацию о приложении
        sb.append("\n\nСоздано в приложении Migraine Tracker\n")
        sb.append("Дата экспорта: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")

        return sb.toString()
    }

    private fun getUserAge(): Int? {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val birthDateStr = prefs.getString("user_birth_date", null) ?: return null
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val birthDate = LocalDate.parse(birthDateStr, formatter)
            val today = LocalDate.now()
            today.year - birthDate.year
        } catch (e: Exception) { null }
    }

    private fun getUserGender(): String? {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val gender = prefs.getString("user_gender", null)
        return when (gender) {
            "female" -> "female"
            "male" -> "male"
            else -> null
        }
    }

    private fun getNormalPressureRange(age: Int?, gender: String?): Triple<Int, Int, Pair<Int, Int>> {
        if (age == null) return Triple(120, 120, Pair(80, 80))

        return when {
            age in 20..30 -> {
                if (gender == "female") Triple(120, 120, Pair(75, 75))
                else Triple(126, 126, Pair(79, 79))
            }
            age in 31..40 -> {
                if (gender == "female") Triple(127, 127, Pair(80, 80))
                else Triple(129, 129, Pair(81, 81))
            }
            age in 41..50 -> {
                if (gender == "female") Triple(137, 137, Pair(84, 84))
                else Triple(135, 135, Pair(83, 83))
            }
            age in 51..60 -> {
                if (gender == "female") Triple(144, 144, Pair(85, 85))
                else Triple(142, 142, Pair(85, 85))
            }
            age in 61..70 -> {
                if (gender == "female") Triple(159, 159, Pair(85, 85))
                else Triple(145, 145, Pair(82, 82))
            }
            age in 71..80 -> {
                if (gender == "female") Triple(157, 157, Pair(83, 83))
                else Triple(147, 147, Pair(82, 82))
            }
            age in 81..90 -> {
                if (gender == "female") Triple(150, 150, Pair(79, 79))
                else Triple(145, 145, Pair(78, 78))
            }
            else -> Triple(120, 120, Pair(80, 80))
        }
    }

    private fun showAddMeasurementDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_measurement, null)
        val editSystolic = dialogView.findViewById<EditText>(R.id.edit_measurement_systolic)
        val editDiastolic = dialogView.findViewById<EditText>(R.id.edit_measurement_diastolic)
        val editPulse = dialogView.findViewById<EditText>(R.id.edit_measurement_pulse)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val systolicStr = editSystolic.text.toString().trim()
                val diastolicStr = editDiastolic.text.toString().trim()
                val pulseStr = editPulse.text.toString().trim()

                val systolic = if (systolicStr.isNotEmpty()) systolicStr.toIntOrNull() else null
                val diastolic = if (diastolicStr.isNotEmpty()) diastolicStr.toIntOrNull() else null
                val pulse = if (pulseStr.isNotEmpty()) pulseStr.toIntOrNull() else null

                var hasError = false

                val isSystolicFilled = systolicStr.isNotEmpty()
                val isDiastolicFilled = diastolicStr.isNotEmpty()

                if (isSystolicFilled && !isDiastolicFilled) {
                    editDiastolic.error = "Введите диастолическое давление"
                    hasError = true
                } else if (!isSystolicFilled && isDiastolicFilled) {
                    editSystolic.error = "Введите систолическое давление"
                    hasError = true
                } else if (isSystolicFilled && isDiastolicFilled) {
                    if (systolic != null && (systolic !in 30..250)) {
                        editSystolic.error = "Введите систолическое (30-250)"
                        hasError = true
                    }
                    if (diastolic != null && (diastolic !in 20..200)) {
                        editDiastolic.error = "Введите диастолическое (20-200)"
                        hasError = true
                    }
                }

                if (pulseStr.isNotEmpty()) {
                    if (pulse != null && (pulse !in 30..200)) {
                        editPulse.error = "Введите пульс (30-200)"
                        hasError = true
                    }
                }

                val hasPressure = isSystolicFilled && isDiastolicFilled
                val hasPulse = pulseStr.isNotEmpty()

                if (!hasPressure && !hasPulse) {
                    Toast.makeText(requireContext(), "Укажите давление или пульс", Toast.LENGTH_LONG).show()
                    hasError = true
                }

                if (!hasError) {
                    lifecycleScope.launch {
                        val now = LocalTime.now()
                        val today = LocalDate.now()

                        if (hasPressure && systolic != null && diastolic != null) {
                            val pressureRecord = PressureRecord(
                                date = today,
                                time = now,
                                systolic = systolic,
                                diastolic = diastolic,
                                pulse = pulse ?: 0
                            )
                            repository.addPressureRecord(pressureRecord)
                        }

                        if (hasPulse && pulse != null) {
                            val pulseRecord = PulseRecord(
                                date = today,
                                time = now,
                                pulse = pulse
                            )
                            repository.addPulseRecord(pulseRecord)
                        }

                        Toast.makeText(requireContext(), "Данные сохранены", Toast.LENGTH_SHORT).show()
                        loadData()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val pressureRecords = repository.getPressureRecords()
            pressureAdapter.submitList(pressureRecords)
            updatePressureStatistics(pressureRecords)

            val pulseRecords = repository.getPulseRecords()
            pulseAdapter.submitList(pulseRecords)
            updatePulseStatistics(pulseRecords)
        }
    }

    private fun updatePressureStatistics(records: List<PressureRecord>) {
        if (records.isEmpty()) {
            textPressureCount.text = "0"
            textAvgSystolic.text = "—"
            textAvgDiastolic.text = "—"
            textMinPressure.text = "—"
            textMaxPressure.text = "—"
            return
        }

        textPressureCount.text = records.size.toString()

        val avgSystolic = records.map { it.systolic }.average()
        val avgDiastolic = records.map { it.diastolic }.average()
        textAvgSystolic.text = String.format("%.0f", avgSystolic)
        textAvgDiastolic.text = String.format("%.0f", avgDiastolic)

        val minSystolic = records.minByOrNull { it.systolic }?.systolic ?: 0
        val minDiastolic = records.minByOrNull { it.diastolic }?.diastolic ?: 0
        textMinPressure.text = "$minSystolic/$minDiastolic"

        val maxSystolic = records.maxByOrNull { it.systolic }?.systolic ?: 0
        val maxDiastolic = records.maxByOrNull { it.diastolic }?.diastolic ?: 0
        textMaxPressure.text = "$maxSystolic/$maxDiastolic"
    }

    private fun updatePulseStatistics(records: List<PulseRecord>) {
        if (records.isEmpty()) {
            textPulseCount.text = "0"
            textAvgPulse.text = "—"
            textMinPulse.text = "—"
            textMaxPulse.text = "—"
            return
        }

        textPulseCount.text = records.size.toString()

        val avgPulse = records.map { it.pulse }.average()
        textAvgPulse.text = String.format("%.0f", avgPulse)

        val minPulse = records.minByOrNull { it.pulse }?.pulse ?: 0
        textMinPulse.text = minPulse.toString()

        val maxPulse = records.maxByOrNull { it.pulse }?.pulse ?: 0
        textMaxPulse.text = maxPulse.toString()
    }

    private fun showPressureDetails(record: PressureRecord) {
        val status = getPressureStatus(record.systolic, record.diastolic)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val age = getUserAge()
        val gender = getUserGender()
        val (normalSystolicMin, normalSystolicMax, diastolicRange) = getNormalPressureRange(age, gender)
        val (normalDiastolicMin, normalDiastolicMax) = diastolicRange

        val ageText = if (age != null) " (${age} лет)" else ""
        val genderText = when (gender) {
            "female" -> " (Женщина)"
            "male" -> " (Мужчина)"
            else -> ""
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📊 Детали измерения")
            .setMessage("""
                📅 Дата: ${record.date.format(dateFormatter)}
                ⏰ Время: ${record.time.format(timeFormatter)}
                
                ❤️ Давление: ${record.systolic}/${record.diastolic} мм рт.ст.
                📈 Статус: $status
                💓 Пульс: ${record.pulse} уд/мин
                
                ━━━━━━━━━━━━━━━━━━━━━
                👤 Ваш профиль$ageText$genderText
                🩺 Ваша норма давления: $normalSystolicMin-$normalSystolicMax / $normalDiastolicMin-$normalDiastolicMax
                🟢 Общая норма давления: 120/80
            """.trimIndent())
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun showPulseDetails(record: PulseRecord) {
        val status = getPulseStatus(record.pulse)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("💓 Детали измерения")
            .setMessage("""
                📅 Дата: ${record.date.format(dateFormatter)}
                ⏰ Время: ${record.time.format(timeFormatter)}
                
                💓 Пульс: ${record.pulse} уд/мин
                📈 Статус: $status
                
                ━━━━━━━━━━━━━━━━━━━━━
                🟢 Норма пульса: 60-90 уд/мин
            """.trimIndent())
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun getPressureStatus(systolic: Int, diastolic: Int): String {
        if (systolic < 90 || diastolic < 60) {
            return "🟠 Пониженное"
        }

        return when {
            systolic < 120 && diastolic < 80 -> "🟢 Оптимальное"
            systolic in 120..129 && diastolic in 80..84 -> "🟢 Нормальное"
            systolic in 130..139 && diastolic in 85..89 -> "🟡 Высокое нормальное"
            systolic in 140..159 && diastolic in 90..99 -> "🟠 Гипертензия 1 степени"
            systolic in 160..179 && diastolic in 100..109 -> "🔴 Гипертензия 2 степени"
            systolic >= 180 || diastolic >= 110 -> "🔴 Гипертензия 3 степени"
            else -> {
                when {
                    systolic >= 140 && diastolic < 90 -> "🔴 Изолированная систолическая гипертензия"
                    systolic > 130 -> "🟡 Повышенное систолическое"
                    diastolic > 85 -> "🟡 Повышенное диастолическое"
                    else -> "⚪ Требуется контроль"
                }
            }
        }
    }

    private fun getPulseStatus(pulse: Int): String {
        return when (pulse) {
            in 0..40 -> "🔴 Критически низкий пульс"
            in 41..59 -> "🟠 Пониженный пульс"
            in 60..90 -> "🟢 Нормальный пульс"
            in 91..135 -> "🟡 Тахикардия 1 степени"
            in 136..185 -> "🔴 Тахикардия 2 степени"
            else -> "🔴 Критическая тахикардия"
        }
    }
}