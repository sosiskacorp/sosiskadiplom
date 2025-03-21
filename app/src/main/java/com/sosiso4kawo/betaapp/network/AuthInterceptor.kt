package com.sosiso4kawo.betaapp.network

import android.util.Log
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class AuthInterceptor(
    private val sessionManager: SessionManager,
    private val navigationListener: NavigationListener
) : Interceptor, KoinComponent {

    companion object {
        @Volatile
        private var isRefreshing = false
        private const val BACKOFF_TIME = 1000L  // начальная задержка (1 секунда)
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
                        try {
                            handleTokenRefresh()
                        } catch (e: Exception) {
                            Log.e("AuthInterceptor", "Ошибка обновления токена: ${e.message}")
                            logout()
                        }
                    }
                    isRefreshing = false
                } else {
                    // Если обновление уже идёт – можно подождать небольшой интервал
                    // до появления нового токена, или просто продолжить с устаревшим
                    Thread.sleep(BACKOFF_TIME)
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
                        try {
                            handleTokenRefresh()
                        } catch (e: Exception) {
                            Log.e("AuthInterceptor", "Ошибка обновления токена после 401: ${e.message}")
                            logout()
                        }
                    }
                    isRefreshing = false
                } else {
                    Thread.sleep(BACKOFF_TIME)
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

    // Обработчик обновления токена
    private suspend fun handleTokenRefresh() {
        val refreshToken = sessionManager.getRefreshToken()
        if (refreshToken != null) {
            val authRepository: AuthRepository = get()
            try {
                val result = authRepository.refreshToken(refreshToken).firstOrNull() // Получаем результат запроса
                if (result != null && result.isSuccess) {
                    // Извлекаем успешный результат
                    val authResponse = result.getOrNull()
                    if (authResponse != null) {
                        // Если expiresIn не приходит, устанавливаем срок жизни refresh token равным 7 дням (604800 секунд)
                        val expiresIn = authResponse.expiresIn ?: (7 * 24 * 3600L)
                        sessionManager.saveTokens(
                            authResponse.access_token,
                            authResponse.refresh_token,
                            expiresIn
                        )

                        Log.d("AuthInterceptor", "Токен успешно обновлён.")
                    } else {
                        Log.e("AuthInterceptor", "Не удалось получить токен.")
                        logout()
                    }
                } else {
                    Log.e("AuthInterceptor", "Ошибка обновления токена: ${result?.exceptionOrNull()?.message}")
                    logout()
                }
            } catch (e: Exception) {
                Log.e("AuthInterceptor", "Ошибка обновления токена: ${e.message}")
                logout()
            }
        } else {
            logout()
        }
    }

    // Очистка сессии (логаут) – можно добавить уведомление или навигацию на экран логина
    private fun logout() {
        Log.d("AuthInterceptor", "Токены недействительны или истекли, выполняем логаут.")
        sessionManager.clearSession()

        navigationListener.navigateToLogin()
    }
}
