package com.sosiso4kawo.betaapp.navigation

import android.content.Context
import android.content.Intent
import com.sosiso4kawo.betaapp.MainActivity
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.network.NavigationListener

class AppNavigator(private val context: Context) : NavigationListener {
    override fun navigateToLogin() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NAVIGATION_TARGET", R.id.navigation_login)
        }
        context.startActivity(intent)
    }
}