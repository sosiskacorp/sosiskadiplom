package com.sosiso4kawo.betaapp.ui.achievements

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.Achievement

class AchievementsAdapter : ListAdapter<Achievement, AchievementsAdapter.AchievementViewHolder>(
    DiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val ivCompleted: ImageView = itemView.findViewById(R.id.ivCompleted)

        fun bind(achievement: Achievement) {
            tvTitle.text = achievement.title
            tvDescription.text = achievement.description

            if (achievement.achieved == true) {
                ivCompleted.visibility = View.VISIBLE
                itemView.setBackgroundColor(Color.parseColor("#F97316"))
            } else {
                ivCompleted.visibility = View.GONE
                itemView.setBackgroundColor(Color.WHITE)
            }

            // Устанавливаем квадратную форму: делаем высоту равной ширине
            itemView.post {
                val width = itemView.width
                if (itemView.layoutParams.height != width) {
                    itemView.layoutParams.height = width
                    itemView.requestLayout()
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Achievement>() {
        override fun areItemsTheSame(oldItem: Achievement, newItem: Achievement): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Achievement, newItem: Achievement): Boolean =
            oldItem == newItem
    }
}
