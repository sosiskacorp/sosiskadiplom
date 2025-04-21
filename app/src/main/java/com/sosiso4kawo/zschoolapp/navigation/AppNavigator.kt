package com.sosiso4kawo.zschoolapp.navigation

import android.content.Context
import android.content.Intent
import com.sosiso4kawo.zschoolapp.MainActivity
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.network.NavigationListener

class AppNavigator(private val context: Context) : NavigationListener {
    override fun navigateToLogin() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NAVIGATION_TARGET", R.id.navigation_login)
        }
        context.startActivity(intent)
    }
}