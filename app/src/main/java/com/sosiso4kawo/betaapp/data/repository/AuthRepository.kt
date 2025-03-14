package com.sosiso4kawo.betaapp.data.repository

import android.util.Log
import com.sosiso4kawo.betaapp.data.api.AuthService
import com.sosiso4kawo.betaapp.data.model.*
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AuthRepository(private val authService: AuthService, private val sessionManager: SessionManager) {

    suspend fun login(login: String, password: String): Flow<Result<AuthResponse>> = flow {
        try {
            val request = LoginRequest(login, password)
            Log.d("AuthRepository", "Login request payload: ${com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(request)}")
            val response = authService.login(request)
            Log.d("AuthRepository", "Login response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}")

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    Log.d("AuthRepository", "Login successful, response body: $body")
                    if (body != null) {
                        // Сохраняем токены в SessionManager с expiresIn (если поле отсутствует, используем значение по умолчанию)
                        sessionManager.saveTokens(
                            accessToken = body.access_token,
                            refreshToken = body.refresh_token,
                            expiresIn = body.expiresIn ?: (48 * 3600L)
                        )
                        val profileResponse = authService.getProfile("Bearer ${body.access_token}")
                        if (profileResponse.isSuccessful) {
                            profileResponse.body()?.let { user ->
                                sessionManager.saveUserData(user)
                            }
                        }
                        emit(Result.success(body))
                        Log.d("AuthRepository", "Токены сохранены: access_token=${body.access_token}, refresh_token=${body.refresh_token}")
                    } else {
                        Log.e("AuthRepository", "Login successful but body is null")
                        emit(Result.failure(Exception("Успешная авторизация, но сервер вернул пустой ответ")))
                    }
                }
                response.code() == 401 -> {
                    Log.e("AuthRepository", "Login unauthorized (401): Invalid credentials")
                    emit(Result.failure(Exception("Неверный логин или пароль")))
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    // Если ошибка связана с неверным форматом почты, возвращаем соответствующее сообщение
                    if (errorBody.contains("Field validation for 'Email' failed on the 'email' tag")) {
                        emit(Result.failure(Exception("Неверный формат почты")))
                        return@flow
                    }
                    Log.e("AuthRepository", "Login error: code=${response.code()}, error=$errorBody, headers=${response.headers()}")

                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = com.google.gson.GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка авторизации"
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            "Ошибка при обработке ответа сервера"
                        }
                    }
                    emit(Result.failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login exception: ${e.message}, cause: ${e.cause}, stack trace: ${e.stackTrace.joinToString("\n")}")
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа сервера"
                else -> "Ошибка при авторизации: ${e.message}"
            }
            emit(Result.failure(Exception(errorMessage)))
        }
    }

    suspend fun register(email: String, password: String): Flow<Result<AuthResponse>> = flow {
        try {
            val request = RegisterRequest(email, password)
            Log.d("AuthRepository", "Registration request payload: ${com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(request)}")
            val response = authService.register(request)
            Log.d("AuthRepository", "Registration response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}")

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    Log.d("AuthRepository", "Registration successful, response body: $body")
                    if (body != null) {
                        emit(Result.success(body))
                    } else {
                        val errorMessage = if (response.code() == 204) {
                            "Регистрация успешна"
                        } else {
                            "Успешная регистрация, но сервер вернул пустой ответ"
                        }
                        Log.e("AuthRepository", errorMessage)
                        emit(Result.failure(Exception(errorMessage)))
                    }
                }
                response.code() == 409 -> {
                    Log.e("AuthRepository", "Registration conflict (409): User already exists")
                    emit(Result.failure(Exception("Пользователь с таким email или логином уже существует")))
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("AuthRepository", "Registration error: code=${response.code()}, error=$errorBody, headers=${response.headers()}")

                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = com.google.gson.GsonBuilder()
                                .setLenient()
                                .create()
                            val error = try {
                                gson.fromJson(errorBody, AuthError::class.java)
                            } catch (e: Exception) {
                                null
                            }
                            error?.message ?: "Ошибка регистрации"
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            "Ошибка при обработке ответа сервера"
                        }
                    }
                    emit(Result.failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Registration exception: ${e.message}, cause: ${e.cause}, stack trace: ${e.stackTrace.joinToString("\n")}")
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is com.google.gson.JsonSyntaxException -> "Сервер вернул некорректный ответ"
                else -> "Ошибка при регистрации: ${e.message}"
            }
            emit(Result.failure(Exception(errorMessage)))
        }
    }

    suspend fun refreshToken(refreshToken: String): Flow<Result<AuthResponse>> = flow {
        try {
            val request = RefreshTokenRequest(refreshToken)
            Log.d("AuthRepository", "Token refresh request payload: ${com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(request)}")
            val response = authService.refreshToken(request)
            Log.d("AuthRepository", "Token refresh response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}")

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    Log.d("AuthRepository", "Token refresh successful, response body: $body")
                    if (body != null) {
                        emit(Result.success(body))
                    } else {
                        Log.e("AuthRepository", "Token refresh successful but body is null")
                        emit(Result.failure(Exception("Ошибка обновления токена: сервер вернул пустой ответ")))
                    }
                }
                response.code() == 401 -> {
                    Log.e("AuthRepository", "Token refresh unauthorized (401): Invalid refresh token")
                    emit(Result.failure(Exception("Сессия истекла, требуется повторная авторизация")))
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("AuthRepository", "Token refresh error: code=${response.code()}, error=$errorBody, headers=${response.headers()}")

                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = com.google.gson.GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка обновления токена"
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            "Ошибка при обработке ответа сервера"
                        }
                    }
                    emit(Result.failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Token refresh exception: ${e.message}, cause: ${e.cause}, stack trace: ${e.stackTrace.joinToString("\n")}")
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа сервера"
                else -> "Ошибка при обновлении токена: ${e.message}"
            }
            emit(Result.failure(Exception(errorMessage)))
        }
    }

    suspend fun logout(): Flow<Result<Unit>> = flow {
        try {
            Log.d("AuthRepository", "Initiating logout request")

            // Получаем токен доступа
            val accessToken = sessionManager.getAccessToken()
            if (accessToken.isNullOrEmpty()) {
                Log.w("AuthRepository", "No access token found, skipping server logout")
                emit(Result.success(Unit)) // Если токена нет, считаем выход успешным
                return@flow
            }

            // Выполняем запрос logout с токеном
            val response = authService.logout("Bearer $accessToken")
            Log.d("AuthRepository", "Logout response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}")

            when {
                response.isSuccessful -> {
                    Log.d("AuthRepository", "Logout successful")
                    emit(Result.success(Unit))
                }
                response.code() == 401 -> {
                    Log.e("AuthRepository", "Logout unauthorized (401): Invalid or expired token")
                    emit(Result.success(Unit)) // Даже если токен недействителен, считаем выход успешным
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("AuthRepository", "Logout error: code=${response.code()}, error=$errorBody, headers=${response.headers()}")

                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = com.google.gson.GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка при выходе"
                        } catch (e: Exception) {
                            Log.e("AuthRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                            "Ошибка при обработке ответа сервера"
                        }
                    }
                    emit(Result.failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Logout exception: ${e.message}, cause: ${e.cause}, stack trace: ${e.stackTrace.joinToString("\n")}")
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа сервера"
                else -> "Ошибка при выходе: ${e.message}"
            }
            emit(Result.failure(Exception(errorMessage)))
        }
    }
}
