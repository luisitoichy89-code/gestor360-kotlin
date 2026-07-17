package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import org.luisito.gestor360.data.models.Product
import java.time.LocalDateTime

/**
 * v9: "id" pasa de Long (autoincremental de Supabase) a String (UUID generado
 * en el dispositivo). Esto elimina el mecanismo de "id temporal negativo +
 * reemplazarIdTemporal()" que existía antes: ahora el mismo id que se genera
 * al crear el producto offline es el id definitivo, tanto en Room como en
 * Supabase, así que no hay nada que reemplazar después de sincronizar.
 *
 * "pendienteSync" reemplaza la vieja convención de "id negativo = no
 * sincronizado". Se pone en true cuando el producto se crea o edita
 * localmente y todavía no se confirmó contra el servidor; se pone en false
 * (vía toEntity() con datos que vienen del servidor) cuando refrescarDesdeServidor
 * trae la versión confirmada.
 */
@Entity(tableName = "productos_cache", primaryKeys = ["id", "localId"])
data class ProductoEntity(
    val id: String,
    val nombre: String,
    val precio: Double,
    val stock: Double,
    val ubicacion: String?,
    val categoria: String?,
    val localId: Long,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val pendienteSync: Boolean = false
)

fun ProductoEntity.toModel() = Product(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, local_id = localId
)

/** Para datos que vienen del servidor (siempre confirmados: pendienteSync = false). */
fun Product.toEntity(localId: Long, pendienteSync: Boolean = false) = ProductoEntity(
    id = id, nombre = nombre, precio = precio, stock = stock,
    ubicacion = ubicacion, categoria = categoria, localId = localId,
    createdAt = LocalDateTime.now().toString(),
    updatedAt = LocalDateTime.now().toString(),
    pendienteSync = pendienteSync
)
