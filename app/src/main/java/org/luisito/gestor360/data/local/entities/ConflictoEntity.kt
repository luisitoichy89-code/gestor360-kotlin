package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Algo que la sincronización detectó y que un humano debe decidir cómo resolver. */
@Entity(tableName = "conflictos")
data class ConflictoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val descripcion: String,
    val productoId: String? = null,
    val detectadoEn: Long = System.currentTimeMillis(),
    val resuelto: Boolean = false
)
