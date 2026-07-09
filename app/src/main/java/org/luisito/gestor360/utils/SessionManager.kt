package org.luisito.gestor360.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun saveSession(userId: Long, username: String, rol: String, localId: Long, clienteId: String, androidId: String, nombre: String? = null) {
        prefs.edit {
            putLong("user_id", userId)
            putString("username", username)
            putString("rol", rol)
            putLong("local_id", localId)
            putString("cliente_id", clienteId)
            putString("android_id", androidId)
            nombre?.let { putString("nombre", it) }
        }
    }

    fun isLoggedIn(): Boolean = prefs.contains("user_id")

    fun getUserId(): Long = prefs.getLong("user_id", 0L)
    fun getUsername(): String = prefs.getString("username", "") ?: ""
    fun getRol(): String = prefs.getString("rol", "") ?: ""
    fun getLocalId(): Long = prefs.getLong("local_id", 0L)
    fun getClienteId(): String = prefs.getString("cliente_id", "") ?: ""
    fun getAndroidId(): String = prefs.getString("android_id", "") ?: ""
    fun getNombre(): String = prefs.getString("nombre", "") ?: ""

    fun updateLocalId(localId: Long) {
        prefs.edit { putLong("local_id", localId) }
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
