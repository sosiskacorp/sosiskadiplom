package com.sosiso4kawo.zschoolapp.ui.auth

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
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

@Suppress("SameParameterValue")
class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModel()
    private var codeTimer: CountDownTimer? = null
    private var emailVerificationDialog: AlertDialog? = null

    companion object {
        private const val CODE_TIMER_DURATION = 60000L // 60 секунд
        private const val CODE_TIMER_INTERVAL = 1000L // 1 секунда
    }

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
        observeAuthState()
        observeEmailVerificationState()
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
            binding.emailLayout.error = getString(R.string.error_enter_email)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_invalid_email_format)
            isValid = false
        } else {
            binding.emailLayout.error = null
        }

        if (password.isBlank()) {
            binding.passwordLayout.error = getString(R.string.error_enter_password)
            isValid = false
        } else if (!isPasswordValid(password)) {
            binding.passwordLayout.error = getString(R.string.error_password_requirements)
            isValid = false
        } else {
            binding.passwordLayout.error = null
        }

        if (confirmPassword.isBlank()) {
            binding.confirmPasswordLayout.error = getString(R.string.error_confirm_password)
            isValid = false
        } else if (password != confirmPassword) {
            binding.confirmPasswordLayout.error = getString(R.string.error_passwords_do_not_match)
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

    private fun observeAuthState() {
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
                            showEmailVerificationDialog(binding.emailInput.text.toString())
                        }
                        is AuthUiState.Error -> {
                            binding.registerButton.isEnabled = true
                            binding.progressBar.visibility = View.GONE
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
    private fun observeEmailVerificationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.emailVerificationState.collect { state ->
                    when (state) {
                        is EmailVerificationState.Loading -> {
                            // Можно показать индикатор в диалоге
                        }
                        is EmailVerificationState.CodeSent -> {
                            Toast.makeText(requireContext(), getString(R.string.toast_verification_code_sent), Toast.LENGTH_SHORT).show()
                        }
                        is EmailVerificationState.Success -> {
                            emailVerificationDialog?.dismiss()
                            // Используем строку с плейсхолдером, если она есть, или просто строку
                            showSuccess(getString(R.string.toast_email_verified_successfully_redirect_login))
                            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
                        }
                        is EmailVerificationState.Error -> {
                            showError(state.message)
                        }
                        EmailVerificationState.Idle -> {
                            // Начальное состояние или после сброса
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
        val sendCodeButton = dialogView.findViewById<MaterialButton>(R.id.sendCodeButton) // Используем MaterialButton
        val confirmButton = dialogView.findViewById<MaterialButton>(R.id.confirmButton) // Используем MaterialButton

        confirmButton.isEnabled = false
        codeInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                confirmButton.isEnabled = s?.length == 6
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        emailVerificationDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_email_verification))
            .setView(dialogView)
            .setCancelable(false)
            .create()

        viewModel.sendVerificationCode(email)
        sendCodeButton.isEnabled = false
        startCodeTimer(sendCodeButton, getString(R.string.button_send_code_timed), getString(R.string.button_send_code))


        sendCodeButton.setOnClickListener {
            viewModel.sendVerificationCode(email)
            sendCodeButton.isEnabled = false
            startCodeTimer(sendCodeButton, getString(R.string.button_send_code_timed), getString(R.string.button_send_code))
        }

        confirmButton.setOnClickListener {
            val code = codeInput.text.toString()
            if (code.isBlank()) {
                codeInputLayout.error = getString(R.string.error_enter_code)
            } else {
                codeInputLayout.error = null
                viewModel.verifyEmail(email, code)
            }
        }
        emailVerificationDialog?.setOnDismissListener {
            codeTimer?.cancel()
            viewModel.resetEmailVerificationState()
        }
        emailVerificationDialog?.show()
    }

    // Убедимся, что тип Button здесь соответствует тому, что используется в dialog_email_verification.xml (MaterialButton)
    private fun startCodeTimer(button: MaterialButton, timedTextFormat: String, defaultText: String) {
        codeTimer?.cancel()
        button.isEnabled = false
        codeTimer = object : CountDownTimer(CODE_TIMER_DURATION, CODE_TIMER_INTERVAL) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                button.text = String.format(timedTextFormat, millisUntilFinished / 1000)
            }
            override fun onFinish() {
                button.text = defaultText
                button.isEnabled = true
            }
        }.start()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_error))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_ok), null)
            .show()
    }

    private fun showSuccess(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_success))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_ok), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        codeTimer?.cancel()
        emailVerificationDialog?.dismiss()
        _binding = null
    }
}