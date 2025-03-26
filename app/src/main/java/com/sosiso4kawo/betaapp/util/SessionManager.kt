package com.sosiso4kawo.betaapp.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.sosiso4kawo.betaapp.data.model.User

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_EXPIRES_IN = "expires_in"
    }

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        // expiresIn передаётся в секундах, переводим в миллисекунды
        val expiryTime = System.currentTimeMillis() + expiresIn * 1000
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_TOKEN_EXPIRY, expiryTime)
            putLong(KEY_EXPIRES_IN, expiresIn)
            apply()
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getTokenExpiry(): Long = prefs.getLong(KEY_TOKEN_EXPIRY, 0)

    fun isAccessTokenExpired(): Boolean {
        return System.currentTimeMillis() >= getTokenExpiry()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun saveUserData(user: User) {
        prefs.edit().putString(KEY_USER_DATA, gson.toJson(user)).apply()
    }

    fun getUserData(): User? {
        val json = prefs.getString(KEY_USER_DATA, null)
        return if (json != null) gson.fromJson(json, User::class.java) else null
    }

    fun getUserUuid(): String? = getUserData()?.uuid

    fun getExpiresIn(): Long = prefs.getLong(KEY_EXPIRES_IN, 7 * 24 * 3600L)
}