package org.luisito.gestor360.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/**
 * Passphrase de cifrado de la base de datos Room local (gestor360.db).
 *
 * Por qué: la base local cachea precios, stock por local, tarjetas de
 * cobro, ventas, aprobaciones pendientes, etc. Sin cifrar, cualquiera con
 * acceso root (o simplemente un `adb backup` en un dispositivo con
 * depuración USB habilitada) puede abrir ese .db con cualquier lector de
 * SQLite y leer o EDITAR filas directamente — por ejemplo, un vendedor
 * deshonesto podría subir el stock cacheado de un producto para "cuadrar"
 * un faltante, o cambiarse a sí mismo el rol/local en la sesión. Cifrando
 * el archivo completo con SQLCipher, sin la llave (que vive en el Android
 * Keystore, protegida por hardware cuando el dispositivo lo soporta) el
 * archivo es bytes ilegibles: no se puede leer ni editar con herramientas
 * externas, solo corromperlo (lo cual la propia app puede detectar).
 *
 * La llave se genera una sola vez por instalación (256 bits aleatorios) y
 * se guarda en SharedPreferences cifradas (EncryptedPrefs). Nunca se envía
 * a ningún servidor ni se expone fuera del dispositivo.
 */
object DbKeyProvider {
    private const val PREFS_NAME = "gestor360_db_key"
    private const val KEY_PASSPHRASE = "db_passphrase"

    fun obtenerOCrearPassphrase(context: Context): CharArray {
        val prefs = EncryptedPrefs.abrir(context, PREFS_NAME)
        val existente = prefs.getString(KEY_PASSPHRASE, null)
        if (existente != null) return existente.toCharArray()

        val nueva = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nuevaB64 = Base64.encodeToString(nueva, Base64.NO_WRAP)
        prefs.edit().putString(KEY_PASSPHRASE, nuevaB64).apply()
        return nuevaB64.toCharArray()
    }
}
