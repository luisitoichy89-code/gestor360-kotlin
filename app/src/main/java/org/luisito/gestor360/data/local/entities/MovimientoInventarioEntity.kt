package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movimientos_inventario_cache")
data class MovimientoInventarioEntity(
    @PrimaryKey val id: String,
    val productoId: String,
    val turnoId: Long,
    val localId: Long,
    val tipo: String,
    val cantidad: Double,
    val stockAnterior: Double,
    val stockNuevo: Double,
    val createdAt: String?,
    val sincronizada: Boolean = false
)
