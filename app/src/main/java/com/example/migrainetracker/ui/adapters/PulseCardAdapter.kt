package com.example.migrainetracker.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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

    private fun getPulseStatus(pulse: Int): String {
        return when {
            pulse < 50 -> "🔴 Критически низкий пульс"

            pulse in 50..59 -> "🟠 Пониженный пульс (брадикардия)"

            pulse in 60..90 -> "🟢 Нормальный пульс"

            pulse in 91..135 -> "🟡 Тахикардия 1 степени (умеренная)"

            pulse in 136..185 -> "🔴 Тахикардия 2 степени (выраженная)"

            else -> "🔴 Критическая тахикардия"
        }
    }

    private fun getPulseStatusColor(pulse: Int, context: android.content.Context): Int {
        return when {
            pulse < 50 -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
            pulse in 50..59 -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
            pulse in 60..90 -> ContextCompat.getColor(context, android.R.color.holo_green_light)
            pulse in 91..135 -> ContextCompat.getColor(context, android.R.color.holo_orange_light)
            pulse in 136..185 -> ContextCompat.getColor(context, android.R.color.holo_red_light)
            else -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
        }
    }

    inner class PulseViewHolder(
        private val binding: CardPulseItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: PulseRecord) {
            val dateFormatter = DateTimeFormatter.ofPattern("dd LLLL yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            binding.textDate.text = record.date.format(dateFormatter)
            binding.textTime.text = record.time.format(timeFormatter)
            binding.textPulse.text = record.pulse.toString()

            val status = getPulseStatus(record.pulse)
            binding.textStatus.text = status
            binding.textStatus.setTextColor(getPulseStatusColor(record.pulse, binding.root.context))

            binding.root.setOnClickListener {
                onItemClick(record)
            }

            binding.buttonDelete.setOnClickListener {
                onItemDelete(record)
            }
        }
    }
}