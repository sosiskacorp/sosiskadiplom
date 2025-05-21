package com.sosiso4kawo.zschoolapp.data.repository

import android.content.Context // <<< ДОБАВЛЕН ИМПОРТ
import com.sosiso4kawo.zschoolapp.R // <<< ДОБАВЛЕН ИМПОРТ для строк (если они используются в репозитории)
import com.sosiso4kawo.zschoolapp.data.api.AuthService
import com.sosiso4kawo.zschoolapp.data.model.AuthResponse
import com.sosiso4kawo.zschoolapp.data.model.EmailResponse
import com.sosiso4kawo.zschoolapp.data.model.LoginRequest
import com.sosiso4kawo.zschoolapp.data.model.PasswordResetRequest
import com.sosiso4kawo.zschoolapp.data.model.RefreshTokenRequest
import com.sosiso4kawo.zschoolapp.data.model.RegisterRequest
import com.sosiso4kawo.zschoolapp.data.model.User
import com.sosiso4kawo.zschoolapp.data.model.VerificationRequest
import com.sosiso4kawo.zschoolapp.util.Result
import com.sosiso4kawo.zschoolapp.util.SessionManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every // <<< ИМПОРТ для every (не coEvery)
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@ExperimentalCoroutinesApi
class AuthRepositoryTest {

    private lateinit var mockContext: Context // <<< МОК для Context
    private lateinit var authService: AuthService
    private lateinit var sessionManager: SessionManager
    private lateinit var authRepository: AuthRepository

    private val testDispatcher = StandardTestDispatcher()

    private val validEmail = "test@test25.com"
    private val validPassword = "1testTest"
    private val validAccessToken = "valid_access_token"
    private val validRefreshToken = "valid_refresh_token"
    private val validAuthResponse = AuthResponse(validAccessToken, validRefreshToken)
    private val validUser = User(
        uuid = "test-uuid",
        login = "testLogin",
        name = "Test",
        last_name = "User",
        second_name = null,
        avatar = null,
        total_points = 100,
        finished_courses = 1
    )

    @Before
    fun setUp() {
        mockContext = mockk() // <<< ИНИЦИАЛИЗАЦИЯ МОКА Context
        authService = mockk()
        sessionManager = mockk(relaxed = true)

        // <<< ИЗМЕНЕНИЕ: Передаем mockContext в конструктор
        authRepository = AuthRepository(mockContext, authService, sessionManager)

        Dispatchers.setMain(testDispatcher)

        // Мокаем вызовы getString, которые могут использоваться в AuthRepository
        // Вам нужно будет добавить моки для всех строк, которые реально используются
        // в AuthRepository при формировании сообщений об ошибках.
        every { mockContext.getString(R.string.error_login_success_empty_body_repo) } returns "Успешная авторизация, но сервер вернул пустой ответ"
        every { mockContext.getString(R.string.error_invalid_login_or_password_repo) } returns "Неверный логин или пароль"
        every { mockContext.getString(R.string.error_invalid_email_format_repo) } returns "Неверный формат почты"
        every { mockContext.getString(R.string.error_server_with_code_repo, any<Int>()) } answers { "Ошибка сервера (${args[1]})" }
        every { mockContext.getString(R.string.error_auth_failed_repo) } returns "Ошибка авторизации"
        every { mockContext.getString(R.string.error_parsing_server_response_repo) } returns "Ошибка при обработке ответа от сервера"
        every { mockContext.getString(R.string.error_no_internet_connection_repo) } returns "Нет подключения к интернету"
        every { mockContext.getString(R.string.error_server_timeout_repo) } returns "Превышено время ожидания ответа от сервера"
        every { mockContext.getString(R.string.error_auth_exception_repo, any<String>()) } answers { "Ошибка при авторизации: ${args[1]}" }
        every { mockContext.getString(R.string.info_registration_successful_repo) } returns "Регистрация успешна"
        every { mockContext.getString(R.string.error_registration_success_empty_body_repo) } returns "Успешная регистрация, но сервер вернул пустой ответ"
        every { mockContext.getString(R.string.error_user_already_exists_repo) } returns "Пользователь с таким email или логином уже существует"
        every { mockContext.getString(R.string.error_registration_failed_repo) } returns "Ошибка регистрации"
        every { mockContext.getString(R.string.error_parsing_server_response_repo_malformed) } returns "Сервер вернул некорректный ответ"
        every { mockContext.getString(R.string.error_registration_exception_repo, any<String>()) } answers { "Ошибка при регистрации: ${args[1]}" }
        every { mockContext.getString(R.string.error_token_refresh_empty_body_repo) } returns "Ошибка обновления токена: сервер вернул пустой ответ"
        every { mockContext.getString(R.string.error_session_expired_relogin_repo) } returns "Сессия истекла, требуется повторная авторизация"
        every { mockContext.getString(R.string.error_token_refresh_failed_repo) } returns "Ошибка обновления токена"
        every { mockContext.getString(R.string.error_token_refresh_exception_repo, any<String>()) } answers { "Ошибка при обновлении токена: ${args[1]}" }
        every { mockContext.getString(R.string.error_logout_failed_repo) } returns "Ошибка при выходе"
        every { mockContext.getString(R.string.error_logout_exception_repo, any<String>()) } answers { "Ошибка при выходе: ${args[1]}" }
        every { mockContext.getString(R.string.error_sending_code_failed_repo) } returns "Ошибка отправки кода"
        every { mockContext.getString(R.string.error_email_verification_failed_repo) } returns "Ошибка подтверждения почты"
        every { mockContext.getString(R.string.error_get_email_empty_body_repo) } returns "Не удалось получить email: пустой ответ"
        every { mockContext.getString(R.string.error_auth_required_repo) } returns "Требуется авторизация"
        every { mockContext.getString(R.string.error_email_not_found_repo) } returns "Email не найден"
        every { mockContext.getString(R.string.error_get_email_failed_repo) } returns "Ошибка получения email"
        every { mockContext.getString(R.string.error_get_email_exception_repo, any<String>()) } answers { "Ошибка при получении email: ${args[1]}" }
        every { mockContext.getString(R.string.error_invalid_confirmation_code_repo) } returns "Неверный код подтверждения"
        every { mockContext.getString(R.string.error_email_not_found_for_reset_repo) } returns "Email не найден"
        every { mockContext.getString(R.string.error_password_reset_failed_with_code_repo, any<Int>()) } answers { "Ошибка сброса пароля (${args[1]})" }
        every { mockContext.getString(R.string.error_password_reset_exception_repo, any<String>()) } answers { "Ошибка при сбросе пароля: ${args[1]}" }


        coEvery { sessionManager.saveTokens(any(), any(), any()) } just Runs
        coEvery { sessionManager.saveUserData(any()) } just Runs
        coEvery { sessionManager.clearSession() } just Runs
        coEvery { sessionManager.getAccessToken() } returns validAccessToken
        coEvery { sessionManager.getRefreshToken() } returns validRefreshToken
        coEvery { sessionManager.getUserData() } returns validUser
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // --- Login Tests ---

    @Test
    fun `login success - valid credentials, saves tokens and user data`() = runTest {
        val loginRequest = LoginRequest(validEmail, validPassword)
        coEvery { authService.login(loginRequest) } returns Response.success(validAuthResponse)
        coEvery { authService.getProfile("Bearer $validAccessToken") } returns Response.success(validUser)

        val result = authRepository.login(validEmail, validPassword)

        assertTrue(result is Result.Success)
        assertEquals(validAuthResponse, (result as Result.Success).value)
        coVerifySequence {
            authService.login(loginRequest)
            sessionManager.saveTokens(validAccessToken, validRefreshToken, 604800L)
            authService.getProfile("Bearer $validAccessToken")
            sessionManager.saveUserData(validUser)
        }
    }

    @Test
    fun `login failure - invalid credentials (401)`() = runTest {
        val loginRequest = LoginRequest(validEmail, "wrong_password")
        val errorBody = """{"message": "invalid credentials"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.login(loginRequest) } returns Response.error(401, errorBody)

        val result = authRepository.login(validEmail, "wrong_password")

        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_invalid_login_or_password_repo), (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
        coVerify(exactly = 0) { sessionManager.saveUserData(any()) }
    }

    @Test
    fun `login failure - email not confirmed (401 specific error)`() = runTest {
        val loginRequest = LoginRequest(validEmail, validPassword)
        val errorBody = """{"message": "email not confirmed"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.login(loginRequest) } returns Response.error(401, errorBody)

        val result = authRepository.login(validEmail, validPassword)

        assertTrue(result is Result.Failure)
        // Сообщение "email not confirmed" приходит из EmailNotConfirmedException, а не из getString
        assertEquals("email not confirmed", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
        coVerify(exactly = 0) { sessionManager.saveUserData(any()) }
    }

    @Test
    fun `login failure - invalid email format error from server (400)`() = runTest {
        val invalidEmailFormat = "invalid-email"
        val loginRequest = LoginRequest(invalidEmailFormat, validPassword)
        val errorBody = """{"detail":"Field validation for 'Email' failed on the 'email' tag"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.login(loginRequest) } returns Response.error(400, errorBody)

        val result = authRepository.login(invalidEmailFormat, validPassword)

        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_invalid_email_format_repo), (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `login failure - server error (500)`() = runTest {
        val loginRequest = LoginRequest(validEmail, validPassword)
        val errorBody = """{"message": "Internal Server Error"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.login(loginRequest) } returns Response.error(500, errorBody)

        val result = authRepository.login(validEmail, validPassword)

        assertTrue(result is Result.Failure)
        // Сообщение теперь приходит из error?.message, если парсинг успешен
        assertEquals("Internal Server Error", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `login failure - network error (UnknownHostException)`() = runTest {
        val loginRequest = LoginRequest(validEmail, validPassword)
        coEvery { authService.login(loginRequest) } throws UnknownHostException("Cannot resolve host")

        val result = authRepository.login(validEmail, validPassword)

        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_no_internet_connection_repo), (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `login failure - network error (SocketTimeoutException)`() = runTest {
        val loginRequest = LoginRequest(validEmail, validPassword)
        coEvery { authService.login(loginRequest) } throws SocketTimeoutException("Timeout")

        val result = authRepository.login(validEmail, validPassword)

        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_server_timeout_repo), (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `login success but profile fetch fails`() = runTest {
        val loginRequest = LoginRequest(validEmail, validPassword)
        coEvery { authService.login(loginRequest) } returns Response.success(validAuthResponse)
        coEvery { authService.getProfile("Bearer $validAccessToken") } returns Response.error(404, "".toResponseBody())

        val result = authRepository.login(validEmail, validPassword)

        assertTrue(result is Result.Success)
        assertEquals(validAuthResponse, (result as Result.Success).value)
        coVerifySequence {
            authService.login(loginRequest)
            sessionManager.saveTokens(validAccessToken, validRefreshToken, 604800L)
            authService.getProfile("Bearer $validAccessToken")
        }
        coVerify(exactly = 0) { sessionManager.saveUserData(any()) }
    }


    // --- Registration Tests ---

    @Test
    fun `register success - valid data`() = runTest {
        val registerRequest = RegisterRequest(validEmail, validPassword)
        coEvery { authService.register(registerRequest) } returns Response.success(200, validAuthResponse)

        val result = authRepository.register(validEmail, validPassword)

        assertTrue(result is Result.Success)
        assertEquals(validAuthResponse, (result as Result.Success).value)
    }

    @Test
    fun `register success - 204 No Content`() = runTest {
        val registerRequest = RegisterRequest(validEmail, validPassword)
        coEvery { authService.register(registerRequest) } returns Response.success(204, null as AuthResponse?)

        val result = authRepository.register(validEmail, validPassword)

        assertTrue(result is Result.Failure)
        // Проверяем, что сообщение соответствует одному из ожидаемых (зависит от кода 204)
        val expectedMessage1 = mockContext.getString(R.string.info_registration_successful_repo)
        val expectedMessage2 = mockContext.getString(R.string.error_registration_success_empty_body_repo)
        val actualMessage = (result as Result.Failure).exception.message
        assertTrue(actualMessage == expectedMessage1 || actualMessage == expectedMessage2)
    }

    @Test
    fun `register failure - user already exists (409)`() = runTest {
        val registerRequest = RegisterRequest(validEmail, validPassword)
        val errorBody = """{"message": "User already exists"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.register(registerRequest) } returns Response.error(409, errorBody)

        val result = authRepository.register(validEmail, validPassword)

        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_user_already_exists_repo), (result as Result.Failure).exception.message)
    }

    // ... (остальные тесты для register, refreshToken, logout, sendVerificationCode, verifyEmail, getEmail, resetPassword)
    // В них также нужно будет заменить ожидаемые строки на mockContext.getString(...)
    // Пример для refreshToken failure - invalid refresh token (401):
    @Test
    fun `refreshToken failure - invalid refresh token (401)`() = runTest {
        val invalidRefreshToken = "invalid_token"
        val refreshRequest = RefreshTokenRequest(invalidRefreshToken)
        val errorBody = """{"message": "Invalid token"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.refreshToken(refreshRequest) } returns Response.error(401, errorBody)

        val result = authRepository.refreshToken(invalidRefreshToken)

        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_session_expired_relogin_repo), (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    // ... (продолжите аналогично для всех остальных тестов, где есть assertEquals с текстом ошибки) ...

    // ПРИМЕРЫ для оставшихся тестов (нужно будет доделать все)

    @Test
    fun `logout success - valid access token`() = runTest {
        coEvery { sessionManager.getAccessToken() } returns validAccessToken
        coEvery { authService.logout("Bearer $validAccessToken") } returns Response.success(200, null as Void?)
        val results = mutableListOf<Result<Unit>>()
        authRepository.logout().collect { results.add(it) }
        assertTrue(results.isNotEmpty())
        assertTrue(results.last() is Result.Success)
    }

    @Test
    fun `sendVerificationCode success`() = runTest {
        coEvery { authService.sendVerificationCode(validEmail) } returns Response.success(200, Unit)
        val result = authRepository.sendVerificationCode(validEmail)
        assertTrue(result is Result.Success)
    }

    @Test
    fun `sendVerificationCode failure - server error`() = runTest {
        val errorBody = "".toResponseBody()
        coEvery { authService.sendVerificationCode(validEmail) } returns Response.error(500, errorBody)
        val result = authRepository.sendVerificationCode(validEmail)
        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_sending_code_failed_repo), (result as Result.Failure).exception.message)
    }


    @Test
    fun `verifyEmail success`() = runTest {
        val code = "123456"
        val request = VerificationRequest(validEmail, code)
        coEvery { authService.verifyEmail(request) } returns Response.success(200, Unit)
        val result = authRepository.verifyEmail(validEmail, code)
        assertTrue(result is Result.Success)
    }

    @Test
    fun `verifyEmail failure - server error`() = runTest {
        val code = "123456"
        val request = VerificationRequest(validEmail, code)
        val errorBody = "".toResponseBody()
        coEvery { authService.verifyEmail(request) } returns Response.error(400, errorBody)
        val result = authRepository.verifyEmail(validEmail, code)
        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_email_verification_failed_repo), (result as Result.Failure).exception.message)
    }

    @Test
    fun `getEmail success`() = runTest {
        val emailResponse = EmailResponse(validEmail)
        coEvery { authService.getEmail("Bearer $validAccessToken") } returns Response.success(emailResponse)
        val results = mutableListOf<Result<EmailResponse>>()
        authRepository.getEmail("Bearer $validAccessToken").collect{ results.add(it) }
        assertTrue(results.isNotEmpty())
        val lastResult = results.last()
        assertTrue(lastResult is Result.Success)
        assertEquals(emailResponse, (lastResult as Result.Success).value)
    }

    @Test
    fun `getEmail failure - 401 Unauthorized`() = runTest {
        val errorBody = "".toResponseBody()
        coEvery { authService.getEmail("Bearer $validAccessToken") } returns Response.error(401, errorBody)
        val results = mutableListOf<Result<EmailResponse>>()
        authRepository.getEmail("Bearer $validAccessToken").collect{ results.add(it) }
        assertTrue(results.isNotEmpty())
        val lastResult = results.last()
        assertTrue(lastResult is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_auth_required_repo), (lastResult as Result.Failure).exception.message)
    }

    @Test
    fun `resetPassword success`() = runTest {
        val code = "123456"
        val newPassword = "NewPassword123!"
        val request = PasswordResetRequest(validEmail, code, newPassword)
        coEvery { authService.resetPassword(request) } returns Response.success(200, Unit)
        val result = authRepository.resetPassword(validEmail, code, newPassword)
        assertTrue(result is Result.Success)
    }

    @Test
    fun `resetPassword failure - invalid code (400)`() = runTest {
        val code = "wrongcode"
        val newPassword = "NewPassword123!"
        val request = PasswordResetRequest(validEmail, code, newPassword)
        val errorBody = "".toResponseBody()
        coEvery { authService.resetPassword(request) } returns Response.error(400, errorBody)
        val result = authRepository.resetPassword(validEmail, code, newPassword)
        assertTrue(result is Result.Failure)
        assertEquals(mockContext.getString(R.string.error_invalid_confirmation_code_repo), (result as Result.Failure).exception.message)
    }
}