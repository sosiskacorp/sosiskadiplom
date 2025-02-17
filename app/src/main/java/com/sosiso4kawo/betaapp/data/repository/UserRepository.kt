package com.sosiso4kawo.betaapp.data.repository

import UserService
import android.util.Log
import com.google.gson.GsonBuilder
import com.sosiso4kawo.betaapp.data.model.AuthError
import com.sosiso4kawo.betaapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.betaapp.data.model.User
import com.sosiso4kawo.betaapp.util.Result
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response

class UserRepository(
    private val userService: UserService,
    private val sessionManager: SessionManager
) {

    /**
     * Получает данные профиля пользователя.
     */
    suspend fun getProfile(): Flow<Result<User>> = flow {
        try {
            Log.d("UserRepository", "Fetching user profile")
            val accessToken = sessionManager.getAccessToken()
            if (accessToken.isNullOrEmpty()) {
                Log.e("UserRepository", "Access token is null or empty")
                emit(Result.Failure(Exception("Токен доступа отсутствует")))
                return@flow
            }

            // Проверяем локальные данные пользователя
            val localUser = sessionManager.getUserData()
            if (localUser != null) {
                Log.d("UserRepository", "Using cached user data: $localUser")
                emit(Result.Success(localUser))
            }

            // Пытаемся получить данные с сервера
            val response = userService.getProfile("Bearer $accessToken")
            Log.d(
                "UserRepository",
                "Profile response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}"
            )

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        Log.d("UserRepository", "Profile fetched successfully: $body")
                        // Сохраняем данные пользователя локально
                        sessionManager.saveUserData(body)
                        emit(Result.Success(body))
                    } else {
                        Log.e("UserRepository", "Profile response is null")
                        emit(Result.Failure(Exception("Профиль пользователя пуст")))
                    }
                }
                response.code() == 401 -> {
                    Log.e("UserRepository", "Unauthorized access (401): Invalid or expired token")
                    emit(Result.Failure(Exception("Недействительный или просроченный токен")))
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e(
                        "UserRepository",
                        "Profile fetch error: code=${response.code()}, error=$errorBody, headers=${response.headers()}"
                    )
                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка при получении профиля"
                        } catch (e: Exception) {
                            Log.e(
                                "UserRepository",
                                "Error parsing response: ${e.message}, raw error body: $errorBody"
                            )
                            "Ошибка при обработке ответа сервера"
                        }
                    }
                    emit(Result.Failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e(
                "UserRepository",
                "Profile fetch exception: ${e.message}, cause: ${e.cause}, stack trace: ${e.stackTrace.joinToString("\n")}"
            )
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа сервера"
                else -> "Ошибка при получении профиля: ${e.message}"
            }
            emit(Result.Failure(Exception(errorMessage)))
        }
    }

    /**
     * Обновляет данные профиля пользователя.
     */
    suspend fun updateProfile(request: UpdateProfileRequest): Flow<Result<Unit>> = flow {
        try {
            Log.d("UserRepository", "Updating user profile")
            val accessToken = sessionManager.getAccessToken()
            if (accessToken.isNullOrEmpty()) {
                Log.e("UserRepository", "Access token is null or empty")
                emit(Result.Failure(Exception("Токен доступа отсутствует")))
                return@flow
            }

            val response = userService.updateProfile("Bearer $accessToken", request)
            Log.d(
                "UserRepository",
                "Profile update response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}"
            )

            when {
                response.isSuccessful -> {
                    Log.d("UserRepository", "Profile updated successfully")
                    emit(Result.Success(Unit)) // Используем Result.Success
                }
                response.code() == 401 -> {
                    Log.e("UserRepository", "Unauthorized access (401): Invalid or expired token")
                    emit(Result.Failure(Exception("Недействительный или просроченный токен")))
                }
                else -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e(
                        "UserRepository",
                        "Profile update error: code=${response.code()}, error=$errorBody, headers=${response.headers()}"
                    )
                    val errorMessage = if (errorBody.isBlank()) {
                        "Ошибка сервера (${response.code()})"
                    } else {
                        try {
                            val gson = GsonBuilder().setLenient().create()
                            val error = gson.fromJson(errorBody, AuthError::class.java)
                            error?.message ?: "Ошибка при обновлении профиля"
                        } catch (e: Exception) {
                            Log.e(
                                "UserRepository",
                                "Error parsing response: ${e.message}, raw error body: $errorBody"
                            )
                            "Ошибка при обработке ответа сервера"
                        }
                    }
                    emit(Result.Failure(Exception(errorMessage)))
                }
            }
        } catch (e: Exception) {
            Log.e(
                "UserRepository",
                "Profile update exception: ${e.message}, cause: ${e.cause}, stack trace: ${e.stackTrace.joinToString("\n")}"
            )
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Нет подключения к интернету"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is com.google.gson.JsonSyntaxException -> "Ошибка при обработке ответа сервера"
                else -> "Ошибка при обновлении профиля: ${e.message}"
            }
            emit(Result.Failure(Exception(errorMessage)))
        }
    }
}