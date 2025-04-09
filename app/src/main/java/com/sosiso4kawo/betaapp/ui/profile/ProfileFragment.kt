@file:Suppress("DEPRECATION")

package com.sosiso4kawo.betaapp.ui.profile

import android.annotation.SuppressLint
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.databinding.FragmentProfileBinding
import com.sosiso4kawo.betaapp.ui.auth.AuthUiState
import com.sosiso4kawo.betaapp.ui.auth.AuthViewModel
import com.sosiso4kawo.betaapp.ui.settings.FontSettingsBottomSheetFragment
import com.sosiso4kawo.betaapp.util.Result
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProfileFragment : Fragment(), KoinComponent {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Получаем ViewModel и Repository через Koin
    private val authViewModel: AuthViewModel by viewModel()
    private val userRepository: UserRepository by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        // Настраиваем header: устанавливаем фон и заголовок
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_profile)
            setTitle("Профиль")
            showEditProfileButton(true)
            setOnEditProfileClickListener {
                findNavController().navigate(R.id.action_navigation_profile_to_editProfileFragment)
            }
        }

        // Получаем SharedPreferences для сохранения состояния переключателей
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Настраиваем переключатель для тёмной темы
        val darkThemeSwitch = binding.personalizationContainer.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.darkThemeSwitch)
        darkThemeSwitch.isChecked = sharedPreferences.getBoolean("dark_theme_enabled", false)
        darkThemeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("dark_theme_enabled", isChecked).apply()
            // Применяем тёмную тему
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // Настраиваем переключатель для звуков
        val soundSwitch = binding.personalizationContainer.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.soundSwitch)
        soundSwitch.isChecked = sharedPreferences.getBoolean("sound_enabled", true)
        soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("sound_enabled", isChecked).apply()
            // Пример: выводим сообщение или вызываем метод для управления звуком
            if (isChecked) {
                // Включаем звуки (реализуйте свою логику)
                Toast.makeText(requireContext(), "Звуки включены", Toast.LENGTH_SHORT).show()
            } else {
                // Выключаем звуки (реализуйте свою логику)
                Toast.makeText(requireContext(), "Звуки выключены", Toast.LENGTH_SHORT).show()
            }
        }

        val fontSizeRow = binding.personalizationContainer.findViewById<View>(R.id.fontSizeRow)
        fontSizeRow.setOnClickListener {
            // Здесь открываем BottomSheet для настройки размера шрифта
            val bottomSheet = FontSettingsBottomSheetFragment()
            bottomSheet.show(childFragmentManager, "FontSettings")
        }

        return binding.root
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Загружаем данные профиля
        loadProfile()

        // Загружаем прогресс пользователя
        loadProgress()

        // Обработчик нажатия кнопки "Выйти из аккаунта"
        binding.logoutButton.setOnClickListener {
            authViewModel.logout()
        }

        // Наблюдаем за состоянием logout через StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Loading -> binding.logoutButton.isEnabled = false
                        is AuthUiState.LoggedOut -> {
                            binding.logoutButton.isEnabled = true
                            val navOptions = NavOptions.Builder()
                                .setPopUpTo(R.id.mobile_navigation, true)
                                .build()
                            findNavController().navigate(R.id.navigation_login, null, navOptions)
                        }
                        is AuthUiState.Error -> {
                            binding.logoutButton.isEnabled = true
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> binding.logoutButton.isEnabled = true
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadProfile() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getProfile().collect { result ->
                    when (result) {
                        is Result.Success -> {
                            val user = result.value
                            binding.userFullName.text = "${user.last_name} ${user.name} ${user.second_name}"
                            if (!user.avatar.isNullOrEmpty()) {
                                binding.avatarImageView.apply {
                                    // Устанавливаем фон-бордер (предварительно создайте drawable circular_border.xml)
                                    setBackgroundResource(R.drawable.circular_border)
                                }
                                Glide.with(this@ProfileFragment)
                                    .load(user.avatar)
                                    .circleCrop()
                                    .placeholder(R.drawable.placeholder_avatar)
                                    .error(R.drawable.error_avatar)
                                    .into(binding.avatarImageView)
                            } else {
                                binding.avatarImageView.setImageResource(R.drawable.placeholder_avatar)
                            }
                        }
                        is Result.Failure -> {
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

    @SuppressLint("SetTextI18n")
    private fun loadProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getProgress().collect { result ->
                    when (result) {
                        is Result.Success -> {
                            val progressResponse = result.value

                            // Считаем общий прогресс
                            val totalPoints = progressResponse.lessons.sumOf { it.total_points }
                            val completedCoursesCount = progressResponse.courses.size

                            // Обновляем текст в соответствующих TextView
                            binding.tvTotalPoints.text = "Всего поинтов: $totalPoints"
                            binding.tvCompletedCourses.text = "Пройденных курсов: $completedCoursesCount"
                        }
                        is Result.Failure -> {
                            Toast.makeText(
                                context,
                                "Ошибка загрузки прогресса: ${result.exception.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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
