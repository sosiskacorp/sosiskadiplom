package com.sosiso4kawo.betaapp.ui.custom

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.ViewCustomHeaderBinding

class CustomHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewCustomHeaderBinding
    private var onNotificationClickListener: (() -> Unit)? = null

    init {
        binding = ViewCustomHeaderBinding.inflate(LayoutInflater.from(context), this, true)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setupNotificationButton()
        // Initially hide progress bar and notification button
        binding.progressBar.visibility = GONE
        binding.notificationButton.visibility = GONE
        binding.headerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun setupNotificationButton() {
        binding.notificationButton.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setOnClickListener {
                onNotificationClickListener?.invoke()
            }
        }
    }

    fun setTitle(title: String?) {
        binding.headerTitle.text = title
    }

    fun setProgress(progress: Int) {
        binding.progressBar.progress = progress
    }

    fun setOnNotificationClickListener(listener: () -> Unit) {
        onNotificationClickListener = listener
    }

    @SuppressLint("ResourceType")
    override fun setBackgroundColor(color: Int) {
        binding.headerContainer.setBackgroundColor(
            ContextCompat.getColor(context, color)
        )
    }
}