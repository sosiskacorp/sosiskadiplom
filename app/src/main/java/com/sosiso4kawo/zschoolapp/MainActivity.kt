package com.sosiso4kawo.zschoolapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sosiso4kawo.zschoolapp.databinding.ActivityMainBinding
import com.sosiso4kawo.zschoolapp.data.repository.UserRepository
import com.sosiso4kawo.zschoolapp.notification.ReminderWorker
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private val userRepository: UserRepository by inject()
    private val sessionManager: SessionManager by inject()

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Разрешение на уведомления получено")
                scheduleRepeatingReminderNotification()
            } else {
                Log.d(TAG, "Разрешение на уведомления не получено")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotificationPermission()

        intent?.getIntExtra("NAVIGATION_TARGET", -1)?.takeIf { it != -1 }?.let { target ->
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
                ?.findNavController()
                ?.navigate(target)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!shouldRequestNotificationPermission()) {
            scheduleRepeatingReminderNotification()
        } else {
            Log.d(TAG, "Ожидаем разрешения для планирования периодической задачи")
        }

        lifecycleScope.launch {
            try {
                userRepository.getProfile().collect { result ->
                    setupNavigationAfterAuth()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения профиля: ${e.message}")
                setupNavigationForLogin()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessionManager.isAccessTokenExpired()) {
            sessionManager.clearSession()
        }
    }

    private fun setupNavigationAfterAuth() {
        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)?.let { navHostFragment ->
            val navController = androidx.navigation.fragment.NavHostFragment.findNavController(navHostFragment)
            val navView: BottomNavigationView = binding.navView
            setupNavigation(navView, navController)

            lifecycleScope.launch {
                if (sessionManager.getUserData() == null) {
                    userRepository.getProfile().collect { /* Кеширование данных */ }
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
        AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_rating,
                R.id.navigation_achievements,
                R.id.navigation_profile
            )
        )
        navView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _: NavController, destination: NavDestination, _: Bundle? ->
            when (destination.id) {
                R.id.navigation_login,
                R.id.navigation_register,
                R.id.navigation_course_detail,
                R.id.navigation_edit_profile,
                R.id.exerciseQuestionsFragment,
                R.id.exerciseDetailFragment,
                R.id.lessonContentFragment,
                R.id.lessonCompletionFragment -> navView.visibility = View.GONE
                else -> navView.visibility = View.VISIBLE
            }
        }
    }

    private fun scheduleRepeatingReminderNotification() {
        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            12, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "reminder_notification_repeating",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(TAG, "Запланирована периодическая задача каждые 12 часов")
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    private fun checkNotificationPermission() {
        if (shouldRequestNotificationPermission()) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}