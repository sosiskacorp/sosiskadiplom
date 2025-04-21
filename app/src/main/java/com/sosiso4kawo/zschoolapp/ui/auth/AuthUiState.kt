package com.sosiso4kawo.zschoolapp.ui.auth

import com.sosiso4kawo.zschoolapp.data.model.AuthResponse

sealed class AuthUiState {
    data object Initial : AuthUiState()
    data object Loading : AuthUiState()
    data object LoggedOut : AuthUiState()
    data class Success(val response: AuthResponse) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
