package com.example.migrainetracker.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.migrainetracker.databinding.FragmentOnboardingFeaturesBinding

class FeaturesFragment : Fragment() {

    private var _binding: FragmentOnboardingFeaturesBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val PREFS_NAME = "features_prefs"
        private const val USER_PREFS_NAME = "user_prefs"
        private const val KEY_MIGRAINE = "feature_migraine"
        private const val KEY_PRESSURE = "feature_pressure"
        private const val KEY_MENSTRUATION = "feature_menstruation"
        private const val KEY_GENDER = "user_gender"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingFeaturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        UserInfoFragment.onGenderChanged = { gender ->
            updateMenstruationVisibility(gender)
        }

        updateMenstruationVisibility(getCurrentGender())
        loadSelectedFeatures()
    }

    private fun getCurrentGender(): String {
        val prefs = requireContext().getSharedPreferences(USER_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GENDER, "") ?: ""
    }

    private fun updateMenstruationVisibility(gender: String) {
        if (gender == "female") {
            binding.cardMenstruation.visibility = View.VISIBLE
        } else {
            binding.cardMenstruation.visibility = View.GONE
            binding.checkMenstruation.isChecked = false
        }
    }

    private fun loadSelectedFeatures() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.checkMigraine.isChecked = prefs.getBoolean(KEY_MIGRAINE, true)
        binding.checkPressure.isChecked = prefs.getBoolean(KEY_PRESSURE, true)

        if (binding.cardMenstruation.visibility == View.VISIBLE) {
            binding.checkMenstruation.isChecked = prefs.getBoolean(KEY_MENSTRUATION, true)
        }
    }

    fun saveSelectedFeatures() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_MIGRAINE, binding.checkMigraine.isChecked)
            putBoolean(KEY_PRESSURE, binding.checkPressure.isChecked)
            if (binding.cardMenstruation.visibility == View.VISIBLE) {
                putBoolean(KEY_MENSTRUATION, binding.checkMenstruation.isChecked)
            } else {
                putBoolean(KEY_MENSTRUATION, false)
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        UserInfoFragment.onGenderChanged = null
    }
}