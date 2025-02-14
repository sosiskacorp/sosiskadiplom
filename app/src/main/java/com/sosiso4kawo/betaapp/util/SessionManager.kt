package com.sosiso4kawo.betaapp.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.sosiso4kawo.betaapp.data.model.User

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
    }

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        val expiryTime = System.currentTimeMillis() + expiresIn * 1000 // если expiresIn в секундах
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_TOKEN_EXPIRY, expiryTime)
            apply()
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getTokenExpiry(): Long = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)

    fun saveUserData(user: User) {
        val json = Gson().toJson(user)
        prefs.edit().putString(KEY_USER_DATA, json).apply()
    }

    fun getUserData(): User? {
        val json = prefs.getString(KEY_USER_DATA, null)
        return if (json != null) {
            Gson().fromJson(json, User::class.java)
        } else {
            null
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}