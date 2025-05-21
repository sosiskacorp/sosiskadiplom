package com.sosiso4kawo.zschoolapp.ui.auth

import android.app.Application // Для доступа к ресурсам
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel // Меняем ViewModel на AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sosiso4kawo.zschoolapp.R // Импорт R для доступа к строкам
import com.sosiso4kawo.zschoolapp.data.model.AuthResponse
import com.sosiso4kawo.zschoolapp.data.model.EmailNotConfirmedException // Импорт кастомного исключения
import com.sosiso4kawo.zschoolapp.data.repository.AuthRepository
import com.sosiso4kawo.zschoolapp.util.Result
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Вспомогательная функция fold остается такой же
fun <T, R> Result<T>.fold(onSuccess: (T) -> R, onFailure: (Exception) -> R): R = when (this) {
    is Result.Success -> onSuccess(value)
    is Result.Failure -> onFailure(exception)
}

class AuthViewModel(
    application: Application, // Добавляем Application
    private val repository: AuthRepository,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) { // Наследуемся от AndroidViewModel

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState: StateFlow<AuthUiState> = _uiState

    private val _emailVerificationState = MutableStateFlow<EmailVerificationState>(EmailVerificationState.Idle)
    val emailVerificationState: StateFlow<EmailVerificationState> = _emailVerificationState

    private val _passwordResetState = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            val accessToken = sessionManager.getAccessToken()
            if (accessToken != null && !sessionManager.isAccessTokenExpired()) {
                _uiState.value = AuthUiState.Success(
                    AuthResponse(
                        access_token = accessToken,
                        refresh_token = sessionManager.getRefreshToken() ?: ""
                    )
                )
            } else {
                _uiState.value = AuthUiState.LoggedOut
            }
        }
    }

    fun login(login: String, password: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(login).matches()) {
            _uiState.value = AuthUiState.Error(getApplication<Application>().getString(R.string.error_invalid_email_format_vm))
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.login(login, password)
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Success(it) },
                onFailure = { exception ->
                    if (exception is EmailNotConfirmedException) {
                        AuthUiState.ErrorEmailNotConfirmed(login) // Передаем email
                    } else {
                        AuthUiState.Error(exception.message ?: getApplication<Application>().getString(R.string.error_unknown_vm))
                    }
                }
            )
        }
    }

    fun register(email: String, password: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = AuthUiState.Error(getApplication<Application>().getString(R.string.error_invalid_email_format_vm))
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.register(email, password)
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Success(it) }, // После регистрации можно сразу считать Success или перенаправлять на верификацию
                onFailure = { AuthUiState.Error(it.message ?: getApplication<Application>().getString(R.string.error_unknown_vm)) }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.logout().collect { result ->
                _uiState.value = result.fold(
                    onSuccess = {
                        sessionManager.clearSession()
                        AuthUiState.LoggedOut
                    },
                    onFailure = { AuthUiState.Error(it.message ?: getApplication<Application>().getString(R.string.error_logout_failed_vm)) }
                )
            }
        }
    }

    fun sendVerificationCode(email: String, isForReset: Boolean = false) {
        viewModelScope.launch {
            if (isForReset) {
                _passwordResetState.value = PasswordResetState.Loading
            } else {
                _emailVerificationState.value = EmailVerificationState.Loading
            }
            val result = repository.sendVerificationCode(email)
            result.fold(
                onSuccess = {
                    if (isForReset) {
                        _passwordResetState.value = PasswordResetState.CodeSent
                    } else {
                        _emailVerificationState.value = EmailVerificationState.CodeSent
                    }
                },
                onFailure = {
                    val errorMessage = it.message ?: getApplication<Application>().getString(R.string.error_sending_code_vm)
                    if (isForReset) {
                        _passwordResetState.value = PasswordResetState.Error(errorMessage)
                    } else {
                        _emailVerificationState.value = EmailVerificationState.Error(errorMessage)
                    }
                }
            )
        }
    }

    fun verifyEmail(email: String, code: String) {
        viewModelScope.launch {
            _emailVerificationState.value = EmailVerificationState.Loading
            val result = repository.verifyEmail(email, code)
            result.fold(
                onSuccess = { _emailVerificationState.value = EmailVerificationState.Success },
                onFailure = {
                    _emailVerificationState.value = EmailVerificationState.Error(it.message ?: getApplication<Application>().getString(R.string.error_verifying_email_vm))
                }
            )
        }
    }

    fun resetPassword(email: String, code: String, newPassword: String) {
        viewModelScope.launch {
            _passwordResetState.value = PasswordResetState.Loading
            val result = repository.resetPassword(email, code, newPassword)
            result.fold(
                onSuccess = { _passwordResetState.value = PasswordResetState.Success },
                onFailure = {
                    _passwordResetState.value = PasswordResetState.Error(it.message ?: getApplication<Application>().getString(R.string.error_resetting_password_vm))
                }
            )
        }
    }

    fun resetEmailVerificationState() {
        _emailVerificationState.value = EmailVerificationState.Idle
    }

    fun resetPasswordResetState() {
        _passwordResetState.value = PasswordResetState.Idle
    }

    fun loadEmail(callback: (String?) -> Unit) {
        viewModelScope.launch {
            val token = "Bearer " + (sessionManager.getAccessToken() ?: "")
            repository.getEmail(token).collect { result ->
                result.fold(
                    onSuccess = { callback(it.email) },
                    onFailure = {
                        Log.e("AuthViewModel", getApplication<Application>().getString(R.string.log_failed_to_load_email, it.message))
                        callback(null) // Передаем null в колбэк при ошибке
                    }
                )
            }
        }
    }
}