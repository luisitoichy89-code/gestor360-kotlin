package org.luisito.gestor360.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

class DataStoreManager(private val context: Context) {

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_ROL = stringPreferencesKey("user_rol")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_NOMBRE = stringPreferencesKey("nombre")
        private val KEY_TOKEN_EXPIRES = stringPreferencesKey("token_expires")
    }

    suspend fun saveSession(userId: String, userRol: String, username: String, nombre: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = userId
            preferences[KEY_USER_ROL] = userRol
            preferences[KEY_USERNAME] = username
            preferences[KEY_NOMBRE] = nombre
            preferences[KEY_TOKEN_EXPIRES] = System.currentTimeMillis().toString()
        }
    }

    fun getSession(): Flow<SessionData?> {
        return context.dataStore.data.map { preferences ->
            val userId = preferences[KEY_USER_ID]
            val userRol = preferences[KEY_USER_ROL]
            val username = preferences[KEY_USERNAME]
            val nombre = preferences[KEY_NOMBRE]
            val tokenExpires = preferences[KEY_TOKEN_EXPIRES]?.toLongOrNull() ?: 0L ?: 0L

            if (userId != null && userRol != null && username != null && tokenExpires != null) {
                val isExpired = System.currentTimeMillis() - tokenExpires > 30 * 24 * 60 * 60 * 1000
                if (!isExpired) {
                    SessionData(userId, userRol, username, nombre)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

data class SessionData(
    val userId: String,
    val userRol: String,
    val username: String,
    val nombre: String
)
