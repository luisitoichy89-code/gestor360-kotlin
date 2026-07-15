package org.luisito.gestor360.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "productos_eliminados_cache",
    primaryKeys = ["id", "localId"]
)
data class ProductoEliminadoCacheEntity(
    val id: Long,
    val localId: Long,
    val nombre: String,
    val stock: Double = 0.0,
    val fecha: String
)
