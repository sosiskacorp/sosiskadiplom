package com.sosiso4kawo.zschoolapp.di

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.sosiso4kawo.zschoolapp.navigation.AppNavigator
import com.sosiso4kawo.zschoolapp.data.api.AchievementsService
import com.sosiso4kawo.zschoolapp.data.api.AuthService
import com.sosiso4kawo.zschoolapp.data.api.CoursesService
import com.sosiso4kawo.zschoolapp.data.api.ExercisesService
import com.sosiso4kawo.zschoolapp.data.api.LessonsService
import com.sosiso4kawo.zschoolapp.data.api.UserService
import com.sosiso4kawo.zschoolapp.data.repository.AuthRepository
import com.sosiso4kawo.zschoolapp.data.repository.UserRepository
import com.sosiso4kawo.zschoolapp.network.AuthInterceptor
import com.sosiso4kawo.zschoolapp.network.NavigationListener
import com.sosiso4kawo.zschoolapp.ui.auth.AuthViewModel
import com.sosiso4kawo.zschoolapp.util.Constants
import com.sosiso4kawo.zschoolapp.util.SessionManager
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {
    single { SessionManager(androidContext()) }
    single<NavigationListener> { AppNavigator(get()) } // get() здесь получит androidContext()
    single {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get(), get())) // SessionManager, NavigationListener
            .build()
    }
    single {
        GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }
    single {
        Retrofit.Builder()
            .baseUrl(Constants.API_BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create(get()))
            .build()
    }

    // API Services
    single { get<Retrofit>().create(AuthService::class.java) }
    single { get<Retrofit>().create(AchievementsService::class.java) }
    single { get<Retrofit>().create(UserService::class.java) }
    single { get<Retrofit>().create(CoursesService::class.java) }
    single { get<Retrofit>().create(LessonsService::class.java) }
    single { get<Retrofit>().create(ExercisesService::class.java) }

    // Repositories
    single { AuthRepository(androidContext(), get(), get()) } // Context, AuthService, SessionManager
    single { UserRepository(get(), get()) } // UserService, SessionManager

    // ViewModels
    // Синтаксис viewModel { ... } должен остаться тем же с новым импортом
    viewModel { AuthViewModel(androidApplication(), get(), get()) } // Application, AuthRepository, SessionManager
}