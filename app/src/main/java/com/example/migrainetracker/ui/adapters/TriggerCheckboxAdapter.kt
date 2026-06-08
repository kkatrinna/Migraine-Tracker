package com.example.migrainetracker.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.example.migrainetracker.R
import com.example.migrainetracker.data.entity.Trigger

class TriggerCheckboxAdapter(
    private val triggers: List<Trigger>,
    private val selectedIds: MutableSet<Int>
) : RecyclerView.Adapter<TriggerCheckboxAdapter.TriggerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TriggerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trigger_checkbox, parent, false)
        return TriggerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TriggerViewHolder, position: Int) {
        val trigger = triggers[position]
        holder.bind(trigger)
    }

    override fun getItemCount(): Int = triggers.size

    inner class TriggerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_trigger)

        fun bind(trigger: Trigger) {
            checkBox.text = trigger.name
            checkBox.isChecked = selectedIds.contains(trigger.id)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedIds.add(trigger.id)
                } else {
                    selectedIds.remove(trigger.id)
                }
            }
        }
    }
}