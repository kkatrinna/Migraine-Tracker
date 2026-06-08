package com.example.migrainetracker.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.migrainetracker.R
import com.example.migrainetracker.databinding.ActivityMainBinding
import com.example.migrainetracker.ui.fragment.*
import com.example.migrainetracker.utils.ThemeManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isFemale = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedTheme = ThemeManager.getCurrentTheme(this)
        ThemeManager.applyTheme(savedTheme)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkUserGender()

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(MainFragment())
        }
    }

    private fun checkUserGender() {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val gender = prefs.getString("user_gender", null)
        isFemale = gender == "female"
    }

    private fun setupBottomNavigation() {
        val menu = binding.bottomNavigation.menu

        val menstruationItem = menu.findItem(R.id.nav_menstruation)
        menstruationItem?.isVisible = isFemale

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
                    if (isFemale) {
                        loadFragment(MenstruationCalendarFragment())
                    } else {
                        loadFragment(SettingsFragment())
                    }
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

    fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    public override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val newGender = prefs.getString("user_gender", null)
        val newIsFemale = newGender == "female"

        if (isFemale != newIsFemale) {
            isFemale = newIsFemale
            setupBottomNavigation()

            if (!isFemale) {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment is MenstruationCalendarFragment) {
                    loadFragment(SettingsFragment())
                    binding.bottomNavigation.selectedItemId = R.id.nav_settings
                }
            }
        }
    }
}