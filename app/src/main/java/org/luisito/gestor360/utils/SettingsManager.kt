package org.luisito.gestor360.utils

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsManager(context: Context) {
    private val settings = Settings(context)

    // Definición de claves con valores por defecto (evita String?)
    private val KEY_USER_ID = "user_id"
    private val KEY_USER_ROL = "user_rol"
    private val KEY_USERNAME = "username"
    private val KEY_NOMBRE = "nombre"
    private val KEY_SESSION_ACTIVE = "session_active"

    // Guardar sesión completa
    suspend fun saveSession(userId: String, userRol: String, username: String, nombre: String) {
        settings[KEY_USER_ID] = userId
        settings[KEY_USER_ROL] = userRol
        settings[KEY_USERNAME] = username
        settings[KEY_NOMBRE] = nombre
        settings[KEY_SESSION_ACTIVE] = true
    }

    // Leer sesión como Flow (compatible con DataStore)
    fun getSession(): Flow<SessionData?> {
        val flowSettings = FlowSettings(settings)
        return flowSettings.getBoolean(KEY_SESSION_ACTIVE, false).map { isActive ->
            if (isActive) {
                val userId = settings.getString(KEY_USER_ID, "")
                val userRol = settings.getString(KEY_USER_ROL, "")
                val username = settings.getString(KEY_USERNAME, "")
                val nombre = settings.getString(KEY_NOMBRE, "")

                if (userId.isNotEmpty() && userRol.isNotEmpty() && username.isNotEmpty()) {
                    SessionData(userId, userRol, username, nombre)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    // Leer sesión directamente (suspend)
    suspend fun getSessionData(): SessionData? {
        val isActive = settings.getBoolean(KEY_SESSION_ACTIVE, false)
        return if (isActive) {
            val userId = settings.getString(KEY_USER_ID, "")
            val userRol = settings.getString(KEY_USER_ROL, "")
            val username = settings.getString(KEY_USERNAME, "")
            val nombre = settings.getString(KEY_NOMBRE, "")

            if (userId.isNotEmpty() && userRol.isNotEmpty() && username.isNotEmpty()) {
                SessionData(userId, userRol, username, nombre)
            } else {
                null
            }
        } else {
            null
        }
    }

    // Limpiar sesión
    suspend fun clearSession() {
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_USER_ROL)
        settings.remove(KEY_USERNAME)
        settings.remove(KEY_NOMBRE)
        settings.remove(KEY_SESSION_ACTIVE)
    }
}

data class SessionData(
    val userId: String,
    val userRol: String,
    val username: String,
    val nombre: String
)
