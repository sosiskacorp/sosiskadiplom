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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeState()
    }

    private fun setupViews() {
        binding.loginButton.setOnClickListener {
            val login = binding.loginInput.text.toString()
            val password = binding.passwordInput.text.toString()

            if (validateInput(login, password)) {
                viewModel.login(login, password)
            }
        }

        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    private fun validateInput(login: String, password: String): Boolean {
        var isValid = true

        if (login.isBlank()) {
            binding.loginLayout.error = "Введите логин"
            isValid = false
        } else {
            binding.loginLayout.error = null
        }

        if (password.isBlank()) {
            binding.passwordLayout.error = "Введите пароль"
            isValid = false
        } else {
            binding.passwordLayout.error = null
        }

        return isValid
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Loading -> {
                            binding.loginButton.isEnabled = false
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is AuthUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                        }
                        is AuthUiState.Error -> {
                            binding.loginButton.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            binding.loginButton.isEnabled = true
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