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
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentProfileBinding
import com.sosiso4kawo.betaapp.ui.auth.AuthUiState
import com.sosiso4kawo.betaapp.ui.auth.AuthViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Получаем AuthViewModel через Koin
    private val authViewModel: AuthViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        // Настраиваем header: устанавливаем фон и заголовок
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_profile)
            setTitle("Профиль")
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
