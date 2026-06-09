package com.example.migrainetracker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.viewpager2.widget.ViewPager2
import com.example.migrainetracker.R
import com.example.migrainetracker.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var currentPage = 0

    companion object {
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        fun isOnboardingCompleted(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }

        fun setOnboardingCompleted(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, true) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.setBackgroundResource(R.drawable.bg_onboarding_gradient)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        val fragments = listOf(
            WelcomeFragment(),
            UserInfoFragment(),
            FeaturesFragment()
        )

        val adapter = OnboardingPagerAdapter(this, fragments)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage = position
                updateButtons()
            }
        })
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            if (currentPage > 0) {
                binding.viewPager.currentItem = currentPage - 1
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentPage < 2) {
                binding.viewPager.currentItem = currentPage + 1
            } else {
                completeOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun updateButtons() {
        if (currentPage == 2) {
            binding.btnNext.text = "Завершить"
        } else {
            binding.btnNext.text = "Далее"
        }

        if (currentPage == 2) {
            binding.btnBack.visibility = View.VISIBLE
        } else {
            binding.btnBack.visibility = View.INVISIBLE
        }

        binding.btnSkip.visibility = View.VISIBLE
    }

    private fun completeOnboarding() {
        val userInfoFragment = supportFragmentManager.findFragmentByTag("f1") as? UserInfoFragment
        userInfoFragment?.saveUserData()

        val featuresFragment = supportFragmentManager.findFragmentByTag("f2") as? FeaturesFragment
        featuresFragment?.saveSelectedFeatures()

        setOnboardingCompleted(this)

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}