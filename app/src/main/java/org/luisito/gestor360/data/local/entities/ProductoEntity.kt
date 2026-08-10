package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import org.luisito.gestor360.data.models.Product
import java.time.LocalDateTime

@Entity(tableName = "productos_cache", primaryKeys = ["id", "localId"])
data class ProductoEntity(
    val id: String,
    val nombre: String,
    val precio: Double,
    val stock: Double,
    val ubicacion: String?,
    val categoria: String?,
    val localId: Long,
    val turnoId: Long = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val pendienteSync: Boolean = false
)

fun ProductoEntity.toModel() = Product(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, local_id = localId
)

fun Product.toEntity(localId: Long, pendienteSync: Boolean = false, anterior: ProductoEntity? = null): ProductoEntity {
    val ahora = LocalDateTime.now().toString()
    val cambioAlgunCampo = anterior == null ||
        anterior.nombre != nombre || anterior.precio != precio || anterior.stock != stock ||
        anterior.ubicacion != ubicacion || anterior.categoria != categoria
    return ProductoEntity(
        id = id, nombre = nombre, precio = precio, stock = stock,
        ubicacion = ubicacion, categoria = categoria, localId = localId,
        turnoId = anterior?.turnoId ?: 0,
        createdAt = anterior?.createdAt ?: ahora,
        updatedAt = if (cambioAlgunCampo) ahora else (anterior?.updatedAt ?: ahora),
        pendienteSync = pendienteSync
    )
}
