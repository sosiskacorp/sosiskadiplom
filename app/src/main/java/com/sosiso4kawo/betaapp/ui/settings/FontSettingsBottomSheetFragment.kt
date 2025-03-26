package com.sosiso4kawo.betaapp.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.sosiso4kawo.betaapp.R

class FontSettingsBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var previewText: TextView
    private lateinit var systemFontSizeSwitch: SwitchCompat
    private lateinit var fontSizeSlider: Slider
    private lateinit var cancelButton: Button
    private lateinit var applyButton: Button

    // Значение выбранного размера шрифта (sp)
    private var selectedFontSize: Float = 16f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_font_settings, container, false)
        previewText = view.findViewById(R.id.previewText)
        systemFontSizeSwitch = view.findViewById(R.id.systemFontSizeSwitch)
        fontSizeSlider = view.findViewById(R.id.fontSizeSlider)
        cancelButton = view.findViewById(R.id.cancelButton)
        applyButton = view.findViewById(R.id.applyButton)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        // Загружаем сохранённые значения (если они есть)
        selectedFontSize = sharedPreferences.getFloat("font_size", 16f)
        val systemFontEnabled = sharedPreferences.getBoolean("system_font_size", true)

        // Инициализируем элементы
        previewText.textSize = selectedFontSize
        fontSizeSlider.value = selectedFontSize
        systemFontSizeSwitch.isChecked = systemFontEnabled
        fontSizeSlider.isEnabled = !systemFontEnabled

        systemFontSizeSwitch.setOnCheckedChangeListener { _, isChecked ->
            fontSizeSlider.isEnabled = !isChecked
        }

        fontSizeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                previewText.textSize = value
                selectedFontSize = value
            }
        }

        cancelButton.setOnClickListener { dismiss() }

        applyButton.setOnClickListener {
            sharedPreferences.edit()
                .putFloat("font_size", selectedFontSize)
                .putBoolean("system_font_size", systemFontSizeSwitch.isChecked)
                .apply()
            // Пересоздаем активность, чтобы изменения применились мгновенно
            requireActivity().recreate()
            dismiss()
        }

        return view
    }
}
