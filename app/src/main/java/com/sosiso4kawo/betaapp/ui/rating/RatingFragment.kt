package com.sosiso4kawo.betaapp.ui.rating

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
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.LeaderboardUser
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

    private var currentOffset = 0
    private val pageSize = 20
    private var isLoading = false
    private var isLastPage = false
    private var allUsers = emptyList<LeaderboardUser>()

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
        // Initial podium setup with empty views
        binding.podiumLayout.ivFirstAvatar.setImageResource(R.drawable.placeholder_avatar)
        binding.podiumLayout.ivSecondAvatar.setImageResource(R.drawable.placeholder_avatar)
        binding.podiumLayout.ivThirdAvatar.setImageResource(R.drawable.placeholder_avatar)
    }

    private fun loadInitialData() {
        currentOffset = 0
        isLastPage = false
        allUsers = emptyList()
        loadUsers(currentOffset)
    }

    private fun loadMoreData() {
        if (!isLoading && !isLastPage) {
            loadUsers(currentOffset)
        }
    }

    private fun loadUsers(offset: Int) {
        isLoading = true
        lifecycleScope.launch {
            userRepository.getAllUsers(limit = pageSize, offset = offset).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val newUsers = result.value.sortedByDescending { it.total_points ?: 0 }

                        if (offset == 0) {
                            allUsers = newUsers
                            updatePodium()
                            userAdapter.updateUsers(newUsers.drop(3))
                        } else {
                            allUsers = allUsers + newUsers
                            userAdapter.addUsers(newUsers)
                        }

                        if (newUsers.size < pageSize) {
                            isLastPage = true
                        } else {
                            currentOffset += pageSize
                        }
                        isLoading = false
                    }
                    is Result.Failure -> {
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

    private fun updatePodium() {
        val topUsers = allUsers.take(3)
        with(binding.podiumLayout) {
            listOf(firstPlaceContainer, secondPlaceContainer, thirdPlaceContainer).forEach {
                it.setOnClickListener(null)
            }
            topUsers.forEachIndexed { index, user ->
                when (index) {
                    0 -> {
                        Glide.with(this@RatingFragment)
                            .load(user.avatar)
                            .circleCrop()
                            .placeholder(R.drawable.placeholder_avatar)
                            .error(R.drawable.error_avatar)
                            .into(ivFirstAvatar)
                        tvFirstName.text = user.login ?: user.name ?: "Аноним"
                        tvFirstPoints.text =
                            getString(R.string.points_format, user.total_points ?: 0)
                        firstPlaceContainer.setOnClickListener { handlePodiumClick(user) }
                    }

                    1 -> {
                        Glide.with(this@RatingFragment)
                            .load(user.avatar)
                            .circleCrop()
                            .placeholder(R.drawable.placeholder_avatar)
                            .error(R.drawable.error_avatar)
                            .into(ivSecondAvatar)
                        tvSecondName.text = user.login ?: user.name ?: "Аноним"
                        tvSecondPoints.text =
                            getString(R.string.points_format, user.total_points ?: 0)
                        secondPlaceContainer.setOnClickListener { handlePodiumClick(user) }
                    }

                    2 -> {
                        Glide.with(this@RatingFragment)
                            .load(user.avatar)
                            .circleCrop()
                            .placeholder(R.drawable.placeholder_avatar)
                            .error(R.drawable.error_avatar)
                            .into(ivThirdAvatar)
                        tvThirdName.text = user.login ?: user.name ?: "Аноним"
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