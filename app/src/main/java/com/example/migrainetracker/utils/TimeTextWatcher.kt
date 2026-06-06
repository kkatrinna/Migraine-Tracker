package com.example.migrainetracker.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class TimeTextWatcher(private val editText: EditText) : TextWatcher {

    private var isFormatting = false
    private var previousText = ""

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting) return

        val text = s?.toString() ?: return
        if (text == previousText) return

        isFormatting = true
        previousText = text

        val digits = text.replace(Regex("[^0-9]"), "")

        val formatted = when {
            digits.length <= 2 -> digits
            digits.length == 3 -> "${digits.substring(0, 2)}:${digits.substring(2)}"
            digits.length >= 4 -> "${digits.substring(0, 2)}:${digits.substring(2, 4)}"
            else -> digits
        }

        editText.setText(formatted)
        editText.setSelection(formatted.length)

        isFormatting = false
    }
}