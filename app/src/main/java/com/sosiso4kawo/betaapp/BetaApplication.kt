package com.sosiso4kawo.betaapp

import android.app.Application
import com.sosiso4kawo.betaapp.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class BetaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@BetaApplication)
            modules(appModule)
        }
    }
}