package com.sosiso4kawo.betaapp.ui.profile

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.CountDownTimer
import android.os.Bundle
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
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.betaapp.data.model.User
import com.sosiso4kawo.betaapp.databinding.FragmentEditProfileBinding
import com.sosiso4kawo.betaapp.ui.auth.AuthViewModel
import com.sosiso4kawo.betaapp.util.SessionManager
import com.yalantis.ucrop.UCrop
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val userRepository: com.sosiso4kawo.betaapp.data.repository.UserRepository by inject()
    private lateinit var sessionManager: SessionManager
    private var currentUser: User? = null

    // Для работы с reset password через AuthViewModel
    private val authViewModel: AuthViewModel by inject()

    // Выбор изображения из галереи
    private val getContent = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            startCrop(it)
        }
    }

    // Получение результата из uCrop
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
            Toast.makeText(requireContext(), "Ошибка обрезки изображения", Toast.LENGTH_SHORT).show()
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
            setTitle("Редактирование")
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

        // Загружаем email пользователя и отображаем его в emailTextView
        authViewModel.loadEmail { email ->
            binding.emailTextView.text = email.toString()
        }

        binding.resetPasswordButton.setOnClickListener {
            val email = binding.emailTextView.text.toString()
            if (email.isNotBlank() && email != "Email не найден") {
                showResetPasswordDialog(email)
            } else {
                Toast.makeText(requireContext(), "Email не найден", Toast.LENGTH_SHORT).show()
            }
        }

        loadCurrentProfile()
        setupSaveButton()
    }

    private fun loadCurrentProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getProfile().collect { result ->
                    when (result) {
                        is com.sosiso4kawo.betaapp.util.Result.Success -> {
                            currentUser = result.value
                            populateFields(result.value)
                        }
                        is com.sosiso4kawo.betaapp.util.Result.Failure -> {
                            Toast.makeText(
                                requireContext(),
                                "Ошибка загрузки профиля: ${result.exception.message}",
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
            // Email будет загружен через authViewModel.loadEmail()
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
                            is com.sosiso4kawo.betaapp.util.Result.Success -> {
                                Toast.makeText(requireContext(), "Профиль успешно обновлен", Toast.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }
                            is com.sosiso4kawo.betaapp.util.Result.Failure -> {
                                Toast.makeText(requireContext(), "Ошибка обновления профиля: ${result.exception.message}", Toast.LENGTH_SHORT).show()
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
        val sendCodeButton = dialogView.findViewById<MaterialButton>(R.id.sendCodeButton)
        val resetPasswordButton = dialogView.findViewById<MaterialButton>(R.id.resetPasswordButton)

        resetPasswordButton.isEnabled = false

        codeEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                resetPasswordButton.isEnabled = s?.length == 6
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Сброс пароля")
            .setView(dialogView)
            .setCancelable(true) // Разрешаем закрытие диалога при нажатии вне его области
            .create()

        // Добавляем слушатель для отмены диалога при нажатии вне его области
        dialog.setOnCancelListener {
            // Можно добавить всплывающее сообщение при отмене
            Toast.makeText(requireContext(), "Сброс пароля отменен", Toast.LENGTH_SHORT).show()
        }

        sendCodeButton.setOnClickListener {
            authViewModel.sendVerificationCode(email)
            sendCodeButton.isEnabled = false
            startCodeTimer(sendCodeButton)
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
            if (newPassword.isBlank() || confirmPassword.isBlank()) {
                newPasswordInputLayout.error = "Введите новый пароль и подтвердите его"
                return@setOnClickListener
            } else if (newPassword != confirmPassword) {
                newPasswordInputLayout.error = "Пароли не совпадают"
                return@setOnClickListener
            } else if (!isPasswordValid(newPassword)) {
                newPasswordInputLayout.error = "Пароль должен содержать минимум 8 символов, одну заглавную букву, одну маленькую букву, одну цифру и один специальный символ"
                return@setOnClickListener
            } else {
                newPasswordInputLayout.error = null
            }

            authViewModel.resetPassword(email, code, newPassword) { success, message ->
                if (success) {
                    Toast.makeText(requireContext(), "Пароль успешно сброшен", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Ошибка: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Добавляем обработчик нажатия кнопки "назад"
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dialog.dismiss()
                return@setOnKeyListener true
            }
            false
        }

        dialog.show()
    }

    private fun startCodeTimer(sendCodeButton: MaterialButton) {
        object : CountDownTimer(60000, 1000) {
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
        val passwordPattern = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}\$")
        return passwordPattern.matches(password)
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "cropped_image.jpg"))
        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(false)
            setShowCropGrid(false)
        }
        val uCropInstance = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(500, 500)
            .withOptions(options)
        cropActivityResult.launch(uCropInstance.getIntent(requireContext()))
    }

    private fun uploadCroppedAvatar(uri: Uri) {
        val file = File(uri.path ?: return)
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val token = "Bearer " + (sessionManager.getAccessToken() ?: "")

        viewLifecycleOwner.lifecycleScope.launch {
            val response = userRepository.uploadAvatar(token, body)
            if (response.isSuccessful) {
                Toast.makeText(requireContext(), "Аватар обновлен", Toast.LENGTH_SHORT).show()
                loadCurrentProfile()
            } else {
                Toast.makeText(requireContext(), "Ошибка загрузки аватара", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
