package com.example.migrainetracker.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

        if (!OnboardingActivity.isOnboardingCompleted(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkUserGender()
        setupBottomNavigation()
        checkAllPermissions()

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

    private fun checkAllPermissions() {
        // Для Android 13+ уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        // Для Android 12+ точные будильники
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                    startActivity(it)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            100 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Разрешение на уведомления получено
                } else {
                    // Разрешение не получено - напоминания будут работать, но без уведомлений
                }
            }
        }
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