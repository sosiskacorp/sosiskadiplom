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
import com.sosiso4kawo.zschoolapp.util.SessionManager
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