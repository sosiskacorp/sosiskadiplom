package com.sosiso4kawo.zschoolapp.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sosiso4kawo.zschoolapp.data.repository.AuthRepository
import com.sosiso4kawo.zschoolapp.util.Result
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.IOException

class AuthInterceptor(
    private val sessionManager: SessionManager,
    private val navigationListener: NavigationListener
) : Interceptor, KoinComponent {

    companion object {
        @Volatile
        private var isRefreshing = false
        private const val BACKOFF_TIME_MS = 1000L
        private const val TOKEN_REFRESH_THRESHOLD_MS = 60 * 1000L // 60 секунд
        private const val TAG = "AuthInterceptor"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_BEARER_PREFIX = "Bearer "
        private const val HEADER_RETRY_KEY = "X-Retry"
        private const val HEADER_RETRY_VALUE = "true"

        private val AUTH_PATHS = setOf(
            "v1/auth/login",
            "v1/auth/register",
            "v1/auth/refresh"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val path = request.url.encodedPathSegments.joinToString("/")

        if (AUTH_PATHS.any { path.contains(it) }) {
            return chain.proceed(request)
        }

        val currentTime = System.currentTimeMillis()
        val tokenExpiry = sessionManager.getTokenExpiry()
        val timeLeft = tokenExpiry - currentTime

        Log.d(TAG, "Текущее время: $currentTime, время истечения токена: $tokenExpiry, осталось: $timeLeft мс для пути $path")

        if (timeLeft < TOKEN_REFRESH_THRESHOLD_MS && !isRefreshing) {
            Log.d(TAG, "Осталось менее ${TOKEN_REFRESH_THRESHOLD_MS / 1000} секунд. Инициируем обновление токена.")
            synchronized(this) {
                if (!isRefreshing) {
                    isRefreshing = true
                    runBlocking {
                        try {
                            handleTokenRefresh()
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка при обновлении токена в блоке упреждающего обновления: ${e.message}")
                            logoutAndNavigate()
                        } finally {
                            isRefreshing = false
                        }
                    }
                } else {
                    Log.d(TAG, "Обновление токена уже запущено другим потоком, ждем $BACKOFF_TIME_MS мс.")
                    try { Thread.sleep(BACKOFF_TIME_MS) } catch (ie: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }
        } else if (timeLeft < TOKEN_REFRESH_THRESHOLD_MS && isRefreshing) {
            Log.d(TAG, "Обновление токена уже идет, ожидаем $BACKOFF_TIME_MS мс.")
            try { Thread.sleep(BACKOFF_TIME_MS) } catch (ie: InterruptedException) { Thread.currentThread().interrupt() }
        }


        sessionManager.getAccessToken()?.let { token ->
            request = request.newBuilder()
                .header(HEADER_AUTHORIZATION, "$HEADER_BEARER_PREFIX$token")
                .build()
        } ?: run {
            Log.e(TAG, "Нет access token для запроса $path, выполняется logout.")
            logoutAndNavigate()
            // Возвращаем "фейковый" ответ, так как запрос не может быть выполнен
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401) // Неавторизован
                .message("No access token available, navigation to login initiated")
                .body("{\"error\":\"No access token\"}".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            // Если произошла ошибка сети до получения ответа (например, нет интернета)
            Log.e(TAG, "IOException при выполнении запроса $path: ${e.message}")
            throw e // Пробрасываем ошибку дальше
        }


        if (response.code == 401 && request.header(HEADER_RETRY_KEY) == null) {
            Log.d(TAG, "Получен 401 для запроса $path, пытаемся обновить токен и повторить запрос.")
            response.close()

            synchronized(this) {
                val currentAccessToken = sessionManager.getAccessToken()
                val requestToken = request.header(HEADER_AUTHORIZATION)?.substringAfter(HEADER_BEARER_PREFIX)

                // Проверяем, не был ли токен уже обновлен другим потоком
                if (currentAccessToken != null && currentAccessToken != requestToken) {
                    Log.d(TAG, "Токен был обновлен другим потоком. Повторяем запрос с новым токеном.")
                    val newRequest = request.newBuilder()
                        .header(HEADER_AUTHORIZATION, "$HEADER_BEARER_PREFIX$currentAccessToken")
                        .header(HEADER_RETRY_KEY, HEADER_RETRY_VALUE)
                        .build()
                    return chain.proceed(newRequest)
                }

                if (!isRefreshing) {
                    isRefreshing = true
                    runBlocking {
                        try {
                            handleTokenRefresh()
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка при обновлении токена после 401: ${e.message}")
                            // logoutAndNavigate() вызывается внутри handleTokenRefresh при ошибке
                        } finally {
                            isRefreshing = false
                        }
                    }
                } else {
                    Log.d(TAG, "Обновление токена уже запущено (после 401), ждем $BACKOFF_TIME_MS мс.")
                    try { Thread.sleep(BACKOFF_TIME_MS) } catch (ie: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }

            sessionManager.getAccessToken()?.let { refreshedToken ->
                val newRequest = request.newBuilder()
                    .header(HEADER_AUTHORIZATION, "$HEADER_BEARER_PREFIX$refreshedToken")
                    .header(HEADER_RETRY_KEY, HEADER_RETRY_VALUE)
                    .build()
                Log.d(TAG, "Повторный запрос с обновленным токеном (после 401) для $path")
                return chain.proceed(newRequest) // Возвращаем результат нового запроса
            } ?: run {
                // Если токена все еще нет, значит, обновление не удалось и logoutAndNavigate был вызван
                Log.e(TAG, "Токен все еще отсутствует после попытки обновления. Возвращаем фейковый 401.")
                // Поскольку исходный response был закрыт, создаем новый "фейковый" 401
                return Response.Builder()
                    .request(request) // Используем исходный request для контекста
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Token refresh failed and no access token available")
                    .body("{\"error\":\"Token refresh failed\"}".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
        }
        return response
    }

    private suspend fun handleTokenRefresh() {
        Log.d(TAG, "Запуск процедуры обновления токена.")
        val refreshToken = sessionManager.getRefreshToken() ?: run {
            Log.e(TAG, "Refresh token отсутствует, logout.")
            logoutAndNavigate()
            return
        }

        try {
            val authRepository = get<AuthRepository>()
            val result = authRepository.refreshToken(refreshToken)

            if (result is Result.Success) {
                Log.d(TAG, "Токен успешно обновлён.")
            } else if (result is Result.Failure) {
                Log.e(TAG, "Не удалось обновить токен: ${result.exception.message}")
                logoutAndNavigate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при обновлении токена: ${e.message}")
            logoutAndNavigate()
        }
    }

    private fun logoutAndNavigate() {
        Handler(Looper.getMainLooper()).post {
            sessionManager.clearSession()
            try {
                Log.d(TAG, "Вызов navigationListener.navigateToLogin()")
                navigationListener.navigateToLogin()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка навигации при logout: ${e.message}")
            }
        }
    }
}