@file:Suppress("SameParameterValue")

package com.sosiso4kawo.betaapp.ui.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Button
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

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModel()
    private var codeTimer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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

        // Обработка клика по "Забыли пароль?"
        binding.forgotPasswordText.setOnClickListener {
            val email = binding.loginInput.text.toString().trim()
            if (email.isNotBlank()) {
                showResetPasswordDialog(email)
            } else {
                Toast.makeText(context, "Введите email в поле логина", Toast.LENGTH_SHORT).show()
            }
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
                            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                        }
                        is AuthUiState.Error -> {
                            binding.loginButton.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            // Если ошибка равна "email not confirmed", можем открыть диалог подтверждения
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
        // Реализация диалога подтверждения почты (аналогична вашей существующей)
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_email_verification, null)
        val codeInputLayout = dialogView.findViewById<TextInputLayout>(R.id.codeInputLayout)
        val codeInput = dialogView.findViewById<TextInputEditText>(R.id.codeInput)
        val sendCodeButton = dialogView.findViewById<MaterialButton>(R.id.sendCodeButton)
        val confirmButton = dialogView.findViewById<MaterialButton>(R.id.confirmButton)

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

        dialog.setOnCancelListener {
            codeTimer?.cancel()
        }

        dialog.show()
    }

    private fun showResetPasswordDialog(email: String) {
        // Используем вашу готовую разметку для диалога сброса пароля (dialog_reset_password.xml)
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_reset_password, null)
        // Получаем ссылки на элементы диалога
        val codeInputLayout = dialogView.findViewById<TextInputLayout>(R.id.codeInputLayout)
        val codeEditText = dialogView.findViewById<TextInputEditText>(R.id.codeEditText)
        val newPasswordInputLayout = dialogView.findViewById<TextInputLayout>(R.id.newPasswordInputLayout)
        val newPasswordEditText = dialogView.findViewById<TextInputEditText>(R.id.newPasswordEditText)
        val confirmPasswordEditText = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordEditText)
        val sendCodeButton = dialogView.findViewById<Button>(R.id.sendCodeButton)
        val resetPasswordButton = dialogView.findViewById<Button>(R.id.resetPasswordButton)

        resetPasswordButton.isEnabled = false

        // Включаем кнопку сброса, когда введён код из 6 цифр
        codeEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                resetPasswordButton.isEnabled = s?.length == 6
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.setOnCancelListener {
            codeTimer?.cancel()
            Toast.makeText(requireContext(), "Сброс пароля отменен", Toast.LENGTH_SHORT).show()
        }

        sendCodeButton.setOnClickListener {
            viewModel.sendVerificationCode(email)
            sendCodeButton.isEnabled = false
            startCodeTimerForButton(sendCodeButton)
        }

        resetPasswordButton.setOnClickListener {
            val code = codeEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (code.isBlank()) {
                codeInputLayout.error = "Введите код подтверждения"
                return@setOnClickListener
            } else {
                codeInputLayout.error = null
            }

            if (newPassword.isBlank()) {
                newPasswordInputLayout.error = "Введите новый пароль"
                return@setOnClickListener
            } else if (!isPasswordValid(newPassword)) {
                newPasswordInputLayout.error = "Пароль должен содержать минимум 8 символов, одну заглавную букву, одну маленькую букву, одну цифру и один специальный символ"
                return@setOnClickListener
            } else {
                newPasswordInputLayout.error = null
            }

            if (confirmPassword.isBlank()) {
                newPasswordInputLayout.error = "Подтвердите пароль"
                return@setOnClickListener
            } else if (newPassword != confirmPassword) {
                newPasswordInputLayout.error = "Пароли не совпадают"
                return@setOnClickListener
            } else {
                newPasswordInputLayout.error = null
            }

            viewModel.resetPassword(email, code, newPassword) { success, message ->
                if (success) {
                    Toast.makeText(requireContext(), "Пароль успешно сброшен", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Ошибка: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Обработка кнопки "Назад" (если требуется)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dialog.dismiss()
                return@setOnKeyListener true
            }
            false
        }

        dialog.show()
    }

    // Метод для запуска таймера в диалоге подтверждения кода (с MaterialButton)
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

    // Отдельный метод для кнопок типа Button (используемых в dialog_reset_password.xml)
    private fun startCodeTimerForButton(sendCodeButton: Button) {
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

    private fun isPasswordValid(password: String): Boolean {
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }
        val hasMinLength = password.length >= 8
        val hasNumber = password.any { it.isDigit() }
        return hasUpperCase && hasLowerCase && hasSpecialChar && hasMinLength && hasNumber
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
