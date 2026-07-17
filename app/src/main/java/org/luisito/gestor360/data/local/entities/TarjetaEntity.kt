package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Tarjeta = medio/cuenta de cobro informativa. Pertenece a UN local.
 * Se usa para etiquetar en Ventas por qué medio entró el dinero cuando
 * el pago es transferencia o mixto.
 *
 * Alineada al patrón real de Productos:
 * - id: UUID generado en el dispositivo (RN #1)
 * - localId: Long, igual que productos.local_id (bigint autoincremental
 *   de Supabase, NO uuid)
 * - Sin created_at/updated_at/version: Productos tampoco los tiene.
 * - `pendienteSync` es solo un flag local para la UI, no viaja a Supabase.
 *
 * A diferencia de Productos (que tiene Product como modelo remoto separado
 * de ProductoEntity), acá no vino un modelo aparte y no se debe inventar
 * uno: esta misma clase sirve de Entity de Room (columnas camelCase, ver
 * Migration_11_12.kt) y de DTO para decodificar directo la respuesta de
 * postgrest sobre la tabla "tarjetas" (columnas snake_case, ver
 * tarjetas_setup.sql) — de ahí los @SerialName. "pendienteSync" no existe
 * en el servidor: al no venir esa clave en el JSON remoto, kotlinx.serialization
 * usa el valor por defecto (@Transient para que ni siquiera intente leerla).
 */
@Serializable
@Entity(
    tableName = "tarjetas",
    indices = [Index(value = ["localId"])]
)
data class TarjetaEntity(
    @PrimaryKey
    val id: String, // UUID como String en Room; se envía como uuid al RPC

    @SerialName("local_id")
    val localId: Long, // FK lógica a Local (bigint, igual que Productos)

    val nombre: String,

    val tipo: String? = null,

    @SerialName("numero_cuenta")
    val numeroCuenta: String? = null,

    val activo: Boolean = true,

    @Transient
    val pendienteSync: Boolean = true // true mientras haya una AccionPendienteEntity para este id
)
