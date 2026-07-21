package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Merma = solicitud de baja de stock por producto dañado/vencido/perdido,
 * sujeta a aprobación de un admin. Pertenece a UN local.
 *
 * Rediseñada al patrón de Tarjetas/Productos: id UUID generado en el
 * dispositivo. Antes era un bigint que asignaba el servidor, con un id
 * temporal negativo mientras no sincronizaba (ver AccionPendienteEntity.
 * idLocalTemporal) — con UUID generado de entrada ese mecanismo ya no
 * hace falta para este módulo.
 *
 * Igual que TarjetaEntity: esta misma clase sirve de Entity de Room y de
 * DTO para decodificar directo la respuesta de postgrest sobre la tabla
 * "mermas" (columnas snake_case, ver mermas_setup.sql), de ahí los
 * @SerialName. "pendienteSync" no existe en el servidor.
 *
 * NOTA: existía un modelo de dominio separado (MermaPendiente, en
 * data.models) con sus propios toModel()/toEntity(), y un MermaDao con
 * métodos para id: Long. Ninguno de los dos se incluyó en los archivos a
 * corregir, así que no se adivinó su contenido — se reemplazan acá por el
 * mismo patrón de clase única usado en TarjetaEntity. Si algo más en la
 * app todavía importa MermaPendiente o llama al MermaDao viejo, va a dejar
 * de compilar y hay que actualizarlo también.
 */
@Serializable
@Entity(
    tableName = "mermas_cache",
    indices = [Index(value = ["localId"])]
)
data class MermaEntity(
    @PrimaryKey
    val id: String, // UUID como String en Room; se envía como uuid al RPC

    @SerialName("local_id")
    val localId: Long,

    @SerialName("producto_id")
    val productoId: String,

    @SerialName("producto_nombre")
    val productoNombre: String,

    val cantidad: Double,

    val motivo: String? = null,

    @SerialName("solicitado_por")
    val solicitadoPor: Long? = null,

    @SerialName("solicitado_por_nombre")
    val solicitadoPorNombre: String? = null,

    val estado: String = "pendiente", // pendiente | aprobada | rechazada

    // NUEVO: turno al que pertenece esta merma (ver migracion_turno_id.sql).
    // Nullable: mermas ya existentes antes de la migración no lo tienen.
    @SerialName("turno_id")
    val turnoId: Long? = null,

    @Transient
    val pendienteSync: Boolean = true
)
