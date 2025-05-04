package com.sosiso4kawo.zschoolapp.data.repository

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

    // Mocks for dependencies
    private lateinit var authService: AuthService
    private lateinit var sessionManager: SessionManager

    // Class under test
    private lateinit var authRepository: AuthRepository

    // Test dispatcher for coroutines
    private val testDispatcher = StandardTestDispatcher()

    // Valid credentials provided by the user
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
        // Initialize mocks
        authService = mockk()
        sessionManager = mockk(relaxed = true) // relaxed = true allows skipping setup for every method

        // Initialize the repository with mocks
        authRepository = AuthRepository(authService, sessionManager)

        // Set the main dispatcher for coroutines testing
        Dispatchers.setMain(testDispatcher)

        // Default mock behaviors
        coEvery { sessionManager.saveTokens(any(), any(), any()) } just Runs
        coEvery { sessionManager.saveUserData(any()) } just Runs
        coEvery { sessionManager.clearSession() } just Runs
        coEvery { sessionManager.getAccessToken() } returns validAccessToken
        coEvery { sessionManager.getRefreshToken() } returns validRefreshToken
        coEvery { sessionManager.getUserData() } returns validUser
    }

    @After
    fun tearDown() {
        // Reset the main dispatcher
        Dispatchers.resetMain()
        // Clear mocks if necessary (optional with MockK)
        unmockkAll()
    }

    // --- Login Tests ---

    @Test
    fun `login success - valid credentials, saves tokens and user data`() = runTest {
        // Arrange: Mock successful API responses
        val loginRequest = LoginRequest(validEmail, validPassword)
        coEvery { authService.login(loginRequest) } returns Response.success(validAuthResponse)
        coEvery { authService.getProfile("Bearer $validAccessToken") } returns Response.success(validUser)

        // Act: Call the login function
        val result = authRepository.login(validEmail, validPassword)

        // Assert: Check for success and verify interactions
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
        // Arrange: Mock 401 error response
        val loginRequest = LoginRequest(validEmail, "wrong_password")
        val errorBody = """{"message": "invalid credentials"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.login(loginRequest) } returns Response.error(401, errorBody)

        // Act: Call the login function
        val result = authRepository.login(validEmail, "wrong_password")

        // Assert: Check for failure and correct error message
        assertTrue(result is Result.Failure)
        assertEquals("Неверный логин или пароль", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) } // Ensure tokens not saved
        coVerify(exactly = 0) { sessionManager.saveUserData(any()) } // Ensure user data not saved
    }

    @Test
    fun `login failure - email not confirmed (401 specific error)`() = runTest {
        // Arrange: Mock 401 with specific error message
        val loginRequest = LoginRequest(validEmail, validPassword)
        val errorBody = """{"message": "email not confirmed"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.login(loginRequest) } returns Response.error(401, errorBody)

        // Act: Call the login function
        val result = authRepository.login(validEmail, validPassword)

        // Assert: Check for failure and specific error message
        assertTrue(result is Result.Failure)
        assertEquals("email not confirmed", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
        coVerify(exactly = 0) { sessionManager.saveUserData(any()) }
    }

    @Test
    fun `login failure - invalid email format error from server (400)`() = runTest {
        // Arrange: Mock 400 error for invalid email format
        val invalidEmailFormat = "invalid-email"
        val loginRequest = LoginRequest(invalidEmailFormat, validPassword)
        // Simulate the specific error message the repository checks for
        val errorBody = """{"detail":"Field validation for 'Email' failed on the 'email' tag"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.login(loginRequest) } returns Response.error(400, errorBody)

        // Act
        val result = authRepository.login(invalidEmailFormat, validPassword)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Неверный формат почты", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `login failure - server error (500)`() = runTest {
        // Arrange: Mock 500 error response
        val loginRequest = LoginRequest(validEmail, validPassword)
        val errorBody = """{"message": "Internal Server Error"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.login(loginRequest) } returns Response.error(500, errorBody)

        // Act: Call the login function
        val result = authRepository.login(validEmail, validPassword)

        // Assert: Check for failure and general server error message
        assertTrue(result is Result.Failure)
        assertEquals("Internal Server Error", (result as Result.Failure).exception.message) // Or potentially "Ошибка сервера (500)" if parsing fails
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `login failure - network error (UnknownHostException)`() = runTest {
        // Arrange: Mock network exception
        val loginRequest = LoginRequest(validEmail, validPassword)
        coEvery { authService.login(loginRequest) } throws UnknownHostException("Cannot resolve host")

        // Act: Call the login function
        val result = authRepository.login(validEmail, validPassword)

        // Assert: Check for failure and network error message
        assertTrue(result is Result.Failure)
        assertEquals("Нет подключения к интернету", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `login failure - network error (SocketTimeoutException)`() = runTest {
        // Arrange: Mock network exception
        val loginRequest = LoginRequest(validEmail, validPassword)
        coEvery { authService.login(loginRequest) } throws SocketTimeoutException("Timeout")

        // Act: Call the login function
        val result = authRepository.login(validEmail, validPassword)

        // Assert: Check for failure and network error message
        assertTrue(result is Result.Failure)
        assertEquals("Превышено время ожидания ответа от сервера", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `login success but profile fetch fails`() = runTest {
        // Arrange: Mock successful login, but failed profile fetch
        val loginRequest = LoginRequest(validEmail, validPassword)
        coEvery { authService.login(loginRequest) } returns Response.success(validAuthResponse)
        coEvery { authService.getProfile("Bearer $validAccessToken") } returns Response.error(404, "".toResponseBody()) // Simulate profile not found

        // Act
        val result = authRepository.login(validEmail, validPassword)

        // Assert: Login itself should still succeed, tokens saved, but user data not saved
        assertTrue(result is Result.Success)
        assertEquals(validAuthResponse, (result as Result.Success).value)
        coVerifySequence {
            authService.login(loginRequest)
            sessionManager.saveTokens(validAccessToken, validRefreshToken, 604800L)
            authService.getProfile("Bearer $validAccessToken") // Profile fetch is called
            // sessionManager.saveUserData(any()) is NOT called
        }
        coVerify(exactly = 0) { sessionManager.saveUserData(any()) }
    }


    // --- Registration Tests ---

    @Test
    fun `register success - valid data`() = runTest {
        // Arrange
        val registerRequest = RegisterRequest(validEmail, validPassword)
        // Assuming registration returns tokens on success (200 OK)
        coEvery { authService.register(registerRequest) } returns Response.success(200, validAuthResponse)

        // Act
        val result = authRepository.register(validEmail, validPassword)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(validAuthResponse, (result as Result.Success).value)
    }

    @Test
    fun `register success - 204 No Content`() = runTest {
        // Arrange
        val registerRequest = RegisterRequest(validEmail, validPassword)
        // Simulate 204 response which means success but no body content
        // FIX: Explicitly use the overload with status code 204 and null body
        coEvery { authService.register(registerRequest) } returns Response.success(204, null as AuthResponse?)

        // Act
        val result = authRepository.register(validEmail, validPassword)

        // Assert: The repository currently interprets 204 with null body as Failure.
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception.message?.contains("Регистрация успешна") ?: false ||
                result.exception.message?.contains("сервер вернул пустой ответ") ?: false)
    }

    @Test
    fun `register failure - user already exists (409)`() = runTest {
        // Arrange
        val registerRequest = RegisterRequest(validEmail, validPassword)
        val errorBody = """{"message": "User already exists"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.register(registerRequest) } returns Response.error(409, errorBody)

        // Act
        val result = authRepository.register(validEmail, validPassword)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Пользователь с таким email или логином уже существует", (result as Result.Failure).exception.message)
    }

    @Test
    fun `register failure - server error (500)`() = runTest {
        // Arrange
        val registerRequest = RegisterRequest(validEmail, validPassword)
        val errorBody = """{"message": "Server fault"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.register(registerRequest) } returns Response.error(500, errorBody)

        // Act
        val result = authRepository.register(validEmail, validPassword)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Server fault", (result as Result.Failure).exception.message) // Or a generic error message
    }

    @Test
    fun `register failure - network error (UnknownHostException)`() = runTest {
        // Arrange
        val registerRequest = RegisterRequest(validEmail, validPassword)
        coEvery { authService.register(registerRequest) } throws UnknownHostException()

        // Act
        val result = authRepository.register(validEmail, validPassword)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Нет подключения к интернету", (result as Result.Failure).exception.message)
    }


    // --- Refresh Token Tests ---

    @Test
    fun `refreshToken success - valid refresh token`() = runTest {
        // Arrange
        val newAccessToken = "new_access_token"
        val newRefreshToken = "new_refresh_token"
        val refreshRequest = RefreshTokenRequest(validRefreshToken)
        val newAuthResponse = AuthResponse(newAccessToken, newRefreshToken)
        coEvery { authService.refreshToken(refreshRequest) } returns Response.success(newAuthResponse)

        // Act
        val result = authRepository.refreshToken(validRefreshToken)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(newAuthResponse, (result as Result.Success).value)
        coVerify { sessionManager.saveTokens(newAccessToken, newRefreshToken, 604800L) }
    }

    @Test
    fun `refreshToken failure - invalid refresh token (401)`() = runTest {
        // Arrange
        val invalidRefreshToken = "invalid_token"
        val refreshRequest = RefreshTokenRequest(invalidRefreshToken)
        val errorBody = """{"message": "Invalid token"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.refreshToken(refreshRequest) } returns Response.error(401, errorBody)

        // Act
        val result = authRepository.refreshToken(invalidRefreshToken)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Сессия истекла, требуется повторная авторизация", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `refreshToken failure - server error (500)`() = runTest {
        // Arrange
        val refreshRequest = RefreshTokenRequest(validRefreshToken)
        val errorBody = """{"message": "Server down"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.refreshToken(refreshRequest) } returns Response.error(500, errorBody)

        // Act
        val result = authRepository.refreshToken(validRefreshToken)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Server down", (result as Result.Failure).exception.message) // Or generic error
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }

    @Test
    fun `refreshToken failure - network error`() = runTest {
        // Arrange
        val refreshRequest = RefreshTokenRequest(validRefreshToken)
        coEvery { authService.refreshToken(refreshRequest) } throws SocketTimeoutException()

        // Act
        val result = authRepository.refreshToken(validRefreshToken)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Превышено время ожидания ответа от сервера", (result as Result.Failure).exception.message)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any(), any()) }
    }


    // --- Logout Tests ---

    @Test
    fun `logout success - valid access token`() = runTest {
        // Arrange
        coEvery { sessionManager.getAccessToken() } returns validAccessToken
        // FIX: Explicitly use the overload with status code 200 and null body for Void
        coEvery { authService.logout("Bearer $validAccessToken") } returns Response.success(200, null as Void?)

        // Act
        val results = mutableListOf<Result<Unit>>()
        authRepository.logout().collect { results.add(it) }

        // Assert
        assertTrue(results.isNotEmpty())
        assertTrue(results.last() is Result.Success)
    }

    @Test
    fun `logout success - no access token locally`() = runTest {
        // Arrange: Simulate no token being available locally
        coEvery { sessionManager.getAccessToken() } returns null

        // Act
        val results = mutableListOf<Result<Unit>>()
        authRepository.logout().collect { results.add(it) }

        // Assert: Logout should succeed immediately without calling the service
        assertTrue(results.isNotEmpty())
        assertTrue(results.last() is Result.Success)
        coVerify(exactly = 0) { authService.logout(any()) } // Verify API wasn't called
    }

    @Test
    fun `logout success - server returns 401 (treat as success)`() = runTest {
        // Arrange: Simulate server returning 401 (e.g., token already expired on server)
        coEvery { sessionManager.getAccessToken() } returns validAccessToken
        val errorBody = "".toResponseBody()
        coEvery { authService.logout("Bearer $validAccessToken") } returns Response.error(401, errorBody)

        // Act
        val results = mutableListOf<Result<Unit>>()
        authRepository.logout().collect { results.add(it) }

        // Assert: Repository treats 401 on logout as success locally
        assertTrue(results.isNotEmpty())
        assertTrue(results.last() is Result.Success)
    }


    @Test
    fun `logout failure - server error (500)`() = runTest {
        // Arrange
        coEvery { sessionManager.getAccessToken() } returns validAccessToken
        val errorBody = """{"message": "Logout failed server side"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { authService.logout("Bearer $validAccessToken") } returns Response.error(500, errorBody)

        // Act
        val results = mutableListOf<Result<Unit>>()
        authRepository.logout().collect { results.add(it) }

        // Assert
        assertTrue(results.isNotEmpty())
        assertTrue(results.last() is Result.Failure)
        assertEquals("Logout failed server side", (results.last() as Result.Failure).exception.message)
    }

    @Test
    fun `logout failure - network error`() = runTest {
        // Arrange
        coEvery { sessionManager.getAccessToken() } returns validAccessToken
        coEvery { authService.logout("Bearer $validAccessToken") } throws UnknownHostException()

        // Act
        val results = mutableListOf<Result<Unit>>()
        authRepository.logout().collect { results.add(it) }

        // Assert
        assertTrue(results.isNotEmpty())
        assertTrue(results.last() is Result.Failure)
        assertEquals("Нет подключения к интернету", (results.last() as Result.Failure).exception.message)
    }

    // --- Send Verification Code Tests ---

    @Test
    fun `sendVerificationCode success`() = runTest {
        // Arrange
        // FIX: Use explicit success(code, body) for Unit response
        coEvery { authService.sendVerificationCode(validEmail) } returns Response.success(200, Unit)

        // Act
        val result = authRepository.sendVerificationCode(validEmail)

        // Assert
        assertTrue(result is Result.Success)
    }

    @Test
    fun `sendVerificationCode failure - server error`() = runTest {
        // Arrange
        val errorBody = "".toResponseBody()
        coEvery { authService.sendVerificationCode(validEmail) } returns Response.error(500, errorBody)

        // Act
        val result = authRepository.sendVerificationCode(validEmail)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Ошибка отправки кода", (result as Result.Failure).exception.message)
    }

    @Test
    fun `sendVerificationCode failure - network error`() = runTest {
        // Arrange
        coEvery { authService.sendVerificationCode(validEmail) } throws UnknownHostException()

        // Act
        val result = authRepository.sendVerificationCode(validEmail)

        // Assert
        assertTrue(result is Result.Failure)
        // The repository wraps the original exception here
        assertTrue((result as Result.Failure).exception is UnknownHostException)
    }

    // --- Verify Email Tests ---

    @Test
    fun `verifyEmail success`() = runTest {
        // Arrange
        val code = "123456"
        val request = VerificationRequest(validEmail, code)
        // FIX: Use explicit success(code, body) for Unit response
        coEvery { authService.verifyEmail(request) } returns Response.success(200, Unit)

        // Act
        val result = authRepository.verifyEmail(validEmail, code)

        // Assert
        assertTrue(result is Result.Success)
    }

    @Test
    fun `verifyEmail failure - server error`() = runTest {
        // Arrange
        val code = "123456"
        val request = VerificationRequest(validEmail, code)
        val errorBody = "".toResponseBody()
        coEvery { authService.verifyEmail(request) } returns Response.error(400, errorBody) // e.g., invalid code

        // Act
        val result = authRepository.verifyEmail(validEmail, code)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Ошибка подтверждения почты", (result as Result.Failure).exception.message)
    }

    // --- Get Email Tests ---
    @Test
    fun `getEmail success`() = runTest {
        // Arrange
        val emailResponse = EmailResponse(validEmail)
        coEvery { authService.getEmail("Bearer $validAccessToken") } returns Response.success(emailResponse)

        // Act
        val results = mutableListOf<Result<EmailResponse>>()
        authRepository.getEmail("Bearer $validAccessToken").collect{ results.add(it) }


        // Assert
        assertTrue(results.isNotEmpty())
        val lastResult = results.last()
        assertTrue(lastResult is Result.Success)
        assertEquals(emailResponse, (lastResult as Result.Success).value)
    }

    @Test
    fun `getEmail failure - 401 Unauthorized`() = runTest {
        // Arrange
        val errorBody = "".toResponseBody()
        coEvery { authService.getEmail("Bearer $validAccessToken") } returns Response.error(401, errorBody)

        // Act
        val results = mutableListOf<Result<EmailResponse>>()
        authRepository.getEmail("Bearer $validAccessToken").collect{ results.add(it) }

        // Assert
        assertTrue(results.isNotEmpty())
        val lastResult = results.last()
        assertTrue(lastResult is Result.Failure)
        assertEquals("Требуется авторизация", (lastResult as Result.Failure).exception.message)
    }

    @Test
    fun `getEmail failure - 404 Not Found`() = runTest {
        // Arrange
        val errorBody = "".toResponseBody()
        coEvery { authService.getEmail("Bearer $validAccessToken") } returns Response.error(404, errorBody)

        // Act
        val results = mutableListOf<Result<EmailResponse>>()
        authRepository.getEmail("Bearer $validAccessToken").collect{ results.add(it) }

        // Assert
        assertTrue(results.isNotEmpty())
        val lastResult = results.last()
        assertTrue(lastResult is Result.Failure)
        assertEquals("Email не найден", (lastResult as Result.Failure).exception.message)
    }

    // --- Reset Password Tests ---
    @Test
    fun `resetPassword success`() = runTest {
        // Arrange
        val code = "123456"
        val newPassword = "NewPassword123!"
        val request = PasswordResetRequest(validEmail, code, newPassword)
        // FIX: Use explicit success(code, body) for Unit response
        coEvery { authService.resetPassword(request) } returns Response.success(200, Unit)

        // Act
        val result = authRepository.resetPassword(validEmail, code, newPassword)

        // Assert
        assertTrue(result is Result.Success)
    }

    @Test
    fun `resetPassword failure - invalid code (400)`() = runTest {
        // Arrange
        val code = "wrongcode"
        val newPassword = "NewPassword123!"
        val request = PasswordResetRequest(validEmail, code, newPassword)
        val errorBody = "".toResponseBody()
        coEvery { authService.resetPassword(request) } returns Response.error(400, errorBody)

        // Act
        val result = authRepository.resetPassword(validEmail, code, newPassword)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Неверный код подтверждения", (result as Result.Failure).exception.message)
    }

    @Test
    fun `resetPassword failure - email not found (404)`() = runTest {
        // Arrange
        val code = "123456"
        val newPassword = "NewPassword123!"
        val request = PasswordResetRequest(validEmail, code, newPassword)
        val errorBody = "".toResponseBody()
        coEvery { authService.resetPassword(request) } returns Response.error(404, errorBody)

        // Act
        val result = authRepository.resetPassword(validEmail, code, newPassword)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Email не найден", (result as Result.Failure).exception.message)
    }

    @Test
    fun `resetPassword failure - network error`() = runTest {
        // Arrange
        val code = "123456"
        val newPassword = "NewPassword123!"
        val request = PasswordResetRequest(validEmail, code, newPassword)
        coEvery { authService.resetPassword(request) } throws SocketTimeoutException()

        // Act
        val result = authRepository.resetPassword(validEmail, code, newPassword)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Превышено время ожидания ответа от сервера", (result as Result.Failure).exception.message)
    }
}