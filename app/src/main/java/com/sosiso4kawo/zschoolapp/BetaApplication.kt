package com.sosiso4kawo.zschoolapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.sosiso4kawo.zschoolapp.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class BetaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Читаем сохранённое значение для тёмной темы
        val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val darkThemeEnabled = sharedPreferences.getBoolean("dark_theme_enabled", false)

        // Применяем выбранную тему до создания активностей
        if (darkThemeEnabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Инициализация Koin
        startKoin {
            androidLogger()
            androidContext(this@BetaApplication)
            modules(appModule)
        }
    }
}
