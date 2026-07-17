package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.luisito.gestor360.data.models.Sale
import java.util.UUID

@Entity(tableName = "ventas_cache")
data class VentaEntity(
    @PrimaryKey val id: String,
    val productoId: String,
    val productoNombre: String? = null,
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
    val tarjetaId: Long?,
    val createdAt: String?,
    val sincronizada: Boolean = true
)

fun VentaEntity.toModel() = Sale(
    id = id, producto_id = productoId, producto_nombre = productoNombre,
    cantidad = cantidad, total = total, metodo = metodo,
    efectivo = efectivo, transferencia = transferencia, usuario_id = usuarioId, local_id = localId,
    cliente_ci = clienteCi, cliente_tel = clienteTel, cliente_nombre = clienteNombre,
    tarjeta_id = tarjetaId, created_at = createdAt
)

fun Sale.toEntity(localId: Long, sincronizada: Boolean = true) = VentaEntity(
    id = id ?: "local_${UUID.randomUUID()}", productoId = producto_id, productoNombre = producto_nombre,
    cantidad = cantidad, total = total, metodo = metodo, efectivo = efectivo, transferencia = transferencia,
    usuarioId = usuario_id, localId = localId, clienteCi = cliente_ci, clienteTel = cliente_tel, clienteNombre = cliente_nombre,
    tarjetaId = tarjeta_id, createdAt = created_at, sincronizada = sincronizada
)
