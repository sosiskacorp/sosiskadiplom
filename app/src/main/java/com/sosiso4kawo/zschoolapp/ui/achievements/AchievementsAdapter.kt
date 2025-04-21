package com.sosiso4kawo.zschoolapp.ui.achievements

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.model.Achievement

class AchievementsAdapter : ListAdapter<Achievement, AchievementsAdapter.AchievementViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val ivCompleted: ImageView = itemView.findViewById(R.id.ivCompleted)
        private val overlay: View = itemView.findViewById(R.id.overlay)

        fun bind(achievement: Achievement) {
            tvTitle.text = achievement.title
            tvDescription.text = achievement.description

            if (achievement.achieved == true) {
                overlay.visibility = View.VISIBLE
                ivCompleted.visibility = View.VISIBLE
                tvTitle.alpha = 0.4f
                tvDescription.alpha = 0.4f
            } else {
                overlay.visibility = View.GONE
                ivCompleted.visibility = View.GONE
                tvTitle.alpha = 1f
                tvDescription.alpha = 1f
            }

            // Make square aspect ratio
            itemView.post {
                val width = itemView.width
                val params = itemView.layoutParams
                if (params.height != width) {
                    params.height = width
                    itemView.layoutParams = params
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
