package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.luisito.gestor360.data.models.Product

@Entity(tableName = "productos_cache")
data class ProductoEntity(
    @PrimaryKey val id: Long,
    val nombre: String,
    val precio: Double,
    val stock: Double,
    val ubicacion: String?,
    val categoria: String?,
    val localId: Long? = null
)

fun ProductoEntity.toModel() = Product(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, local_id = localId
)

fun Product.toEntity() = ProductoEntity(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, localId = local_id
)
