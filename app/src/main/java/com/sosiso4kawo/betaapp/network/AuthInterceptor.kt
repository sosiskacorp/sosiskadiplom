package com.sosiso4kawo.betaapp.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
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
        private const val REFRESH_THRESHOLD = 60 * 1000L  // если осталось менее 10 секунд до истечения токена
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val path = request.url.encodedPath

        // Исключаем эндпоинты логина, регистрации и обновления токена из обработки перехватчиком
        if (path.contains("v1/auth/login") ||
            path.contains("v1/auth/register") ||
            path.contains("v1/auth/refresh")
        ) {
            return chain.proceed(request)
        }

        val currentTime = System.currentTimeMillis()
        val tokenExpiry = sessionManager.getTokenExpiry()
        val timeLeft = tokenExpiry - currentTime
        Log.d("AuthInterceptor", "Текущее время: $currentTime, время истечения токена: $tokenExpiry, осталось: $timeLeft мс")

        // Если осталось менее 10 секунд до истечения, инициируем обновление токена
        if (timeLeft < REFRESH_THRESHOLD) {
            Log.d("AuthInterceptor", "Осталось менее 10 секунд до истечения токена. Инициируем обновление токена.")
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
                    Log.d("AuthInterceptor", "Обновление токена уже запущено, ждем $BACKOFF_TIME мс.")
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
            Log.e("AuthInterceptor", "Нет access token, выполняется logout.")
            logout()
        }

        var response = chain.proceed(request)

        // Если получен 401 и запрос ещё не был повторен – пробуем обновить токен и повторить запрос
        if (response.code == 401 && request.header("X-Retry") == null) {
            Log.d("AuthInterceptor", "Получен 401, пытаемся обновить токен и повторить запрос.")
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
                    Log.d("AuthInterceptor", "Обновление токена уже идёт, ждем $BACKOFF_TIME мс.")
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

    // Обработчик обновления токена с использованием ручного сбора Flow
    private suspend fun handleTokenRefresh() {
        Log.d("AuthInterceptor", "Запуск процедуры обновления токена.")
        val refreshToken = sessionManager.getRefreshToken()
        if (refreshToken != null) {
            val authRepository: AuthRepository = get()
            try {
                var result: Result<com.sosiso4kawo.betaapp.data.model.AuthResponse>? = null
                authRepository.refreshToken(refreshToken).collect { r ->
                    result = r
                }
                if (result != null && result!!.isSuccess) {
                    val authResponse = result!!.getOrNull()
                    if (authResponse != null) {
                        // Устанавливаем время жизни токена равным 1 минуте (60 секунд)
                        val expiresIn = 604800L
                        sessionManager.saveTokens(
                            authResponse.access_token,
                            authResponse.refresh_token,
                            expiresIn
                        )
                        Log.d("AuthInterceptor", "Токен успешно обновлён. Новый access_token: ${authResponse.access_token}")
                    } else {
                        Log.e("AuthInterceptor", "Не удалось получить токен после обновления.")
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
            Log.e("AuthInterceptor", "Refresh token отсутствует. Выполняется logout.")
            logout()
        }
    }

    // Очистка сессии (логаут) с навигацией на главный поток
    private fun logout() {
        Log.d("AuthInterceptor", "Токены недействительны или истекли, выполняем логаут.")
        sessionManager.clearSession()
//        Handler(Looper.getMainLooper()).post {
//            try {
//                navigationListener.navigateToLogin()
//            } catch (e: Exception) {
//                Log.e("AuthInterceptor", "Ошибка навигации при логауте: ${e.message}")
//            }
//        }
    }
}
