package com.sosiso4kawo.zschoolapp.ui.rating

import android.annotation.SuppressLint
// import android.graphics.Color // Больше не нужен прямой импорт Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat // Для получения цвета из ресурсов
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.model.LeaderboardUser

class UserAdapter : ListAdapter<LeaderboardUser, UserAdapter.UserViewHolder>(UserDiffCallback()) {

    var onUserClick: ((LeaderboardUser) -> Unit)? = null
    private var currentUserId: String? = null

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView
        private val avatarImageView: ImageView = itemView.findViewById(R.id.avatarImageView)
        private val loginTextView: TextView = itemView.findViewById(R.id.loginTextView)
        private val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)
        private val tvRank: TextView = itemView.findViewById(R.id.tvRank)

        @SuppressLint("SetTextI18n")
        fun bind(user: LeaderboardUser, listPosition: Int) {
            tvRank.text = (listPosition + 1 + 3).toString()
            tvPoints.text = itemView.context.getString(R.string.points_format, user.total_points ?: 0)

            if (user.user_uuid == currentUserId) {
                // Используем цвет из ресурсов
                container.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.currentUserHighlight))
                loginTextView.text = itemView.context.getString(R.string.text_you)
            } else {
                container.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.transparent)) // Явное указание прозрачного фона
                loginTextView.text = user.login ?: user.name ?: itemView.context.getString(R.string.text_anonymous)
            }

            Glide.with(itemView.context)
                .load(user.avatar)
                .circleCrop()
                .placeholder(R.drawable.placeholder_avatar)
                .error(R.drawable.error_avatar)
                .into(avatarImageView)

            container.setOnClickListener { onUserClick?.invoke(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setCurrentUserId(userId: String) {
        currentUserId = userId
        notifyDataSetChanged()
    }

    class UserDiffCallback : DiffUtil.ItemCallback<LeaderboardUser>() {
        override fun areItemsTheSame(oldItem: LeaderboardUser, newItem: LeaderboardUser): Boolean {
            return oldItem.user_uuid == newItem.user_uuid
        }

        override fun areContentsTheSame(oldItem: LeaderboardUser, newItem: LeaderboardUser): Boolean {
            return oldItem == newItem
        }
    }
}