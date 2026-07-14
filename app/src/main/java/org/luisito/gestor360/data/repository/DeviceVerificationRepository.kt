package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.UserEntity
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.security.PinSecurity
import org.luisito.gestor360.utils.AppContextHolder
import java.time.LocalDate

sealed class VerificacionResultado {
    data class Autorizado(val usuario: User) : VerificacionResultado()
    object DispositivoNoAutorizado : VerificacionResultado()
    object UsuarioInactivo : VerificacionResultado()
    data class LicenciaVencida(val diasVencida: Long) : VerificacionResultado()
    object LicenciaInactiva : VerificacionResultado()
    object SinConexionPrimerInicio : VerificacionResultado()
    data class Error(val mensaje: String) : VerificacionResultado()
}

@Serializable
private data class LicenciaFila(val cliente_id: String, val activo: Boolean, val expiracion: String)

class DeviceVerificationRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)

    suspend fun verificar(androidId: String): VerificacionResultado {
        return try {
            val usuarios = SupabaseClientProvider.client.postgrest.rpc(
                "get_usuarios", buildJsonObject { put("p_android_id", androidId) }
            ).decodeList<User>()
            val usuario = usuarios.firstOrNull() ?: return VerificacionResultado.DispositivoNoAutorizado
            if (!usuario.activo) return VerificacionResultado.UsuarioInactivo

            val licencias = SupabaseClientProvider.client.postgrest.rpc(
                "get_licencias", buildJsonObject { put("p_android_id", androidId) }
            ).decodeList<LicenciaFila>()
            val licencia = licencias.firstOrNull() ?: return VerificacionResultado.LicenciaInactiva
            if (!licencia.activo) return VerificacionResultado.LicenciaInactiva

            val expiracion = LocalDate.parse(licencia.expiracion)
            val hoy = LocalDate.now()
            if (expiracion.isBefore(hoy)) {
                val diasVencida = java.time.temporal.ChronoUnit.DAYS.between(expiracion, hoy)
                return VerificacionResultado.LicenciaVencida(diasVencida)
            }

            // El PIN nunca se guarda tal cual: se hashea acá mismo, antes de
            // tocar disco. Si usuario.pin viene null (no debería, pero por las
            // dudas) se conserva el hash anterior en vez de dejar al usuario
            // sin forma de validar PIN localmente.
            val entidadPrevia = db.userDao().getUserById(usuario.android_id ?: androidId)
            val (pinHash, pinSalt) = if (usuario.pin != null) {
                val h = PinSecurity.hashearPinNuevo(usuario.pin)
                h.hash to h.salt
            } else {
                entidadPrevia?.pinHash to entidadPrevia?.pinSalt
            }

            db.userDao().insertUser(UserEntity(
                id = usuario.android_id ?: androidId,
                authId = usuario.auth_id ?: "",
                username = usuario.username,
                nombre = usuario.nombre,
                rol = usuario.rol,
                localId = usuario.local_id,
                activo = usuario.activo,
                pinHash = pinHash,
                pinSalt = pinSalt
            ))

            VerificacionResultado.Autorizado(usuario)
        } catch (e: Exception) {
            val userLocal = db.userDao().getUserById(androidId)
            if (userLocal != null) {
                // pin = null a propósito: nunca tenemos el PIN en texto plano acá,
                // solo su hash (ver validarPinLocal más abajo).
                val usuario = User(
                    id = 0L, auth_id = userLocal.authId, cliente_id = "",
                    username = userLocal.username, nombre = userLocal.nombre,
                    rol = userLocal.rol, pin = null, android_id = userLocal.id,
                    local_id = userLocal.localId, activo = userLocal.activo
                )
                VerificacionResultado.Autorizado(usuario)
            } else {
                VerificacionResultado.SinConexionPrimerInicio
            }
        }
    }

    /**
     * Valida el PIN contra el hash cacheado localmente (nunca contra texto
     * plano: ni lo tenemos guardado ni lo tendremos). Funciona igual online
     * u offline porque siempre compara contra lo último cacheado en Room.
     */
    suspend fun validarPinLocal(androidId: String, pin: String): Boolean {
        val userLocal = db.userDao().getUserById(androidId) ?: return false
        val hash = userLocal.pinHash ?: return false
        val salt = userLocal.pinSalt ?: return false
        return PinSecurity.verificar(pin, salt, hash)
    }
}
