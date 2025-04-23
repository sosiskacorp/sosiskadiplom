// app/src/main/java/com/sosiso4kawo/zschoolapp/ui/userdetails/UserDetailsFragment.kt
@file:Suppress("DEPRECATION")
package com.sosiso4kawo.zschoolapp.ui.userdetails

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.api.AchievementsService
import com.sosiso4kawo.zschoolapp.data.repository.UserRepository
import com.sosiso4kawo.zschoolapp.databinding.FragmentUserDetailsBinding
import com.sosiso4kawo.zschoolapp.util.Result
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class UserDetailsFragment : Fragment() {

    private var _binding: FragmentUserDetailsBinding? = null
    private val binding get() = _binding!!

    private val userRepository: UserRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val achievementsService: AchievementsService by inject()

    companion object {
        const val ARG_USER_UUID = "user_uuid"
    }
    private var userUuid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userUuid = arguments?.getString(ARG_USER_UUID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDetailsBinding.inflate(inflater, container, false)
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_rating)
            setTitle("Профиль пользователя")
            showBackButton(true)
            setOnBackClickListener { requireActivity().onBackPressed() }
        }
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uuid = userUuid
        if (uuid == null) {
            Toast.makeText(requireContext(), "Не указан идентификатор пользователя", Toast.LENGTH_SHORT).show()
            return
        }

        // 1) Загрузка базовых данных пользователя
        lifecycleScope.launch {
            userRepository.getUserByUuid(uuid).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val user = result.value

                        // Аватар, логин и ФИО
                        Glide.with(requireContext())
                            .load(user.avatar)
                            .circleCrop()
                            .placeholder(R.drawable.placeholder_avatar)
                            .error(R.drawable.error_avatar)
                            .into(binding.avatarImageView)
                        binding.loginTextView.text = user.login
                        binding.fullNameTextView.text = "${user.last_name} ${user.name} ${user.second_name}"

                        // 2) Отображение total_points и finished_courses
                        binding.totalPointsTextView.text = "Всего поинтов: ${user.total_points}"
                        binding.completedCoursesTextView.text = "Пройденных курсов: ${user.finished_courses}"

                    }
                    is Result.Failure -> {
                        Log.e("UserDetailsFragment",
                            "Ошибка загрузки пользователя: ${result.exception.message}",
                            result.exception
                        )
                        Toast.makeText(requireContext(),
                            "Ошибка загрузки данных пользователя",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            try {
                val token = "Bearer ${sessionManager.getAccessToken()}"
                val allResp = achievementsService.getAchievements(token)
                val userResp = achievementsService.getUserAchievements(uuid, token)
                if (allResp.isSuccessful && userResp.isSuccessful) {
                    val allCount = allResp.body()?.achievements?.size ?: 0
                    val doneCount = userResp.body()?.achievements
                        ?.count { it.achieved == true } ?: 0
                    binding.achievementsTextView.text = "Достижений: $doneCount/$allCount"
                } else {
                    binding.achievementsTextView.text = "Достижений: 0/0"
                }
            } catch (e: Exception) {
                Log.e("UserDetailsFragment", "Ошибка загрузки достижений", e)
                binding.achievementsTextView.text = "Достижений: 0/0"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
