package com.sosiso4kawo.betaapp.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
        private const val BACKOFF_TIME = 1000L
        private const val REFRESH_THRESHOLD = 60 * 1000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val path = request.url.encodedPath
// Исключаем эндпоинты логина, регистрации и обновления токена из обработки перехватчиком
        if (path.contains("v1/auth/login") ||
            path.contains("v1/auth/register") ||
            path.contains("v1/auth/refresh")) {
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
                            Log.e("AuthInterceptor", "Refresh error: ${e.message}")
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
            return chain.proceed(request)
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
                            Log.e("AuthInterceptor", "Refresh error: ${e.message}")
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

    private suspend fun handleTokenRefresh() {
    Log.d("AuthInterceptor", "Запуск процедуры обновления токена.")
        val refreshToken = sessionManager.getRefreshToken() ?: run {
            logout()
            return
        }

        try {
            val result = get<AuthRepository>().refreshToken(refreshToken).first()
            result.getOrNull()?.let {
                sessionManager.saveTokens(
                    it.access_token,
                    it.refresh_token,
                    604800L
                )
                Log.d("AuthInterceptor", "Токен успешно обновлён.")
            } ?:
            Log.e("AuthInterceptor", "Не удалось получить токен после обновления.")
            logout()
        } catch (e: Exception) {
            Log.e("AuthInterceptor", "Ошибка обновления токена")
            logout()
        }
    }

    private fun logout() {
        sessionManager.clearSession()
        Handler(Looper.getMainLooper()).post {
            try {
                navigationListener.navigateToLogin()
            } catch (e: Exception) {
                Log.e("AuthInterceptor", "Navigation error: ${e.message}")
            }
        }
    }
}