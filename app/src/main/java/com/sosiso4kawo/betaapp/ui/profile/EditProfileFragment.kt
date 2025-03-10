package com.sosiso4kawo.betaapp.ui.profile

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.betaapp.data.model.User
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.databinding.FragmentEditProfileBinding
import com.sosiso4kawo.betaapp.util.SessionManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.android.ext.android.inject
import java.io.File

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val userRepository: UserRepository by inject()
    private lateinit var sessionManager: SessionManager
    private var currentUser: User? = null

    // Выбор изображения из галереи
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            startCrop(it)
        }
    }

    // Получение результата из uCrop
    private val cropActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val resultUri = UCrop.getOutput(data)
                resultUri?.let { uri ->
                    // Отображаем обрезанное изображение
                    binding.avatarImageView.setImageURI(uri)
                    // Загружаем обрезанный аватар на сервер
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

        // При нажатии на аватар открываем галерею
        binding.avatarImageView.setOnClickListener {
            getContent.launch("image/*")
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
            // Если URL аватара указан, загружаем его с трансформацией circleCrop
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
                avatar = null // Аватар обновляется через отдельный эндпоинт
            )

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    userRepository.updateProfile(updateRequest).collect { result ->
                        when (result) {
                            is com.sosiso4kawo.betaapp.util.Result.Success -> {
                                Toast.makeText(
                                    requireContext(),
                                    "Профиль успешно обновлен",
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().navigateUp()
                            }
                            is com.sosiso4kawo.betaapp.util.Result.Failure -> {
                                Toast.makeText(
                                    requireContext(),
                                    "Ошибка обновления профиля: ${result.exception.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    // Запуск uCrop с настройками для круговой обрезки
    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "cropped_image.jpg"))
        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)  // Включить круговой оверлей
            setShowCropFrame(false)     // Скрыть рамку
            setShowCropGrid(false)      // Скрыть сетку
        }
        val uCropInstance = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(500, 500)
            .withOptions(options)
        cropActivityResult.launch(uCropInstance.getIntent(requireContext()))
    }

    // Загрузка обрезанного аватара на сервер
    private fun uploadCroppedAvatar(uri: Uri) {
        val file = File(uri.path ?: return)
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val token = "Bearer " + (sessionManager.getAccessToken() ?: "")

        viewLifecycleOwner.lifecycleScope.launch {
            val response = userRepository.uploadAvatar(token, body)
            if (response.isSuccessful) {
                Toast.makeText(requireContext(), "Аватар обновлен", Toast.LENGTH_SHORT).show()
                // Обновляем профиль после успешной загрузки
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
