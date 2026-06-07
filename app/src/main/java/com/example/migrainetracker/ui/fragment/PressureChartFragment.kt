package com.example.migrainetracker.ui.fragment

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class PressureChartFragment : Fragment() {

    private lateinit var repository: TrackerRepository
    private lateinit var db: AppDatabase
    private lateinit var chartPressure: LineChart
    private lateinit var chartPulse: LineChart
    private lateinit var spinnerPeriod: Spinner
    private lateinit var textPressureStats: TextView
    private lateinit var textPulseStats: TextView
    private lateinit var recyclerPressure: RecyclerView
    private lateinit var recyclerPulse: RecyclerView
    private lateinit var pressureAdapter: PressureCardAdapter
    private lateinit var pulseAdapter: PulseCardAdapter

    private var currentPeriod = "week"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pressure_chart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())
        repository = TrackerRepository(db)

        chartPressure = view.findViewById(R.id.chart_pressure)
        chartPulse = view.findViewById(R.id.chart_pulse)
        spinnerPeriod = view.findViewById(R.id.spinner_period)
        textPressureStats = view.findViewById(R.id.text_pressure_stats)
        textPulseStats = view.findViewById(R.id.text_pulse_stats)
        recyclerPressure = view.findViewById(R.id.recycler_pressure)
        recyclerPulse = view.findViewById(R.id.recycler_pulse)

        setupSpinner()
        setupRecyclerViews()
        setupButtons(view)

        spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentPeriod = when (position) {
                    0 -> "week"
                    1 -> "month"
                    else -> "all"
                }
                loadData()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        loadData()
    }

    private fun setupSpinner() {
        val periods = listOf("Неделя", "Месяц", "Всё время")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPeriod.adapter = adapter
    }

    private fun setupRecyclerViews() {
        pressureAdapter = PressureCardAdapter(
            onItemClick = { showPressureDetails(it) },
            onItemDelete = {
                lifecycleScope.launch {
                    repository.deletePressureRecord(it)
                    loadData()
                }
            }
        )
        recyclerPressure.layoutManager = LinearLayoutManager(requireContext())
        recyclerPressure.adapter = pressureAdapter

        pulseAdapter = PulseCardAdapter(
            onItemClick = { showPulseDetails(it) },
            onItemDelete = {
                lifecycleScope.launch {
                    repository.deletePulseRecord(it)
                    loadData()
                }
            }
        )
        recyclerPulse.layoutManager = LinearLayoutManager(requireContext())
        recyclerPulse.adapter = pulseAdapter
    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.btn_add_pressure).setOnClickListener {
            showAddPressureDialog()
        }
        view.findViewById<Button>(R.id.btn_add_pulse).setOnClickListener {
            showAddPulseDialog()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val allPressure = repository.getPressureRecords()
            val allPulse = repository.getPulseRecords()

            val filteredPressure = filterPressureByPeriod(allPressure)
            val filteredPulse = filterPulseByPeriod(allPulse)

            pressureAdapter.submitList(filteredPressure)
            pulseAdapter.submitList(filteredPulse)

            updatePressureChart(filteredPressure)
            updatePulseChart(filteredPulse)
            updatePressureStats(filteredPressure)
            updatePulseStats(filteredPulse)
        }
    }

    private fun filterPressureByPeriod(records: List<PressureRecord>): List<PressureRecord> {
        val now = LocalDate.now()
        return when (currentPeriod) {
            "week" -> records.filter { it.date >= now.minusWeeks(1) }
            "month" -> records.filter { it.date >= now.minusMonths(1) }
            else -> records
        }
    }

    private fun filterPulseByPeriod(records: List<PulseRecord>): List<PulseRecord> {
        val now = LocalDate.now()
        return when (currentPeriod) {
            "week" -> records.filter { it.date >= now.minusWeeks(1) }
            "month" -> records.filter { it.date >= now.minusMonths(1) }
            else -> records
        }
    }

    private fun updatePressureChart(records: List<PressureRecord>) {
        if (records.isEmpty()) {
            chartPressure.clear()
            chartPressure.setNoDataText("Нет данных")
            chartPressure.invalidate()
            return
        }

        val grouped = records.groupBy { it.date }
        val sortedDates = grouped.keys.sorted()

        if (sortedDates.isEmpty()) {
            chartPressure.clear()
            chartPressure.setNoDataText("Нет данных")
            chartPressure.invalidate()
            return
        }

        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val systolicEntries = mutableListOf<Entry>()
        val diastolicEntries = mutableListOf<Entry>()

        sortedDates.forEachIndexed { index, date ->
            val avgSystolic = grouped[date]?.map { it.systolic }?.average() ?: 0.0
            val avgDiastolic = grouped[date]?.map { it.diastolic }?.average() ?: 0.0
            systolicEntries.add(Entry(index.toFloat(), avgSystolic.toFloat()))
            diastolicEntries.add(Entry(index.toFloat(), avgDiastolic.toFloat()))
        }

        val systolicDataSet = LineDataSet(systolicEntries, "Систолическое (верхнее)").apply {
            color = if (isNightMode) Color.parseColor("#FF6B6B") else Color.parseColor("#E53935")
            setCircleColor(if (isNightMode) Color.parseColor("#FF6B6B") else Color.parseColor("#E53935"))
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 10f
            valueTextColor = if (isNightMode) Color.parseColor("#FF6B6B") else Color.parseColor("#E53935")
            setDrawValues(false)
        }

        val diastolicDataSet = LineDataSet(diastolicEntries, "Диастолическое (нижнее)").apply {
            color = if (isNightMode) Color.parseColor("#64B5F6") else Color.parseColor("#1E88E5")
            setCircleColor(if (isNightMode) Color.parseColor("#64B5F6") else Color.parseColor("#1E88E5"))
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 10f
            valueTextColor = if (isNightMode) Color.parseColor("#64B5F6") else Color.parseColor("#1E88E5")
            setDrawValues(false)
        }

        val data = LineData(systolicDataSet, diastolicDataSet)
        chartPressure.data = data

        chartPressure.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = if (isNightMode) Color.WHITE else Color.BLACK
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return if (index >= 0 && index < sortedDates.size) {
                        sortedDates[index].format(DateTimeFormatter.ofPattern("dd.MM"))
                    } else {
                        ""
                    }
                }
            }
            granularity = 1f
            setDrawGridLines(false)
            setDrawLabels(true)
            labelCount = sortedDates.size.coerceAtMost(6)
        }

        chartPressure.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 200f
            setDrawGridLines(true)
            textColor = if (isNightMode) Color.WHITE else Color.BLACK
            gridColor = if (isNightMode) Color.parseColor("#333333") else Color.parseColor("#CCCCCC")
            labelCount = 5
        }

        chartPressure.axisRight.isEnabled = false
        chartPressure.description.isEnabled = false
        chartPressure.legend.isEnabled = true
        chartPressure.legend.textSize = 10f
        chartPressure.legend.textColor = if (isNightMode) Color.WHITE else Color.BLACK
        chartPressure.setBackgroundColor(Color.TRANSPARENT)
        chartPressure.invalidate()

        addPressureZones(isNightMode)
    }

    private fun addPressureZones(isNightMode: Boolean) {
        val leftAxis = chartPressure.axisLeft

        leftAxis.removeAllLimitLines()

        val normalZone = com.github.mikephil.charting.components.LimitLine(120f, "Норма (до 120)")
        normalZone.apply {
            lineColor = Color.parseColor("#4CAF50")
            lineWidth = 1.5f
            enableDashedLine(10f, 10f, 0f)
            textColor = if (isNightMode) Color.parseColor("#81C784") else Color.parseColor("#4CAF50")
            textSize = 10f
        }

        val preZone = com.github.mikephil.charting.components.LimitLine(140f, "Предгипертензия")
        preZone.apply {
            lineColor = Color.parseColor("#FF9800")
            lineWidth = 1.5f
            enableDashedLine(10f, 10f, 0f)
            textColor = if (isNightMode) Color.parseColor("#FFB74D") else Color.parseColor("#FF9800")
            textSize = 10f
        }

        val hyperZone = com.github.mikephil.charting.components.LimitLine(180f, "Гипертензия")
        hyperZone.apply {
            lineColor = Color.parseColor("#F44336")
            lineWidth = 1.5f
            enableDashedLine(10f, 10f, 0f)
            textColor = if (isNightMode) Color.parseColor("#EF9A9A") else Color.parseColor("#F44336")
            textSize = 10f
        }

        leftAxis.addLimitLine(normalZone)
        leftAxis.addLimitLine(preZone)
        leftAxis.addLimitLine(hyperZone)
    }

    private fun updatePulseChart(records: List<PulseRecord>) {
        if (records.isEmpty()) {
            chartPulse.clear()
            chartPulse.setNoDataText("Нет данных")
            chartPulse.invalidate()
            return
        }

        val grouped = records.groupBy { it.date }
        val sortedDates = grouped.keys.sorted()

        if (sortedDates.isEmpty()) {
            chartPulse.clear()
            chartPulse.setNoDataText("Нет данных")
            chartPulse.invalidate()
            return
        }

        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val entries = mutableListOf<Entry>()

        sortedDates.forEachIndexed { index, date ->
            val avgPulse = grouped[date]?.map { it.pulse }?.average() ?: 0.0
            entries.add(Entry(index.toFloat(), avgPulse.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Пульс").apply {
            color = if (isNightMode) Color.parseColor("#CE93D8") else Color.parseColor("#9C27B0")
            setCircleColor(if (isNightMode) Color.parseColor("#CE93D8") else Color.parseColor("#9C27B0"))
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 10f
            valueTextColor = if (isNightMode) Color.parseColor("#CE93D8") else Color.parseColor("#9C27B0")
            setDrawFilled(true)
            fillColor = if (isNightMode) Color.parseColor("#CE93D8") else Color.parseColor("#9C27B0")
            fillAlpha = 50
            setDrawValues(false)
        }

        val data = LineData(dataSet)
        chartPulse.data = data

        chartPulse.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = if (isNightMode) Color.WHITE else Color.BLACK
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return if (index >= 0 && index < sortedDates.size) {
                        sortedDates[index].format(DateTimeFormatter.ofPattern("dd.MM"))
                    } else {
                        ""
                    }
                }
            }
            granularity = 1f
            setDrawGridLines(false)
            setDrawLabels(true)
            labelCount = sortedDates.size.coerceAtMost(6)
        }

        chartPulse.axisLeft.apply {
            axisMinimum = 30f
            axisMaximum = 150f
            setDrawGridLines(true)
            textColor = if (isNightMode) Color.WHITE else Color.BLACK
            gridColor = if (isNightMode) Color.parseColor("#333333") else Color.parseColor("#CCCCCC")
            labelCount = 5
        }

        chartPulse.axisRight.isEnabled = false
        chartPulse.description.isEnabled = false
        chartPulse.legend.isEnabled = true
        chartPulse.legend.textSize = 10f
        chartPulse.legend.textColor = if (isNightMode) Color.WHITE else Color.BLACK
        chartPulse.setBackgroundColor(Color.TRANSPARENT)
        chartPulse.invalidate()

        addPulseZones(isNightMode)
    }

    private fun addPulseZones(isNightMode: Boolean) {
        val leftAxis = chartPulse.axisLeft

        leftAxis.removeAllLimitLines()

        val lowZone = com.github.mikephil.charting.components.LimitLine(60f, "Низкий пульс")
        lowZone.apply {
            lineColor = Color.parseColor("#FF9800")
            lineWidth = 1.5f
            enableDashedLine(10f, 10f, 0f)
            textColor = if (isNightMode) Color.parseColor("#FFB74D") else Color.parseColor("#FF9800")
            textSize = 10f
        }

        val normalZone = com.github.mikephil.charting.components.LimitLine(80f, "Норма")
        normalZone.apply {
            lineColor = Color.parseColor("#4CAF50")
            lineWidth = 1.5f
            enableDashedLine(10f, 10f, 0f)
            textColor = if (isNightMode) Color.parseColor("#81C784") else Color.parseColor("#4CAF50")
            textSize = 10f
        }

        val highZone = com.github.mikephil.charting.components.LimitLine(100f, "Тахикардия")
        highZone.apply {
            lineColor = Color.parseColor("#F44336")
            lineWidth = 1.5f
            enableDashedLine(10f, 10f, 0f)
            textColor = if (isNightMode) Color.parseColor("#EF9A9A") else Color.parseColor("#F44336")
            textSize = 10f
        }

        leftAxis.addLimitLine(lowZone)
        leftAxis.addLimitLine(normalZone)
        leftAxis.addLimitLine(highZone)
    }

    private fun updatePressureStats(records: List<PressureRecord>) {
        if (records.isEmpty()) {
            textPressureStats.text = "Нет данных за выбранный период"
            return
        }

        val avgSystolic = records.map { it.systolic }.average()
        val avgDiastolic = records.map { it.diastolic }.average()
        val minSystolic = records.minByOrNull { it.systolic }?.systolic ?: 0
        val maxSystolic = records.maxByOrNull { it.systolic }?.systolic ?: 0
        val totalDays = records.map { it.date }.distinct().size

        val status = when {
            avgSystolic < 120 && avgDiastolic < 80 -> "🟢 Нормальное"
            avgSystolic < 130 -> "🟡 Повышенное"
            avgSystolic < 140 -> "🟠 Гипертензия 1 степени"
            else -> "🔴 Гипертензия 2+ степени"
        }

        textPressureStats.text = buildString {
            appendLine(" Статистика за период:")
            appendLine("")
            appendLine(" Среднее: ${avgSystolic.roundToInt()}/${avgDiastolic.roundToInt()} мм рт.ст.")
            appendLine(" Минимум: $minSystolic/${records.minByOrNull { it.diastolic }?.diastolic ?: 0} мм рт.ст.")
            appendLine(" Максимум: $maxSystolic/${records.maxByOrNull { it.diastolic }?.diastolic ?: 0} мм рт.ст.")
            appendLine(" Дней с замерами: $totalDays")
            appendLine("")
            appendLine("🏷 Статус: $status")
        }
    }

    private fun updatePulseStats(records: List<PulseRecord>) {
        if (records.isEmpty()) {
            textPulseStats.text = "Нет данных за выбранный период"
            return
        }

        val avgPulse = records.map { it.pulse }.average()
        val minPulse = records.minByOrNull { it.pulse }?.pulse ?: 0
        val maxPulse = records.maxByOrNull { it.pulse }?.pulse ?: 0
        val totalDays = records.map { it.date }.distinct().size

        val status = when {
            avgPulse < 60 -> "🟡 Брадикардия"
            avgPulse <= 80 -> "🟢 Нормальный пульс"
            avgPulse <= 100 -> "🟠 Учащенный пульс"
            else -> "🔴 Тахикардия"
        }

        textPulseStats.text = buildString {
            appendLine(" Статистика за период:")
            appendLine("")
            appendLine(" Средний: ${avgPulse.roundToInt()} уд/мин")
            appendLine(" Минимальный: $minPulse уд/мин")
            appendLine(" Максимальный: $maxPulse уд/мин")
            appendLine(" Дней с замерами: $totalDays")
            appendLine("")
            appendLine("🏷 Статус: $status")
        }
    }

    private fun showPressureDetails(record: PressureRecord) {
        val status = when {
            record.systolic < 90 && record.diastolic < 60 -> "Пониженное"
            record.systolic in 90..129 && record.diastolic in 60..79 -> "Нормальное"
            record.systolic in 130..149 && record.diastolic < 80 -> "Повышенное"
            record.systolic in 150..169 || record.diastolic in 80..89 -> "Гипертензия 1 степени"
            record.systolic in 170..189 || record.diastolic in 90..119 -> "Гипертензия 2 степени"
            record.systolic >= 190 || record.diastolic >= 120 -> "Гипертонический криз"
            else -> "Не определено"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Детали измерения")
            .setMessage(
                """
                Дата: ${record.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}
                Время: ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))}
                
                Давление: ${record.systolic}/${record.diastolic} мм рт.ст.
                Статус: $status
                
                ━━━━━━━━━━━━━━━━━━━━━
                Норма: 120/80 мм рт.ст.
            """.trimIndent()
            )
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun showPulseDetails(record: PulseRecord) {
        val status = when (record.pulse) {
            in 0..40 -> "Очень низкий пульс"
            in 41..59 -> "Низкий пульс"
            in 60..79 -> "Нормальный пульс"
            in 80..99 -> "Учащенный пульс"
            in 100..119 -> "Тахикардия легкая"
            in 120..139 -> "Тахикардия средняя"
            else -> "Тахикардия тяжелая"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("📊 Детали измерения")
            .setMessage(
                """
                Дата: ${record.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}
                Время: ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))}
                
                Пульс: ${record.pulse} уд/мин
                Статус: $status
                
                ━━━━━━━━━━━━━━━━━━━━━
                Норма: 60-80 уд/мин
            """.trimIndent()
            )
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun showAddPressureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_pressure, null)
        val editSystolic = dialogView.findViewById<EditText>(R.id.edit_systolic)
        val editDiastolic = dialogView.findViewById<EditText>(R.id.edit_diastolic)
        val editNotes = dialogView.findViewById<EditText>(R.id.edit_notes)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val systolic = editSystolic.text.toString().toIntOrNull()
                val diastolic = editDiastolic.text.toString().toIntOrNull()
                val notes = editNotes.text.toString()

                if (systolic != null && diastolic != null && systolic in 30..250 && diastolic in 20..200) {
                    lifecycleScope.launch {
                        val record = PressureRecord(
                            date = LocalDate.now(),
                            time = LocalTime.now(),
                            systolic = systolic,
                            diastolic = diastolic
                        )
                        repository.addPressureRecord(record)
                        loadData()
                        Toast.makeText(requireContext(), "Давление добавлено", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Введите корректные значения (30-250/20-200)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddPulseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_pulse, null)
        val editPulse = dialogView.findViewById<EditText>(R.id.edit_pulse)
        val editNotes = dialogView.findViewById<EditText>(R.id.edit_notes)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val pulse = editPulse.text.toString().toIntOrNull()
                val notes = editNotes.text.toString()

                if (pulse != null && pulse in 30..200) {
                    lifecycleScope.launch {
                        val record = PulseRecord(
                            date = LocalDate.now(),
                            time = LocalTime.now(),
                            pulse = pulse
                        )
                        repository.addPulseRecord(record)
                        loadData()
                        Toast.makeText(requireContext(), "✅ Пульс добавлен", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        " Введите корректный пульс (30-200)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}