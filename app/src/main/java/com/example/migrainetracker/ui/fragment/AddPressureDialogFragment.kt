package com.example.migrainetracker.ui.fragment

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.PressureRecord
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class AddPressureDialogFragment : DialogFragment() {

    private var onSaveListener: (() -> Unit)? = null

    fun setOnSaveListener(listener: () -> Unit) {
        onSaveListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_add_pressure, null)

        val editSystolic = view.findViewById<EditText>(R.id.edit_systolic)
        val editDiastolic = view.findViewById<EditText>(R.id.edit_diastolic)

        builder.setTitle("Добавить запись о давлении")
            .setView(view)
            .setPositiveButton("Сохранить") { _, _ ->
                val systolic = editSystolic.text.toString().toIntOrNull()
                val diastolic = editDiastolic.text.toString().toIntOrNull()

                if (systolic != null && diastolic != null && systolic in 30..250 && diastolic in 20..200) {
                    val record = PressureRecord(
                        date = LocalDate.now(),
                        time = LocalTime.now(),
                        systolic = systolic,
                        diastolic = diastolic
                    )

                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(requireContext())
                        db.pressureRecordDao().insert(record)
                        onSaveListener?.invoke()
                        dismiss()
                    }
                } else {
                    Toast.makeText(requireContext(), "Введите корректные значения давления", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)

        return builder.create()
    }
}