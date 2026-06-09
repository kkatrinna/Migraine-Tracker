package com.example.migrainetracker.ui.fragment

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.MenstruationDay
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

class MenstruationCalendarFragment : Fragment() {

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var db: AppDatabase
    private var currentYearMonth = YearMonth.now()
    private var menstruationDays = mutableMapOf<LocalDate, MenstruationDay>()
    private var predictedDays = mutableSetOf<LocalDate>()
    private var fertileWindowDays = mutableSetOf<LocalDate>()
    private var ovulationDays = mutableSetOf<LocalDate>()

    private lateinit var textMonthTitle: TextView
    private lateinit var recyclerCalendar: RecyclerView
    private lateinit var textLastMenstruation: TextView
    private lateinit var textCycleLength: TextView
    private lateinit var textPeriodLength: TextView
    private lateinit var textNextPredicted: TextView
    private lateinit var textOvulationPredicted: TextView
    private lateinit var cardExport: MaterialCardView

    private var avgCycleLength = 28
    private var minCycleLength = 28
    private var maxCycleLength = 28
    private var avgPeriodLength = 5
    private var nextPeriods = mutableListOf<Pair<LocalDate, LocalDate>>()
    private var periodStartDates = mutableListOf<LocalDate>()
    private var sentNotifications = mutableSetOf<String>()

    companion object {
        private const val PREFS_NAME = "menstruation_prefs"
        private const val KEY_SENT_NOTIFICATIONS = "sent_notifications"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_menstruation_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())
        loadSentNotifications()

        textMonthTitle = view.findViewById(R.id.text_month_title)
        recyclerCalendar = view.findViewById(R.id.recycler_view_calendar)
        textLastMenstruation = view.findViewById(R.id.text_last_menstruation)
        textCycleLength = view.findViewById(R.id.text_cycle_length)
        textPeriodLength = view.findViewById(R.id.text_period_length)
        textNextPredicted = view.findViewById(R.id.text_next_predicted)
        textOvulationPredicted = view.findViewById(R.id.text_ovulation_predicted)
        cardExport = view.findViewById(R.id.card_export)

        view.findViewById<Button>(R.id.btn_test_calendar)?.setOnClickListener {
            showAddToCalendarDialog()
        }

        setupCalendar()
        setupButtons()

        lifecycleScope.launch { loadData() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        cardExport.setOnClickListener { exportToCSV() }
        requestCalendarPermission()
    }

    private fun requestCalendarPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_CALENDAR), 101)
            }
        }
    }

    private fun showAddToCalendarDialog() {
        lifecycleScope.launch {
            val allMenstruationDays = withContext(Dispatchers.IO) {
                db.menstruationDayDao().getAllMenstruationDays().filter { it.isMenstruating }
            }

            if (allMenstruationDays.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Нет данных")
                    .setMessage("Для создания прогнозов необходимо добавить дни менструаций.\n\nНажмите на любой день в календаре и отметьте его как день месячных.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            if (nextPeriods.isEmpty()) {
                calculateCycleStatistics()
                calculateFertileWindowAndOvulation()
                calculatePredictedDays()
                updateCalendar()
            }

            if (nextPeriods.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Недостаточно данных")
                    .setMessage("Для создания прогнозов необходимо добавить минимум 2 менструальных цикла.\n\nТекущие данные: ${allMenstruationDays.size} дней")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            showPeriodSelectionDialog()
        }
    }

    private fun showPeriodSelectionDialog() {
        val periodOptions = nextPeriods.mapIndexed { index, period ->
            "${index + 1}. ${period.first.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} - ${period.second.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📅 Добавить в календарь")
            .setItems(periodOptions) { _, which ->
                val selectedPeriod = nextPeriods[which]
                addEventToCalendar(selectedPeriod.first, selectedPeriod.second)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadSentNotifications() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SENT_NOTIFICATIONS, "")
        sentNotifications = if (!saved.isNullOrEmpty()) saved.split(",").toMutableSet() else mutableSetOf()
    }

    private fun saveSentNotifications() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SENT_NOTIFICATIONS, sentNotifications.joinToString(",")).apply()
    }

    private fun setupCalendar() {
        calendarAdapter = CalendarAdapter(
            onDayClick = { date ->
                if (date.isAfter(LocalDate.now())) {
                    Toast.makeText(requireContext(), "Нельзя отмечать будущие даты", Toast.LENGTH_SHORT).show()
                } else {
                    toggleMenstruationDay(date)
                }
            }
        )

        recyclerCalendar.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = calendarAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupButtons() {
        view?.findViewById<Button>(R.id.button_previous_month)?.setOnClickListener {
            currentYearMonth = currentYearMonth.minusMonths(1)
            updateMonthTitle()
            lifecycleScope.launch { loadData() }
        }

        view?.findViewById<Button>(R.id.button_next_month)?.setOnClickListener {
            currentYearMonth = currentYearMonth.plusMonths(1)
            updateMonthTitle()
            lifecycleScope.launch { loadData() }
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

            val days = withContext(Dispatchers.IO) {
                db.menstruationDayDao().getDaysInRange(startDate, endDate)
            }

            menstruationDays.clear()
            for (day in days) {
                menstruationDays[day.date] = day
            }

            calculateCycleStatistics()
            calculateFertileWindowAndOvulation()
            calculatePredictedDays()
            updateCalendar()
            checkAndSendReminders()
        }
    }

    private fun checkAndSendReminders() {
        val today = LocalDate.now()

        for (period in nextPeriods) {
            val periodStart = period.first
            val reminderDate = periodStart.minusDays(5)

            if (reminderDate == today) {
                val notificationKey = "reminder_${periodStart}"

                if (!sentNotifications.contains(notificationKey)) {
                    sendReminderNotification(periodStart)
                    sentNotifications.add(notificationKey)
                    saveSentNotifications()
                }
            }
        }
    }

    private fun sendReminderNotification(periodStartDate: LocalDate) {
        try {
            val dateStr = periodStartDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val notificationManager = NotificationManagerCompat.from(requireContext())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "menstruation_reminder",
                    "Напоминания о месячных",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Напоминания о начале месячных"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(requireContext(), "menstruation_reminder")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🩸 Напоминание о месячных")
                .setContentText("Ожидаемое начало месячных: $dateStr")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Через 5 дней ожидается начало месячных."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(periodStartDate.hashCode(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(), "Разрешение на календарь получено", Toast.LENGTH_SHORT).show()
                    showAddToCalendarDialog()
                } else {
                    Toast.makeText(requireContext(), "Разрешение на календарь не получено", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addEventToCalendar(startDate: LocalDate, endDate: LocalDate) {
        try {
            val startMillis = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = endDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                putExtra(CalendarContract.Events.TITLE, "🩸 Месячные")
                putExtra(CalendarContract.Events.DESCRIPTION, "Начало менструации")
                putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            startActivity(Intent.createChooser(intent, "Выберите календарь"))
            Toast.makeText(requireContext(), "Открыт выбор календаря для добавления события", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun calculateCycleStatistics() = withContext(Dispatchers.Main) {
        val allMenstruationDays = withContext(Dispatchers.IO) {
            db.menstruationDayDao().getAllMenstruationDays().filter { it.isMenstruating }.sortedBy { it.date }
        }

        if (allMenstruationDays.isEmpty()) {
            textLastMenstruation.text = "Нет данных"
            textCycleLength.text = "—"
            textPeriodLength.text = "—"
            textNextPredicted.text = "—"
            textOvulationPredicted.text = "—"
            avgCycleLength = 28
            minCycleLength = 28
            maxCycleLength = 28
            avgPeriodLength = 5
            nextPeriods.clear()
            predictedDays.clear()
            fertileWindowDays.clear()
            ovulationDays.clear()
            return@withContext
        }

        val periods = findPeriods(allMenstruationDays)
        periodStartDates = periods.map { it.first() }.toMutableList()

        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM")
        val lastPeriod = periods.lastOrNull()

        if (lastPeriod != null && lastPeriod.isNotEmpty()) {
            textLastMenstruation.text = "${lastPeriod.first().format(dateFormatter)} - ${lastPeriod.last().format(dateFormatter)}"
            val periodLengths = periods.map { it.size }
            avgPeriodLength = if (periodLengths.isNotEmpty()) periodLengths.average().toInt() else 5
            textPeriodLength.text = "$avgPeriodLength дн"
        } else {
            textPeriodLength.text = "—"
        }

        val cycles = mutableListOf<Long>()
        for (i in 1 until periodStartDates.size) {
            cycles.add(ChronoUnit.DAYS.between(periodStartDates[i - 1], periodStartDates[i]))
        }

        if (cycles.isNotEmpty()) {
            avgCycleLength = cycles.average().toLong().toInt()
            minCycleLength = cycles.minOrNull()?.toInt() ?: avgCycleLength
            maxCycleLength = cycles.maxOrNull()?.toInt() ?: avgCycleLength
            textCycleLength.text = "$avgCycleLength дн"
        } else {
            textCycleLength.text = "—"
            avgCycleLength = 28
            minCycleLength = 28
            maxCycleLength = 28
        }

        nextPeriods.clear()
        val lastStart = periodStartDates.lastOrNull()

        if (lastStart != null) {
            var nextStart = lastStart.plusDays(avgCycleLength.toLong())

            for (i in 1..6) {
                nextPeriods.add(Pair(nextStart, nextStart.plusDays((avgPeriodLength - 1).toLong())))
                nextStart = nextStart.plusDays(avgCycleLength.toLong())
            }

            val firstNext = nextPeriods.first()
            textNextPredicted.text = "${firstNext.first.format(dateFormatter)} - ${firstNext.second.format(dateFormatter)}"
            textOvulationPredicted.text = firstNext.first.plusDays((avgCycleLength - 14).toLong()).format(DateTimeFormatter.ofPattern("dd.MM"))
        } else {
            textNextPredicted.text = "—"
            textOvulationPredicted.text = "—"
        }
    }

    private fun calculateFertileWindowAndOvulation() {
        fertileWindowDays.clear()
        ovulationDays.clear()

        if (periodStartDates.size < 2) {
            textOvulationPredicted.text = "—"
            return
        }

        for (periodStart in periodStartDates) {
            val ovulationDayNumber = avgCycleLength - 14
            val ovulationDate = periodStart.plusDays(ovulationDayNumber.toLong())
            ovulationDays.add(ovulationDate)

            for (dayOffset in -5..1) {
                val fertileDate = periodStart.plusDays(ovulationDayNumber.toLong() + dayOffset)
                if (fertileDate.isAfter(periodStart.minusDays(1))) {
                    fertileWindowDays.add(fertileDate)
                }
            }
        }

        if (nextPeriods.isNotEmpty()) {
            for (period in nextPeriods) {
                val ovulationDayNumber = avgCycleLength - 14
                val ovulationDate = period.first.plusDays(ovulationDayNumber.toLong())
                ovulationDays.add(ovulationDate)

                for (dayOffset in -5..1) {
                    fertileWindowDays.add(period.first.plusDays(ovulationDayNumber.toLong() + dayOffset))
                }
            }
        }
    }

    private fun findPeriods(days: List<MenstruationDay>): List<List<LocalDate>> {
        val periods = mutableListOf<List<LocalDate>>()
        var currentPeriod = mutableListOf<LocalDate>()

        for (day in days) {
            if (currentPeriod.isEmpty()) {
                currentPeriod.add(day.date)
            } else if (day.date == currentPeriod.last().plusDays(1)) {
                currentPeriod.add(day.date)
            } else {
                if (currentPeriod.isNotEmpty()) periods.add(currentPeriod)
                currentPeriod = mutableListOf(day.date)
            }
        }
        if (currentPeriod.isNotEmpty()) periods.add(currentPeriod)
        return periods
    }

    private fun calculatePredictedDays() {
        predictedDays.clear()

        for (period in nextPeriods) {
            var currentDate = period.first
            while (currentDate <= period.second) {
                if ((!menstruationDays.containsKey(currentDate) || menstruationDays[currentDate]?.isMenstruating != true) &&
                    currentDate.year == currentYearMonth.year && currentDate.month == currentYearMonth.month) {
                    predictedDays.add(currentDate)
                }
                currentDate = currentDate.plusDays(1)
            }
        }
    }

    private fun updateCalendar() {
        val firstDayOfMonth = currentYearMonth.atDay(1)
        val daysInMonth = currentYearMonth.lengthOfMonth()
        val days = mutableListOf<CalendarDay>()

        for (i in 1 until firstDayOfMonth.dayOfWeek.value) {
            days.add(CalendarDay(null, false, false, false, false))
        }

        for (day in 1..daysInMonth) {
            val date = currentYearMonth.atDay(day)
            val isMenstruating = menstruationDays[date]?.isMenstruating ?: false
            val isPredicted = predictedDays.contains(date) && !isMenstruating
            val isOvulation = ovulationDays.contains(date) && !isMenstruating && !isPredicted
            val isFertile = fertileWindowDays.contains(date) && !isMenstruating && !isPredicted && !isOvulation
            days.add(CalendarDay(date, isMenstruating, isPredicted, isOvulation, isFertile))
        }

        calendarAdapter.submitList(days)
    }

    private fun toggleMenstruationDay(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) {
            Toast.makeText(requireContext(), "Нельзя отмечать будущие даты", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val existing = menstruationDays[date]
            if (existing != null && existing.isMenstruating) {
                db.menstruationDayDao().delete(date)
            } else {
                db.menstruationDayDao().insertOrUpdate(MenstruationDay(date = date, isMenstruating = true))
            }
            loadData()
        }
    }

    private fun exportToCSV() {
        lifecycleScope.launch {
            val allMenstruation = db.menstruationDayDao().getAllMenstruationDays().sortedBy { it.date }

            val sb = StringBuilder()
            sb.append("Дата,Месячные\n")
            for (day in allMenstruation) {
                sb.append("${day.date},${if (day.isMenstruating) "Да" else "Нет"}\n")
            }

            try {
                val fileName = "menstruation_data_${LocalDate.now()}.csv"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = requireContext().contentResolver
                    val contentValues = ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.write(sb.toString().toByteArray())
                        }
                        showDownloadNotification(fileName, uri)
                    } ?: run {
                        Toast.makeText(requireContext(), "Ошибка сохранения файла", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)
                    FileWriter(file).use { it.write(sb.toString()) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                    showDownloadNotification(fileName, uri)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDownloadNotification(fileName: String, fileUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel("download_channel", "Загрузки", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Уведомления о загрузке файлов"
                enableVibration(true)
                setShowBadge(true)
            }
            requireContext().getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val openPendingIntent = android.app.PendingIntent.getActivity(requireContext(), 0, openIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = androidx.core.app.NotificationCompat.Builder(requireContext(), "download_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("📁 Файл сохранен")
            .setContentText(fileName)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("Файл сохранен в папку Downloads. Нажмите для открытия."))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .build()

        requireContext().getSystemService(android.app.NotificationManager::class.java).notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    inner class CalendarAdapter(private val onDayClick: (LocalDate) -> Unit) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

        private var days = listOf<CalendarDay>()

        fun submitList(newDays: List<CalendarDay>) {
            days = newDays
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            return DayViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_menstruation_day, parent, false))
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) = holder.bind(days[position])
        override fun getItemCount() = days.size

        inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textDay: TextView = itemView.findViewById(R.id.text_day)
            private val cardDay: MaterialCardView = itemView.findViewById(R.id.card_day)

            fun bind(day: CalendarDay) {
                if (day.date != null) {
                    textDay.text = day.date.dayOfMonth.toString()
                    val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    val isFutureDate = day.date.isAfter(LocalDate.now())

                    if (isFutureDate) {
                        when {
                            day.isPredicted -> {
                                cardDay.setCardBackgroundColor(Color.parseColor("#F8BBD9"))
                                textDay.setTextColor(Color.parseColor("#AD1457"))
                            }
                            day.isFertile -> {
                                cardDay.setCardBackgroundColor(Color.parseColor("#C8E6C9"))
                                textDay.setTextColor(Color.parseColor("#2E7D32"))
                            }
                            day.isOvulation -> {
                                cardDay.setCardBackgroundColor(Color.parseColor("#B3E5FC"))
                                textDay.setTextColor(Color.parseColor("#0277BD"))
                            }
                            else -> {
                                cardDay.setCardBackgroundColor(if (isNightMode) Color.parseColor("#333333") else Color.parseColor("#EEEEEE"))
                                textDay.setTextColor(if (isNightMode) Color.parseColor("#666666") else Color.parseColor("#999999"))
                            }
                        }
                        cardDay.cardElevation = 0f
                        cardDay.alpha = 0.7f
                        cardDay.isClickable = false
                        return
                    }

                    cardDay.alpha = 1f
                    cardDay.cardElevation = 2f
                    cardDay.isClickable = true

                    when {
                        day.isMenstruating -> {
                            cardDay.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.menstruation_color))
                            textDay.setTextColor(Color.WHITE)
                        }
                        day.isOvulation -> {
                            cardDay.setCardBackgroundColor(Color.parseColor("#81D4FA"))
                            textDay.setTextColor(Color.WHITE)
                        }
                        day.isFertile -> {
                            cardDay.setCardBackgroundColor(Color.parseColor("#A5D6A7"))
                            textDay.setTextColor(Color.parseColor("#1B5E20"))
                        }
                        day.isPredicted -> {
                            cardDay.setCardBackgroundColor(Color.parseColor("#F8BBD9"))
                            textDay.setTextColor(Color.parseColor("#AD1457"))
                        }
                        else -> {
                            cardDay.setCardBackgroundColor(Color.TRANSPARENT)
                            textDay.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
                        }
                    }

                    cardDay.setOnClickListener { onDayClick(day.date) }
                } else {
                    textDay.text = ""
                    cardDay.setCardBackgroundColor(Color.TRANSPARENT)
                    cardDay.isClickable = false
                    cardDay.setOnClickListener(null)
                }
            }
        }
    }

    data class CalendarDay(
        val date: LocalDate?,
        val isMenstruating: Boolean,
        val isPredicted: Boolean = false,
        val isOvulation: Boolean = false,
        val isFertile: Boolean = false
    )
}