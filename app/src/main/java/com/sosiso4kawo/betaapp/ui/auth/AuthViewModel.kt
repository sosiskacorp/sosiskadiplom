package com.sosiso4kawo.betaapp.ui.auth

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosiso4kawo.betaapp.data.model.AuthResponse
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState: StateFlow<AuthUiState> = _uiState

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
            _uiState.value = AuthUiState.Error("Неверный формат почты")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.login(login, password).collect { result ->
                _uiState.value = result.fold(
                    onSuccess = { AuthUiState.Success(it) },
                    onFailure = { AuthUiState.Error(it.message ?: "Неизвестная ошибка") }
                )
            }
        }
    }

    fun register(email: String, password: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = AuthUiState.Error("Неверный формат почты")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.register(email, password).collect { result ->
                _uiState.value = result.fold(
                    onSuccess = { AuthUiState.Success(it) },
                    onFailure = { AuthUiState.Error(it.message ?: "Неизвестная ошибка") }
                )
            }
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
                    onFailure = { AuthUiState.Error(it.message ?: "Ошибка при выходе") }
                )
            }
        }
    }

    fun sendVerificationCode(email: String) {
        viewModelScope.launch {
            repository.sendVerificationCode(email)
            // Можно обновить UI состояние, если требуется
        }
    }

    fun verifyEmail(email: String, code: String, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.verifyEmail(email, code)
            result.fold(
                onSuccess = { callback(true, "") },
                onFailure = { callback(false, it.message ?: "Неизвестная ошибка") }
            )
        }
    }

    fun resetPassword(email: String, code: String, newPassword: String, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.resetPassword(email, code, newPassword)
            result.fold(
                onSuccess = { callback(true, "") },
                onFailure = { callback(false, it.message ?: "Неизвестная ошибка") }
            )
        }
    }

    fun loadEmail(callback: (String?) -> Unit) {
        viewModelScope.launch {
            val token = "Bearer " + (sessionManager.getAccessToken() ?: "")
            repository.getEmail(token).collect { result ->
                result.fold(
                    onSuccess = { callback(it.email) },
                    onFailure = {
                        Log.e("AuthViewModel", "Failed to load email: ${it.message}")
                        callback(null)
                    }
                )
            }
        }
    }
}
