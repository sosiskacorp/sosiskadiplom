package com.sosiso4kawo.betaapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosiso4kawo.betaapp.data.model.AuthResponse
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            val accessToken = sessionManager.getAccessToken()
            if (!accessToken.isNullOrEmpty()) {
                // Если токен существует, пользователь уже авторизован.
                // Передаём значение по умолчанию для expiresIn, например 3600 секунд.
                _uiState.value = AuthUiState.Success(AuthResponse(accessToken, "", 60L))
            } else {
                // Если токена нет, пользователь не авторизован.
                _uiState.value = AuthUiState.LoggedOut
            }
        }
    }

    fun login(login: String, password: String) {
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
                        sessionManager.clearSession() // Очищаем сессию при выходе
                        AuthUiState.LoggedOut
                    },
                    onFailure = { AuthUiState.Error(it.message ?: "Ошибка при выходе") }
                )
            }
        }
    }
}