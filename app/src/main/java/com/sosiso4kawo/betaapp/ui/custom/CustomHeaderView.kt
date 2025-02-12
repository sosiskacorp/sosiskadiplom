package com.sosiso4kawo.betaapp.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.ColorRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.ViewCustomHeaderBinding

class CustomHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewCustomHeaderBinding =
        ViewCustomHeaderBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        // Настройка аппаратного ускорения для отрисовки
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        // Изначально скрываем индикатор прогресса и кнопку уведомлений
        binding.progressBar.visibility = GONE
        binding.notificationButton.visibility = GONE
        binding.headerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun setTitle(title: String?) {
        binding.headerTitle.text = title
    }

    fun setProgress(progress: Int) {
        binding.progressBar.progress = progress
    }

    fun setOnNotificationClickListener(listener: () -> Unit) {
        binding.notificationButton.setOnClickListener {
            listener.invoke()
        }
    }

    /**
     * Устанавливает фон заголовка, используя идентификатор ресурса цвета.
     */
    fun setHeaderBackgroundColor(@ColorRes colorResId: Int) {
        binding.headerContainer.setBackgroundColor(ContextCompat.getColor(context, colorResId))
    }
}
