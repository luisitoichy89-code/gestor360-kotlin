package com.gestor360.tarjetas.data.remote

import com.gestor360.core.sync.AccionPendienteEntity
import com.gestor360.tarjetas.data.TarjetaPayload
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TarjetaRemoteDataSource(private val client: SupabaseClient) {

    /** Despacha la acción del outbox al RPC correspondiente según tipoAccion. */
    suspend fun enviarAccion(accion: AccionPendienteEntity): Result<Unit> = runCatching {
        val payload = Json.decodeFromString(TarjetaPayload.serializer(), accion.payloadJson)
        val rpc = when (accion.tipoAccion) {
            "CREAR" -> "crear_tarjeta"
            "ACTUALIZAR" -> "actualizar_tarjeta"
            "ELIMINAR" -> "eliminar_tarjeta"
            else -> error("tipoAccion desconocido: ${accion.tipoAccion}")
        }
        client.postgrest.rpc(
            rpc,
            TarjetaRpcPayload(
                p_accion_id = accion.accionId,
                p_id = payload.id,
                p_local_id = payload.localId,
                p_nombre = payload.nombre,
                p_tipo = payload.tipo,
                p_numero_cuenta = payload.numeroCuenta,
                p_activo = payload.activo,
                p_creado_por = payload.creadoPor,
                p_updated_at = payload.updatedAt,
                p_deleted_at = payload.deletedAt,
                p_version = payload.version
            )
        )
    }

    suspend fun obtenerCambiosDesde(localId: String, desdeUpdatedAt: Long): List<TarjetaRemoteDto> {
        return client.postgrest["tarjetas"]
            .select {
                filter {
                    eq("local_id", localId)
                    gt("updated_at", desdeUpdatedAt)
                }
            }
            .decodeList()
    }
}

@Serializable
data class TarjetaRpcPayload(
    val p_accion_id: String,
    val p_id: String,
    val p_local_id: String,
    val p_nombre: String,
    val p_tipo: String?,
    val p_numero_cuenta: String?,
    val p_activo: Boolean,
    val p_creado_por: String,
    val p_updated_at: Long,
    val p_deleted_at: Long?,
    val p_version: Int
)

@Serializable
data class TarjetaRemoteDto(
    val id: String,
    val local_id: String,
    val nombre: String,
    val tipo: String?,
    val numero_cuenta: String?,
    val activo: Boolean,
    val creado_por: String,
    val created_at: Long,
    val updated_at: Long,
    val deleted_at: Long?,
    val version: Int
) {
    fun toEntity() = com.gestor360.tarjetas.data.local.TarjetaEntity(
        id = id,
        localId = local_id,
        nombre = nombre,
        tipo = tipo,
        numeroCuenta = numero_cuenta,
        activo = activo,
        creadoPor = creado_por,
        createdAt = created_at,
        updatedAt = updated_at,
        deletedAt = deleted_at,
        version = version,
        pendienteSync = false
    )
}
