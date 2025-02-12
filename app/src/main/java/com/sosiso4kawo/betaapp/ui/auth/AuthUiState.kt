package com.sosiso4kawo.betaapp.ui.auth

import com.sosiso4kawo.betaapp.data.model.AuthResponse

sealed class AuthUiState {
    object Initial : AuthUiState()
    object Loading : AuthUiState()
    object LoggedOut : AuthUiState()
    data class Success(val response: AuthResponse) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
