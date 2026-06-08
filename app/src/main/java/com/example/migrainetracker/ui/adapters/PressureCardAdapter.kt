package com.example.migrainetracker.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.entity.PressureRecord
import com.example.migrainetracker.databinding.CardPressureItemBinding
import java.time.format.DateTimeFormatter

class PressureCardAdapter(
    private val onItemClick: (PressureRecord) -> Unit,
    private val onItemDelete: (PressureRecord) -> Unit
) : RecyclerView.Adapter<PressureCardAdapter.PressureViewHolder>() {

    private var items = listOf<PressureRecord>()
    private lateinit var context: Context

    fun submitList(newItems: List<PressureRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PressureViewHolder {
        context = parent.context
        val binding = CardPressureItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PressureViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PressureViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    private fun getPressureStatus(systolic: Int, diastolic: Int): String {
        // Критически низкое давление
        if (systolic <= 90 && diastolic <= 60) {
            return "🔴 Критически низкое"
        }

        // Пониженное давление (гипотония)
        if ((systolic <= 100 && diastolic <= 60) || (systolic < 100)) {
            return "🟠 Пониженное (гипотония)"
        }

        // Определяем категорию по систолическому и диастолическому
        val systolicCategory = when {
            systolic < 120 -> 0
            systolic in 120..129 -> 1
            systolic in 130..139 -> 2
            systolic in 140..159 -> 3
            systolic in 160..179 -> 4
            systolic >= 180 -> 5
            else -> 0
        }

        val diastolicCategory = when {
            diastolic < 80 -> 0
            diastolic in 80..84 -> 1
            diastolic in 85..89 -> 2
            diastolic in 90..99 -> 3
            diastolic in 100..109 -> 4
            diastolic >= 110 -> 5
            else -> 0
        }

        val category = maxOf(systolicCategory, diastolicCategory)

        return when (category) {
            0 -> "🟢 Оптимальное"
            1 -> "🟢 Нормальное"
            2 -> "🟡 Высокое нормальное"
            3 -> "🟠 Гипертензия 1 степени"
            4 -> "🔴 Гипертензия 2 степени"
            5 -> "🔴 Гипертензия 3 степени"
            else -> "⚪ Не определено"
        }
    }

    private fun getPressureStatusColor(systolic: Int, diastolic: Int, context: Context): Int {
        // Критически низкое давление
        if (systolic <= 90 && diastolic <= 60) {
            return ContextCompat.getColor(context, android.R.color.holo_red_dark)
        }

        // Пониженное давление
        if ((systolic <= 100 && diastolic <= 60) || (systolic < 100)) {
            return ContextCompat.getColor(context, android.R.color.holo_orange_dark)
        }

        val systolicCategory = when {
            systolic < 120 -> 0
            systolic in 120..129 -> 1
            systolic in 130..139 -> 2
            systolic in 140..159 -> 3
            systolic in 160..179 -> 4
            systolic >= 180 -> 5
            else -> 0
        }

        val diastolicCategory = when {
            diastolic < 80 -> 0
            diastolic in 80..84 -> 1
            diastolic in 85..89 -> 2
            diastolic in 90..99 -> 3
            diastolic in 100..109 -> 4
            diastolic >= 110 -> 5
            else -> 0
        }

        val category = maxOf(systolicCategory, diastolicCategory)

        return when (category) {
            0 -> ContextCompat.getColor(context, android.R.color.holo_green_light)
            1 -> ContextCompat.getColor(context, android.R.color.holo_green_dark)
            2 -> ContextCompat.getColor(context, android.R.color.holo_orange_light)
            3 -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
            4 -> ContextCompat.getColor(context, android.R.color.holo_red_light)
            5 -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
            else -> ContextCompat.getColor(context, android.R.color.black)
        }
    }

    inner class PressureViewHolder(
        private val binding: CardPressureItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: PressureRecord) {
            val dateFormatter = DateTimeFormatter.ofPattern("dd LLLL yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            binding.textDate.text = record.date.format(dateFormatter)
            binding.textTime.text = record.time.format(timeFormatter)
            binding.textPressure.text = "${record.systolic}/${record.diastolic}"

            val status = getPressureStatus(record.systolic, record.diastolic)
            binding.textStatus.text = status
            binding.textStatus.setTextColor(getPressureStatusColor(record.systolic, record.diastolic, binding.root.context))

            binding.root.setOnClickListener {
                onItemClick(record)
            }

            binding.buttonDelete.setOnClickListener {
                onItemDelete(record)
            }
        }
    }
}