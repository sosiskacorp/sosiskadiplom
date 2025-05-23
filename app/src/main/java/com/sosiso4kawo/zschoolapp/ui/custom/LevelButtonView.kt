package com.sosiso4kawo.zschoolapp.ui.custom

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.sosiso4kawo.zschoolapp.R

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
    }

    enum class LevelStatus {
        COMPLETED,
        AVAILABLE,
        LOCKED
    }

    @SuppressLint("SetTextI18n")
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
                isEnabled = false
                alpha = 0.5f
            }
        }
    }
}
