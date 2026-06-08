package com.example.migrainetracker.ui.fragment

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.MenstruationDay
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_menstruation_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())

        textMonthTitle = view.findViewById(R.id.text_month_title)
        recyclerCalendar = view.findViewById(R.id.recycler_view_calendar)
        textLastMenstruation = view.findViewById(R.id.text_last_menstruation)
        textCycleLength = view.findViewById(R.id.text_cycle_length)
        textPeriodLength = view.findViewById(R.id.text_period_length)
        textNextPredicted = view.findViewById(R.id.text_next_predicted)
        textOvulationPredicted = view.findViewById(R.id.text_ovulation_predicted)
        cardExport = view.findViewById(R.id.card_export)

        setupCalendar()
        setupButtons()
        loadData()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        cardExport.setOnClickListener {
            exportToCSV()
        }
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
            loadData()
        }

        view?.findViewById<Button>(R.id.button_next_month)?.setOnClickListener {
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

            val days = db.menstruationDayDao().getDaysInRange(startDate, endDate)
            menstruationDays.clear()
            for (day in days) {
                menstruationDays[day.date] = day
            }

            calculateCycleStatistics()
            calculateFertileWindowAndOvulation()
            calculatePredictedDays()
            updateCalendar()
        }
    }

    private fun calculateCycleStatistics() {
        lifecycleScope.launch {
            val allMenstruationDays = db.menstruationDayDao().getAllMenstruationDays()
                .filter { it.isMenstruating }
                .sortedBy { it.date }

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
                updateCalendar()
                return@launch
            }

            val periods = findPeriods(allMenstruationDays)
            periodStartDates = periods.map { it.first() }.toMutableList()

            val dateFormatter = DateTimeFormatter.ofPattern("dd.MM")
            val lastPeriod = periods.lastOrNull()

            if (lastPeriod != null && lastPeriod.isNotEmpty()) {
                val startStr = lastPeriod.first().format(dateFormatter)
                val endStr = lastPeriod.last().format(dateFormatter)
                textLastMenstruation.text = "$startStr - $endStr"

                val periodLengths = periods.map { it.size }
                avgPeriodLength = if (periodLengths.isNotEmpty()) periodLengths.average().toInt() else 5
                textPeriodLength.text = "$avgPeriodLength дн"
            } else {
                textPeriodLength.text = "—"
            }

            val cycles = mutableListOf<Long>()
            for (i in 1 until periodStartDates.size) {
                val prevStart = periodStartDates[i - 1]
                val currentStart = periodStartDates[i]
                val cycleLength = ChronoUnit.DAYS.between(prevStart, currentStart)
                cycles.add(cycleLength)
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

            if (cycles.isNotEmpty()) {
                nextPeriods.clear()
                val lastStart = periodStartDates.lastOrNull() ?: return@launch
                var nextStart = lastStart.plusDays(avgCycleLength.toLong())

                for (i in 1..6) {
                    val nextEnd = nextStart.plusDays((avgPeriodLength - 1).toLong())
                    nextPeriods.add(Pair(nextStart, nextEnd))
                    nextStart = nextStart.plusDays(avgCycleLength.toLong())
                }

                val firstNext = nextPeriods.first()
                val nextStartStr = firstNext.first.format(dateFormatter)
                val nextEndStr = firstNext.second.format(dateFormatter)
                textNextPredicted.text = "$nextStartStr - $nextEndStr"
            } else {
                nextPeriods.clear()
                textNextPredicted.text = "—"
                textOvulationPredicted.text = "—"
            }
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
                    val fertileDate = period.first.plusDays(ovulationDayNumber.toLong() + dayOffset)
                    fertileWindowDays.add(fertileDate)
                }
            }

            val nextOvulationDate = nextPeriods.first().first.plusDays((avgCycleLength - 14).toLong())
            textOvulationPredicted.text = nextOvulationDate.format(DateTimeFormatter.ofPattern("dd.MM"))
        } else {
            textOvulationPredicted.text = "—"
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
                if (currentPeriod.isNotEmpty()) {
                    periods.add(currentPeriod)
                }
                currentPeriod = mutableListOf(day.date)
            }
        }
        if (currentPeriod.isNotEmpty()) {
            periods.add(currentPeriod)
        }
        return periods
    }

    private fun calculatePredictedDays() {
        predictedDays.clear()

        for (period in nextPeriods) {
            var currentDate = period.first
            val endDate = period.second
            while (currentDate <= endDate) {
                if (!menstruationDays.containsKey(currentDate) || !menstruationDays[currentDate]!!.isMenstruating) {
                    if (currentDate.year == currentYearMonth.year && currentDate.month == currentYearMonth.month) {
                        predictedDays.add(currentDate)
                    }
                }
                currentDate = currentDate.plusDays(1)
            }
        }
    }

    private fun updateCalendar() {
        val firstDayOfMonth = currentYearMonth.atDay(1)
        val daysInMonth = currentYearMonth.lengthOfMonth()
        val days = mutableListOf<CalendarDay>()

        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value
        for (i in 1 until firstDayOfWeek) {
            days.add(CalendarDay(null, false, false, false, false))
        }

        for (day in 1..daysInMonth) {
            val date = currentYearMonth.atDay(day)
            val menstruation = menstruationDays[date]
            val isMenstruating = menstruation?.isMenstruating ?: false
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
                val day = MenstruationDay(
                    date = date,
                    isMenstruating = true
                )
                db.menstruationDayDao().insertOrUpdate(day)
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
                sb.append("${day.date},")
                sb.append("${if (day.isMenstruating) "Да" else "Нет"}\n")
            }

            try {
                val fileName = "menstruation_data_${LocalDate.now()}.csv"

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = requireContext().contentResolver
                    val contentValues = android.content.ContentValues().apply {
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
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    )
                    val file = File(downloadsDir, fileName)
                    FileWriter(file).use { writer ->
                        writer.write(sb.toString())
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        file
                    )
                    showDownloadNotification(fileName, uri)
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDownloadNotification(fileName: String, fileUri: android.net.Uri) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "download_channel",
                "Загрузки",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о загрузке файлов"
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = requireContext().getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val openPendingIntent = android.app.PendingIntent.getActivity(
            requireContext(),
            0,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(requireContext(), "download_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("📁 Файл сохранен")
            .setContentText("$fileName")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle()
                .bigText("Файл сохранен в папку Downloads. Нажмите для открытия."))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = requireContext().getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    inner class CalendarAdapter(
        private val onDayClick: (LocalDate) -> Unit
    ) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

        private var days = listOf<CalendarDay>()

        fun submitList(newDays: List<CalendarDay>) {
            days = newDays
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_menstruation_day, parent, false)
            return DayViewHolder(view)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            holder.bind(days[position])
        }

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
                        if (day.isPredicted) {
                            cardDay.setCardBackgroundColor(Color.parseColor("#F8BBD9"))
                            textDay.setTextColor(Color.parseColor("#AD1457"))
                        } else if (day.isFertile) {
                            cardDay.setCardBackgroundColor(Color.parseColor("#C8E6C9"))
                            textDay.setTextColor(Color.parseColor("#2E7D32"))
                        } else if (day.isOvulation) {
                            cardDay.setCardBackgroundColor(Color.parseColor("#B3E5FC"))
                            textDay.setTextColor(Color.parseColor("#0277BD"))
                        } else {
                            if (isNightMode) {
                                cardDay.setCardBackgroundColor(Color.parseColor("#333333"))
                                textDay.setTextColor(Color.parseColor("#666666"))
                            } else {
                                cardDay.setCardBackgroundColor(Color.parseColor("#EEEEEE"))
                                textDay.setTextColor(Color.parseColor("#999999"))
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