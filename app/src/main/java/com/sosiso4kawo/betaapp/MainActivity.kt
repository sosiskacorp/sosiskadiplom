package com.sosiso4kawo.betaapp

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sosiso4kawo.betaapp.databinding.ActivityMainBinding
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val userRepository: UserRepository by inject()
    private val sessionManager: SessionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // При запуске приложения проверяем сессию через получение профиля
        lifecycleScope.launch {
            try {
                userRepository.getProfile().collect { result ->
                    // Если профиль успешно получен, настраиваем навигацию для авторизованного пользователя
                    setupNavigationAfterAuth()
                }
            } catch (e: Exception) {
                // Если профиль не получен (например, из-за истечения токена), настраиваем навигацию для экрана логина
                setupNavigationForLogin()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Централизованная проверка при возобновлении активности
        if (sessionManager.isAccessTokenExpired()) {
            sessionManager.clearSession()
            setupNavigationForLogin()
        }
    }

    private fun setupNavigationAfterAuth() {
        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)?.let { navHostFragment ->
            val navController = androidx.navigation.fragment.NavHostFragment.findNavController(navHostFragment)
            val navView: BottomNavigationView = binding.navView
            setupNavigation(navView, navController)
            lifecycleScope.launch {
                if (sessionManager.getUserData() == null) {
                    userRepository.getProfile().collect { /* Обработка результата */ }
                }
            }
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
                R.id.navigation_home,
                R.id.navigation_rating,
                R.id.navigation_achievements,
                R.id.navigation_profile
            )
        )
        navView.setupWithNavController(navController)

        // Скрываем нижнюю панель навигации на экранах авторизации
        navController.addOnDestinationChangedListener { _: NavController, destination: NavDestination, _: Bundle? ->
            when (destination.id) {
                R.id.navigation_login, R.id.navigation_register, R.id.navigation_course_detail, R.id.navigation_edit_profile, R.id.exerciseQuestionsFragment, R.id.exerciseDetailFragment -> navView.visibility = View.GONE
                else -> navView.visibility = View.VISIBLE
            }
        }
    }
}