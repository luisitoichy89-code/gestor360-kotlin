package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import org.luisito.gestor360.data.models.Product

/**
 * PK compuesta (id, localId): el id de producto lo asigna el servidor POR LOCAL
 * (dos locales distintos pueden tener ambos un producto con id=5), así que "id"
 * solo no es único en un caché que mezcla varios locales. Con PK simple, un
 * "insertarTodos" del local B pisaba (OnConflictStrategy.REPLACE) la fila del
 * local A que compartía el mismo id, incluyendo su columna localId -> el
 * producto del local A "desaparecía" del caché. Este era el bug real de
 * "los locales no son independientes en la inserción de datos".
 */
@Entity(tableName = "productos_cache", primaryKeys = ["id", "localId"])
data class ProductoEntity(
    val id: Long,
    val nombre: String,
    val precio: Double,
    val stock: Double,
    val ubicacion: String?,
    val categoria: String?,
    /** Local al que pertenece esta fila cacheada. Todas las lecturas del DAO filtran por esto. */
    val localId: Long
)

fun ProductoEntity.toModel() = Product(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, local_id = localId
)

fun Product.toEntity(localId: Long) = ProductoEntity(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, localId = localId
)
