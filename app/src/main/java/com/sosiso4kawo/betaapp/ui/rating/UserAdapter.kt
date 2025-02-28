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
import com.sosiso4kawo.betaapp.data.model.User

class UserAdapter(private val users: MutableList<User> = mutableListOf()) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // Callback для обработки клика по пользователю
    var onUserClick: ((User) -> Unit)? = null

    // Идентификатор текущего пользователя для выделения
    private var currentUserId: String? = null

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: View = itemView
        val avatarImageView: ImageView = itemView.findViewById(R.id.avatarImageView)
        val loginTextView: TextView = itemView.findViewById(R.id.loginTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        // Если пользователь является текущим, выделяем ячейку
        if (user.uuid == currentUserId) {
            // Цвет #F97316 с 50% непрозрачностью = "#80F97316"
            holder.container.setBackgroundColor(Color.parseColor("#80F97316"))
            holder.loginTextView.text = "Ты"
        } else {
            holder.container.setBackgroundColor(Color.TRANSPARENT)
            holder.loginTextView.text = user.login
        }

        Glide.with(holder.itemView.context)
            .load(user.avatar)
            .circleCrop()
            .placeholder(R.drawable.placeholder_avatar)
            .error(R.drawable.error_avatar)
            .into(holder.avatarImageView)

        // Передаём событие клика через callback
        holder.container.setOnClickListener {
            onUserClick?.invoke(user)
        }
    }

    override fun getItemCount(): Int = users.size

    // Метод для полной загрузки списка
    fun updateUsers(newUsers: List<User>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }

    // Метод для дозагрузки (пагинация)
    fun addUsers(newUsers: List<User>) {
        val startPos = users.size
        users.addAll(newUsers)
        notifyItemRangeInserted(startPos, newUsers.size)
    }

    // Метод для установки идентификатора текущего пользователя
    fun setCurrentUserId(userId: String) {
        currentUserId = userId
        notifyDataSetChanged()
    }
}
