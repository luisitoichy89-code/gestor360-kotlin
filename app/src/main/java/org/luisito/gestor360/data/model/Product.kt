package org.luisito.gestor360.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "productos")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombre: String,
    val precio: Double,
    val stock: Int,
    val almacenId: String = "1"
)
