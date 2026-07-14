package org.luisito.gestor360.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SharedPreferences cifradas con una llave maestra AES256-GCM que vive en el
 * Android Keystore (respaldado por hardware — StrongBox/TEE — cuando el
 * dispositivo lo soporta). La llave NUNCA sale del Keystore ni se guarda en
 * el propio archivo de preferencias, así que ni haciendo root y copiando el
 * archivo .xml se puede leer o editar el contenido sin volver a pasar por el
 * sistema operativo del propio dispositivo desbloqueado.
 *
 * Esto reemplaza SharedPreferences normales en los lugares donde antes se
 * guardaba texto plano manipulable (rol del usuario, local_id activo,
 * intentos de PIN): un vendedor con acceso root ya no puede simplemente
 * abrir el .xml y cambiar "rol":"seller" por "rol":"admin", ni resetear a
 * mano su contador de intentos fallidos de PIN.
 *
 * Si el archivo llega a corromperse o el Keystore invalida la llave (por
 * ejemplo tras un factory reset parcial o cambio de huella/PIN del SO en
 * dispositivos que lo atan a eso), se recrean las prefs vacías en vez de
 * crashear: el usuario simplemente vuelve a loguearse.
 */
object EncryptedPrefs {
    fun abrir(context: Context, nombre: String): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                nombre,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Llave del Keystore invalidada/corrupta: se descarta el archivo viejo
            // (ilegible de todos modos sin la llave) y se empieza de cero.
            context.applicationContext.getSharedPreferences(nombre, Context.MODE_PRIVATE).edit().clear().apply()
            context.applicationContext.deleteSharedPreferences(nombre)
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                nombre,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
