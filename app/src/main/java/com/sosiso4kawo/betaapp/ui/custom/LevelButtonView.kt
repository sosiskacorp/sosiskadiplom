package com.sosiso4kawo.betaapp.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import com.google.android.material.card.MaterialCardView
import android.view.ViewGroup.MarginLayoutParams
import com.sosiso4kawo.betaapp.R

class LevelButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val levelNumberText: TextView
    private val statusIcon: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_level_button, this, true)
        levelNumberText = findViewById(R.id.level_number)
        statusIcon = findViewById(R.id.status_icon)

        radius = resources.getDimensionPixelSize(R.dimen.level_button_radius).toFloat()
        cardElevation = resources.getDimensionPixelSize(R.dimen.level_button_elevation).toFloat()

        // Вычисляем размер кнопки на основе размера экрана
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val horizontalMargins = 2 * resources.getDimensionPixelSize(R.dimen.level_button_margin)
        val screenPadding = 2 * (32 * displayMetrics.density).toInt() // 32dp * 2 (левый и правый отступы)

        // Размер кнопки = (ширина экрана - отступы) / 3 (количество колонок)
        val buttonSize = (screenWidth - screenPadding - horizontalMargins) / 3
        val margin = resources.getDimensionPixelSize(R.dimen.level_button_margin)

        val params = MarginLayoutParams(buttonSize, buttonSize)
        params.setMargins(margin, margin, margin, margin)
        layoutParams = params
    }

    enum class LevelStatus {
        COMPLETED,
        AVAILABLE,
        LOCKED
    }

    fun setLevel(number: Int, status: LevelStatus) {
        levelNumberText.text = number.toString()

        when (status) {
            LevelStatus.COMPLETED -> {
                statusIcon.setImageResource(R.drawable.ic_check)
                statusIcon.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                isEnabled = true
                alpha = 1.0f
            }
            LevelStatus.AVAILABLE -> {
                statusIcon.setImageResource(R.drawable.ic_star)
                statusIcon.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_orange_light))
                isEnabled = true
                alpha = 1.0f
            }
            LevelStatus.LOCKED -> {
                statusIcon.setImageResource(R.drawable.ic_lock)
                statusIcon.setColorFilter(ContextCompat.getColor(context, android.R.color.darker_gray))
                isEnabled = true
                alpha = 0.5f
            }
        }
    }
}