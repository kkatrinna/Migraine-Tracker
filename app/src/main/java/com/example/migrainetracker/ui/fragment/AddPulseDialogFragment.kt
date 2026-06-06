package com.example.migrainetracker.ui.fragment

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.PulseRecord
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class AddPulseDialogFragment : DialogFragment() {

    private var onSaveListener: (() -> Unit)? = null

    fun setOnSaveListener(listener: () -> Unit) {
        onSaveListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_add_pulse, null)

        val editPulse = view.findViewById<EditText>(R.id.edit_pulse)

        builder.setTitle("Добавить запись о пульсе")
            .setView(view)
            .setPositiveButton("Сохранить") { _, _ ->
                val pulse = editPulse.text.toString().toIntOrNull()

                if (pulse != null && pulse in 30..200) {
                    val record = PulseRecord(
                        date = LocalDate.now(),
                        time = LocalTime.now(),
                        pulse = pulse
                    )

                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(requireContext())
                        db.pulseRecordDao().insert(record)
                        onSaveListener?.invoke()
                        dismiss()
                    }
                } else {
                    Toast.makeText(requireContext(), "Введите корректное значение пульса (30-200)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)

        return builder.create()
    }
}