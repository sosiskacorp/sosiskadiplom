@file:Suppress("SameParameterValue")

package com.sosiso4kawo.betaapp.ui.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

@Suppress("SameParameterValue")
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModel()
    private var codeTimer: CountDownTimer? = null

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
                            binding.loginButton.isEnabled = true
                            // Если вход успешен, переходим на главный экран
                            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                        }
                        is AuthUiState.Error -> {
                            binding.loginButton.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            // Если ошибка равна "email not confirmed", открываем окно подтверждения почты
                            if (state.message == "email not confirmed") {
                                showEmailVerificationDialog(binding.loginInput.text.toString())
                            } else {
                                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            }
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

    private fun showEmailVerificationDialog(email: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_email_verification, null)
        val codeInputLayout = dialogView.findViewById<TextInputLayout>(R.id.codeInputLayout)
        val codeInput = dialogView.findViewById<TextInputEditText>(R.id.codeInput)
        val sendCodeButton = dialogView.findViewById<MaterialButton>(R.id.sendCodeButton)
        val confirmButton = dialogView.findViewById<MaterialButton>(R.id.confirmButton)

        // Отключаем кнопку подтверждения, пока не введено ровно 6 цифр
        confirmButton.isEnabled = false
        codeInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                confirmButton.isEnabled = s?.length == 6
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение почты")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Автоматически отправляем код при открытии диалога
        viewModel.sendVerificationCode(email)
        sendCodeButton.isEnabled = false
        startCodeTimer(sendCodeButton)

        sendCodeButton.setOnClickListener {
            viewModel.sendVerificationCode(email)
            sendCodeButton.isEnabled = false
            startCodeTimer(sendCodeButton)
        }

        confirmButton.setOnClickListener {
            val code = codeInput.text.toString()
            if (code.isBlank()) {
                codeInputLayout.error = "Введите код"
            } else {
                codeInputLayout.error = null
                viewModel.verifyEmail(email, code) { success, message ->
                    if (success) {
                        dialog.dismiss()
                        showSuccess("Почта подтверждена")
                        // После успешной верификации, пытаемся снова войти
                        val password = binding.passwordInput.text.toString()
                        if (password.isNotBlank()) {
                            viewModel.login(email, password)
                        }
                    } else {
                        showError(message)
                    }
                }
            }
        }

        // Добавляем кнопку отмены или возможность закрыть диалог
        dialog.setOnCancelListener {
            codeTimer?.cancel()
        }

        dialog.show()
    }

    private fun startCodeTimer(sendCodeButton: MaterialButton) {
        codeTimer?.cancel()
        codeTimer = object : CountDownTimer(60000, 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                sendCodeButton.text = "Отправить код (${millisUntilFinished / 1000}s)"
            }
            override fun onFinish() {
                sendCodeButton.text = "Отправить код"
                sendCodeButton.isEnabled = true
            }
        }.start()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Ошибка")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSuccess(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Успех")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        codeTimer?.cancel()
        _binding = null
    }
}