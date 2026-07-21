package org.luisito.gestor360.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "productos_eliminados_cache",
    primaryKeys = ["id", "localId"]
)
data class ProductoEliminadoCacheEntity(
    val id: String,
    val localId: Long,
    val nombre: String,
    val stock: Double = 0.0,
    val fecha: String,
    // NUEVO: turno en el que se eliminó el producto (ver migracion_turno_id.sql).
    // Nullable: registros ya existentes antes de la migración no lo tienen,
    // y siguen resolviéndose por "fecha" como fallback (ver InventarioRepository).
    val turnoId: Long? = null
)
