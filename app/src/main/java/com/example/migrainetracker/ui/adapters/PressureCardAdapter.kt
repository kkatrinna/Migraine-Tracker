package com.example.migrainetracker.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.data.entity.PressureRecord
import com.example.migrainetracker.databinding.CardPressureItemBinding
import java.time.format.DateTimeFormatter

class PressureCardAdapter(
    private val onItemClick: (PressureRecord) -> Unit,
    private val onItemDelete: (PressureRecord) -> Unit
) : RecyclerView.Adapter<PressureCardAdapter.PressureViewHolder>() {

    private var items = listOf<PressureRecord>()

    fun submitList(newItems: List<PressureRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PressureViewHolder {
        val binding = CardPressureItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PressureViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PressureViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class PressureViewHolder(
        private val binding: CardPressureItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: PressureRecord) {
            val dateFormatter = DateTimeFormatter.ofPattern("dd LLLL yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            binding.textDate.text = record.date.format(dateFormatter)
            binding.textTime.text = record.time.format(timeFormatter)
            binding.textPressure.text = "${record.systolic}/${record.diastolic}"
            binding.textStatus.text = getPressureStatus(record.systolic, record.diastolic)
            binding.textStatus.setTextColor(getPressureStatusColor(record.systolic, record.diastolic))

            binding.root.setOnClickListener {
                onItemClick(record)
            }

            binding.buttonDelete.setOnClickListener {
                onItemDelete(record)
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

        private fun getPressureStatusColor(systolic: Int, diastolic: Int): Int {
            val context = binding.root.context
            return when {
                systolic < 90 && diastolic < 60 -> context.getColor(android.R.color.holo_green_dark)
                systolic in 90..119 && diastolic in 60..79 -> context.getColor(android.R.color.holo_green_light)
                systolic in 120..129 && diastolic < 80 -> context.getColor(android.R.color.holo_orange_light)
                systolic in 130..139 || diastolic in 80..89 -> context.getColor(android.R.color.holo_orange_dark)
                systolic in 140..179 || diastolic in 90..119 -> context.getColor(android.R.color.holo_red_light)
                systolic >= 180 || diastolic >= 120 -> context.getColor(android.R.color.holo_red_dark)
                else -> context.getColor(android.R.color.black)
            }
        }
    }
}