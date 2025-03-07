package com.sosiso4kawo.betaapp.di

import com.sosiso4kawo.betaapp.data.api.AuthService
import com.sosiso4kawo.betaapp.data.api.UserService
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.data.repository.UserRepository
import com.sosiso4kawo.betaapp.network.AuthInterceptor
import com.sosiso4kawo.betaapp.ui.auth.AuthViewModel
import com.sosiso4kawo.betaapp.util.SessionManager
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {
    // SessionManager с контекстом приложения
    single { SessionManager(androidContext()) }

    // OkHttpClient с AuthInterceptor
    single {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get()))
            .build()
    }

    // Retrofit
    single {
        Retrofit.Builder()
            .baseUrl("http://176.109.108.209:3211")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single { get<Retrofit>().create(AuthService::class.java) }
    single { get<Retrofit>().create(UserService::class.java) }

    // Репозитории
    single { AuthRepository(get(), get()) }
    single { UserRepository(get(), get()) }

    // ViewModel
    viewModel { AuthViewModel(get(), get()) }
}