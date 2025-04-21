package com.sosiso4kawo.zschoolapp.ui.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

@Suppress("SameParameterValue")
class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModel()

    // Переменная для отслеживания таймера отправки кода
    private var codeTimer: CountDownTimer? = null

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
            binding.passwordLayout.error = "Пароль должен содержать минимум 8 символов, одну заглавную букву, одну маленькую букву, одну цифру и один специальный символ"
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
                            // Убираем toast здесь, чтобы уведомление показывалось позже при подтверждении почты
                            showEmailVerificationDialog(binding.emailInput.text.toString())
                        }
                        is AuthUiState.Error -> {
                            binding.registerButton.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            // Выводим ошибку, если регистрация не удалась
                            showError(state.message)
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

    private fun showEmailVerificationDialog(email: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_email_verification, null)
        val codeInputLayout = dialogView.findViewById<TextInputLayout>(R.id.codeInputLayout)
        val codeInput = dialogView.findViewById<TextInputEditText>(R.id.codeInput)
        val sendCodeButton = dialogView.findViewById<MaterialButton>(R.id.sendCodeButton)
        val confirmButton = dialogView.findViewById<MaterialButton>(R.id.confirmButton)

        // Изначально кнопку подтверждения отключаем
        confirmButton.isEnabled = false

        // Добавляем TextWatcher для проверки ввода ровно 6 цифр
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

        sendCodeButton.setOnClickListener {
            viewModel.sendVerificationCode(email)
            // Запускаем таймер на 1 минуту для блокировки кнопки
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
                        // Уведомление выводится после успешного подтверждения кода
                        showSuccess("Почта подтверждена")
                        dialog.dismiss()
                        findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
                    } else {
                        showError(message)
                    }
                }
            }
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
        // Можно заменить на кастомное отображение ошибки
        AlertDialog.Builder(requireContext())
            .setTitle("Ошибка")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSuccess(message: String) {
        // Можно заменить на кастомное отображение успешного сообщения
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
