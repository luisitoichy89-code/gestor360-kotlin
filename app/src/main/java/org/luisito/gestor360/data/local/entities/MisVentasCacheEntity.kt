package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mis_ventas_cache")
data class MisVentasCacheEntity(
    @PrimaryKey val id: String,
    val localId: Long,
    val usuarioId: Long,
    val productoId: String,
    val productoNombre: String?,
    val cantidad: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double,
    val transferencia: Double,
    val tarjetaId: String?,
    val turnoId: Long?,
    val createdAt: String?,
    val sincronizada: Boolean = false
)
