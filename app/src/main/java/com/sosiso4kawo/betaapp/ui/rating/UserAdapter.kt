package com.sosiso4kawo.betaapp.ui.rating

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.LeaderboardUser

class UserAdapter(
    private val leaderboard: MutableList<LeaderboardUser> = mutableListOf()
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    var onUserClick: ((LeaderboardUser) -> Unit)? = null
    private var currentUserId: String? = null

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: View = itemView
        val avatarImageView: ImageView = itemView.findViewById(R.id.avatarImageView)
        val loginTextView: TextView = itemView.findViewById(R.id.loginTextView)
        val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = leaderboard[position]

        holder.apply {
            tvRank.text = (position + 4).toString()
            // Исправляем получение контекста через itemView
            tvPoints.text = itemView.context.getString(R.string.points_format, user.total_points ?: 0)

            if (user.user_uuid == currentUserId) {
                container.setBackgroundColor(Color.parseColor("#80F97316"))
                loginTextView.text = "Ты"
            } else {
                container.setBackgroundColor(Color.TRANSPARENT)
                loginTextView.text = user.login ?: user.name ?: "Аноним"
            }

            Glide.with(holder.itemView.context)
                .load(user.avatar)
                .circleCrop()
                .placeholder(R.drawable.placeholder_avatar)
                .error(R.drawable.error_avatar)
                .into(avatarImageView)

            container.setOnClickListener { onUserClick?.invoke(user) }
        }
    }


    override fun getItemCount(): Int = leaderboard.size

    fun updateUsers(newUsers: List<LeaderboardUser>) {
        leaderboard.clear()
        leaderboard.addAll(newUsers)
        notifyDataSetChanged()
    }

    fun addUsers(newUsers: List<LeaderboardUser>) {
        val startPos = leaderboard.size
        leaderboard.addAll(newUsers)
        notifyItemRangeInserted(startPos, newUsers.size)
    }

    fun setCurrentUserId(userId: String) {
        currentUserId = userId
        notifyDataSetChanged()
    }
}