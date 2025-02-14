package com.sosiso4kawo.betaapp.data.model

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
    val expiresIn: Long?
)

data class AuthError(
    val message: String
)