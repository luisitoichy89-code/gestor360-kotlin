package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.luisito.gestor360.data.models.MermaPendiente

@Entity(tableName = "mermas_cache")
data class MermaEntity(
    @PrimaryKey val id: Long,
    val productoId: Long,
    val productoNombre: String,
    val cantidad: Double,
    val motivo: String?,
    val solicitadoPor: Long?,
    val solicitadoPorNombre: String?,
    val estado: String
)

fun MermaEntity.toModel() = MermaPendiente(
    id = id, producto_id = productoId, producto_nombre = productoNombre, cantidad = cantidad,
    motivo = motivo, solicitado_por = solicitadoPor, solicitado_por_nombre = solicitadoPorNombre, estado = estado
)

fun MermaPendiente.toEntity() = MermaEntity(
    id = id, productoId = producto_id, productoNombre = producto_nombre, cantidad = cantidad,
    motivo = motivo, solicitadoPor = solicitado_por, solicitadoPorNombre = solicitado_por_nombre, estado = estado
)
