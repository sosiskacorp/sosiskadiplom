package com.sosiso4kawo.zschoolapp.data.repository

import android.content.Context // <<< ИМПОРТ, если передаем контекст
import android.util.Log
import com.google.gson.GsonBuilder
import com.sosiso4kawo.zschoolapp.R // <<< ИМПОРТ для ресурсов
import com.sosiso4kawo.zschoolapp.data.api.AuthService
import com.sosiso4kawo.zschoolapp.data.model.AuthError
import com.sosiso4kawo.zschoolapp.data.model.EmailNotConfirmedException // <<< НОВЫЙ ИМПОРТ
import com.sosiso4kawo.zschoolapp.data.model.EmailResponse
import com.sosiso4kawo.zschoolapp.data.model.LoginRequest
import com.sosiso4kawo.zschoolapp.data.model.AuthResponse
import com.sosiso4kawo.zschoolapp.data.model.RegisterRequest
import com.sosiso4kawo.zschoolapp.data.model.RefreshTokenRequest
import com.sosiso4kawo.zschoolapp.data.model.PasswordResetRequest
import com.sosiso4kawo.zschoolapp.data.model.VerificationRequest
import com.sosiso4kawo.zschoolapp.util.SessionManager
import com.sosiso4kawo.zschoolapp.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class AuthRepository(
    private val context: Context, // <<< ПРИМЕР: Добавляем контекст
    private val authService: AuthService,
    private val sessionManager: SessionManager
) {
    suspend fun login(login: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = LoginRequest(login, password)
                Log.d(
                    "AuthRepository",
                    "Login request payload: ${GsonBuilder().setPrettyPrinting().create().toJson(request)}"
                )
                val response = authService.login(request)
                Log.d(
                    "AuthRepository",
                    "Login response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        sessionManager.saveTokens(
                            accessToken = body.access_token,
                            refreshToken = body.refresh_token,
                            expiresIn = 604800L
                        )
                        val profileResponse = authService.getProfile("Bearer ${body.access_token}")
                        if (profileResponse.isSuccessful) {
                            profileResponse.body()?.let { user ->
                                sessionManager.saveUserData(user)
                            }
                        }
                        Log.d(
                            "AuthRepository",
                            "Токены сохранены: access_token=${body.access_token}, refresh_token=${body.refresh_token}"
                        )
                        Result.Success(body)
                    } else {
                        Log.e("AuthRepository", "Login successful but body is null")
                        Result.Failure(Exception(context.getString(R.string.error_login_success_empty_body_repo)))
                    }
                } else if (response.code() == 401) {
                    val errorBody = response.errorBody()?.string() ?: ""
                    try {
                        val gson = GsonBuilder().setLenient().create()
                        val error = gson.fromJson(errorBody, AuthError::class.java)
                        if (error?.message == "email not confirmed") {
                            Log.d("AuthRepository", "Login failed: Email not confirmed")
                            Result.Failure(EmailNotConfirmedException("email not confirmed")) // Используем кастомное исключение
                        } else {
                            Log.e("AuthRepository", "Login unauthorized (401): Invalid credentials")
                            Result.Failure(Exception(context.getString(R.string.error_invalid_login_or_password_repo)))
                        }
                    } catch (e: Exception) {
                        Log.e("AuthRepository", "Error parsing 401 response: ${e.message}")
                        Result.Failure(Exception(context.getString(R.string.error_invalid_login_or_password_repo)))
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    if (errorBody.contains("Field validation for 'Email' failed on the 'email' tag")) {
                        Result.Failure(Exception(context.getString(R.string.error_invalid_email_format_repo)))
                    } else {
                        Log.e(
                            "AuthRepository",
                            "Login error: code=${response.code()}, error=$errorBody, headers=${response.headers()}"
                        )
                        val errorMessage = if (errorBody.isBlank()) {
                            context.getString(R.string.error_server_with_code_repo, response.code())
                        } else {
                            try {
                                val gson = GsonBuilder().setLenient().create()
                                val error = gson.fromJson(errorBody, AuthError::class.java)
                                error?.message ?: context.getString(R.string.error_auth_failed_repo)
                            } catch (e: Exception) {
                                Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                                context.getString(R.string.error_parsing_server_response_repo)
                            }
                        }
                        Result.Failure(Exception(errorMessage))
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Login exception: ${e.message}, cause: ${e.cause}")
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> context.getString(R.string.error_no_internet_connection_repo)
                    is java.net.SocketTimeoutException -> context.getString(R.string.error_server_timeout_repo)
                    is com.google.gson.JsonSyntaxException -> context.getString(R.string.error_parsing_server_response_repo)
                    else -> context.getString(R.string.error_auth_exception_repo, e.message)
                }
                Result.Failure(Exception(errorMessage))
            }
        }

    suspend fun register(email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = RegisterRequest(email, password)
                Log.d(
                    "AuthRepository",
                    "Registration request payload: ${GsonBuilder().setPrettyPrinting().create().toJson(request)}"
                )
                val response = authService.register(request)
                Log.d(
                    "AuthRepository",
                    "Registration response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Result.Success(body)
                    } else {
                        val errorMessage = if (response.code() == 204) {
                            context.getString(R.string.info_registration_successful_repo)
                        } else {
                            context.getString(R.string.error_registration_success_empty_body_repo)
                        }
                        Log.e("AuthRepository", errorMessage)
                        Result.Failure(Exception(errorMessage)) // Считаем это ошибкой, если тело null, а код не 204
                    }
                } else if (response.code() == 409) {
                    Log.e("AuthRepository", "Registration conflict (409): User already exists")
                    Result.Failure(Exception(context.getString(R.string.error_user_already_exists_repo)))
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e(
                        "AuthRepository",
                        "Registration error: code=${response.code()}, error=$errorBody, headers=${response.headers()}"
                    )
                    val errorMessage = if (errorBody.isBlank()) {
                        context.getString(R.string.error_server_with_code_repo, response.code())
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: context.getString(R.string.error_registration_failed_repo)
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            context.getString(R.string.error_parsing_server_response_repo)
                        }
                    }
                    Result.Failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Registration exception: ${e.message}, cause: ${e.cause}")
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> context.getString(R.string.error_no_internet_connection_repo)
                    is java.net.SocketTimeoutException -> context.getString(R.string.error_server_timeout_repo)
                    is com.google.gson.JsonSyntaxException -> context.getString(R.string.error_parsing_server_response_repo_malformed)
                    else -> context.getString(R.string.error_registration_exception_repo, e.message)
                }
                Result.Failure(Exception(errorMessage))
            }
        }

    suspend fun refreshToken(refreshToken: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = RefreshTokenRequest(refreshToken)
                Log.d(
                    "AuthRepository",
                    "Token refresh request payload: ${GsonBuilder().setPrettyPrinting().create().toJson(request)}"
                )
                val response = authService.refreshToken(request)
                Log.d(
                    "AuthRepository",
                    "Token refresh response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        sessionManager.saveTokens(
                            accessToken = body.access_token,
                            refreshToken = body.refresh_token,
                            expiresIn = 604800L
                        )
                        Log.d("AuthRepository", "Token refresh successful, response body: $body")
                        Result.Success(body)
                    } else {
                        Log.e("AuthRepository", "Token refresh successful but body is null")
                        Result.Failure(Exception(context.getString(R.string.error_token_refresh_empty_body_repo)))
                    }
                } else if (response.code() == 401) {
                    Log.e("AuthRepository", "Token refresh unauthorized (401): Invalid refresh token")
                    Result.Failure(Exception(context.getString(R.string.error_session_expired_relogin_repo)))
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("AuthRepository", "Token refresh error: code=${response.code()}, error=$errorBody")
                    val errorMessage = if (errorBody.isBlank()) {
                        context.getString(R.string.error_server_with_code_repo, response.code())
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: context.getString(R.string.error_token_refresh_failed_repo)
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            context.getString(R.string.error_parsing_server_response_repo)
                        }
                    }
                    Result.Failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Token refresh exception: ${e.message}, cause: ${e.cause}")
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> context.getString(R.string.error_no_internet_connection_repo)
                    is java.net.SocketTimeoutException -> context.getString(R.string.error_server_timeout_repo)
                    is com.google.gson.JsonSyntaxException -> context.getString(R.string.error_parsing_server_response_repo)
                    else -> context.getString(R.string.error_token_refresh_exception_repo, e.message)
                }
                Result.Failure(Exception(errorMessage))
            }
        }
    fun logout(): Flow<Result<Unit>> = flow {
        try {
            Log.d("AuthRepository", "Initiating logout request")
            val accessToken = sessionManager.getAccessToken()
            if (accessToken.isNullOrEmpty()) {
                Log.w("AuthRepository", "No access token found, skipping server logout")
                emit(Result.Success(Unit))
                return@flow
            }
            val response = authService.logout("Bearer $accessToken")
            Log.d(
                "AuthRepository",
                "Logout response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}"
            )
            when {
                response.isSuccessful -> {
                    Log.d("AuthRepository", "Logout successful")
                    emit(Result.Success(Unit))
                }
                response.code() == 401 -> { // Если токен уже невалиден на сервере, считаем выход успешным локально
                    Log.e("AuthRepository", "Logout unauthorized (401): Invalid or expired token")
                    emit(Result.Success(Unit))
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e(
                        "AuthRepository",
                        "Logout error: code=${response.code()}, error=$errorBody, headers=${response.headers()}"
                    )
                    val errorMessage = if (errorBody.isBlank()) {
                        context.getString(R.string.error_server_with_code_repo, response.code())
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: context.getString(R.string.error_logout_failed_repo)
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            context.getString(R.string.error_parsing_server_response_repo)
                        }
                    }
                    emit(Result.Failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e(
                "AuthRepository",
                "Logout exception: ${e.message}, cause: ${e.cause}, stack trace: ${e.stackTrace.joinToString("\n")}"
            )
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> context.getString(R.string.error_no_internet_connection_repo)
                is java.net.SocketTimeoutException -> context.getString(R.string.error_server_timeout_repo)
                is com.google.gson.JsonSyntaxException -> context.getString(R.string.error_parsing_server_response_repo)
                else -> context.getString(R.string.error_logout_exception_repo, e.message)
            }
            emit(Result.Failure(Exception(errorMessage)))
        }
    }
    suspend fun sendVerificationCode(email: String): Result<Unit> {
        return try {
            val response = authService.sendVerificationCode(email)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Failure(Exception(context.getString(R.string.error_sending_code_failed_repo)))
            }
        } catch (e: Exception) {
            Result.Failure(e) // Исключение будет обработано во ViewModel
        }
    }
    suspend fun verifyEmail(email: String, code: String): Result<Unit> {
        return try {
            val request = VerificationRequest(email, code)
            val response = authService.verifyEmail(request)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Failure(Exception(context.getString(R.string.error_email_verification_failed_repo)))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    fun getEmail(token: String): Flow<Result<EmailResponse>> = flow {
        try {
            val response = authService.getEmail(token)
            Log.d("AuthRepository", "Get email response: code=${response.code()}")
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        Log.d("AuthRepository", "Get email successful, email: ${body.email}")
                        emit(Result.Success(body))
                    } else {
                        Log.e("AuthRepository", "Get email successful but body is null")
                        emit(Result.Failure(Exception(context.getString(R.string.error_get_email_empty_body_repo))))
                    }
                }
                response.code() == 401 -> {
                    Log.e("AuthRepository", "Get email unauthorized (401): Invalid token")
                    emit(Result.Failure(Exception(context.getString(R.string.error_auth_required_repo))))
                }
                response.code() == 404 -> {
                    Log.e("AuthRepository", "Get email not found (404)")
                    emit(Result.Failure(Exception(context.getString(R.string.error_email_not_found_repo))))
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("AuthRepository", "Get email error: code=${response.code()}, error=$errorBody")
                    val errorMessage = if (errorBody.isBlank()) {
                        context.getString(R.string.error_server_with_code_repo, response.code())
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: context.getString(R.string.error_get_email_failed_repo)
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}")
                            context.getString(R.string.error_parsing_server_response_repo)
                        }
                    }
                    emit(Result.Failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Get email exception: ${e.message}")
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> context.getString(R.string.error_no_internet_connection_repo)
                is java.net.SocketTimeoutException -> context.getString(R.string.error_server_timeout_repo)
                is com.google.gson.JsonSyntaxException -> context.getString(R.string.error_parsing_server_response_repo)
                else -> context.getString(R.string.error_get_email_exception_repo, e.message)
            }
            emit(Result.Failure(Exception(errorMessage)))
        }
    }
    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Unit> {
        return try {
            val request = PasswordResetRequest(email, code, newPassword)
            val response = authService.resetPassword(request)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                val errorMessage = when (response.code()) {
                    400 -> context.getString(R.string.error_invalid_confirmation_code_repo)
                    404 -> context.getString(R.string.error_email_not_found_for_reset_repo)
                    else -> context.getString(R.string.error_password_reset_failed_with_code_repo, response.code())
                }
                Result.Failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> context.getString(R.string.error_no_internet_connection_repo)
                is java.net.SocketTimeoutException -> context.getString(R.string.error_server_timeout_repo)
                else -> context.getString(R.string.error_password_reset_exception_repo, e.message)
            }
            Result.Failure(Exception(errorMessage))
        }
    }
}