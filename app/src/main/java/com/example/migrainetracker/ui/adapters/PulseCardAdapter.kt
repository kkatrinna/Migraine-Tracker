package com.example.migrainetracker.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.data.entity.PulseRecord
import com.example.migrainetracker.databinding.CardPulseItemBinding
import java.time.format.DateTimeFormatter

class PulseCardAdapter(
    private val onItemClick: (PulseRecord) -> Unit,
    private val onItemDelete: (PulseRecord) -> Unit
) : RecyclerView.Adapter<PulseCardAdapter.PulseViewHolder>() {

    private var items = listOf<PulseRecord>()

    fun submitList(newItems: List<PulseRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PulseViewHolder {
        val binding = CardPulseItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PulseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PulseViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class PulseViewHolder(
        private val binding: CardPulseItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: PulseRecord) {
            val dateFormatter = DateTimeFormatter.ofPattern("dd LLLL yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            binding.textDate.text = record.date.format(dateFormatter)
            binding.textTime.text = record.time.format(timeFormatter)
            binding.textPulse.text = record.pulse.toString()
            binding.textStatus.text = getPulseStatus(record.pulse)
            binding.textStatus.setTextColor(getPulseStatusColor(record.pulse))

            binding.root.setOnClickListener {
                onItemClick(record)
            }

            binding.buttonDelete.setOnClickListener {
                onItemDelete(record)
            }
        }

        private fun getPulseStatus(pulse: Int): String {
            return when (pulse) {
                in 0..40 -> "Очень низкий пульс"
                in 41..59 -> "Низкий пульс"
                in 60..79 -> "Нормальный пульс"
                in 80..99 -> "Учащенный пульс"
                in 100..119 -> "Тахикардия легкая"
                in 120..139 -> "Тахикардия средняя"
                else -> "Тахикардия тяжелая"
            }
        }

        private fun getPulseStatusColor(pulse: Int): Int {
            val context = binding.root.context
            return when (pulse) {
                in 0..40 -> context.getColor(android.R.color.holo_red_dark)
                in 41..59 -> context.getColor(android.R.color.holo_orange_dark)
                in 60..79 -> context.getColor(android.R.color.holo_green_light)
                in 80..99 -> context.getColor(android.R.color.holo_orange_light)
                in 100..119 -> context.getColor(android.R.color.holo_orange_dark)
                in 120..139 -> context.getColor(android.R.color.holo_red_light)
                else -> context.getColor(android.R.color.holo_red_dark)
            }
        }
    }
}