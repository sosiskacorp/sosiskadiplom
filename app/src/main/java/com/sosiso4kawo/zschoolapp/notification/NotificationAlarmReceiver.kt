package com.sosiso4kawo.zschoolapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sosiso4kawo.zschoolapp.MainActivity
import com.sosiso4kawo.zschoolapp.R

import android.app.AlarmManager
import android.os.Build

class NotificationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "reminder_channel_alarm"
        const val NOTIFICATION_ID = 1002 // Используем другой ID, чтобы не конфликтовать с Worker
        const val TAG = "NotificationAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive() вызван")
        createNotificationChannel(context)
        showNotification(context)
        scheduleNextAlarm(context) // Добавляем планирование следующего будильника
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminder Notifications (Alarm)", // Обновленное имя для ясности
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(100, 200, 100, 200)
            enableLights(true)
            lightColor = Color.BLUE
            description = "Channel for reminder notifications via AlarmManager"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created or already exists.")
    }

    private fun showNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (title, message) = when ((1..3).random()) {
            1 -> "Время учиться (Alarm)" to "Пора проверить новые задания! (Alarm)"
            2 -> "Напоминание (Alarm)" to "Не забывайте о своих целях! Продолжайте обучение. (Alarm)"
            else -> "Обнови знания (Alarm)" to "Минутное напоминание пришло. Проверь, как идёт обучение! (Alarm)"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // .setFullScreenIntent(pendingIntent, true) // Full screen intent может быть навязчивым для периодических напоминаний
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification shown.")
    }

    private fun scheduleNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0, // Используем тот же requestCode, чтобы перезаписывать
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Интервал для следующего уведомления (например, 12 часов, для теста можно меньше)
        val intervalMillis = 12 * 60 * 60 * 1000L // 12 часов
        // val intervalMillis = 60 * 1000L // 1 минута для тестирования
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.d(TAG, "Next exact alarm scheduled with setExactAndAllowWhileIdle at $triggerAtMillis")
                } else {
                    // Альтернатива, если нет разрешения SCHEDULE_EXACT_ALARM
                    // Можно использовать setWindow или setExact с менее строгими гарантиями
                    // Или запросить разрешение у пользователя
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted. Using setAndAllowWhileIdle for next alarm.")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d(TAG, "Next exact alarm scheduled with setExactAndAllowWhileIdle for API < S at $triggerAtMillis")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling next alarm: ${e.message}")
            // Обработка случая, когда нет разрешения SCHEDULE_EXACT_ALARM на API 31+
            // Можно попробовать запланировать неточный будильник или уведомить пользователя
            // Для простоты, пока просто логируем
        }
    }
}