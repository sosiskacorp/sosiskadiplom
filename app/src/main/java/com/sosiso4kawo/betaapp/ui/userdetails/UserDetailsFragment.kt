@file:Suppress("DEPRECATION")

package com.sosiso4kawo.betaapp.ui.userdetails

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
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.databinding.FragmentUserDetailsBinding
import com.sosiso4kawo.betaapp.util.Result
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class UserDetailsFragment : Fragment() {

    private var _binding: FragmentUserDetailsBinding? = null
    private val binding get() = _binding!!
    private val userRepository: UserRepository by inject()

    companion object {
        const val ARG_USER_UUID = "user_uuid"
    }

    private var userUuid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userUuid = arguments?.getString(ARG_USER_UUID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserDetailsBinding.inflate(inflater, container, false)
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_rating)
            setTitle("Профиль пользователя")
            showBackButton(true)
            setOnBackClickListener {
                requireActivity().onBackPressed()
            }
        }
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userUuid?.let { uuid ->
            lifecycleScope.launch {
                userRepository.getUserByUuid(uuid).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            val user = result.value
                            // Обновляем UI: аватар, логин и ФИО
                            Glide.with(requireContext())
                                .load(user.avatar)
                                .circleCrop()
                                .placeholder(R.drawable.placeholder_avatar)
                                .error(R.drawable.error_avatar)
                                .into(binding.avatarImageView)
                            binding.loginTextView.text = user.login
                            binding.fullNameTextView.text = "${user.last_name} ${user.name} ${user.second_name}"
                        }
                        is Result.Failure -> {
                            Log.e("UserDetailsFragment", "Ошибка загрузки пользователя: ${result.exception.message}", result.exception)
                            Toast.makeText(requireContext(), "Ошибка загрузки данных пользователя", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } ?: run {
            Toast.makeText(requireContext(), "Не указан идентификатор пользователя", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
