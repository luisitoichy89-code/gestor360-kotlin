package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.UserEntity
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.security.PinSecurity
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
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

/**
 * Resultado de la revisión "en caliente" (ver verificarEnCaliente()). A
 * diferencia de VerificacionResultado (que se usa en la pantalla de
 * verificación completa), este solo tiene tres salidas posibles porque su
 * único trabajo es decidir si el acceso cacheado sigue siendo válido.
 */
sealed class VerificacionEnCalienteResultado {
    /** El servidor respondió y todo sigue en orden: usuario activo, licencia vigente. */
    object Ok : VerificacionEnCalienteResultado()
    /** El servidor respondió y dijo explícitamente que ya no hay acceso. */
    data class Bloqueado(val mensaje: String) : VerificacionEnCalienteResultado()
    /** No hay internet, o el request falló por cualquier motivo de red/servidor. No bloquea. */
    object NoVerificado : VerificacionEnCalienteResultado()
}

@Serializable
private data class LicenciaFila(val cliente_id: String, val activo: Boolean, val expiracion: String)

class DeviceVerificationRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)
    private val sessionManager = SessionManager(context)

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

            // Licencia vigente: guardamos hasta cuándo, para que la próxima vez
            // (con o sin internet) la app pueda saltar esta verificación entera
            // y entrar directo a pedir el PIN — ver intentarAccesoCacheado().
            sessionManager.guardarLicenciaVerificada(androidId, licencia.expiracion)

            val entidadPrevia = db.userDao().getUserById(usuario.android_id ?: androidId)
            val (pinHash, pinSalt) = if (usuario.pin != null) {
                // PBKDF2 con 120k iteraciones es trabajo de CPU, nunca en Main.
                val h = withContext(Dispatchers.Default) { PinSecurity.hashearPinNuevo(usuario.pin) }
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
                pinSalt = pinSalt,
                foto = entidadPrevia?.foto
            ))

            VerificacionResultado.Autorizado(usuario)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val userLocal = db.userDao().getUserById(androidId)
            if (userLocal != null) {
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
     * Revisión "en caliente": el punto que faltaba para tapar el hueco de
     * seguridad de un empleado desactivado que sigue offline. SOLO tiene
     * sentido llamarla cuando ya existe acceso cacheado (ver
     * intentarAccesoCacheado) y HAY internet — el llamador (MainActivity) es
     * quien decide eso antes de invocarla.
     *
     * Si no hay internet, o el request falla por cualquier motivo de red o
     * servidor, devuelve NoVerificado: NO bloquea nada, se sigue confiando
     * en la caché tal cual funcionaba antes. Bloquea ÚNICAMENTE cuando el
     * servidor respondió y dijo explícitamente que el usuario ya no está
     * activo o la licencia ya no es válida/vigente.
     */
    suspend fun verificarEnCaliente(androidId: String): VerificacionEnCalienteResultado {
        if (!NetworkMonitor.hayInternet(context)) return VerificacionEnCalienteResultado.NoVerificado
        return try {
            val usuarios = SupabaseClientProvider.client.postgrest.rpc(
                "get_usuarios", buildJsonObject { put("p_android_id", androidId) }
            ).decodeList<User>()
            val usuario = usuarios.firstOrNull()
                ?: return VerificacionEnCalienteResultado.Bloqueado(
                    "Este dispositivo ya no está autorizado. Contacta al admin."
                )
            if (!usuario.activo) {
                return VerificacionEnCalienteResultado.Bloqueado(
                    "Tu usuario está desactivado. Contacta al admin del negocio."
                )
            }

            val licencias = SupabaseClientProvider.client.postgrest.rpc(
                "get_licencias", buildJsonObject { put("p_android_id", androidId) }
            ).decodeList<LicenciaFila>()
            val licencia = licencias.firstOrNull()
                ?: return VerificacionEnCalienteResultado.Bloqueado(
                    "La licencia del negocio no está activa. Contacta al admin."
                )
            if (!licencia.activo) {
                return VerificacionEnCalienteResultado.Bloqueado(
                    "La licencia del negocio no está activa. Contacta al admin."
                )
            }
            val expiracion = LocalDate.parse(licencia.expiracion)
            if (expiracion.isBefore(LocalDate.now())) {
                return VerificacionEnCalienteResultado.Bloqueado(
                    "La licencia del negocio venció. Debe renovarse para continuar."
                )
            }

            // Aprovechamos para refrescar la fecha cacheada, por si la
            // licencia se extendió o se acortó desde la última verificación.
            sessionManager.guardarLicenciaVerificada(androidId, licencia.expiracion)
            VerificacionEnCalienteResultado.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VerificacionEnCalienteResultado.NoVerificado
        }
    }

    suspend fun validarPinLocal(androidId: String, pin: String): Boolean {
        val userLocal = db.userDao().getUserById(androidId) ?: return false
        val hash = userLocal.pinHash ?: return false
        val salt = userLocal.pinSalt ?: return false
        // PBKDF2 con 120k iteraciones es trabajo de CPU: nunca en Main.
        return withContext(Dispatchers.Default) { PinSecurity.verificar(pin, salt, hash) }
    }

    /**
     * Punto de entrada del "salto de verificación": se llama al abrir la app
     * (antes de mostrar VerificarDispositivoScreen). Si este dispositivo ya
     * se verificó antes y la licencia cacheada localmente todavía no venció,
     * arma el User directo desde Room (sin tocar la red) y la app va directo
     * al PIN. Devuelve null si nunca se verificó, si la licencia cacheada ya
     * venció, o si por algún motivo no hay usuario local guardado — en
     * cualquiera de esos casos se cae de nuevo en la verificación normal.
     */
    suspend fun intentarAccesoCacheado(androidId: String): User? {
        val expiracionCache = sessionManager.getLicenciaVerificadaVigente(androidId) ?: return null
        val vigente = try {
            !LocalDate.parse(expiracionCache).isBefore(LocalDate.now())
        } catch (e: Exception) {
            false
        }
        if (!vigente) return null

        val userLocal = db.userDao().getUserById(androidId) ?: return null
        return User(
            id = 0L, auth_id = userLocal.authId, cliente_id = "",
            username = userLocal.username, nombre = userLocal.nombre,
            rol = userLocal.rol, pin = null, android_id = userLocal.id,
            local_id = userLocal.localId, activo = userLocal.activo
        )
    }
}
