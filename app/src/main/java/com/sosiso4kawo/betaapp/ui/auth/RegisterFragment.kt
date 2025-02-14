package com.sosiso4kawo.betaapp.ui.auth

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
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeState()
    }

    private fun setupViews() {
        binding.registerButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            if (validateInput(email, password, confirmPassword)) {
                viewModel.register(email, password)
            }
        }

        binding.loginButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun validateInput(email: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (email.isBlank()) {
            binding.emailLayout.error = "Введите email"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "Введите корректный email"
            isValid = false
        } else {
            binding.emailLayout.error = null
        }

        if (password.isBlank()) {
            binding.passwordLayout.error = "Введите пароль"
            isValid = false
        } else if (!isPasswordValid(password)) {
            binding.passwordLayout.error = "Пароль должен содержать минимум 8 символов, одну заглавную букву, одну цифру и один специальный символ"
            isValid = false
        } else {
            binding.passwordLayout.error = null
        }

        if (confirmPassword.isBlank()) {
            binding.confirmPasswordLayout.error = "Подтвердите пароль"
            isValid = false
        } else if (password != confirmPassword) {
            binding.confirmPasswordLayout.error = "Пароли не совпадают"
            isValid = false
        } else {
            binding.confirmPasswordLayout.error = null
        }

        return isValid
    }

    private fun isPasswordValid(password: String): Boolean {
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }
        val hasMinLength = password.length >= 8
        val hasNumber = password.any { it.isDigit() }
        return hasUpperCase && hasSpecialChar && hasMinLength && hasNumber
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Loading -> {
                            binding.registerButton.isEnabled = false
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is AuthUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            findNavController().navigate(R.id.action_registerFragment_to_homeFragment)
                        }
                        is AuthUiState.Error -> {
                            binding.registerButton.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            binding.registerButton.isEnabled = true
                            binding.progressBar.visibility = View.GONE
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