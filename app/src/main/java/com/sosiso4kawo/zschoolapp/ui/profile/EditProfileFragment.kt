package com.sosiso4kawo.zschoolapp.ui.profile

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.zschoolapp.data.model.User
import com.sosiso4kawo.zschoolapp.data.repository.UserRepository
import com.sosiso4kawo.zschoolapp.databinding.FragmentEditProfileBinding
import com.sosiso4kawo.zschoolapp.ui.auth.AuthViewModel
import com.sosiso4kawo.zschoolapp.ui.auth.PasswordResetState
import com.sosiso4kawo.zschoolapp.util.SessionManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val userRepository: UserRepository by inject()
    private lateinit var sessionManager: SessionManager
    private var currentUser: User? = null
    private val authViewModel: AuthViewModel by viewModel()
    private var resetPasswordDialog: AlertDialog? = null
    private var codeTimer: CountDownTimer? = null

    companion object {
        private const val CODE_TIMER_DURATION = 60000L
        private const val CODE_TIMER_INTERVAL = 1000L
    }


    private val getContent = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            startCrop(it)
        }
    }
    private val cropActivityResult = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val resultUri = UCrop.getOutput(data)
                resultUri?.let { uri ->
                    binding.avatarImageView.setImageURI(uri)
                    uploadCroppedAvatar(uri)
                }
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.toast_crop_error), Toast.LENGTH_SHORT).show()
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        sessionManager = SessionManager(requireContext())
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_profile)
            setTitle(getString(R.string.header_title_edit_profile))
            showBackButton(true)
            setOnBackClickListener { findNavController().navigateUp() }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.avatarImageView.setOnClickListener {
            getContent.launch("image/*")
        }
        authViewModel.loadEmail { email ->
            binding.emailTextView.text = email ?: getString(R.string.text_email_not_found)
        }
        binding.resetPasswordButton.setOnClickListener {
            val email = binding.emailTextView.text.toString()
            if (email.isNotBlank() && email != getString(R.string.text_email_not_found)) {
                showResetPasswordDialog(email)
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_email_not_found_for_reset), Toast.LENGTH_SHORT).show()
            }
        }
        loadCurrentProfile()
        setupSaveButton()
        observePasswordResetState()
    }

    private fun observePasswordResetState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.passwordResetState.collect { state ->
                    when (state) {
                        is PasswordResetState.Loading -> {
                            // Можно показать индикатор в диалоге
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
                            // Начальное состояние или после сброса
                        }
                    }
                }
            }
        }
    }


    private fun loadCurrentProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getProfile().collect { result ->
                    when (result) {
                        is com.sosiso4kawo.zschoolapp.util.Result.Success -> {
                            currentUser = result.value
                            populateFields(result.value)
                        }
                        is com.sosiso4kawo.zschoolapp.util.Result.Failure -> {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.error_loading_profile_with_message, result.exception.message),
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
            if (!user.avatar.isNullOrEmpty()) {
                Glide.with(this@EditProfileFragment)
                    .load(user.avatar)
                    .placeholder(R.drawable.placeholder_avatar)
                    .error(R.drawable.error_avatar)
                    .circleCrop()
                    .into(avatarImageView)
            } else {
                avatarImageView.setImageResource(R.drawable.placeholder_avatar)
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
                avatar = null
            )
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    userRepository.updateProfile(updateRequest).collect { result ->
                        when (result) {
                            is com.sosiso4kawo.zschoolapp.util.Result.Success -> {
                                Toast.makeText(requireContext(), getString(R.string.toast_profile_updated_successfully), Toast.LENGTH_SHORT).show() // ИСПРАВЛЕНО
                                findNavController().navigateUp()
                            }
                            is com.sosiso4kawo.zschoolapp.util.Result.Failure -> {
                                Toast.makeText(requireContext(), getString(R.string.error_updating_profile_with_message, result.exception.message), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
    private fun showResetPasswordDialog(email: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_reset_password, null)
        val codeInputLayout = dialogView.findViewById<TextInputLayout>(R.id.codeInputLayout)
        val codeEditText = dialogView.findViewById<TextInputEditText>(R.id.codeEditText)
        val newPasswordInputLayout = dialogView.findViewById<TextInputLayout>(R.id.newPasswordInputLayout)
        val newPasswordEditText = dialogView.findViewById<TextInputEditText>(R.id.newPasswordEditText)
        val confirmPasswordEditText = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordEditText)
        val sendCodeButton = dialogView.findViewById<MaterialButton>(R.id.sendCodeButton) // Предполагаем MaterialButton
        val resetPasswordButton = dialogView.findViewById<MaterialButton>(R.id.resetPasswordButton) // Предполагаем MaterialButton

        resetPasswordButton.isEnabled = false
        codeEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                resetPasswordButton.isEnabled = s?.length == 6
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        resetPasswordDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_reset_password))
            .setView(dialogView)
            .setCancelable(true)
            .create()

        resetPasswordDialog?.setOnCancelListener {
            codeTimer?.cancel()
            Toast.makeText(requireContext(), getString(R.string.toast_password_reset_cancelled), Toast.LENGTH_SHORT).show()
            authViewModel.resetPasswordResetState()
        }

        sendCodeButton.setOnClickListener {
            authViewModel.sendVerificationCode(email, isForReset = true)
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
            authViewModel.resetPassword(email, code, newPassword)
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

    // Убедитесь, что тип Button здесь соответствует тому, что используется в dialog_reset_password.xml (MaterialButton)
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

    private fun isPasswordValid(password: String): Boolean {
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }
        val hasMinLength = password.length >= 8
        val hasNumber = password.any { it.isDigit() }
        return hasUpperCase && hasLowerCase && hasSpecialChar && hasMinLength && hasNumber
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationFileName = "cropped_image_${System.currentTimeMillis()}.jpg"
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))
        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(false)
            setShowCropGrid(false)
            setCompressionQuality(90)
        }
        val uCropInstance = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(500, 500)
            .withOptions(options)
        cropActivityResult.launch(uCropInstance.getIntent(requireContext()))
    }
    private fun uploadCroppedAvatar(uri: Uri) {
        val filePath = uri.path ?: return
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), getString(R.string.toast_error_file_not_found_for_upload), Toast.LENGTH_SHORT).show()
            return
        }

        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val token = "Bearer " + (sessionManager.getAccessToken() ?: run {
            Toast.makeText(requireContext(), getString(R.string.toast_error_auth_token_missing), Toast.LENGTH_SHORT).show()
            return
        })

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = userRepository.uploadAvatar(token, body)
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), getString(R.string.toast_avatar_updated_successfully), Toast.LENGTH_SHORT).show()
                    loadCurrentProfile()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.toast_error_uploading_avatar_code, response.code()), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_error_uploading_avatar_exception, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        codeTimer?.cancel()
        resetPasswordDialog?.dismiss()
        _binding = null
    }
}