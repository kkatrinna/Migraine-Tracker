package com.example.migrainetracker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.migrainetracker.R
import com.example.migrainetracker.databinding.ActivityMainBinding
import com.example.migrainetracker.ui.fragment.*
import com.example.migrainetracker.utils.ThemeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedTheme = ThemeManager.getCurrentTheme(this)
        ThemeManager.applyTheme(savedTheme)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(MainFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_main -> {
                    loadFragment(MainFragment())
                    true
                }
                R.id.nav_blood_pressure -> {
                    loadFragment(BloodPressureFragment())
                    true
                }
                R.id.nav_menstruation -> {
                    loadFragment(MenstruationCalendarFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}