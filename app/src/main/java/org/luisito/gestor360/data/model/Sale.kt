package org.luisito.gestor360.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ventas")
data class Sale(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productoId: Long,
    val productoNombre: String,
    val cantidad: Int,
    val precioUnit: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double,
    val transferencia: Double,
    val usuarioId: Long,
    val almacenId: String,
    val clienteCi: String = "",
    val clienteTel: String = "",
    val clienteNombre: String = "",
    val createdAt: String = ""
)
