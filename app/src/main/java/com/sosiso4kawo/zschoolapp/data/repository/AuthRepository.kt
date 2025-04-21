package com.sosiso4kawo.zschoolapp.data.repository

import android.util.Log
import com.google.gson.GsonBuilder
import com.sosiso4kawo.zschoolapp.data.api.AuthService
import com.sosiso4kawo.zschoolapp.data.model.AuthError
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
                        Result.Failure(Exception("Успешная авторизация, но сервер вернул пустой ответ"))
                    }
                } else if (response.code() == 401) {
                    val errorBody = response.errorBody()?.string() ?: ""
                    try {
                        val gson = GsonBuilder().setLenient().create()
                        val error = gson.fromJson(errorBody, AuthError::class.java)
                        if (error?.message == "email not confirmed") {
                            Log.d("AuthRepository", "Login failed: Email not confirmed")
                            Result.Failure(Exception("email not confirmed"))
                        } else {
                            Log.e("AuthRepository", "Login unauthorized (401): Invalid credentials")
                            Result.Failure(Exception("Неверный логин или пароль"))
                        }
                    } catch (e: Exception) {
                        Log.e("AuthRepository", "Error parsing 401 response: ${e.message}")
                        Result.Failure(Exception("Неверный логин или пароль"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    if (errorBody.contains("Field validation for 'Email' failed on the 'email' tag")) {
                        Result.Failure(Exception("Неверный формат почты"))
                    } else {
                        Log.e(
                            "AuthRepository",
                            "Login error: code=${response.code()}, error=$errorBody, headers=${response.headers()}"
                        )
                        val errorMessage = if (errorBody.isBlank()) {
                            "Ошибка сервера (${response.code()})"
                        } else {
                            try {
                                val gson = GsonBuilder().setLenient().create()
                                val error = gson.fromJson(errorBody, AuthError::class.java)
                                error?.message ?: "Ошибка авторизации"
                            } catch (e: Exception) {
                                Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                                "Ошибка при обработке ответа сервера"
                            }
                        }
                        Result.Failure(Exception(errorMessage))
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Login exception: ${e.message}, cause: ${e.cause}")
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Нет подключения к интернету"
                    is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                    is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
                    else -> "Ошибка при авторизации: ${e.message}"
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
                            "Регистрация успешна"
                        } else {
                            "Успешная регистрация, но сервер вернул пустой ответ"
                        }
                        Log.e("AuthRepository", errorMessage)
                        Result.Failure(Exception(errorMessage))
                    }
                } else if (response.code() == 409) {
                    Log.e("AuthRepository", "Registration conflict (409): User already exists")
                    Result.Failure(Exception("Пользователь с таким email или логином уже существует"))
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e(
                        "AuthRepository",
                        "Registration error: code=${response.code()}, error=$errorBody, headers=${response.headers()}"
                    )
                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка регистрации"
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            "Ошибка при обработке ответа от сервера"
                        }
                    }
                    Result.Failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Registration exception: ${e.message}, cause: ${e.cause}")
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Нет подключения к интернету"
                    is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                    is com.google.gson.JsonSyntaxException -> "Сервер вернул некорректный ответ"
                    else -> "Ошибка при регистрации: ${e.message}"
                }
                Result.Failure(Exception(errorMessage))
            }
        }

    // --- Метод refreshToken ---
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
                        Result.Failure(Exception("Ошибка обновления токена: сервер вернул пустой ответ"))
                    }
                } else if (response.code() == 401) {
                    Log.e("AuthRepository", "Token refresh unauthorized (401): Invalid refresh token")
                    Result.Failure(Exception("Сессия истекла, требуется повторная авторизация"))
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("AuthRepository", "Token refresh error: code=${response.code()}, error=$errorBody")
                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка обновления токена"
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            "Ошибка при обработке ответа от сервера"
                        }
                    }
                    Result.Failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Token refresh exception: ${e.message}, cause: ${e.cause}")
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Нет подключения к интернету"
                    is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                    is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
                    else -> "Ошибка при обновлении токена: ${e.message}"
                }
                Result.Failure(Exception(errorMessage))
            }
        }

    fun logout(): Flow<Result<Unit>> = flow {
        try {
            Log.d("AuthRepository", "Initiating logout request")

            // Получаем токен доступа
            val accessToken = sessionManager.getAccessToken()
            if (accessToken.isNullOrEmpty()) {
                Log.w("AuthRepository", "No access token found, skipping server logout")
                emit(Result.Success(Unit))
                return@flow
            }

            // Выполняем запрос logout с токеном
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
                response.code() == 401 -> {
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
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка при выходе"
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            "Ошибка при обработке ответа сервера"
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
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
                else -> "Ошибка при выходе: ${e.message}"
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
                Result.Failure(Exception("Ошибка отправки кода"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun verifyEmail(email: String, code: String): Result<Unit> {
        return try {
            val request = VerificationRequest(email, code)
            val response = authService.verifyEmail(request)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Failure(Exception("Ошибка подтверждения почты"))
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
                        emit(Result.Failure(Exception("Не удалось получить email: пустой ответ")))
                    }
                }
                response.code() == 401 -> {
                    Log.e("AuthRepository", "Get email unauthorized (401): Invalid token")
                    emit(Result.Failure(Exception("Требуется авторизация")))
                }
                response.code() == 404 -> {
                    Log.e("AuthRepository", "Get email not found (404)")
                    emit(Result.Failure(Exception("Email не найден")))
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("AuthRepository", "Get email error: code=${response.code()}, error=$errorBody")
                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка получения email"
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}")
                            "Ошибка при обработке ответа сервера"
                        }
                    }
                    emit(Result.Failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Get email exception: ${e.message}")
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
                else -> "Ошибка при получении email: ${e.message}"
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
                    400 -> "Неверный код подтверждения"
                    404 -> "Email не найден"
                    else -> "Ошибка сброса пароля (${response.code()})"
                }
                Result.Failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                else -> "Ошибка при сбросе пароля: ${e.message}"
            }
            Result.Failure(Exception(errorMessage))
        }
    }
}
