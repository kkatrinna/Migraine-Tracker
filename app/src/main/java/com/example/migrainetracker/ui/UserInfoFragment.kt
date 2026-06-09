package com.example.migrainetracker.ui

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.migrainetracker.R
import com.example.migrainetracker.databinding.FragmentOnboardingUserInfoBinding
import java.util.*

class UserInfoFragment : Fragment() {

    private var _binding: FragmentOnboardingUserInfoBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val PREFS_NAME = "user_prefs"
        private const val KEY_NAME = "user_name"
        private const val KEY_BIRTH_DATE = "user_birth_date"
        private const val KEY_GENDER = "user_gender"
        private const val KEY_WEIGHT = "user_weight"
        private const val KEY_HEIGHT = "user_height"

        var onGenderChanged: ((String) -> Unit)? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingUserInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDatePicker()
        loadExistingData()

        binding.radioGroupGender.setOnCheckedChangeListener { _, checkedId ->
            val gender = when (checkedId) {
                R.id.radio_female -> "female"
                R.id.radio_male -> "male"
                else -> ""
            }
            saveUserData()
            onGenderChanged?.invoke(gender)
        }
    }

    private fun setupDatePicker() {
        binding.editBirthDate.setOnClickListener {
            showDatePickerDialog()
        }
        binding.editBirthDate.isFocusable = false
        binding.editBirthDate.isClickable = true
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = String.format("%02d.%02d.%04d", selectedDay, selectedMonth + 1, selectedYear)
            binding.editBirthDate.setText(formattedDate)
        }, year, month, day).show()
    }

    private fun loadExistingData() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.editName.setText(prefs.getString(KEY_NAME, ""))
        binding.editBirthDate.setText(prefs.getString(KEY_BIRTH_DATE, ""))
        binding.editWeight.setText(prefs.getString(KEY_WEIGHT, ""))
        binding.editHeight.setText(prefs.getString(KEY_HEIGHT, ""))

        val gender = prefs.getString(KEY_GENDER, "")
        when (gender) {
            "female" -> binding.radioGroupGender.check(R.id.radio_female)
            "male" -> binding.radioGroupGender.check(R.id.radio_male)
            else -> binding.radioGroupGender.clearCheck()
        }
    }

    fun saveUserData() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val name = binding.editName.text.toString().trim()
        val birthDate = binding.editBirthDate.text.toString().trim()
        val weight = binding.editWeight.text.toString().trim()
        val height = binding.editHeight.text.toString().trim()

        val gender = when (binding.radioGroupGender.checkedRadioButtonId) {
            R.id.radio_female -> "female"
            R.id.radio_male -> "male"
            else -> ""
        }

        prefs.edit {
            putString(KEY_NAME, name.ifEmpty { "Пользователь" })
            putString(KEY_BIRTH_DATE, birthDate)
            putString(KEY_GENDER, gender)
            putString(KEY_WEIGHT, weight)
            putString(KEY_HEIGHT, height)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}