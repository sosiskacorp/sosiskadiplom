package com.sosiso4kawo.zschoolapp.data.model

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String
)

data class RefreshTokenRequest(
    val refresh_token: String
)

data class AuthResponse(
    val access_token: String,
    val refresh_token: String,
)

data class AuthError(
    val message: String
)

data class VerificationRequest(
    val email: String,
    val code: String
)

data class PasswordResetRequest(
    val email: String,
    val code: String,
    val new_password: String
)

data class EmailResponse(
    val email: String
)