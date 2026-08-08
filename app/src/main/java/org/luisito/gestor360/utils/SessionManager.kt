package org.luisito.gestor360.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.luisito.gestor360.security.EncryptedPrefs

class SessionManager(context: Context) {

    companion object {
        private val _sesionRevocada = MutableStateFlow(false)
        val sesionRevocada: StateFlow<Boolean> = _sesionRevocada.asStateFlow()
    }

    private val prefs: SharedPreferences = EncryptedPrefs.abrir(context, "gestor360_session")
    private val licenciaPrefs: SharedPreferences = EncryptedPrefs.abrir(context, "gestor360_licencia_dispositivo")

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
        if (getLocalId() == null) {
            setLocalId(localId)
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)

    fun getUserId(): Long = prefs.getLong("user_id", 0L)

    fun getUsername(): String = prefs.getString("username", "") ?: ""

    fun getNombre(): String = prefs.getString("nombre", "") ?: ""

    fun getRol(): String = prefs.getString("rol", "seller") ?: "seller"

    fun getLocalId(): Long? {
        val valor = licenciaPrefs.getLong("local_id", -1L)
        return if (valor == -1L) null else valor
    }

    fun setLocalId(localId: Long?) {
        licenciaPrefs.edit().apply {
            if (localId == null) remove("local_id") else putLong("local_id", localId)
        }.apply()
    }

    fun getClienteId(): String = prefs.getString("cliente_id", "") ?: ""

    fun getAndroidId(): String = prefs.getString("android_id", "") ?: ""

    fun getUltimaPrecarga(localId: Long): Long =
        prefs.getLong("ultima_precarga_$localId", 0L)

    fun setUltimaPrecarga(localId: Long, timestampMs: Long) {
        prefs.edit().putLong("ultima_precarga_$localId", timestampMs).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun guardarLicenciaVerificada(androidId: String, expiracion: String) {
        licenciaPrefs.edit()
            .putString("android_id_verificado", androidId)
            .putString("licencia_expiracion", expiracion)
            .apply()
    }

    fun getLicenciaVerificadaVigente(androidId: String): String? {
        val guardado = licenciaPrefs.getString("android_id_verificado", null) ?: return null
        if (guardado != androidId) return null
        return licenciaPrefs.getString("licencia_expiracion", null)
    }

    fun limpiarLicenciaVerificada() {
        licenciaPrefs.edit().clear().apply()
    }

    fun marcarSesionRevocada() {
        licenciaPrefs.edit().putBoolean("sesion_revocada", true).apply()
        _sesionRevocada.value = true
    }

    fun limpiarSesionRevocada() {
        licenciaPrefs.edit().remove("sesion_revocada").apply()
        _sesionRevocada.value = false
    }

    fun haySesionRevocadaPersistida(): Boolean = licenciaPrefs.getBoolean("sesion_revocada", false)

    /**
     * Verifica contra Supabase que el local_id guardado en sesión
     * todavía existe. Si no existe, limpia el local_id para que
     * el usuario no siga operando sobre un local eliminado.
     *
     * @return true si el local existe, false si fue eliminado.
     */

    fun verificarLocalExiste(): Boolean {
        val localId = getLocalId() ?: return false
        return try {
            val db = org.luisito.gestor360.data.local.AppDatabase.obtener(
                org.luisito.gestor360.utils.AppContextHolder.context
            )
            val count = db.productoDao().obtenerTodos(localId).size
            if (count == 0) {
                setLocalId(null)
                false
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }
}
