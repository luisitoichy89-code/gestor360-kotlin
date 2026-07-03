package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
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

class DeviceVerificationRepository {

    @Serializable
    private data class LicenciaFila(val cliente_id: String, val activo: Boolean, val expiracion: String)

    suspend fun verificar(androidId: String): VerificacionResultado {
        return try {
            val usuarios = SupabaseClientProvider.client.postgrest.rpc(
                "get_usuarios",
                mapOf("p_android_id" to androidId)
            ).decodeList<User>()

            val usuario = usuarios.firstOrNull()
                ?: return VerificacionResultado.DispositivoNoAutorizado

            if (!usuario.activo) return VerificacionResultado.UsuarioInactivo

            val licencias = SupabaseClientProvider.client.postgrest.rpc(
                "get_licencias",
                mapOf("p_android_id" to androidId)
            ).decodeList<LicenciaFila>()

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

    fun validarPin(usuario: User, pinIngresado: String): Boolean {
        return usuario.pin == pinIngresado
    }
}
