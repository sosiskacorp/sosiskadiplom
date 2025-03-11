package com.sosiso4kawo.betaapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosiso4kawo.betaapp.data.model.AuthResponse
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            val accessToken = sessionManager.getAccessToken()
            // Если токен существует и не истёк, считаем пользователя авторизованным
            if (accessToken != null && !sessionManager.isAccessTokenExpired()) {
                _uiState.value = AuthUiState.Success(
                    AuthResponse(
                        access_token = accessToken,
                        refresh_token = sessionManager.getRefreshToken() ?: "",
                        expiresIn = 48*3600L
                    )
                )
            } else {
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
                        sessionManager.clearSession()
                        AuthUiState.LoggedOut
                    },
                    onFailure = { AuthUiState.Error(it.message ?: "Ошибка при выходе") }
                )
            }
        }
    }
}