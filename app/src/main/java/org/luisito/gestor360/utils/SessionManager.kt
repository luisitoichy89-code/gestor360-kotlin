package org.luisito.gestor360.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Fuente de verdad LOCAL (en el dispositivo) de la sesión activa, incluyendo
 * el local_id activo. Es lo que cada repositorio lee para armar el
 * "p_local_id" de cada RPC — así ningún dato se pide o se guarda nunca sin
 * saber a qué local pertenece.
 *
 * IMPORTANTE: localId puede cambiar en caliente (un admin puede tener acceso
 * a varios locales y cambiar de local activo, ver LocalSeleccionViewModel).
 * Todo lo que dependa de localId debe leerlo en el momento de la llamada,
 * nunca cachearlo en un campo de clase.
 */
class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gestor360_session", Context.MODE_PRIVATE)

    fun saveSession(
        userId: Long,
        username: String,
        rol: String,
        localId: Long?,
        clienteId: String,
        androidId: String,
        nombre: String? = null
    ) {
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putLong("user_id", userId)
            .putString("username", username)
            .putString("rol", rol)
            .putString("cliente_id", clienteId)
            .putString("android_id", androidId)
            .putString("nombre", nombre ?: username)
            .apply()
        setLocalId(localId)
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)

    fun getUserId(): Long = prefs.getLong("user_id", 0L)

    fun getUsername(): String = prefs.getString("username", "") ?: ""

    fun getNombre(): String = prefs.getString("nombre", "") ?: ""

    fun getRol(): String = prefs.getString("rol", "seller") ?: "seller"

    /**
     * El local_id ACTIVO en este dispositivo ahora mismo. No hay default
     * silencioso (antes existía un "1" por defecto que era la causa de que
     * todos los locales terminaran compartiendo datos): si no hay local_id
     * seleccionado, esto es null y el llamador debe manejarlo explícitamente
     * (por ejemplo, mostrando el selector de local).
     */
    fun getLocalId(): Long? {
        val valor = prefs.getLong("local_id", -1L)
        return if (valor == -1L) null else valor
    }

    fun setLocalId(localId: Long?) {
        prefs.edit().apply {
            if (localId == null) remove("local_id") else putLong("local_id", localId)
        }.apply()
    }

    fun getClienteId(): String = prefs.getString("cliente_id", "") ?: ""

    fun getAndroidId(): String = prefs.getString("android_id", "") ?: ""

    /**
     * Marca de tiempo (epoch ms) de la última precarga EXITOSA del caché de
     * un local específico. Sirve para: (1) no repetir descargas pesadas de
     * datos si ya se sincronizó hace poco (ahorro de datos), y (2) poder
     * mostrarle al admin "local 2 actualizado hace 3h" en vez de fallar en
     * silencio cuando no hay internet.
     */
    fun getUltimaPrecarga(localId: Long): Long =
        prefs.getLong("ultima_precarga_$localId", 0L)

    fun setUltimaPrecarga(localId: Long, timestampMs: Long) {
        prefs.edit().putLong("ultima_precarga_$localId", timestampMs).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
