package com.sosiso4kawo.zschoolapp.data.repository

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.sosiso4kawo.zschoolapp.data.api.UserService
import com.sosiso4kawo.zschoolapp.data.model.AuthError
import com.sosiso4kawo.zschoolapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.zschoolapp.data.model.User
import com.sosiso4kawo.zschoolapp.util.Result
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody
import retrofit2.HttpException // Импортируем HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UserRepository(
    private val userService: UserService,
    private val sessionManager: SessionManager
) {

    fun getProfile(): Flow<Result<User>> = flow {
        Log.d("UserRepository", "Fetching user profile")
        val accessToken = sessionManager.getAccessToken()
        if (accessToken.isNullOrEmpty()) {
            Log.e("UserRepository", "Access token is null or empty")
            emit(Result.Failure(Exception("Токен доступа отсутствует")))
            return@flow
        }

        val localUser = sessionManager.getUserData()
        if (localUser != null) {
            Log.d("UserRepository", "Using cached user data: $localUser")
            emit(Result.Success(localUser))
        }

        val response = userService.getProfile("Bearer $accessToken")
        Log.d("UserRepository", "Profile response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}")

        when {
            response.isSuccessful -> {
                val body = response.body()
                if (body != null) {
                    Log.d("UserRepository", "Profile fetched successfully: $body")
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
                Log.e("UserRepository", "Profile fetch error: code=${response.code()}, error=$errorBody, headers=${response.headers()}")
                val errorMessage = if (errorBody.isBlank()) {
                    "Ошибка сервера (${response.code()})"
                } else {
                    try {
                        val gson = GsonBuilder().setLenient().create()
                        val error = gson.fromJson(errorBody, AuthError::class.java)
                        error?.message ?: "Ошибка при получении профиля"
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                        "Ошибка при обработке ответа от сервера"
                    }
                }
                emit(Result.Failure(Exception(errorMessage)))
            }
        }
    }.catch { e -> // Используем оператор catch для обработки исключений
        Log.e("UserRepository", "Profile fetch exception: ${e.message}, cause: ${e.cause}")
        val errorMessage = when (e) {
            is UnknownHostException -> "Нет подключения к интернету"
            is SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
            is JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
            is HttpException -> "Ошибка HTTP: ${e.code()}" // Добавим обработку HTTP ошибок
            else -> "Произошла неизвестная ошибка: ${e.message}" // Более общее сообщение для других ошибок
        }
        emit(Result.Failure(Exception(errorMessage)))
    }

    fun updateProfile(request: UpdateProfileRequest): Flow<Result<Unit>> = flow {
        Log.d("UserRepository", "Updating user profile")
        val accessToken = sessionManager.getAccessToken()
        if (accessToken.isNullOrEmpty()) {
            Log.e("UserRepository", "Access token is null or empty")
            emit(Result.Failure(Exception("Токен доступа отсутствует")))
            return@flow
        }

        val response = userService.updateProfile("Bearer $accessToken", request)
        Log.d("UserRepository", "Profile update response: code=${response.code()}, headers=${response.headers()}, raw=${response.raw()}")

        when {
            response.isSuccessful -> {
                Log.d("UserRepository", "Profile updated successfully")
                emit(Result.Success(Unit))
            }
            response.code() == 401 -> {
                Log.e("UserRepository", "Unauthorized access (401): Invalid or expired token")
                emit(Result.Failure(Exception("Недействительный или просроченный токен")))
            }
            else -> {
                val errorBody = response.errorBody()?.string() ?: ""
                Log.e("UserRepository", "Profile update error: code=${response.code()}, error=$errorBody, headers=${response.headers()}")
                val errorMessage = if (errorBody.isBlank()) {
                    "Ошибка сервера (${response.code()})"
                } else {
                    try {
                        val gson = GsonBuilder().setLenient().create()
                        val error = gson.fromJson(errorBody, AuthError::class.java)
                        error?.message ?: "Ошибка при обновлении профиля"
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Error parsing response: ${e.message}, raw error body: $errorBody")
                        "Ошибка при обработке ответа от сервера"
                    }
                }
                emit(Result.Failure(Exception(errorMessage)))
            }
        }
    }.catch { e -> // Используем оператор catch для обработки исключений
        Log.e("UserRepository", "Profile update exception: ${e.message}, cause: ${e.cause}")
        val errorMessage = when (e) {
            is UnknownHostException -> "Нет подключения к интернету"
            is SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
            is JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
            is HttpException -> "Ошибка HTTP: ${e.code()}"
            else -> "Произошла неизвестная ошибка: ${e.message}"
        }
        emit(Result.Failure(Exception(errorMessage)))
    }

    // Этот метод не возвращает Flow<Result<T>>, поэтому он сохраняет структуру try/catch,
    // хотя для консистентности можно рассмотреть использование runCatching
    suspend fun uploadAvatar(token: String, file: MultipartBody.Part): Response<Void> {
        // В данном случае, поскольку это suspend функция, а не Flow, try-catch здесь допустим
        // и, возможно, уже есть где-то выше по стеку вызовов.
        // Если нет, можно обернуть в try-catch или runCatching для обработки сетевых ошибок.
        return userService.uploadAvatar(token, file)
    }

    fun getAllUsers(limit: Int, offset: Int) = flow {
        val token = sessionManager.getAccessToken()
        if (token.isNullOrEmpty()) {
            emit(Result.Failure(Exception("Токен доступа отсутствует")))
            return@flow
        }
        val response = userService.getAllUsers(limit, offset)
        if (response.isSuccessful) {
            val users = response.body()?.leaderboard?.sortedByDescending { it.total_points ?: 0 } ?: emptyList()
            emit(Result.Success(users))
        } else {
            emit(Result.Failure(Exception("Ошибка сервера: ${response.code()}")))
        }
    }.catch { e -> // Используем оператор catch для обработки исключений
        Log.e("UserRepository", "getAllUsers exception: ${e.message}, cause: ${e.cause}")
        val errorMessage = when (e) {
            is UnknownHostException -> "Нет подключения к интернету"
            is SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
            is JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
            is HttpException -> "Ошибка HTTP: ${e.code()}"
            else -> "Произошла неизвестная ошибка: ${e.message}"
        }
        emit(Result.Failure(Exception(errorMessage)))
    }

    fun getProgress() = flow {
        val token = sessionManager.getAccessToken()
        if (token != null) {
            val response = userService.getProgress("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                emit(Result.Success(response.body()!!))
            } else {
                emit(Result.Failure(Exception("Ошибка загрузки прогресса")))
            }
        } else {
            emit(Result.Failure(Exception("Нет токена авторизации")))
        }
    }.flowOn(Dispatchers.IO)
        .catch { e -> // Используем оператор catch для обработки исключений
            Log.e("UserRepository", "getProgress exception: ${e.message}, cause: ${e.cause}")
            val errorMessage = when (e) {
                is UnknownHostException -> "Нет подключения к интернету"
                is SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
                is HttpException -> "Ошибка HTTP: ${e.code()}"
                else -> "Произошла неизвестная ошибка: ${e.message}"
            }
            emit(Result.Failure(Exception(errorMessage)))
        }


    fun getUserByUuid(uuid: String) = flow {
        val token = sessionManager.getAccessToken()
        if (token.isNullOrEmpty()) {
            emit(Result.Failure(Exception("Токен доступа отсутствует")))
            return@flow
        }
        val response = userService.getUserByUuid(uuid, "Bearer $token")
        if (response.isSuccessful) {
            response.body()?.let { emit(Result.Success(it)) }
                ?: emit(Result.Failure(Exception("Пустой ответ сервера")))
        } else {
            emit(Result.Failure(Exception("Ошибка сервера: ${response.code()}")))
        }
    }.catch { e -> // Используем оператор catch для обработки исключений
        Log.e("UserRepository", "getUserByUuid exception: ${e.message}, cause: ${e.cause}")
        val errorMessage = when (e) {
            is UnknownHostException -> "Нет подключения к интернету"
            is SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
            is JsonSyntaxException -> "Ошибка при обработке ответа от сервера"
            is HttpException -> "Ошибка HTTP: ${e.code()}"
            else -> "Произошла неизвестная ошибка: ${e.message}"
        }
        emit(Result.Failure(Exception(errorMessage)))
    }
}