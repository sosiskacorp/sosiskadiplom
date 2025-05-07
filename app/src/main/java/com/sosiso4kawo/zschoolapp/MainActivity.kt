package com.sosiso4kawo.zschoolapp

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sosiso4kawo.zschoolapp.data.repository.UserRepository
import com.sosiso4kawo.zschoolapp.databinding.ActivityMainBinding
import com.sosiso4kawo.zschoolapp.notification.NotificationAlarmReceiver
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import android.content.Intent as AlarmIntent

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
                scheduleAlarm()
            } else {
                Log.d(TAG, "Разрешение на уведомления не получено")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannelIfNeeded() // Создаем канал при старте активити
        checkNotificationPermission()

        intent?.getIntExtra("NAVIGATION_TARGET", -1)?.takeIf { it != -1 }?.let { target ->
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
                ?.findNavController()
                ?.navigate(target)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!shouldRequestNotificationPermission()) {
            scheduleAlarm()
        } else {
            Log.d(TAG, "Ожидаем разрешения для планирования AlarmManager")
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

    private fun scheduleAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = AlarmIntent(this, NotificationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0, // requestCode
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Для Android 12 (API 31) и выше, если требуется точное время, нужно разрешение SCHEDULE_EXACT_ALARM
        // Для повторяющихся задач setInexactRepeating или setRepeating предпочтительнее для экономии батареи
        // Однако, setRepeating устарел с API 31, и setInexactRepeating может иметь большие задержки.
        // Для более точных повторяющихся задач, лучше планировать следующий будильник из BroadcastReceiver.
        // Здесь для простоты используем setRepeating, но для production стоит рассмотреть альтернативы.

        // Устанавливаем первый будильник. Последующие будут планироваться из NotificationAlarmReceiver.
        val intervalMillis = 12 * 60 * 60 * 1000L // 12 часов
        // val intervalMillis = 60 * 1000L // 1 минута для тестирования
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.d(TAG, "Initial exact alarm scheduled with setExactAndAllowWhileIdle at $triggerAtMillis")
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted. Using setAndAllowWhileIdle for initial alarm.")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d(TAG, "Initial exact alarm scheduled with setExactAndAllowWhileIdle for API < S at $triggerAtMillis")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling initial alarm: ${e.message}")
            // Можно попробовать запланировать неточный будильник или уведомить пользователя
        }
    }

    // Метод для создания канала уведомлений, если он еще не создан
    // (Перенесено из ReminderWorker и адаптировано, чтобы быть вызванным один раз)
    // Хотя NotificationAlarmReceiver также создает канал, дублирование не страшно, 
    // так как createNotificationChannel ничего не делает, если канал уже существует.
    // Это гарантирует, что канал будет создан при запуске приложения.
    private fun createNotificationChannelIfNeeded() {
        val channelId = NotificationAlarmReceiver.CHANNEL_ID
        val existingChannel = getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
        if (existingChannel == null) {
            val name = "Reminder Notifications (Alarm)"
            val descriptionText = "Channel for reminder notifications via AlarmManager"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 100, 200)
                enableLights(true)
                lightColor = Color.BLUE
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel '${channelId}' created in MainActivity.")
        } else {
            Log.d(TAG, "Notification channel '${channelId}' already exists.")
        }
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