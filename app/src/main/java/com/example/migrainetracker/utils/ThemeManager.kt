package com.example.migrainetracker.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_SYSTEM = 2

    fun applyTheme(themeMode: Int) {
        when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun getCurrentTheme(context: Context): Int {
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPref.getInt("theme_mode", THEME_SYSTEM)
    }

    fun saveTheme(context: Context, themeMode: Int) {
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPref.edit().putInt("theme_mode", themeMode).apply()
        applyTheme(themeMode)
    }
}