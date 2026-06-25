package org.luisito.gestor360.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gestor360_session", Context.MODE_PRIVATE)

    fun saveSession(userId: Int, username: String, rol: String) {
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putInt("user_id", userId)
            .putString("username", username)
            .putString("rol", rol)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("is_logged_in", false)
    }

    fun getUserId(): Int {
        return prefs.getInt("user_id", 0)
    }

    fun getUsername(): String {
        return prefs.getString("username", "") ?: ""
    }

    fun getRol(): String {
        return prefs.getString("rol", "seller") ?: "seller"
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
