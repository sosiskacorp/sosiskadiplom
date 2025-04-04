package com.sosiso4kawo.betaapp.di

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.sosiso4kawo.betaapp.navigation.AppNavigator
import com.sosiso4kawo.betaapp.data.api.AchievementsService
import com.sosiso4kawo.betaapp.data.api.AuthService
import com.sosiso4kawo.betaapp.data.api.CoursesService
import com.sosiso4kawo.betaapp.data.api.ExercisesService
import com.sosiso4kawo.betaapp.data.api.LessonsService
import com.sosiso4kawo.betaapp.data.api.UserService
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.network.AuthInterceptor
import com.sosiso4kawo.betaapp.network.NavigationListener
import com.sosiso4kawo.betaapp.ui.auth.AuthViewModel
import com.sosiso4kawo.betaapp.util.SessionManager
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {
    // SessionManager с контекстом приложения
    single { SessionManager(androidContext()) }
    single<NavigationListener> { AppNavigator(get()) }

    // OkHttpClient с AuthInterceptor
    single {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get(), get()))
            .build()
    }

    // Создаем кастомный Gson-инстанс с нужной политикой именования
    single {
        GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }

    // Retrofit с кастомным Gson
    single {
        Retrofit.Builder()
            .baseUrl("http://37.18.102.166:3211")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create(get()))
            .build()
    }

    single { get<Retrofit>().create(AuthService::class.java) }
    single { get<Retrofit>().create(AchievementsService::class.java) }
    single { get<Retrofit>().create(UserService::class.java) }
    single { get<Retrofit>().create(CoursesService::class.java) }
    single { get<Retrofit>().create(LessonsService::class.java) }
    single { get<Retrofit>().create(ExercisesService::class.java) }

    // Репозитории
    single { AuthRepository(get(), get()) }
    single { UserRepository(get(), get()) }

    // ViewModel
    viewModel { AuthViewModel(get(), get()) }
}