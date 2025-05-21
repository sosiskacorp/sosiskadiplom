package com.sosiso4kawo.zschoolapp.ui.auth

import com.sosiso4kawo.zschoolapp.data.model.AuthResponse

sealed class AuthUiState {
    data object Initial : AuthUiState()
    data object Loading : AuthUiState()
    data object LoggedOut : AuthUiState()
    data class Success(val response: AuthResponse) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    data class ErrorEmailNotConfirmed(val email: String) : AuthUiState() // <<< Добавлено
}

sealed class EmailVerificationState {
    data object Idle : EmailVerificationState()
    data object Loading : EmailVerificationState()
    data object CodeSent : EmailVerificationState()
    data object Success : EmailVerificationState()
    data class Error(val message: String) : EmailVerificationState()
}

sealed class PasswordResetState {
    data object Idle : PasswordResetState()
    data object Loading : PasswordResetState()
    data object CodeSent : PasswordResetState()
    data object Success : PasswordResetState()
    data class Error(val message: String) : PasswordResetState()
}