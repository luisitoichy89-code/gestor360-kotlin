package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.User
import java.time.LocalDate

sealed class VerificacionResultado {
    data class Autorizado(val usuario: User) : VerificacionResultado()
    object DispositivoNoAutorizado : VerificacionResultado()
    object UsuarioInactivo : VerificacionResultado()
    data class LicenciaVencida(val diasVencida: Long) : VerificacionResultado()
    object LicenciaInactiva : VerificacionResultado()
    data class Error(val mensaje: String) : VerificacionResultado()
}

/**
 * Primer paso al abrir la app: se busca en "usuarios" cuál usuario tiene este Android ID
 * autorizado, y se valida que la licencia principal del negocio (cliente_id) esté vigente.
 * Si todo está bien, se pasa a la pantalla de PIN para terminar de entrar.
 */
class DeviceVerificationRepository {

    @Serializable
    private data class LicenciaFila(val cliente_id: String, val activo: Boolean, val expiracion: String)

    suspend fun verificar(androidId: String): VerificacionResultado {
        return try {
            val usuarios = SupabaseClientProvider.client
                .from("usuarios")
                .select { filter { eq("android_id", androidId) } }
                .decodeList<User>()

            val usuario = usuarios.firstOrNull()
                ?: return VerificacionResultado.DispositivoNoAutorizado

            if (!usuario.activo) return VerificacionResultado.UsuarioInactivo

            val licencias = SupabaseClientProvider.client
                .from("licencias")
                .select { filter { eq("cliente_id", usuario.cliente_id) } }
                .decodeList<LicenciaFila>()

            val licencia = licencias.firstOrNull() ?: return VerificacionResultado.LicenciaInactiva
            if (!licencia.activo) return VerificacionResultado.LicenciaInactiva

            val expiracion = LocalDate.parse(licencia.expiracion)
            val hoy = LocalDate.now()
            if (expiracion.isBefore(hoy)) {
                val diasVencida = java.time.temporal.ChronoUnit.DAYS.between(expiracion, hoy)
                return VerificacionResultado.LicenciaVencida(diasVencida)
            }

            VerificacionResultado.Autorizado(usuario)
        } catch (e: Exception) {
            VerificacionResultado.Error(e.message ?: "Error al verificar el dispositivo")
        }
    }

    /** Valida el PIN localmente contra el usuario ya verificado (no vuelve a pegarle a la red). */
    fun validarPin(usuario: User, pinIngresado: String): Boolean {
        return usuario.pin == pinIngresado
    }
}
