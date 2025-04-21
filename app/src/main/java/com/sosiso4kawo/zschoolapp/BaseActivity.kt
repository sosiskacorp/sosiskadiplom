package com.sosiso4kawo.zschoolapp

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(newBase)
        val useSystemFontSize = sharedPreferences.getBoolean("system_font_size", true)
        // Получаем сохранённый размер шрифта (например, 16sp по умолчанию)
        val selectedFontSize = sharedPreferences.getFloat("font_size", 16f)
        // Если выбран системный размер, оставляем масштаб по умолчанию
        val scaleFactor = if (useSystemFontSize) 1.0f else (selectedFontSize / 16f)
        val configuration = newBase.resources.configuration
        configuration.fontScale = scaleFactor
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }
}
