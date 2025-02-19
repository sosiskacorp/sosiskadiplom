package com.sosiso4kawo.betaapp

import android.os.Bundle
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.lifecycleScope
import com.sosiso4kawo.betaapp.databinding.ActivityMainBinding
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val userRepository: UserRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check session validity before setting up navigation
        lifecycleScope.launch {
            try {
                userRepository.getProfile().collect { result ->
                    // If profile fetch succeeds, proceed with navigation setup
                    setupNavigationAfterAuth()
                }
            } catch (e: Exception) {
                // If profile fetch fails (expired/missing tokens), setup navigation with login
                setupNavigationForLogin()
            }
        }
    }

    private fun setupNavigationAfterAuth() {
        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)?.let { navHostFragment ->
            val navController = androidx.navigation.fragment.NavHostFragment.findNavController(navHostFragment)
            val navView: BottomNavigationView = binding.navView
            setupNavigation(navView, navController)
        }
    }

    private fun setupNavigationForLogin() {
        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)?.let { navHostFragment ->
            val navController = androidx.navigation.fragment.NavHostFragment.findNavController(navHostFragment)
            val navView: BottomNavigationView = binding.navView
            navView.visibility = View.GONE
            navController.navigate(R.id.navigation_login)
        }
    }

    private fun setupNavigation(navView: BottomNavigationView, navController: NavController) {
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_rating, R.id.navigation_achievements, R.id.navigation_profile
            )
        )
        navView.setupWithNavController(navController)

        // Hide bottom navigation on auth screens
        navController.addOnDestinationChangedListener { _: NavController, destination: NavDestination, _: Bundle? ->
            when (destination.id) {
                R.id.navigation_login, R.id.navigation_register -> {
                    navView.visibility = View.GONE
                }
                else -> {
                    navView.visibility = View.VISIBLE
                }
            }
        }
    }
}