package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import org.luisito.gestor360.data.models.Product
import java.time.LocalDateTime

@Entity(tableName = "productos_cache", primaryKeys = ["id", "localId"])
data class ProductoEntity(
    val id: Long,
    val nombre: String,
    val precio: Double,
    val stock: Double,
    val ubicacion: String?,
    val categoria: String?,
    val localId: Long,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

fun ProductoEntity.toModel() = Product(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, local_id = localId
)

fun Product.toEntity(localId: Long) = ProductoEntity(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, localId = localId,
    createdAt = LocalDateTime.now().toString(),
    updatedAt = LocalDateTime.now().toString()
)
