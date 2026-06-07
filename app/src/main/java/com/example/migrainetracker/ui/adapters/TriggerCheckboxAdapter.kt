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
        holder.bind(triggers[position])
    }

    override fun getItemCount(): Int = triggers.size

    fun getSelectedTriggerIds(): Set<Int> = selectedIds.toSet()

    inner class TriggerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_trigger)
        private var currentTrigger: Trigger? = null

        init {
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                currentTrigger?.let { trigger ->
                    if (isChecked) {
                        selectedIds.add(trigger.id)
                    } else {
                        selectedIds.remove(trigger.id)
                    }
                }
            }
        }

        fun bind(trigger: Trigger) {
            currentTrigger = trigger
            checkBox.text = trigger.name
            checkBox.isChecked = selectedIds.contains(trigger.id)
        }
    }
}