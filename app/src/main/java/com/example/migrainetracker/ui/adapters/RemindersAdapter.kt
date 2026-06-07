package com.example.migrainetracker.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.entity.MedicineReminder
import java.time.format.DateTimeFormatter

class RemindersAdapter(
    private var reminders: List<MedicineReminder>,
    private val onDeleteClick: (MedicineReminder) -> Unit,
    private val onToggleClick: (MedicineReminder, Boolean) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(reminders[position])
    }

    override fun getItemCount(): Int = reminders.size

    fun updateList(newReminders: List<MedicineReminder>) {
        reminders = newReminders
        notifyDataSetChanged()
    }

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMedicineName: TextView = itemView.findViewById(R.id.text_medicine_name)
        private val textReminderTime: TextView = itemView.findViewById(R.id.text_reminder_time)
        private val textRepeat: TextView = itemView.findViewById(R.id.text_repeat)
        private val btnDelete: Button = itemView.findViewById(R.id.btn_delete_reminder)
        private val btnToggle: Button = itemView.findViewById(R.id.btn_toggle_reminder)

        fun bind(reminder: MedicineReminder) {
            textMedicineName.text = reminder.medicineName
            textReminderTime.text = reminder.reminderTime.format(timeFormatter)

            textRepeat.text = when {
                reminder.repeatInterval == 0 -> "Однократное"
                reminder.repeatInterval == 24 -> "Ежедневно"
                else -> "Каждые ${reminder.repeatInterval} часов"
            }

            btnToggle.text = if (reminder.isEnabled) "Выключить" else "Включить"
            btnToggle.setBackgroundColor(
                if (reminder.isEnabled)
                    itemView.context.getColor(android.R.color.holo_orange_dark)
                else
                    itemView.context.getColor(android.R.color.holo_green_dark)
            )

            btnToggle.setOnClickListener {
                onToggleClick(reminder, !reminder.isEnabled)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(reminder)
            }
        }
    }
}