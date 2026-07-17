package com.gestor360.tarjetas.data.remote

import com.gestor360.core.sync.AccionPendienteEntity
import com.gestor360.tarjetas.data.TarjetaPayload
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TarjetaRemoteDataSource(
    private val client: SupabaseClient,
    private val androidIdProvider: () -> String // Settings.Secure.ANDROID_ID del dispositivo, o el id de sesión que ya uses en Productos
) {

    suspend fun enviarAccion(accion: AccionPendienteEntity): Result<Unit> = runCatching {
        val p = Json.decodeFromString(TarjetaPayload.serializer(), accion.payloadJson)
        val androidId = androidIdProvider()

        when (accion.tipoAccion) {
            "CREAR" -> client.postgrest.rpc(
                "crear_tarjeta",
                CrearTarjetaPayload(
                    p_android_id = androidId, p_local_id = p.localId, p_id = p.id,
                    p_nombre = p.nombre, p_tipo = p.tipo, p_numero_cuenta = p.numeroCuenta,
                    p_accion_id = accion.accionId
                )
            )
            "ACTUALIZAR" -> client.postgrest.rpc(
                "actualizar_tarjeta",
                ActualizarTarjetaPayload(
                    p_android_id = androidId, p_local_id = p.localId, p_id = p.id,
                    p_nombre = p.nombre, p_tipo = p.tipo, p_numero_cuenta = p.numeroCuenta,
                    p_activo = p.activo, p_accion_id = accion.accionId
                )
            )
            "ELIMINAR" -> client.postgrest.rpc(
                "eliminar_tarjeta",
                EliminarTarjetaPayload(
                    p_android_id = androidId, p_local_id = p.localId, p_id = p.id,
                    p_accion_id = accion.accionId
                )
            )
            else -> error("tipoAccion desconocido: ${accion.tipoAccion}")
        }
    }

    /** Refresco completo: sin updated_at, se trae todo lo del local. */
    suspend fun obtenerTodasDeLocal(localId: Long): List<TarjetaRemoteDto> {
        return client.postgrest["tarjetas"]
            .select { filter { eq("local_id", localId) } }
            .decodeList()
    }
}

@Serializable
data class CrearTarjetaPayload(
    val p_android_id: String, val p_local_id: Long, val p_id: String,
    val p_nombre: String, val p_tipo: String?, val p_numero_cuenta: String?,
    val p_accion_id: String
)

@Serializable
data class ActualizarTarjetaPayload(
    val p_android_id: String, val p_local_id: Long, val p_id: String,
    val p_nombre: String, val p_tipo: String?, val p_numero_cuenta: String?,
    val p_activo: Boolean, val p_accion_id: String
)

@Serializable
data class EliminarTarjetaPayload(
    val p_android_id: String, val p_local_id: Long, val p_id: String,
    val p_accion_id: String
)

@Serializable
data class TarjetaRemoteDto(
    val id: String,
    val local_id: Long,
    val nombre: String,
    val tipo: String?,
    val numero_cuenta: String?,
    val activo: Boolean
) {
    fun toEntity() = com.gestor360.tarjetas.data.local.TarjetaEntity(
        id = id,
        localId = local_id,
        nombre = nombre,
        tipo = tipo,
        numeroCuenta = numero_cuenta,
        activo = activo,
        pendienteSync = false
    )
}
