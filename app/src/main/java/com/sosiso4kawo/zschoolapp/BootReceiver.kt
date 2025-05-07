package com.sosiso4kawo.zschoolapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent as AlarmIntent // Alias to avoid conflict
import android.os.Build
import android.util.Log
import com.sosiso4kawo.zschoolapp.notification.NotificationAlarmReceiver

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed, rescheduling alarm.")
            scheduleAlarm(context)
        }
    }

    private fun scheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = AlarmIntent(context, NotificationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0, // requestCode - должен быть таким же, как при планировании в MainActivity, чтобы cancel работал корректно
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Устанавливаем первый будильник после перезагрузки. Последующие будут планироваться из NotificationAlarmReceiver.
        val intervalMillis = 12 * 60 * 60 * 1000L // 12 часов
        // val intervalMillis = 60 * 1000L // 1 минута для тестирования
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis // Планируем первый запуск

        // Отменяем предыдущий будильник с тем же PendingIntent, если он был, чтобы избежать дублирования
        alarmManager.cancel(pendingIntent)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.d("BootReceiver", "Initial exact alarm scheduled with setExactAndAllowWhileIdle at $triggerAtMillis after boot.")
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.w("BootReceiver", "SCHEDULE_EXACT_ALARM permission not granted. Using setAndAllowWhileIdle for initial alarm after boot.")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d("BootReceiver", "Initial exact alarm scheduled with setExactAndAllowWhileIdle for API < S at $triggerAtMillis after boot.")
            }
        } catch (e: SecurityException) {
            Log.e("BootReceiver", "SecurityException while scheduling alarm after boot: ${e.message}")
        }
    }
}