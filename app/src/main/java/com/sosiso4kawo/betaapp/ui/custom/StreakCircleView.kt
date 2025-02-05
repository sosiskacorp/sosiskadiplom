package com.sosiso4kawo.betaapp.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sosiso4kawo.betaapp.R

class StreakCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.streak_circle_background)
        alpha = 128 // 50% transparency
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.streak_text_color)
        textAlign = Paint.Align.CENTER
    }

    private var streakCount = 0
    private val rect = RectF()

    fun setStreak(count: Int) {
        streakCount = count
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = 300 // Default size in pixels
        val width = resolveSize(size, widthMeasureSpec)
        val height = resolveSize(size, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 2f) * 0.8f

        // Draw circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // Draw streak number
        textPaint.textSize = radius * 0.5f
        canvas.drawText(streakCount.toString(), centerX, centerY - radius * 0.1f, textPaint)

        // Draw "дней подряд"
        textPaint.textSize = radius * 0.2f
        canvas.drawText(
            getDaysText(streakCount),
            centerX,
            centerY + radius * 0.3f,
            textPaint
        )
    }

    private fun getDaysText(count: Int): String {
        val lastDigit = count % 10
        val lastTwoDigits = count % 100

        return when {
            lastTwoDigits in 11..19 -> "дней подряд"
            lastDigit == 1 -> "день подряд"
            lastDigit in 2..4 -> "дня подряд"
            else -> "дней подряд"
        }
    }
}