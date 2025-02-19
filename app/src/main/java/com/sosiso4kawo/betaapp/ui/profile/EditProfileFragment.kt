package com.sosiso4kawo.betaapp.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.betaapp.data.model.User
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.databinding.FragmentEditProfileBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val userRepository: UserRepository by inject()
    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)

        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_profile)
            setTitle("Редактирование профиля")
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCurrentProfile()
        setupSaveButton()
    }

    private fun loadCurrentProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getProfile().collect { result ->
                    when (result) {
                        is com.sosiso4kawo.betaapp.util.Result.Success -> {
                            currentUser = result.value
                            populateFields(result.value)
                        }
                        is com.sosiso4kawo.betaapp.util.Result.Failure -> {
                            Toast.makeText(
                                context,
                                "Ошибка загрузки профиля: ${result.exception.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun populateFields(user: User) {
        binding.apply {
            loginEditText.setText(user.login)
            lastNameEditText.setText(user.last_name)
            firstNameEditText.setText(user.name)
            middleNameEditText.setText(user.second_name)
            avatarUrlEditText.setText(user.avatar)

            // Load avatar preview
            if (!user.avatar.isNullOrEmpty()) {
                Glide.with(this@EditProfileFragment)
                    .load(user.avatar)
                    .placeholder(R.drawable.placeholder_avatar)
                    .error(R.drawable.error_avatar)
                    .into(avatarPreviewImageView)
            } else {
                avatarPreviewImageView.setImageResource(R.drawable.placeholder_avatar)
            }
        }
    }

    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            val updateRequest = UpdateProfileRequest(
                login = binding.loginEditText.text.toString().takeIf { it.isNotBlank() },
                last_name = binding.lastNameEditText.text.toString().takeIf { it.isNotBlank() },
                name = binding.firstNameEditText.text.toString().takeIf { it.isNotBlank() },
                second_name = binding.middleNameEditText.text.toString().takeIf { it.isNotBlank() },
                avatar = binding.avatarUrlEditText.text.toString().takeIf { it.isNotBlank() }
            )

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    userRepository.updateProfile(updateRequest).collect { result ->
                        when (result) {
                            is com.sosiso4kawo.betaapp.util.Result.Success -> {
                                Toast.makeText(
                                    context,
                                    "Профиль успешно обновлен",
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().navigateUp()
                            }
                            is com.sosiso4kawo.betaapp.util.Result.Failure -> {
                                Toast.makeText(
                                    context,
                                    "Ошибка обновления профиля: ${result.exception.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }

        // Preview avatar when URL changes
        binding.previewAvatarButton.setOnClickListener {
            val avatarUrl = binding.avatarUrlEditText.text.toString()
            if (avatarUrl.isNotBlank()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.placeholder_avatar)
                    .error(R.drawable.error_avatar)
                    .into(binding.avatarPreviewImageView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}