@file:Suppress("SameParameterValue")
package com.sosiso4kawo.zschoolapp.ui.auth

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
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModel()
    private var codeTimer: CountDownTimer? = null
    private var emailVerificationDialog: AlertDialog? = null
    private var resetPasswordDialog: AlertDialog? = null

    companion object {
        private const val CODE_TIMER_DURATION = 60000L
        private const val CODE_TIMER_INTERVAL = 1000L
    }

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
        observeAuthState()
        observeEmailVerificationState()
        observePasswordResetState()
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
        binding.forgotPasswordText.setOnClickListener {
            val email = binding.loginInput.text.toString().trim()
            if (email.isNotBlank()) {
                showResetPasswordDialog(email)
            } else {
                Toast.makeText(context, getString(R.string.toast_enter_email_for_reset), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateInput(login: String, password: String): Boolean {
        var isValid = true
        if (login.isBlank()) {
            binding.loginLayout.error = getString(R.string.error_enter_login)
            isValid = false
        } else {
            binding.loginLayout.error = null
        }
        if (password.isBlank()) {
            binding.passwordLayout.error = getString(R.string.error_enter_password)
            isValid = false
        } else {
            binding.passwordLayout.error = null
        }
        return isValid
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true

                    when (state) {
                        is AuthUiState.Loading -> {
                            binding.loginButton.isEnabled = false
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is AuthUiState.Success -> {
                            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                        }
                        is AuthUiState.ErrorEmailNotConfirmed -> { // <<< ОБРАБОТКА НОВОГО СОСТОЯНИЯ
                            showEmailVerificationDialog(state.email) // Используем email из state
                        }
                        is AuthUiState.Error -> {
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // AuthUiState.Initial или AuthUiState.LoggedOut
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
                            // Можно показать индикатор
                        }
                        is EmailVerificationState.CodeSent -> {
                            Toast.makeText(requireContext(), getString(R.string.toast_verification_code_sent), Toast.LENGTH_SHORT).show()
                        }
                        is EmailVerificationState.Success -> {
                            emailVerificationDialog?.dismiss()
                            showSuccess(getString(R.string.toast_email_verified_successfully))
                            val email = binding.loginInput.text.toString()
                            val password = binding.passwordInput.text.toString()
                            if (email.isNotBlank() && password.isNotBlank()) {
                                viewModel.login(email, password)
                            }
                        }
                        is EmailVerificationState.Error -> {
                            showError(state.message)
                        }
                        EmailVerificationState.Idle -> {
                            // Idle state
                        }
                    }
                }
            }
        }
    }
    private fun observePasswordResetState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.passwordResetState.collect { state ->
                    when (state) {
                        is PasswordResetState.Loading -> {
                            // Можно показать индикатор
                        }
                        is PasswordResetState.CodeSent -> {
                            Toast.makeText(requireContext(), getString(R.string.toast_reset_code_sent), Toast.LENGTH_SHORT).show()
                        }
                        is PasswordResetState.Success -> {
                            resetPasswordDialog?.dismiss()
                            Toast.makeText(requireContext(), getString(R.string.toast_password_reset_successfully), Toast.LENGTH_SHORT).show()
                        }
                        is PasswordResetState.Error -> {
                            Toast.makeText(requireContext(), getString(R.string.error_generic_with_message, state.message), Toast.LENGTH_SHORT).show()
                        }
                        PasswordResetState.Idle -> {
                            // Idle state
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

    private fun showResetPasswordDialog(email: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_reset_password, null)
        val codeInputLayout = dialogView.findViewById<TextInputLayout>(R.id.codeInputLayout)
        val codeEditText = dialogView.findViewById<TextInputEditText>(R.id.codeEditText)
        val newPasswordInputLayout = dialogView.findViewById<TextInputLayout>(R.id.newPasswordInputLayout)
        val newPasswordEditText = dialogView.findViewById<TextInputEditText>(R.id.newPasswordEditText)
        val confirmPasswordEditText = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordEditText)
        // В dialog_reset_password.xml кнопки sendCodeButton и resetPasswordButton являются android.widget.Button
        val sendCodeButton = dialogView.findViewById<Button>(R.id.sendCodeButton)
        val resetPasswordButton = dialogView.findViewById<Button>(R.id.resetPasswordButton)


        resetPasswordButton.isEnabled = false
        codeEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                resetPasswordButton.isEnabled = s?.length == 6
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        resetPasswordDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        resetPasswordDialog?.setOnCancelListener {
            codeTimer?.cancel()
            Toast.makeText(requireContext(), getString(R.string.toast_password_reset_cancelled), Toast.LENGTH_SHORT).show()
            viewModel.resetPasswordResetState()
        }

        sendCodeButton.setOnClickListener {
            viewModel.sendVerificationCode(email, isForReset = true)
            sendCodeButton.isEnabled = false
            startCodeTimer(sendCodeButton, getString(R.string.button_send_code_timed), getString(R.string.button_send_code))
        }

        resetPasswordButton.setOnClickListener {
            val code = codeEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (code.isBlank()) {
                codeInputLayout.error = getString(R.string.error_enter_confirmation_code)
                return@setOnClickListener
            } else {
                codeInputLayout.error = null
            }

            if (newPassword.isBlank()) {
                newPasswordInputLayout.error = getString(R.string.error_enter_new_password)
                return@setOnClickListener
            } else if (!isPasswordValid(newPassword)) {
                newPasswordInputLayout.error = getString(R.string.error_password_requirements)
                return@setOnClickListener
            } else {
                newPasswordInputLayout.error = null
            }

            if (confirmPassword.isBlank()) {
                newPasswordInputLayout.error = getString(R.string.error_confirm_password)
                return@setOnClickListener
            } else if (newPassword != confirmPassword) {
                newPasswordInputLayout.error = getString(R.string.error_passwords_do_not_match)
                return@setOnClickListener
            } else {
                newPasswordInputLayout.error = null
            }
            viewModel.resetPassword(email, code, newPassword)
        }
        resetPasswordDialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                resetPasswordDialog?.dismiss()
                return@setOnKeyListener true
            }
            false
        }
        resetPasswordDialog?.show()
    }

    // Перегружаем startCodeTimer для MaterialButton (используется в showEmailVerificationDialog)
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

    // Перегружаем startCodeTimer для android.widget.Button (используется в showResetPasswordDialog)
    private fun startCodeTimer(button: Button, timedTextFormat: String, defaultText: String) {
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
        resetPasswordDialog?.dismiss()
        _binding = null
    }
}