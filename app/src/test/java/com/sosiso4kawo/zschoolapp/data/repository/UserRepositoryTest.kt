package com.sosiso4kawo.zschoolapp.data.repository

import com.sosiso4kawo.zschoolapp.data.api.UserService
import com.sosiso4kawo.zschoolapp.data.model.CourseProgress
import com.sosiso4kawo.zschoolapp.data.model.ExerciseProgress
import com.sosiso4kawo.zschoolapp.data.model.LeaderboardResponse
import com.sosiso4kawo.zschoolapp.data.model.LeaderboardUser
import com.sosiso4kawo.zschoolapp.data.model.LessonProgress
import com.sosiso4kawo.zschoolapp.data.model.ProgressResponse
import com.sosiso4kawo.zschoolapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.zschoolapp.data.model.User
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
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
class UserRepositoryTest {

    private lateinit var userService: UserService
    private lateinit var sessionManager: SessionManager
    private lateinit var userRepository: UserRepository
    private val testDispatcher = StandardTestDispatcher()

    private val validAccessToken = "valid_access_token"
    private val validUser = User(
        uuid = "test-uuid",
        login = "testLogin",
        name = "Test",
        last_name = "User",
        second_name = null,
        avatar = "avatar_url",
        total_points = 100,
        finished_courses = 5
    )
    private val validProgressResponse = ProgressResponse(
        courses = listOf(CourseProgress("course-uuid-1", "course1", 50, "date")),
        exercises = listOf(ExerciseProgress("exercise-uuid-1", "exercise1", 10, "date")),
        lessons = listOf(LessonProgress("lesson-uuid-1", "lesson1", 20, "date"))
    )
    private val validLeaderboard = listOf(
        LeaderboardUser("user-uuid-1", "user1", null, null, null, null, 1, 200),
        LeaderboardUser("user-uuid-2", "user2", null, null, null, null, 2, 150)
    )

    @Before
    fun setUp() {
        userService = mockk()
        sessionManager = mockk(relaxed = true)
        userRepository = UserRepository(userService, sessionManager)
        Dispatchers.setMain(testDispatcher)

        // Default behavior for sessionManager mocks
        coEvery { sessionManager.getAccessToken() } returns validAccessToken
        coEvery { sessionManager.getUserData() } returns validUser
        coEvery { sessionManager.saveUserData(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `getProfile success - data from API`() = runTest {
        // Arrange
        coEvery { sessionManager.getUserData() } returns null // Simulate no cached data
        coEvery { userService.getProfile("Bearer $validAccessToken") } returns Response.success<User>(validUser)

        // Act
        val result = userRepository.getProfile().first()

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(validUser, (result as Result.Success).value)
        // В этом сценарии кеша нет, поэтому все вызовы должны произойти последовательно до эмиссии Success
        coVerifySequence {
            sessionManager.getAccessToken()
            sessionManager.getUserData()
            userService.getProfile(eq("Bearer $validAccessToken")) // Исправлено
            sessionManager.saveUserData(validUser)
        }
    }

    @Test
    fun `getProfile success - data from cache`() = runTest {
        // Arrange
        coEvery { sessionManager.getUserData() } returns validUser // Simulate cached data
        // API call still happens in parallel, but first() collects cached data immediately
        coEvery { userService.getProfile("Bearer $validAccessToken") } returns Response.success<User>(validUser)
        coEvery { sessionManager.saveUserData(validUser) } just Runs


        // Act
        val result = userRepository.getProfile().first() // Collects the cached data immediately

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(validUser, (result as Result.Success).value)
        // В этом сценарии getProfile() сначала проверяет кэш, эмиттит его и только потом делает вызов API.
        // first() отменяет Flow после первой эмиссии (из кэша).
        // Поэтому в последовательности должны быть только вызовы до первой эмиссии.
        coVerifySequence {
            sessionManager.getAccessToken()
            sessionManager.getUserData()
            // Вызовы userService.getProfile и sessionManager.saveUserData не в строгой последовательности до первой эмиссии
        }
        // В этом сценарии first() получает данные из кеша и отменяет Flow.
        // Вызовы userService.getProfile и sessionManager.saveUserData происходят в репозитории,
        // но не успевают выполниться в рамках этого теста до отмены Flow оператором first().
        // Поэтому мы не верифицируем их здесь.
    }


    @Test
    fun `getProfile failure - no access token`() = runTest {
        // Arrange
        coEvery { sessionManager.getAccessToken() } returns null

        // Act
        val result = userRepository.getProfile().first()

        // Assert
        assertTrue(result is Result.Failure)
        // Теперь сообщение об ошибке будет соответствовать тому, что генерирует репозиторий при отсутствии токена
        assertEquals("Токен доступа отсутствует", (result as Result.Failure).exception.message)
        // Проверяем только вызов getAccessToken, так как Flow завершается сразу после этой проверки
        coVerifySequence {
            sessionManager.getAccessToken()
        }
        coVerify(exactly = 0) { sessionManager.getUserData() }
        coVerify(exactly = 0) { userService.getProfile(any()) }
        coVerify(exactly = 0) { sessionManager.saveUserData(any()) }
    }

    @Test
    fun `getProfile failure - API returns 401`() = runTest {
        // Arrange
        coEvery { sessionManager.getUserData() } returns null // Убедимся, что кеш пуст
        val errorBody = """{"message": "Invalid token"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { userService.getProfile("Bearer $validAccessToken") } returns Response.error<User>(401, errorBody) // Явно указываем тип User

        // Act
        val result = userRepository.getProfile().first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Недействительный или просроченный токен", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента ошибки и эмиссии Failure
        coVerifySequence {
            sessionManager.getAccessToken()
            sessionManager.getUserData() // Этот вызов происходит перед API вызовом
            userService.getProfile(eq("Bearer $validAccessToken")) // Исправлено
        }
    }

    @Test
    fun `getProfile failure - network error`() = runTest {
        // Arrange
        coEvery { sessionManager.getUserData() } returns null // Убедимся, что кеш пуст
        coEvery { userService.getProfile("Bearer $validAccessToken") } throws UnknownHostException()

        // Act
        val result = userRepository.getProfile().first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Нет подключения к интернету", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента возникновения сетевой ошибки
        coVerifySequence {
            sessionManager.getAccessToken()
            sessionManager.getUserData() // Этот вызов происходит перед API вызовом
            userService.getProfile(eq("Bearer $validAccessToken")) // Исправлено
        }
    }

    @Test
    fun `updateProfile success`() = runTest {
        // Arrange
        val updateRequest = UpdateProfileRequest("newLogin", "newName", "newLastName", null, null)
        coEvery { userService.updateProfile("Bearer $validAccessToken", updateRequest) } returns Response.success<Void>(200, null)
        // Act
        val result = userRepository.updateProfile(updateRequest).first()

        // Assert
        assertTrue(result is Result.Success)
        // Проверяем вызовы до эмиссии Success
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.updateProfile(eq("Bearer $validAccessToken"), eq(updateRequest)) // Уточняем eq для аргументов
        }
    }

    @Test
    fun `updateProfile failure - no access token`() = runTest {
        // Arrange
        coEvery { sessionManager.getAccessToken() } returns null
        val updateRequest = UpdateProfileRequest("newLogin", null, null, null, null)

        // Act
        val result = userRepository.updateProfile(updateRequest).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Токен доступа отсутствует", (result as Result.Failure).exception.message)
        // Проверяем только вызов getAccessToken, так как Flow завершается сразу
        coVerifySequence {
            sessionManager.getAccessToken()
        }
        coVerify(exactly = 0) { userService.updateProfile(any(), any()) }
    }

    @Test
    fun `updateProfile failure - API returns 401`() = runTest {
        // Arrange
        val updateRequest = UpdateProfileRequest("newLogin", null, null, null, null)
        val errorBody = """{"message": "Invalid token"}""".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { userService.updateProfile("Bearer $validAccessToken", updateRequest) } returns Response.error<Void>(401, errorBody)

        // Act
        val result = userRepository.updateProfile(updateRequest).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Недействительный или просроченный токен", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента ошибки и эмиссии Failure
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.updateProfile(eq("Bearer $validAccessToken"), eq(updateRequest)) // Уточняем eq для аргументов
        }
    }

    @Test
    fun `updateProfile failure - network error`() = runTest {
        // Arrange
        val updateRequest = UpdateProfileRequest("newLogin", null, null, null, null)
        coEvery { userService.updateProfile("Bearer $validAccessToken", updateRequest) } throws SocketTimeoutException()

        // Act
        val result = userRepository.updateProfile(updateRequest).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Превышено время ожидания ответа от сервера", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента возникновения сетевой ошибки
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.updateProfile(eq("Bearer $validAccessToken"), eq(updateRequest)) // Уточняем eq для аргументов
        }
    }

    @Test
    fun `uploadAvatar success`() = runTest {
        // Arrange
        val file = mockk<MultipartBody.Part>()
        coEvery { userService.uploadAvatar("Bearer $validAccessToken", file) } returns Response.success<Void>(200, null)

        // Act
        val response = userRepository.uploadAvatar("Bearer $validAccessToken", file)

        // Assert
        assertTrue(response.isSuccessful)
        assertEquals(200, response.code())
        // Это suspend функция, не Flow, поэтому просто верифицируем вызов
        coVerify(exactly = 1) { userService.uploadAvatar(eq("Bearer $validAccessToken"), eq(file)) } // Уточняем eq
    }

    @Test
    fun `uploadAvatar failure - API returns error`() = runTest {
        // Arrange
        val file = mockk<MultipartBody.Part>()
        val errorBody = "".toResponseBody()
        coEvery { userService.uploadAvatar("Bearer $validAccessToken", file) } returns Response.error<Void>(500, errorBody)

        // Act
        val response = userRepository.uploadAvatar("Bearer $validAccessToken", file)

        // Assert
        assertTrue(!response.isSuccessful)
        assertEquals(500, response.code())
        // Это suspend функция, не Flow, поэтому просто верифицируем вызов
        coVerify(exactly = 1) { userService.uploadAvatar(eq("Bearer $validAccessToken"), eq(file)) } // Уточняем eq
    }

    @Test
    fun `uploadAvatar failure - network error`() = runTest {
        // Arrange
        val file = mockk<MultipartBody.Part>()
        coEvery { userService.uploadAvatar("Bearer $validAccessToken", file) } throws UnknownHostException()

        // Act & Assert
        try {
            userRepository.uploadAvatar("Bearer $validAccessToken", file)
            assert(false) { "Expected UnknownHostException" }
        } catch (e: UnknownHostException) {
            assertTrue(true) // Успех, если поймали ожидаемое исключение
        } catch (e: Exception) {
            assert(false) { "Expected UnknownHostException but caught ${e::class.java.simpleName}" }
        }
        // Это suspend функция, не Flow, поэтому просто верифицируем вызов
        coVerify(exactly = 1) { userService.uploadAvatar(eq("Bearer $validAccessToken"), eq(file)) } // Уточняем eq
    }


    @Test
    fun `getAllUsers success`() = runTest {
        // Arrange
        val leaderboardResponse = LeaderboardResponse(validLeaderboard)
        coEvery { userService.getAllUsers(10, 0) } returns Response.success<LeaderboardResponse>(leaderboardResponse)

        // Act
        val result = userRepository.getAllUsers(10, 0).first()

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(validLeaderboard, (result as Result.Success).value)
        // Проверяем вызовы до эмиссии Success
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getAllUsers(eq(10), eq(0)) // Уточняем eq
        }
    }

    @Test
    fun `getAllUsers failure - no access token`() = runTest {
        // Arrange
        coEvery { sessionManager.getAccessToken() } returns null

        // Act
        val result = userRepository.getAllUsers(10, 0).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Токен доступа отсутствует", (result as Result.Failure).exception.message)
        // Проверяем только вызов getAccessToken, так как Flow завершается сразу
        coVerifySequence {
            sessionManager.getAccessToken()
        }
        coVerify(exactly = 0) { userService.getAllUsers(any(), any()) }
    }

    @Test
    fun `getAllUsers failure - API returns error`() = runTest {
        // Arrange
        val errorBody = "".toResponseBody()
        coEvery { userService.getAllUsers(10, 0) } returns Response.error<LeaderboardResponse>(500, errorBody)

        // Act
        val result = userRepository.getAllUsers(10, 0).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Ошибка сервера: 500", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента ошибки и эмиссии Failure
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getAllUsers(eq(10), eq(0)) // Уточняем eq
        }
    }

    @Test
    fun `getAllUsers failure - network error`() = runTest {
        // Arrange
        coEvery { userService.getAllUsers(10, 0) } throws SocketTimeoutException()

        // Act
        val result = userRepository.getAllUsers(10, 0).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Превышено время ожидания ответа от сервера", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента возникновения сетевой ошибки
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getAllUsers(eq(10), eq(0)) // Уточняем eq
        }
    }

    @Test
    fun `getProgress success`() = runTest {
        // Arrange
        coEvery { userService.getProgress("Bearer $validAccessToken") } returns Response.success<ProgressResponse>(validProgressResponse)

        // Act
        val result = userRepository.getProgress().first()

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(validProgressResponse, (result as Result.Success).value)
        // Проверяем вызовы до эмиссии Success
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getProgress(eq("Bearer $validAccessToken")) // Исправлено
        }
    }

    @Test
    fun `getProgress failure - no access token`() = runTest {
        // Arrange
        coEvery { sessionManager.getAccessToken() } returns null

        // Act
        val result = userRepository.getProgress().first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Нет токена авторизации", (result as Result.Failure).exception.message)
        // Проверяем только вызов getAccessToken, так как Flow завершается сразу
        coVerifySequence {
            sessionManager.getAccessToken()
        }
        coVerify(exactly = 0) { userService.getProgress(any()) }
    }

    @Test
    fun `getProgress failure - API returns error`() = runTest {
        // Arrange
        val errorBody = "".toResponseBody()
        coEvery { userService.getProgress("Bearer $validAccessToken") } returns Response.error<ProgressResponse>(500, errorBody)

        // Act
        val result = userRepository.getProgress().first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Ошибка загрузки прогресса", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента ошибки и эмиссии Failure
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getProgress(eq("Bearer $validAccessToken")) // Исправлено
        }
    }

    @Test
    fun `getProgress failure - network error`() = runTest {
        // Arrange
        coEvery { userService.getProgress("Bearer $validAccessToken") } throws UnknownHostException()

        // Act
        val result = userRepository.getProgress().first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Нет подключения к интернету", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента возникновения сетевой ошибки
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getProgress(eq("Bearer $validAccessToken")) // Исправлено
        }
    }

    @Test
    fun `getUserByUuid success`() = runTest {
        // Arrange
        val userUuid = "some-user-uuid"
        val userResponse = User(userUuid, "otherUser", null, null, null, null, 50, 2)
        coEvery { userService.getUserByUuid(userUuid, "Bearer $validAccessToken") } returns Response.success<User>(userResponse)

        // Act
        val result = userRepository.getUserByUuid(userUuid).first()

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(userResponse, (result as Result.Success).value)
        // Проверяем вызовы до эмиссии Success
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getUserByUuid(eq(userUuid), eq("Bearer $validAccessToken")) // Уточняем eq
        }
    }

    @Test
    fun `getUserByUuid failure - no access token`() = runTest {
        // Arrange
        coEvery { sessionManager.getAccessToken() } returns null
        val userUuid = "some-user-uuid"

        // Act
        val result = userRepository.getUserByUuid(userUuid).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Токен доступа отсутствует", (result as Result.Failure).exception.message)
        // Проверяем только вызов getAccessToken, так как Flow завершается сразу
        coVerifySequence {
            sessionManager.getAccessToken()
        }
        coVerify(exactly = 0) { userService.getUserByUuid(any(), any()) }
    }

    @Test
    fun `getUserByUuid failure - API returns error`() = runTest {
        // Arrange
        val userUuid = "some-user-uuid"
        val errorBody = "".toResponseBody()
        coEvery { userService.getUserByUuid(userUuid, "Bearer $validAccessToken") } returns Response.error<User>(404, errorBody)

        // Act
        val result = userRepository.getUserByUuid(userUuid).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Ошибка сервера: 404", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента ошибки и эмиссии Failure
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getUserByUuid(eq(userUuid), eq("Bearer $validAccessToken")) // Уточняем eq
        }
    }

    @Test
    fun `getUserByUuid failure - network error`() = runTest {
        // Arrange
        val userUuid = "some-user-uuid"
        coEvery { userService.getUserByUuid(userUuid, "Bearer $validAccessToken") } throws SocketTimeoutException()

        // Act
        val result = userRepository.getUserByUuid(userUuid).first()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Превышено время ожидания ответа от сервера", (result as Result.Failure).exception.message)
        // Проверяем вызовы до момента возникновения сетевой ошибки
        coVerifySequence {
            sessionManager.getAccessToken()
            userService.getUserByUuid(eq(userUuid), eq("Bearer $validAccessToken")) // Уточняем eq
        }
    }
}