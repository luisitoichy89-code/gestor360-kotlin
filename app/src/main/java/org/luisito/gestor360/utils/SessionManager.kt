package org.luisito.gestor360.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gestor360_session", Context.MODE_PRIVATE)

    fun saveSession(
        userId: Long, username: String, rol: String,
        almacenId: String, clienteId: String, androidId: String,
        nombre: String? = null,
        localId: Long? = null          // FK → locales.id, null = admin multi-local
    ) {
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putLong("user_id", userId)
            .putString("username", username)
            .putString("rol", rol)
            .putString("almacen_id", almacenId)
            .putString("cliente_id", clienteId)
            .putString("android_id", androidId)
            .putString("nombre", nombre ?: username)
            .putLong("local_id", localId ?: -1L)
            .apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)

    fun getUserId(): Long = prefs.getLong("user_id", 0L)

    fun getUsername(): String = prefs.getString("username", "") ?: ""

    fun getNombre(): String = prefs.getString("nombre", "") ?: ""

    fun getRol(): String = prefs.getString("rol", "seller") ?: "seller"

    fun getAlmacenId(): String = prefs.getString("almacen_id", "1") ?: "1"

    fun getClienteId(): String = prefs.getString("cliente_id", "") ?: ""

    fun getAndroidId(): String = prefs.getString("android_id", "") ?: ""

    /**
     * Devuelve el local_id activo: el del usuario (vendedor) o el que seleccionó
     * el admin en SelectorDeLocalBar. Null = admin sin filtro, ve todos los locales.
     */
    fun getLocalId(): Long? {
        val id = prefs.getLong("local_id", -1L)
        return if (id < 0L) null else id
    }

    /**
     * Actualiza el local activo en sesión. Lo llama LocalSeleccionViewModel
     * cuando el admin cambia de local desde SelectorDeLocalBar.
     */
    fun setLocalId(localId: Long?) {
        prefs.edit().putLong("local_id", localId ?: -1L).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
