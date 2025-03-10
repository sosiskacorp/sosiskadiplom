package com.sosiso4kawo.betaapp.network

import android.util.Log
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor, KoinComponent {

    companion object {
        @Volatile
        private var isRefreshing = false
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val path = request.url.encodedPath

        // Не обрабатывать эндпоинты логина и регистрации
        if (path.contains("v1/auth/login") || path.contains("v1/auth/register")) {
            return chain.proceed(request)
        }

        val currentTime = System.currentTimeMillis()
        val tokenExpiry = sessionManager.getTokenExpiry()

        // Если осталось менее минуты до истечения, пытаемся обновить токен,
        // но только если обновление ещё не запущено
        if (tokenExpiry - currentTime < 60 * 1000) {
            synchronized(this) {
                if (!isRefreshing) {
                    isRefreshing = true
                    runBlocking {
                        sessionManager.getRefreshToken()?.let { refreshToken ->
                            val authRepository: AuthRepository = get()
                            authRepository.refreshToken(refreshToken).collect { result ->
                                result.onSuccess { authResponse ->
                                    // Если expiresIn не пришёл, используем значение по умолчанию (например, 3600 сек)
                                    val expiresIn = authResponse.expiresIn ?: (48 * 3600L)
                                    sessionManager.saveTokens(
                                        authResponse.access_token,
                                        authResponse.refresh_token,
                                        expiresIn
                                    )
                                    Log.d("AuthInterceptor", "Токен успешно обновлён.")
                                }
                                result.onFailure { exception ->
                                    Log.e("AuthInterceptor", "Ошибка обновления токена: ${exception.message}")
                                    logout()
                                }
                            }
                        } ?: run {
                            logout()
                        }
                    }
                    isRefreshing = false
                } else {
                    // Если обновление уже идёт – можно подождать небольшой интервал
                    // до появления нового токена, или просто продолжить с устаревшим
                    Thread.sleep(500)
                }
            }
        }

        // Добавляем access token, если он есть
        sessionManager.getAccessToken()?.let { token ->
            request = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } ?: run {
            logout()
        }

        var response = chain.proceed(request)

        // Если получен 401 и запрос ещё не был повторен – пробуем обновить токен и повторить запрос
        if (response.code == 401 && request.header("X-Retry") == null) {
            response.close()
            synchronized(this) {
                if (!isRefreshing) {
                    isRefreshing = true
                    runBlocking {
                        sessionManager.getRefreshToken()?.let { refreshToken ->
                            val authRepository: AuthRepository = get()
                            authRepository.refreshToken(refreshToken).collect { result ->
                                result.onSuccess { authResponse ->
                                    val expiresIn = authResponse.expiresIn ?: (48 * 3600L)
                                    sessionManager.saveTokens(
                                        authResponse.access_token,
                                        authResponse.refresh_token,
                                        expiresIn
                                    )
                                    Log.d("AuthInterceptor", "Токен обновлён после 401.")
                                }
                                result.onFailure { exception ->
                                    Log.e("AuthInterceptor", "Ошибка обновления токена после 401: ${exception.message}")
                                    logout()
                                }
                            }
                        } ?: run {
                            logout()
                        }
                    }
                    isRefreshing = false
                } else {
                    Thread.sleep(500)
                }
            }
            sessionManager.getAccessToken()?.let { token ->
                request = request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("X-Retry", "true")
                    .build()
                response = chain.proceed(request)
            }
        }
        return response
    }

    // Очистка сессии (логаут) – можно добавить уведомление или навигацию на экран логина
    private fun logout() {
        Log.d("AuthInterceptor", "Токены недействительны или истекли, выполняем логаут.")
        sessionManager.clearSession()
    }
}