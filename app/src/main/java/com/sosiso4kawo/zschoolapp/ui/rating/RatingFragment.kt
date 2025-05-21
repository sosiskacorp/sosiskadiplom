package com.sosiso4kawo.zschoolapp.ui.rating

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.model.LeaderboardUser
import com.sosiso4kawo.zschoolapp.data.repository.UserRepository
import com.sosiso4kawo.zschoolapp.databinding.FragmentRatingBinding
import com.sosiso4kawo.zschoolapp.ui.userdetails.UserDetailsFragment
import com.sosiso4kawo.zschoolapp.util.Result
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class RatingFragment : Fragment() {

    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!
    private lateinit var userAdapter: UserAdapter
    private val userRepository: UserRepository by inject()
    private val sessionManager: SessionManager by inject()

    private var currentOffset = 0
    private val pageSize = 20
    private var isLoading = false
    private var isLastPage = false
    // Используем MutableList для allUsers, чтобы можно было добавлять элементы
    private var allUsers = mutableListOf<LeaderboardUser>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRatingBinding.inflate(inflater, container, false)
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_rating)
            setTitle(getString(R.string.title_rating)) // Используем строку из ресурсов
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupPodium()
        loadInitialData()
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter()
        val layoutManager = LinearLayoutManager(requireContext())
        binding.usersRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = userAdapter
            clipToPadding = false
            updatePadding(bottom = resources.getDimensionPixelSize(R.dimen.bottom_padding))
        }

        sessionManager.getUserData()?.uuid?.let {
            userAdapter.setCurrentUserId(it)
        }

        userAdapter.onUserClick = { user ->
            if (user.user_uuid == sessionManager.getUserData()?.uuid) {
                findNavController().navigate(R.id.navigation_profile)
            } else {
                val bundle = Bundle().apply {
                    putString(UserDetailsFragment.ARG_USER_UUID, user.user_uuid)
                }
                findNavController().navigate(R.id.action_ratingFragment_to_userDetailsFragment, bundle)
            }
        }

        binding.usersRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && !isLastPage && totalItemCount <= (lastVisibleItem + 5)) {
                    loadMoreData()
                }
            }
        })
    }

    private fun setupPodium() {
        binding.podiumLayout.ivFirstAvatar.setImageResource(R.drawable.placeholder_avatar)
        binding.podiumLayout.ivSecondAvatar.setImageResource(R.drawable.placeholder_avatar)
        binding.podiumLayout.ivThirdAvatar.setImageResource(R.drawable.placeholder_avatar)
        // Очистим текстовые поля подиума
        binding.podiumLayout.tvFirstName.text = ""
        binding.podiumLayout.tvFirstPoints.text = ""
        binding.podiumLayout.tvSecondName.text = ""
        binding.podiumLayout.tvSecondPoints.text = ""
        binding.podiumLayout.tvThirdName.text = ""
        binding.podiumLayout.tvThirdPoints.text = ""
    }

    private fun loadInitialData() {
        currentOffset = 0
        isLastPage = false
        allUsers.clear() // Очищаем список
        userAdapter.submitList(emptyList()) // Очищаем адаптер
        loadUsers(currentOffset)
    }

    private fun loadMoreData() {
        if (!isLoading && !isLastPage) {
            loadUsers(currentOffset)
        }
    }

    private fun loadUsers(offset: Int) {
        isLoading = true
        // Можно добавить отображение прогресс-бара для загрузки
        lifecycleScope.launch {
            userRepository.getAllUsers(limit = pageSize, offset = offset).collect { result ->
                when (result) {
                    is Result.Success -> {
                        // Сервер уже возвращает отсортированных пользователей,
                        // но если нет, то сортировка здесь:
                        // val newUsers = result.value.sortedByDescending { it.total_points ?: 0 }
                        val newUsers = result.value // Предполагаем, что сервер сортирует

                        if (offset == 0) {
                            allUsers.clear()
                            allUsers.addAll(newUsers)
                            updatePodium()
                            // Для RecyclerView отправляем список БЕЗ первых трех (тех, что на подиуме)
                            userAdapter.submitList(allUsers.drop(3).toList())
                        } else {
                            allUsers.addAll(newUsers)
                            // Для RecyclerView отправляем обновленный список БЕЗ первых трех
                            userAdapter.submitList(allUsers.drop(3).toList())
                        }

                        if (newUsers.size < pageSize) {
                            isLastPage = true
                        } else {
                            currentOffset += newUsers.size // Увеличиваем offset на количество реально полученных пользователей
                        }
                        isLoading = false
                    }
                    is Result.Failure -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_loading_users_with_message, result.exception.message), // Используем строку из ресурсов
                            Toast.LENGTH_SHORT
                        ).show()
                        isLoading = false
                    }
                }
                // Скрыть прогресс-бар, если он был
            }
        }
    }

    private fun updatePodium() {
        val topUsers = allUsers.take(3)
        with(binding.podiumLayout) {
            // Сначала скроем все элементы подиума или установим плейсхолдеры
            listOf(firstPlaceContainer, secondPlaceContainer, thirdPlaceContainer).forEach {
                it.setOnClickListener(null) // Сбрасываем слушатели
                it.visibility = View.INVISIBLE // Скрываем, если нет пользователя
            }
            ivFirstAvatar.setImageResource(R.drawable.placeholder_avatar)
            tvFirstName.text = ""
            tvFirstPoints.text = ""
            ivSecondAvatar.setImageResource(R.drawable.placeholder_avatar)
            tvSecondName.text = ""
            tvSecondPoints.text = ""
            ivThirdAvatar.setImageResource(R.drawable.placeholder_avatar)
            tvThirdName.text = ""
            tvThirdPoints.text = ""


            topUsers.forEachIndexed { index, user ->
                when (index) {
                    0 -> {
                        firstPlaceContainer.visibility = View.VISIBLE
                        Glide.with(this@RatingFragment)
                            .load(user.avatar)
                            .circleCrop()
                            .placeholder(R.drawable.placeholder_avatar)
                            .error(R.drawable.error_avatar)
                            .into(ivFirstAvatar)
                        tvFirstName.text = user.login ?: user.name ?: getString(R.string.text_anonymous)
                        tvFirstPoints.text =
                            getString(R.string.points_format, user.total_points ?: 0)
                        firstPlaceContainer.setOnClickListener { handlePodiumClick(user) }
                    }

                    1 -> {
                        secondPlaceContainer.visibility = View.VISIBLE
                        Glide.with(this@RatingFragment)
                            .load(user.avatar)
                            .circleCrop()
                            .placeholder(R.drawable.placeholder_avatar)
                            .error(R.drawable.error_avatar)
                            .into(ivSecondAvatar)
                        tvSecondName.text = user.login ?: user.name ?: getString(R.string.text_anonymous)
                        tvSecondPoints.text =
                            getString(R.string.points_format, user.total_points ?: 0)
                        secondPlaceContainer.setOnClickListener { handlePodiumClick(user) }
                    }

                    2 -> {
                        thirdPlaceContainer.visibility = View.VISIBLE
                        Glide.with(this@RatingFragment)
                            .load(user.avatar)
                            .circleCrop()
                            .placeholder(R.drawable.placeholder_avatar)
                            .error(R.drawable.error_avatar)
                            .into(ivThirdAvatar)
                        tvThirdName.text = user.login ?: user.name ?: getString(R.string.text_anonymous)
                        tvThirdPoints.text =
                            getString(R.string.points_format, user.total_points ?: 0)
                        thirdPlaceContainer.setOnClickListener { handlePodiumClick(user) }
                    }
                }
            }
        }
    }

    private fun handlePodiumClick(user: LeaderboardUser) {
        if (user.user_uuid == sessionManager.getUserData()?.uuid) {
            findNavController().navigate(R.id.navigation_profile)
        } else {
            val bundle = Bundle().apply {
                putString(UserDetailsFragment.ARG_USER_UUID, user.user_uuid)
            }
            findNavController().navigate(R.id.action_ratingFragment_to_userDetailsFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}