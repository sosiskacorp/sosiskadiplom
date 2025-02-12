package com.sosiso4kawo.betaapp.di

import com.sosiso4kawo.betaapp.data.api.AuthService
import com.sosiso4kawo.betaapp.data.repository.AuthRepository
import com.sosiso4kawo.betaapp.ui.auth.AuthViewModel
import com.sosiso4kawo.betaapp.util.SessionManager
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {
    // Сеть
    single {
        Retrofit.Builder()
            .baseUrl("http://176.109.108.209:3211")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    single { get<Retrofit>().create(AuthService::class.java) }
    // Репозитории
    single { AuthRepository(get(), get()) }
    // SessionManager
    single { SessionManager(get()) }
    // ViewModel'ы
    viewModel { AuthViewModel(get(), get()) }
}
