package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.luisito.gestor360.data.models.Sale

@Entity(tableName = "ventas_cache")
data class VentaEntity(
    @PrimaryKey val id: String,
    val productoId: Long,
    val cantidad: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double,
    val transferencia: Double,
    val usuarioId: Long?,
    val localId: Long,
    val clienteCi: String?,
    val clienteTel: String?,
    val clienteNombre: String?,
    val createdAt: String?,
    /** false mientras solo existe en este dispositivo, todavía no confirmada por el servidor. */
    val sincronizada: Boolean = true
)

fun VentaEntity.toModel() = Sale(
    id = id, producto_id = productoId, cantidad = cantidad, total = total, metodo = metodo,
    efectivo = efectivo, transferencia = transferencia, usuario_id = usuarioId, local_id = localId,
    cliente_ci = clienteCi, cliente_tel = clienteTel, cliente_nombre = clienteNombre, created_at = createdAt
)

fun Sale.toEntity(localId: Long, sincronizada: Boolean = true) = VentaEntity(
    id = id ?: "local_${System.nanoTime()}", productoId = producto_id, cantidad = cantidad, total = total,
    metodo = metodo, efectivo = efectivo, transferencia = transferencia, usuarioId = usuario_id,
    localId = localId, clienteCi = cliente_ci, clienteTel = cliente_tel, clienteNombre = cliente_nombre,
    createdAt = created_at, sincronizada = sincronizada
)
