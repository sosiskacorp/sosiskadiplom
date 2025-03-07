package com.sosiso4kawo.betaapp.ui.rating

import android.os.Bundle
import android.util.Log
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
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.databinding.FragmentRatingBinding
import com.sosiso4kawo.betaapp.ui.userdetails.UserDetailsFragment
import com.sosiso4kawo.betaapp.util.Result
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class RatingFragment : Fragment() {

    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!
    private lateinit var userAdapter: UserAdapter
    private val userRepository: UserRepository by inject()
    private val sessionManager: SessionManager by inject()

    // Параметры пагинации
    private var currentOffset = 0
    private val pageSize = 20
    private var isLoading = false
    private var isLastPage = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRatingBinding.inflate(inflater, container, false)
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_rating)
            setTitle("Рейтинг")
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userAdapter = UserAdapter()
        val layoutManager = LinearLayoutManager(requireContext())
        binding.usersRecyclerView.layoutManager = layoutManager
        binding.usersRecyclerView.adapter = userAdapter

        // Reset pagination state when fragment is created
        currentOffset = 0
        isLastPage = false

        // Нижний отступ, чтобы последний элемент не наслаивался на navigation bar (например, 72dp)
        binding.usersRecyclerView.apply {
            clipToPadding = false
            updatePadding(bottom = resources.getDimensionPixelSize(R.dimen.bottom_padding))
        }

        // Устанавливаем идентификатор текущего пользователя для выделения
        sessionManager.getUserData()?.uuid?.let {
            userAdapter.setCurrentUserId(it)
        }

        // Обработка кликов по элементу списка
        userAdapter.onUserClick = { user ->
            if (user.uuid == sessionManager.getUserData()?.uuid) {
                // Если нажали на "себя" – переходим в фрагмент профиля
                findNavController().navigate(R.id.navigation_profile)
            } else {
                // Иначе – переходим в новый фрагмент для просмотра информации о пользователе
                val bundle = Bundle().apply {
                    putString(UserDetailsFragment.ARG_USER_UUID, user.uuid)
                }
                findNavController().navigate(R.id.action_ratingFragment_to_userDetailsFragment, bundle)
            }
        }

        // Начальная загрузка пользователей
        loadUsers(currentOffset)

        // Пагинация – дозагрузка при скролле
        binding.usersRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && !isLastPage && totalItemCount <= (lastVisibleItem + 5)) {
                    Log.d("RatingFragment", "Load more triggered: offset=$currentOffset")
                    loadUsers(currentOffset)
                }
            }
        })
    }

    private fun loadUsers(offset: Int) {
        isLoading = true
        Log.d("RatingFragment", "Loading users with offset=$offset, limit=$pageSize")
        lifecycleScope.launch {
            userRepository.getAllUsers(limit = pageSize, offset = offset).collect { result ->
                when (result) {
                    is Result.Success -> {
                        var users = result.value
                        Log.d("RatingFragment", "Loaded ${users.size} users")
                        // При первой загрузке, если текущего пользователя нет в списке, добавляем его в начало
                        if (offset == 0) {
                            sessionManager.getUserData()?.let { currentUser ->
                                if (users.none { it.uuid == currentUser.uuid }) {
                                    users = listOf(currentUser) + users
                                }
                            }
                            userAdapter.updateUsers(users)
                        } else {
                            userAdapter.addUsers(users)
                        }
                        if (users.size < pageSize) {
                            isLastPage = true
                        } else {
                            currentOffset += pageSize
                        }
                        isLoading = false
                    }
                    is Result.Failure -> {
                        Log.e("RatingFragment", "Error loading users: ${result.exception.message}", result.exception)
                        Toast.makeText(
                            requireContext(),
                            "Ошибка загрузки пользователей: ${result.exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        isLoading = false
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
