package com.example.migrainetracker.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

        setupRecyclerViews(view)
        setupButtons(view)
        loadData()
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

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.btn_add_pressure).setOnClickListener {
            showAddPressureDialog()
        }

        view.findViewById<Button>(R.id.btn_add_pulse).setOnClickListener {
            showAddPulseDialog()
        }

        // ДОБАВЬТЕ ОБРАБОТЧИК ДЛЯ КНОПКИ ГРАФИКА
        view.findViewById<Button>(R.id.btn_show_chart).setOnClickListener {
            val chartFragment = PressureChartFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, chartFragment)
                .addToBackStack(null)
                .commit()
        }
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
            textPressureCount.text = "Всего записей: 0"
            textAvgSystolic.text = "—"
            textAvgDiastolic.text = "—"
            textMinPressure.text = "—"
            textMaxPressure.text = "—"
            return
        }

        textPressureCount.text = "Всего записей: ${records.size}"

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
            textPulseCount.text = "Всего записей: 0"
            textAvgPulse.text = "—"
            textMinPulse.text = "—"
            textMaxPulse.text = "—"
            return
        }

        textPulseCount.text = "Всего записей: ${records.size}"

        val avgPulse = records.map { it.pulse }.average()
        textAvgPulse.text = String.format("%.0f", avgPulse)

        val minPulse = records.minByOrNull { it.pulse }?.pulse ?: 0
        textMinPulse.text = minPulse.toString()

        val maxPulse = records.maxByOrNull { it.pulse }?.pulse ?: 0
        textMaxPulse.text = maxPulse.toString()
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
            .setMessage("""
                Дата: ${record.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}
                Время: ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))}
                
                Давление: ${record.systolic}/${record.diastolic} мм рт.ст.
                Статус: $status
                
                ━━━━━━━━━━━━━━━━━━━━━
                Норма: 120/80 мм рт.ст.
            """.trimIndent())
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
            .setTitle("Детали измерения")
            .setMessage("""
                Дата: ${record.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}
                Время: ${record.time.format(DateTimeFormatter.ofPattern("HH:mm"))}
                
                Пульс: ${record.pulse} уд/мин
                Статус: $status
                
                ━━━━━━━━━━━━━━━━━━━━━
                Норма: 60-80 уд/мин
            """.trimIndent())
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun showAddPressureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_pressure, null)
        val editSystolic = dialogView.findViewById<EditText>(R.id.edit_systolic)
        val editDiastolic = dialogView.findViewById<EditText>(R.id.edit_diastolic)

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить давление")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val systolic = editSystolic.text.toString().toIntOrNull()
                val diastolic = editDiastolic.text.toString().toIntOrNull()

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
                        Toast.makeText(requireContext(), "Давление добавлено", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Введите корректные значения (30-250/20-200)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddPulseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_pulse, null)
        val editPulse = dialogView.findViewById<EditText>(R.id.edit_pulse)

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить пульс")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val pulse = editPulse.text.toString().toIntOrNull()

                if (pulse != null && pulse in 30..200) {
                    lifecycleScope.launch {
                        val record = PulseRecord(
                            date = LocalDate.now(),
                            time = LocalTime.now(),
                            pulse = pulse
                        )
                        repository.addPulseRecord(record)
                        loadData()
                        Toast.makeText(requireContext(), "Пульс добавлен", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Введите корректный пульс (30-200)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}