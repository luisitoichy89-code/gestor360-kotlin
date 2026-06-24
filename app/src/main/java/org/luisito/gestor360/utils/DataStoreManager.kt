package org.luisito.gestor360.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("gestor360_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_ROL = stringPreferencesKey("user_rol")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_NOMBRE = stringPreferencesKey("nombre")
        private val KEY_SESSION_ACTIVE = stringPreferencesKey("session_active")
    }

    suspend fun saveSession(userId: String, userRol: String, username: String, nombre: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USER_ROL] = userRol
            prefs[KEY_USERNAME] = username
            prefs[KEY_NOMBRE] = nombre
            prefs[KEY_SESSION_ACTIVE] = "true"
        }
    }

    fun getSession(): Flow<SessionData?> {
        return context.dataStore.data.map { prefs ->
            val userId = prefs[KEY_USER_ID]
            val userRol = prefs[KEY_USER_ROL]
            val username = prefs[KEY_USERNAME]
            val nombre = prefs[KEY_NOMBRE]
            val isActive = prefs[KEY_SESSION_ACTIVE] ?: "" == "true"

            if (userId != null && userRol != null && username != null && isActive) {
            val isActive = prefs[KEY_SESSION_ACTIVE]?.equals("true") ?: false
            } else {
                null
            }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

data class SessionData(
    val userId: String,
    val userRol: String,
    val username: String,
    val nombre: String
)
