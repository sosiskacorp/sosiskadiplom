import com.sosiso4kawo.betaapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.betaapp.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST

interface UserService {
    @GET("v1/users/me")
    suspend fun getProfile(@Header("Authorization") token: String): Response<User>

    @PATCH("v1/users/me")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<Void>
}