package com.example.migrainetracker.ui.fragment

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.MenstruationDay
import com.example.migrainetracker.databinding.FragmentMenstruationCalendarBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MenstruationCalendarFragment : Fragment() {

    private var _binding: FragmentMenstruationCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var db: AppDatabase
    private var currentYearMonth = YearMonth.now()
    private var menstruationDays = mutableSetOf<LocalDate>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenstruationCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())

        setupCalendar()
        setupButtons()
        loadData()
        calculateCycleStatistics()
    }

    private fun setupCalendar() {
        calendarAdapter = CalendarAdapter { date ->
            toggleMenstruationDay(date)
        }

        binding.recyclerViewCalendar.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = calendarAdapter
        }
    }

    private fun setupButtons() {
        binding.buttonPreviousMonth.setOnClickListener {
            currentYearMonth = currentYearMonth.minusMonths(1)
            updateMonthTitle()
            loadData()
        }

        binding.buttonNextMonth.setOnClickListener {
            currentYearMonth = currentYearMonth.plusMonths(1)
            updateMonthTitle()
            loadData()
        }

        updateMonthTitle()
    }

    private fun updateMonthTitle() {
        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy")
        binding.textMonthTitle.text = currentYearMonth.format(formatter)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val startDate = currentYearMonth.atDay(1)
            val endDate = currentYearMonth.atEndOfMonth()

            val days = db.menstruationDayDao()
                .getDaysInRange(startDate, endDate)

            menstruationDays.clear()
            for (day in days) {
                if (day.isMenstruating) {
                    menstruationDays.add(day.date)
                }
            }

            updateCalendar()
            calculateCycleStatistics()
        }
    }

    private fun updateCalendar() {
        val firstDayOfMonth = currentYearMonth.atDay(1)
        val daysInMonth = currentYearMonth.lengthOfMonth()

        val days = mutableListOf<CalendarDay>()

        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value
        for (i in 1 until firstDayOfWeek) {
            days.add(CalendarDay(null, false))
        }

        for (day in 1..daysInMonth) {
            val date = currentYearMonth.atDay(day)
            val isMenstruating = menstruationDays.contains(date)
            days.add(CalendarDay(date, isMenstruating))
        }

        calendarAdapter.submitList(days)
    }

    private fun toggleMenstruationDay(date: LocalDate) {
        lifecycleScope.launch {
            val isCurrentlyMenstruating = menstruationDays.contains(date)

            if (isCurrentlyMenstruating) {
                db.menstruationDayDao().delete(date)
                menstruationDays.remove(date)
            } else {
                val day = MenstruationDay(date, true)
                db.menstruationDayDao().insertOrUpdate(day)
                menstruationDays.add(date)
            }

            updateCalendar()
            calculateCycleStatistics()
        }
    }

    private fun calculateCycleStatistics() {
        lifecycleScope.launch {
            val allMenstruationDays = db.menstruationDayDao().getAllMenstruationDays()
            val sortedDates = allMenstruationDays.sortedBy { it.date }

            if (sortedDates.isEmpty()) {
                binding.textLastMenstruation.text = "Нет данных"
                binding.textNextPredicted.text = "—"
                binding.textAvgCycleLength.text = "—"
                binding.textAvgDaysPerMonth.text = "—"
                return@launch
            }

            val lastDate = sortedDates.last().date
            val dateFormatter = DateTimeFormatter.ofPattern("dd LLLL yyyy")
            binding.textLastMenstruation.text = lastDate.format(dateFormatter)

            val cycleLengths = mutableListOf<Long>()
            var previousDate: LocalDate? = null

            for (day in sortedDates) {
                if (previousDate != null) {
                    val daysBetween = ChronoUnit.DAYS.between(previousDate, day.date)
                    if (daysBetween in 20..40) {
                        cycleLengths.add(daysBetween)
                    }
                }
                previousDate = day.date
            }

            val avgCycleLength = if (cycleLengths.isNotEmpty()) {
                cycleLengths.average().toLong()
            } else {
                0
            }

            binding.textAvgCycleLength.text = if (avgCycleLength > 0) "$avgCycleLength дней" else "—"


            if (sortedDates.isNotEmpty()) {
                val daysByMonth = sortedDates.groupBy { YearMonth.from(it.date) }

                val monthsCount = daysByMonth.keys.size

                val totalDays = sortedDates.size

                val avgDaysPerMonth = if (monthsCount > 0) {
                    totalDays.toDouble() / monthsCount
                } else {
                    0.0
                }

                binding.textAvgDaysPerMonth.text = String.format("%.1f дней", avgDaysPerMonth)
            } else {
                binding.textAvgDaysPerMonth.text = "—"
            }

            val nextPredicted = if (avgCycleLength > 0) {
                lastDate.plusDays(avgCycleLength)
            } else {
                lastDate.plusDays(28)
            }
            binding.textNextPredicted.text = nextPredicted.format(dateFormatter)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                .inflate(R.layout.item_calendar_day, parent, false)
            return DayViewHolder(view)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            holder.bind(days[position])
        }

        override fun getItemCount() = days.size

        inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textDay: TextView = itemView.findViewById(R.id.text_day)
            private val imageMenstruation: ImageView = itemView.findViewById(R.id.image_menstruation)

            fun bind(day: CalendarDay) {
                if (day.date != null) {
                    textDay.text = day.date.dayOfMonth.toString()
                    textDay.isEnabled = true

                    val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

                    if (day.isMenstruating) {
                        imageMenstruation.visibility = View.VISIBLE
                        textDay.setBackgroundResource(R.drawable.bg_menstruation_day)
                        textDay.setTextColor(Color.WHITE)
                    } else {
                        imageMenstruation.visibility = View.GONE
                        textDay.setBackgroundResource(R.drawable.bg_calendar_day)
                        textDay.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
                    }

                    itemView.setOnClickListener { onDayClick(day.date) }
                } else {
                    textDay.text = ""
                    textDay.isEnabled = false
                    imageMenstruation.visibility = View.INVISIBLE
                    itemView.setOnClickListener(null)
                }
            }
        }
    }

    data class CalendarDay(
        val date: LocalDate?,
        val isMenstruating: Boolean
    )
}