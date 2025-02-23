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

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val path = request.url.encodedPath

        // Исключаем эндпоинты логина и регистрации
        if (path.contains("v1/auth/login") || path.contains("v1/auth/register")) {
            Log.d("AuthInterceptor", "Пропускаем интерсептор для эндпоинта: $path")
            return chain.proceed(request)
        }

        Log.d("AuthInterceptor", "Начало обработки запроса: ${request.url}")

        // Проверяем время истечения токена
        val currentTime = System.currentTimeMillis()
        val tokenExpiry = sessionManager.getTokenExpiry()
        Log.d("AuthInterceptor", "Текущее время: $currentTime, время истечения токена: $tokenExpiry")

        // Если осталось менее 1 минуты до истечения — пытаемся обновить токен
        if (tokenExpiry - currentTime < 60 * 1000) {
            Log.d("AuthInterceptor", "Время до истечения токена меньше минуты, начинаем обновление токена")
            runBlocking {
                sessionManager.getRefreshToken()?.let { refreshToken ->
                    Log.d("AuthInterceptor", "Используем refreshToken: $refreshToken для обновления токена")
                    val authRepository: AuthRepository = get()
                    authRepository.refreshToken(refreshToken).collect { result ->
                        result.onSuccess { authResponse ->
                            val expiresIn = authResponse.expiresIn ?: 60L
                            Log.d(
                                "AuthInterceptor",
                                "Токен обновлён успешно. Новый access_token: ${authResponse.access_token}, expiresIn: $expiresIn"
                            )
                            sessionManager.saveTokens(
                                authResponse.access_token,
                                authResponse.refresh_token,
                                expiresIn
                            )
                        }
                        result.onFailure { exception ->
                            Log.e("AuthInterceptor", "Ошибка обновления токена: ${exception.message}")
                            logout()  // В случае неудачного обновления токенов, выполняем выход
                        }
                    }
                } ?: run {
                    Log.e("AuthInterceptor", "Refresh token отсутствует, обновление невозможно")
                    logout()  // Если refresh токен отсутствует, выходим из приложения
                }
            }
        }

        // Добавляем заголовок Authorization, если access token присутствует
        sessionManager.getAccessToken()?.let { token ->
            request = request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            Log.d("AuthInterceptor", "Добавлен заголовок Authorization с token: $token")
        } ?: run {
            Log.e("AuthInterceptor", "Access token отсутствует в сессии")
            logout()  // Если access токен отсутствует, выходим из приложения
        }

        // Выполняем запрос
        var response = chain.proceed(request)
        Log.d("AuthInterceptor", "Ответ получен: код ${response.code} (исправлено), url: ${request.url}")

        // Если получили 401 и запрос ещё не был повторен, пробуем обновить токен и повторить запрос
        if (response.code == 401 && request.header("X-Retry") == null) {
            Log.d("AuthInterceptor", "Получен 401, пытаемся обновить токен и повторить запрос")
            response.close() // Закрываем предыдущий ответ
            runBlocking {
                sessionManager.getRefreshToken()?.let { refreshToken ->
                    Log.d("AuthInterceptor", "Используем refreshToken: $refreshToken для обновления токена (401)")
                    val authRepository: AuthRepository = get()
                    authRepository.refreshToken(refreshToken).collect { result ->
                        result.onSuccess { authResponse ->
                            val expiresIn = authResponse.expiresIn ?: 60L
                            Log.d(
                                "AuthInterceptor",
                                "Токен успешно обновлён после 401. Новый access_token: ${authResponse.access_token}, expiresIn: $expiresIn"
                            )
                            sessionManager.saveTokens(
                                authResponse.access_token,
                                authResponse.refresh_token,
                                expiresIn
                            )
                        }
                        result.onFailure { exception ->
                            Log.e("AuthInterceptor", "Ошибка обновления токена после 401: ${exception.message}")
                            logout()  // Если не удалось обновить токен после 401, выполняем выход
                        }
                    }
                } ?: run {
                    Log.e("AuthInterceptor", "Refresh token отсутствует, обновление невозможно (401)")
                    logout()  // Если refresh токен отсутствует, выходим из приложения
                }
            }
            // Повторяем запрос с обновленным токеном и добавляем заголовок X-Retry, чтобы не зациклиться
            sessionManager.getAccessToken()?.let { token ->
                request = request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("X-Retry", "true")
                    .build()
                response = chain.proceed(request)
                Log.d("AuthInterceptor", "Повторный запрос выполнен с обновленным токеном, код ответа: ${response.code}")
            }
        }

        return response
    }

    // Функция для выхода из приложения
    private fun logout() {
        Log.d("AuthInterceptor", "Токены истекли, выполняем выход из приложения.")
        sessionManager.clearSession()

    }
}
