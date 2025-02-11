package com.sosiso4kawo.betaapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.data.model.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

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

    fun register(email: String, login: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.register(email, login, password).collect { result ->
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
                    onSuccess = { AuthUiState.LoggedOut },
                    onFailure = { AuthUiState.Error(it.message ?: "Ошибка при выходе") }
                )
            }
        }
    }
}

sealed class AuthUiState {
    object Initial : AuthUiState()
    object Loading : AuthUiState()
    object LoggedOut : AuthUiState()
    data class Success(val response: AuthResponse) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}