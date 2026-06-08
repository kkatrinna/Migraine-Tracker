package com.example.migrainetracker.ui.fragment

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

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
    private lateinit var cardExport: com.google.android.material.card.MaterialCardView
    private lateinit var remindersAdapter: RemindersAdapter
    private lateinit var recyclerReminders: RecyclerView
    private lateinit var textEmptyReminders: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
        cardExport = view.findViewById(R.id.card_export)

        setupCalendar()
        setupButtons(view)
        applyThemeColors()
        createNotificationChannels()

        cardExport.setOnClickListener { showExportDialog() }
        view.findViewById<Button>(R.id.btn_reminders)?.setOnClickListener { showRemindersDialog() }

        checkNotificationPermission()
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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val exportChannel = NotificationChannel("download_channel", "Экспорт данных", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Уведомления о завершении экспорта данных"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(null, null)
            }

            val reminderChannel = NotificationChannel("reminder_channel", "Напоминания о лекарствах", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Уведомления о времени приема лекарств"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }

            notificationManager.createNotificationChannel(exportChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Разрешение на будильники")
                    .setMessage("Для точных напоминаний о приёме лекарств необходимо разрешить планирование будильников.")
                    .setPositiveButton("Перейти в настройки") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
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

        val presetTriggers = listOf("Стресс", "Недосып", "Яркий свет", "Громкий звук", "Погода", "Голод", "Кофеин", "Алкоголь", "Гормоны", "Другое")
        for (name in presetTriggers) {
            db.triggerDao().insertTrigger(Trigger(name = name))
        }
        allTriggers = db.triggerDao().getAllTriggers()
    }

    private fun applyThemeColors() {
        view?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background_main))

        textMonthTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_500))

        if (::calendarAdapter.isInitialized) calendarAdapter.notifyDataSetChanged()
    }

    private suspend fun loadTriggers() {
        db.triggerDao().deleteDuplicateTriggers()
        allTriggers = db.triggerDao().getAllTriggersDistinct()

        if (allTriggers.isEmpty()) {
            val presetTriggers = listOf("Стресс", "Недосып", "Яркий свет", "Громкий звук", "Погода", "Голод", "Кофеин", "Алкоголь", "Гормоны", "Другое")
            for (name in presetTriggers) {
                db.triggerDao().insertTrigger(Trigger(name = name))
            }
            allTriggers = db.triggerDao().getAllTriggersDistinct()
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
                if (::textTopTriggers.isInitialized) updateTopTriggersUI(topTriggers)
            } catch (e: Exception) {
                if (::textTopTriggers.isInitialized) textTopTriggers.text = "Ошибка загрузки"
            }
        }
    }

    private fun updateTopTriggersUI(topTriggers: List<com.example.migrainetracker.data.entity.TopTrigger>) {
        val top3Triggers = topTriggers.take(3)

        if (top3Triggers.isEmpty()) {
            textTopTriggers.text = "Нет отмеченных триггеров"
            textTopTriggers.visibility = View.VISIBLE
            layoutTopTriggers.visibility = View.GONE
            return
        }

        textTopTriggers.visibility = View.GONE
        layoutTopTriggers.visibility = View.VISIBLE
        layoutTopTriggers.removeAllViews()

        for (trigger in top3Triggers) {
            val triggerView = layoutInflater.inflate(R.layout.item_top_trigger, layoutTopTriggers, false)
            val imageTriggerIcon = triggerView.findViewById<ImageView>(R.id.image_trigger_icon)
            val textTriggerName = triggerView.findViewById<TextView>(R.id.text_trigger_name)
            val textTriggerCount = triggerView.findViewById<TextView>(R.id.text_trigger_count)
            val progressBar = triggerView.findViewById<ProgressBar>(R.id.progress_bar_trigger)

            textTriggerName.text = trigger.name
            textTriggerCount.text = "${trigger.count} ${getCountWord(trigger.count)}"
            imageTriggerIcon.setImageResource(getTriggerIcon(trigger.name))

            val maxCount = top3Triggers.first().count
            progressBar.progress = (trigger.count.toFloat() / maxCount * 100).toInt()
            layoutTopTriggers.addView(triggerView)
        }
    }

    private fun getTriggerIcon(triggerName: String): Int {
        return when (triggerName.lowercase()) {
            "стресс" -> R.drawable.ic_trigger_stress
            "недосып" -> R.drawable.ic_trigger_sleep
            "яркий свет" -> R.drawable.ic_trigger_light
            "громкий звук" -> R.drawable.ic_trigger_sound
            "погода" -> R.drawable.ic_trigger_weather
            "голод" -> R.drawable.ic_trigger_hunger
            "кофеин" -> R.drawable.ic_trigger_caffeine
            "алкоголь" -> R.drawable.ic_trigger_alcohol
            "гормоны" -> R.drawable.ic_trigger_hormones
            else -> R.drawable.ic_trigger_default
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
        textMonthTitle.text = currentYearMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy"))
    }

    private fun loadData() {
        lifecycleScope.launch {
            val startDate = currentYearMonth.atDay(1)
            val endDate = currentYearMonth.atEndOfMonth()

            val migraineRecords = repository.getMigraineRecordsForDateRange(startDate, endDate)
            migraineDays.clear()
            for (record in migraineRecords) {
                migraineDays.computeIfAbsent(record.date) { mutableListOf() }.add(record)
            }

            val menstruationList = repository.getMenstruationDaysForMonth(startDate, endDate)
            menstruationDays.clear()
            for (day in menstruationList) {
                if (day.isMenstruating) menstruationDays.add(day.date)
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

        for (i in 1 until firstDayOfMonth.dayOfWeek.value) {
            days.add(CalendarDay(null, 0, false))
        }

        for (day in 1..daysInMonth) {
            val date = currentYearMonth.atDay(day)
            val maxIntensity = migraineDays[date]?.maxOfOrNull { it.intensity } ?: 0
            days.add(CalendarDay(date, maxIntensity, menstruationDays.contains(date)))
        }
        calendarAdapter.submitList(days)
    }

    private fun updateStatistics() {
        val allRecords = migraineDays.values.flatten()
        val daysWithMigraine = allRecords.map { it.date }.distinct().size
        val avgIntensity = if (allRecords.isNotEmpty()) allRecords.map { it.intensity }.average() else 0.0
        val maxIntensity = allRecords.maxOfOrNull { it.intensity } ?: 0

        textMigraineDaysCount.text = daysWithMigraine.toString()
        textAvgIntensity.text = String.format("%.1f/10", avgIntensity)
        textMaxIntensity.text = "$maxIntensity/10"
    }

    private fun showExportDialog() {
        if (!checkStoragePermission()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("📁 Доступ к файлам")
                .setMessage("Для сохранения отчетов необходимо разрешить доступ к файлам. Вы можете сохранить отчет в кэш приложения или предоставить разрешение.")
                .setPositiveButton("Предоставить доступ") { _, _ -> requestStoragePermission() }
                .setNegativeButton("Сохранить в кэш") { _, _ -> showExportOptionsDialog() }
                .show()
            return
        }
        showExportOptionsDialog()
    }

    private fun showExportOptionsDialog() {
        lifecycleScope.launch {
            val allRecords = db.migraineRecordDao().getAllRecords()
            if (allRecords.isEmpty()) {
                Snackbar.make(requireView(), "Нет записей для экспорта", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
            val cardAll = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_export_all)
            val cardLastMonth = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_export_last_month)
            val cardDateRange = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_export_date_range)
            val cardStatistics = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_export_statistics)
            val textAllCount = dialogView.findViewById<TextView>(R.id.text_export_all_count)
            textAllCount.text = "${allRecords.size} записей"

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("📤 Экспорт данных")
                .setMessage("Выберите период для экспорта")
                .setView(dialogView)
                .setNegativeButton("Отмена", null)
                .create()

            cardAll.setOnClickListener { dialog.dismiss(); exportRecords(allRecords) }
            cardLastMonth.setOnClickListener { dialog.dismiss(); exportLastMonthRecords() }
            cardDateRange.setOnClickListener { dialog.dismiss(); showDateRangePicker() }
            cardStatistics.setOnClickListener { dialog.dismiss(); exportStatistics() }
            dialog.show()
        }
    }

    private fun exportRecords(records: List<MigraineRecord>) {
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("📤 Экспорт данных...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = records.size
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val file = exportRecordsToCSVWithProgress(records, progressDialog)
                progressDialog.dismiss()
                if (file != null) {
                    val savedFile = saveFileToDownloads(file, "migraine_records")
                    if (savedFile != null) showDownloadSuccessDialog(savedFile)
                    else Snackbar.make(requireView(), "Ошибка сохранения файла", Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(requireView(), "Ошибка создания файла", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showDownloadSuccessDialog(file: File) {
        val fileSize = file.length() / 1024
        showDownloadNotification(file)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✅ Экспорт завершен")
            .setMessage("Файл успешно сохранен!\n\n📄 Имя: ${file.name}\n💾 Размер: $fileSize KB\n📁 Папка: ${file.parentFile?.absolutePath}")
            .setPositiveButton("📂 Открыть файл") { _, _ ->
                try {
                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "text/csv")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) { openDownloadsFolder(file) }
            }
            .setNeutralButton("📂 Открыть папку") { _, _ -> openDownloadsFolder(file) }
            .setNegativeButton("🔒 Закрыть", null)
            .show()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder(requireContext())
                    .setTitle("🔔 Уведомления")
                    .setMessage("Для отображения уведомлений о завершении экспорта файлов необходимо разрешение.")
                    .setPositiveButton("Разрешить") { _, _ -> requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1003) }
                    .setNegativeButton("Не сейчас", null)
                    .show()
            }
        }
    }

    private fun openDownloadsFolder(file: File?) {
        try {
            val targetDir = file?.parentFile ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MigraineTracker")
            if (targetDir?.exists() != true) {
                Snackbar.make(requireView(), "Папка не найдена", Snackbar.LENGTH_SHORT).show()
                return
            }
            val folderUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", targetDir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(requireContext().packageManager) != null) startActivity(intent)
            else Snackbar.make(requireView(), "Папка: ${targetDir.absolutePath}", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Snackbar.make(requireView(), "Папка: ${file?.parentFile?.absolutePath ?: "Загрузки/MigraineTracker"}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun saveFileToDownloads(sourceFile: File, fileNamePrefix: String): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${fileNamePrefix}_$timeStamp.csv"
            val appDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MigraineTracker")
            if (!appDir.exists()) appDir.mkdirs()
            val destinationFile = File(appDir, fileName)
            sourceFile.copyTo(destinationFile, overwrite = true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(destinationFile, fileName)
            destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveToMediaStore(file: File, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MigraineTracker")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        file.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
                    }
                }
            } else {
                val appDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MigraineTracker")
                if (!appDir.exists()) appDir.mkdirs()
                val destinationFile = File(appDir, fileName)
                file.copyTo(destinationFile, overwrite = true)
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(destinationFile)
                requireContext().sendBroadcast(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun exportRecordsToCSVWithProgress(records: List<MigraineRecord>, progressDialog: ProgressDialog): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(requireContext().cacheDir, "migraine_records_$timeStamp.csv")

            FileOutputStream(file).use { outputStream ->
                val headers = listOf("ID", "Дата", "День недели", "Время начала", "Время окончания", "Длительность (часы)", "Интенсивность (1-10)", "Уровень боли", "Лекарство", "Тошнота", "Светобоязнь", "Аура", "Триггеры", "Заметки")
                outputStream.write(headers.joinToString(",").toByteArray())
                outputStream.write("\n".toByteArray())

                records.forEachIndexed { index, record ->
                    val duration = if (record.endTime != null) {
                        val durationMinutes = if (record.endTime.isAfter(record.time)) {
                            java.time.Duration.between(record.time, record.endTime).toMinutes()
                        } else {
                            java.time.Duration.between(record.time, record.endTime.plusHours(24)).toMinutes()
                        }
                        String.format("%.1f", durationMinutes / 60.0)
                    } else ""

                    val painLevel = when (record.intensity) {
                        in 0..2 -> "Слабая"
                        in 3..6 -> "Средняя"
                        else -> "Сильная"
                    }

                    val dayOfWeek = when (record.date.dayOfWeek.value) {
                        1 -> "Понедельник"
                        2 -> "Вторник"
                        3 -> "Среда"
                        4 -> "Четверг"
                        5 -> "Пятница"
                        6 -> "Суббота"
                        7 -> "Воскресенье"
                        else -> ""
                    }

                    val triggerIds = runBlocking { db.migraineRecordTriggerDao().getTriggerIdsForRecord(record.id) }
                    val triggerNames = triggerIds.mapNotNull { id -> allTriggers.find { it.id == id }?.name }.joinToString("; ")

                    val row = listOf(
                        record.id.toString(), record.date.toString(), dayOfWeek, record.time.toString(),
                        record.endTime?.toString() ?: "", duration, record.intensity.toString(), painLevel,
                        record.medicationName?.replace(",", " ") ?: "",
                        if (record.nausea) "Да" else "Нет",
                        if (record.photophobia) "Да" else "Нет",
                        if (record.aura) "Да" else "Нет",
                        triggerNames.replace(",", ";"),
                        record.notes?.replace(",", " ") ?: ""
                    )
                    outputStream.write(row.joinToString(",").toByteArray())
                    outputStream.write("\n".toByteArray())
                    progressDialog.progress = index + 1
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addCategory("android.intent.category.DEFAULT")
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivityForResult(intent, 1001)
            } catch (e: Exception) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), 1001)
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 1001)
        }
    }

    private fun showDownloadNotification(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Snackbar.make(requireView(), "Файл сохранен: ${file.name}", Snackbar.LENGTH_LONG).show()
                    return
                }
            }

            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val fileUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val openPendingIntent = PendingIntent.getActivity(requireContext(), 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val folderUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file.parentFile ?: file)
            val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val folderPendingIntent = PendingIntent.getActivity(requireContext(), 1, folderIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val sharePendingIntent = PendingIntent.getActivity(requireContext(), 2, Intent.createChooser(shareIntent, "Поделиться файлом"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(requireContext(), "download_channel")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("✅ Экспорт завершен")
                .setContentText("${file.name} (${file.length() / 1024} KB)")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Файл сохранен в папке:\n${file.parentFile?.absolutePath ?: "Загрузки"}"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_share, "Поделиться", sharePendingIntent).build())
                .addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_upload, "Открыть папку", folderPendingIntent).build())
                .build()

            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
            Snackbar.make(requireView(), "✅ Файл сохранен: ${file.name}", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(requireView(), "Файл сохранен: ${file.name}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun exportLastMonthRecords() {
        lifecycleScope.launch {
            val endDate = LocalDate.now()
            val startDate = endDate.minusMonths(1)
            val records = db.migraineRecordDao().getMigraineRecordsForDateRange(startDate, endDate)
            if (records.isEmpty()) Snackbar.make(requireView(), "Нет записей за последний месяц", Snackbar.LENGTH_LONG).show()
            else exportRecords(records)
        }
    }

    private fun showDateRangePicker() {
        val datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker().setTitleText("Выберите период").build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = LocalDate.ofEpochDay(selection.first / (24 * 60 * 60 * 1000))
            val endDate = LocalDate.ofEpochDay(selection.second / (24 * 60 * 60 * 1000))
            lifecycleScope.launch {
                val records = db.migraineRecordDao().getMigraineRecordsForDateRange(startDate, endDate)
                if (records.isEmpty()) Snackbar.make(requireView(), "Нет записей за выбранный период", Snackbar.LENGTH_LONG).show()
                else exportRecords(records)
            }
        }
        datePicker.show(parentFragmentManager, "date_range_picker")
    }

    private fun exportStatistics() {
        lifecycleScope.launch {
            val allRecords = db.migraineRecordDao().getAllRecords()
            if (allRecords.isEmpty()) {
                Snackbar.make(requireView(), "Нет данных для статистики", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val progressDialog = ProgressDialog(requireContext()).apply {
                setMessage("Создание статистического отчета...")
                setCancelable(false)
                show()
            }

            try {
                val avgIntensity = allRecords.map { it.intensity }.average()
                val maxIntensity = allRecords.maxOfOrNull { it.intensity } ?: 0
                val totalAttacks = allRecords.size
                val daysWithMigraine = allRecords.map { it.date }.distinct().size

                val statsReport = buildString {
                    appendLine("СТАТИСТИКА МИГРЕНИ")
                    appendLine("=".repeat(40))
                    appendLine("Период: ${allRecords.first().date} - ${allRecords.last().date}\n")
                    appendLine("📊 Общая статистика:")
                    appendLine("  • Всего приступов: $totalAttacks")
                    appendLine("  • Дней с мигренью: $daysWithMigraine")
                    appendLine("  • Средняя интенсивность: ${String.format("%.1f", avgIntensity)}/10")
                    appendLine("  • Максимальная боль: $maxIntensity/10\n")
                    appendLine("💊 Принятые лекарства:")
                    val medications = allRecords.mapNotNull { it.medicationName }.groupingBy { it }.eachCount()
                    if (medications.isNotEmpty()) {
                        medications.forEach { (med, count) -> appendLine("  • $med: $count ${if (count == 1) "раз" else "раза"}") }
                    } else {
                        appendLine("  • Нет записей о лекарствах")
                    }
                    appendLine("\n📈 Рекомендации:")
                    if (avgIntensity > 7) appendLine("  • Высокий уровень боли - обратитесь к врачу")
                    if (totalAttacks > 15) appendLine("  • Частые приступы - требуется профилактическое лечение")
                    if (avgIntensity <= 3 && totalAttacks < 5) appendLine("  • Хороший контроль мигрени, продолжайте в том же духе!")
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val tempFile = File(requireContext().cacheDir, "migraine_statistics_$timeStamp.txt")
                tempFile.writeText(statsReport)

                val savedFile = saveFileToDownloads(tempFile, "migraine_statistics")
                progressDialog.dismiss()
                if (savedFile != null) showDownloadSuccessDialog(savedFile)
                else Snackbar.make(requireView(), "Ошибка сохранения статистики", Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                progressDialog.dismiss()
                Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddMigraineDialog(date: LocalDate, recordToEdit: MigraineRecord? = null) {
        if (date.isAfter(LocalDate.now()) && recordToEdit == null) {
            Snackbar.make(requireView(), "Нельзя добавлять записи о мигрени в будущие даты", Snackbar.LENGTH_LONG).show()
            return
        }

        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Загрузка...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            if (allTriggers.isEmpty()) allTriggers = db.triggerDao().getAllTriggers()
            progressDialog.dismiss()
            showAddMigraineDialogInternal(date, recordToEdit)
        }
    }

    private fun showAddMigraineDialogInternal(date: LocalDate, recordToEdit: MigraineRecord?) {
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

        val medications = listOf("Без лекарства", "Налгезин 500мг", "Налгезин 275мг", "Суматриптан 50мг", "Суматриптан 100мг", "Эксенза", "Диалрапид 100", "Своё")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, medications)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMedication.adapter = spinnerAdapter
        spinnerMedication.setSelection(0)

        fun updateDuration() {
            val startTimeStr = editTime.text.toString()
            val endTimeStr = editEndTime.text.toString()
            if (startTimeStr.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")) && endTimeStr.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$"))) {
                val start = LocalTime.parse(startTimeStr)
                val end = LocalTime.parse(endTimeStr)
                val durationMinutes = if (end.isAfter(start)) java.time.Duration.between(start, end).toMinutes()
                else java.time.Duration.between(start, end.plusHours(24)).toMinutes()
                textDuration.text = "Длительность: ${durationMinutes / 60}ч ${durationMinutes % 60}мин"
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
                String.format("%02d:%02d", time.plusHours(1).hour, time.plusHours(1).minute)
            } else {
                val now = LocalTime.now()
                String.format("%02d:%02d", now.hour, now.minute)
            }
            editEndTime.setText(newTime)
        }

        val selectedTriggerIds = mutableSetOf<Int>()
        if (recordToEdit != null) {
            runBlocking { selectedTriggerIds.addAll(db.migraineRecordTriggerDao().getTriggerIdsForRecord(recordToEdit.id)) }
        }

        val triggerAdapter = TriggerCheckboxAdapter(allTriggers.distinctBy { it.id }, selectedTriggerIds)
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
                if (index != -1) spinnerMedication.setSelection(index)
                else editMedication.setText(recordToEdit.medicationName)
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

        AlertDialog.Builder(requireContext())
            .setTitle("${if (recordToEdit != null) "Редактировать запись" else "Добавить запись"} - ${date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val timeStr = editTime.text.toString()
                val endTimeStr = editEndTime.text.toString()
                val timePattern = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

                if (!timePattern.matches(timeStr)) {
                    Toast.makeText(requireContext(), "Введите время в формате ЧЧ:ММ", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val parts = timeStr.split(":")
                val time = LocalTime.of(parts[0].toInt(), parts[1].toInt())
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
                            db.migraineRecordTriggerDao().deleteByRecordId(recordToEdit.id)
                            for (triggerId in selectedTriggerIds) {
                                db.migraineRecordTriggerDao().insert(MigraineRecordTrigger(recordId = recordToEdit.id, triggerId = triggerId))
                            }
                        } else {
                            val newId = repository.addMigraineRecord(record)
                            for (triggerId in selectedTriggerIds) {
                                db.migraineRecordTriggerDao().insert(MigraineRecordTrigger(recordId = newId.toInt(), triggerId = triggerId))
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
            val isFutureDate = date.isAfter(LocalDate.now())

            val recordsWithTriggers = records.map { record ->
                val triggerIds = db.migraineRecordTriggerDao().getTriggerIdsForRecord(record.id)
                val triggerNames = triggerIds.mapNotNull { id -> allTriggers.find { it.id == id }?.name }
                record to triggerNames
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_day_details, null)
            val textRecordsList = dialogView.findViewById<TextView>(R.id.text_records_list)
            val btnExportDay = dialogView.findViewById<Button>(R.id.btn_export_day)

            val message = buildString {
                if (isFutureDate) append("⚠️ Это будущая дата\nДобавление записей доступно только для прошедших и текущего дня\n\n")
                if (isMenstruating) append("🩸 День месячных\n\n")
                if (recordsWithTriggers.isEmpty()) append("Нет записей о мигрени")
                else {
                    append("📝 Записи о мигрени:\n\n")
                    for ((index, pair) in recordsWithTriggers.withIndex()) {
                        val record = pair.first
                        val triggers = pair.second
                        append("${index + 1}. ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))}\n")
                        append("   💢 Боль: ${record.intensity}/10\n")
                        if (record.medicationName != null) append("   💊 ${record.medicationName}\n")
                        if (triggers.isNotEmpty()) append("   ⚡ Триггеры: ${triggers.joinToString(", ")}\n")
                        append("\n")
                    }
                }
            }
            textRecordsList.text = message

            btnExportDay.setOnClickListener {
                if (records.isNotEmpty()) exportRecords(records)
                else Snackbar.make(dialogView, "Нет записей для экспорта", Snackbar.LENGTH_LONG).show()
            }

            val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Детали дня - ${date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))}")
                .setView(dialogView)
                .setPositiveButton("Закрыть", null)

            if (!isFutureDate) dialogBuilder.setNeutralButton("Добавить запись") { _, _ -> showAddMigraineDialog(date) }
            if (records.isNotEmpty()) dialogBuilder.setNegativeButton("Управление") { _, _ -> showRecordManagementDialog(date, records) }

            dialogBuilder.show()
        }
    }

    private fun showRecordManagementDialog(date: LocalDate, records: List<MigraineRecord>) {
        val items = records.mapIndexed { index, record ->
            "${index + 1}. ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${record.intensity}/10" + (if (record.medicationName != null) " (${record.medicationName})" else "")
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите запись")
            .setItems(items) { _, which -> showRecordActionDialog(date, records[which]) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRecordActionDialog(date: LocalDate, record: MigraineRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("Действие с записью")
            .setItems(arrayOf("Редактировать", "Удалить")) { _, which ->
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
            0 -> ContextCompat.getColor(requireContext(), R.color.pain_none)
            in 1..3 -> ContextCompat.getColor(requireContext(), R.color.pain_mild)
            in 4..6 -> ContextCompat.getColor(requireContext(), R.color.pain_moderate)
            in 7..8 -> ContextCompat.getColor(requireContext(), R.color.pain_severe)
            else -> ContextCompat.getColor(requireContext(), R.color.pain_extreme)
        }
    }

    private fun getIntensityColorWithAlpha(intensity: Int, alpha: Int): Int = ColorUtils.setAlphaComponent(getIntensityColor(intensity), alpha)

    private fun isDarkColor(color: Int): Boolean {
        val brightness = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return brightness < 128
    }

    private fun showRemindersDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reminders, null)
        recyclerReminders = dialogView.findViewById(R.id.recycler_reminders)
        val btnAddReminder = dialogView.findViewById<Button>(R.id.btn_add_reminder)
        textEmptyReminders = dialogView.findViewById(R.id.text_empty_reminders)

        recyclerReminders.layoutManager = LinearLayoutManager(requireContext())
        remindersAdapter = RemindersAdapter(emptyList(), { deleteReminder(it) }, { reminder, isEnabled -> toggleReminder(reminder, isEnabled) })
        recyclerReminders.adapter = remindersAdapter

        loadRemindersData()
        btnAddReminder.setOnClickListener { showAddReminderDialog() }

        AlertDialog.Builder(requireContext()).setView(dialogView).setPositiveButton("Закрыть", null).show()
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
            if (isEnabled) scheduleReminder(updatedReminder)
            else cancelReminder(reminder.id)
            refreshRemindersList()
        }
    }

    private fun cancelReminder(reminderId: Int) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(requireContext(), reminderId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    private fun showAddReminderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_reminder, null)
        val editMedicineName = dialogView.findViewById<TextInputEditText>(R.id.edit_medicine_name)
        val editReminderTime = dialogView.findViewById<TextInputEditText>(R.id.edit_reminder_time)
        val checkRepeat = dialogView.findViewById<CheckBox>(R.id.check_repeat)
        val spinnerRepeat = dialogView.findViewById<Spinner>(R.id.spinner_repeat)

        val buttons = listOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23).associateWith { dialogView.findViewById<MaterialButton>(resources.getIdentifier("btn_time_$it", "id", requireContext().packageName)) }
        editReminderTime.addTextChangedListener(com.example.migrainetracker.utils.TimeTextWatcher(editReminderTime))
        editReminderTime.setText("09:00")
        buttons.forEach { (hour, button) -> button.setOnClickListener { editReminderTime.setText(String.format("%02d:00", hour)) } }

        val repeatIntervals = listOf("Каждые 24 часа (ежедневно)", "Каждые 12 часов", "Каждые 8 часов", "Каждые 6 часов", "Каждые 4 часа", "Каждые 2 часа", "Каждый час")
        val repeatValues = listOf(24, 12, 8, 6, 4, 2, 1)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, repeatIntervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRepeat.adapter = adapter
        spinnerRepeat.isEnabled = false
        checkRepeat.setOnCheckedChangeListener { _, isChecked -> spinnerRepeat.isEnabled = isChecked }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(requireContext())
                .setTitle("Разрешение на уведомления")
                .setMessage("Для получения напоминаний необходимо разрешить уведомления.")
                .setPositiveButton("Разрешить") { _, _ -> requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002) }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Разрешение на будильники")
                    .setMessage("Для точных напоминаний о лекарствах необходимо разрешить планирование будильников в настройках.")
                    .setPositiveButton("Открыть настройки") { _, _ -> startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
                    .setNegativeButton("Отмена", null)
                    .show()
                return
            }
        }

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).setPositiveButton("Добавить", null).setNegativeButton("Отмена", null).create()
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
                val reminderTime = LocalTime.of(parts[0].toInt(), parts[1].toInt())
                val repeatInterval = if (checkRepeat.isChecked) repeatValues[spinnerRepeat.selectedItemPosition] else 0

                if (repeatInterval == 0 && reminderTime.isBefore(LocalTime.now())) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Время прошло")
                        .setMessage("Вы выбрали время, которое уже прошло сегодня. Напоминание установится на завтра.")
                        .setPositiveButton("OK", null)
                        .show()
                }

                lifecycleScope.launch {
                    try {
                        val reminder = MedicineReminder(medicineName = medicineName, reminderTime = reminderTime, isEnabled = true, repeatInterval = repeatInterval)
                        val id = db.medicineReminderDao().insertReminder(reminder)
                        scheduleReminder(reminder.copy(id = id.toInt()))
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

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.reminderTime.hour)
            set(Calendar.MINUTE, reminder.reminderTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (reminder.repeatInterval > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    val intervalMillis = (reminder.repeatInterval * 60 * 60 * 1000).toLong()
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        intervalMillis,
                        pendingIntent
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
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
            return DayViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false))
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) = holder.bind(days[position])
        override fun getItemCount() = days.size

        inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textDay: TextView = itemView.findViewById(R.id.text_day)
            private val imagePain: ImageView = itemView.findViewById(R.id.image_pain)
            private val imageMenstruation: ImageView = itemView.findViewById(R.id.image_menstruation)

            fun bind(day: CalendarDay) {
                if (day.date != null) {
                    val today = LocalDate.now()
                    val isFutureDate = day.date.isAfter(today)
                    val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

                    textDay.text = day.date.dayOfMonth.toString()
                    textDay.isEnabled = !isFutureDate
                    textDay.visibility = View.VISIBLE

                    when (day.intensity) {
                        0 -> imagePain.setImageResource(R.drawable.ic_no_migraine)
                        in 1..3 -> imagePain.setImageResource(R.drawable.ic_migraine_mild)
                        in 4..6 -> imagePain.setImageResource(R.drawable.ic_migraine_moderate)
                        else -> imagePain.setImageResource(R.drawable.ic_migraine_severe)
                    }
                    imagePain.visibility = View.VISIBLE
                    imageMenstruation.visibility = if (day.isMenstruating) View.VISIBLE else View.GONE

                    if (isFutureDate) {
                        textDay.alpha = 0.5f
                        imagePain.alpha = 0.5f
                        imageMenstruation.alpha = 0.5f
                        textDay.setBackgroundColor(Color.TRANSPARENT)
                        textDay.setTextColor(if (isNightMode) Color.GRAY else Color.LTGRAY)
                    } else {
                        textDay.alpha = 1f
                        imagePain.alpha = 1f
                        imageMenstruation.alpha = 1f
                        if (day.intensity > 0) {
                            val backgroundColor = getIntensityColorWithAlpha(day.intensity, 100)
                            textDay.setBackgroundColor(backgroundColor)
                            textDay.setTextColor(if (isDarkColor(getIntensityColor(day.intensity))) Color.WHITE else Color.BLACK)
                        } else {
                            textDay.background = null
                            textDay.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
                        }
                    }

                    if (!isFutureDate) {
                        itemView.setOnClickListener { onDayClick(day.date) }
                        itemView.setOnLongClickListener { onDayLongClick(day.date); true }
                    } else {
                        itemView.setOnClickListener(null)
                        itemView.setOnLongClickListener(null)
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

    data class CalendarDay(val date: LocalDate?, val intensity: Int, val isMenstruating: Boolean)
}