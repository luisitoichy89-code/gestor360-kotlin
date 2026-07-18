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
// FIX: antes esto pisaba SIEMPRE createdAt/updatedAt con LocalDateTime.now(),
// incluso para productos que ya existían y solo se estaban refrescando desde
// el servidor. Como refrescarDesdeServidor() corre seguido (cada vez que se
// abre la lista de productos, por diseño), esto corrompía la fecha real en
// cada sincronización — el catálogo entero terminaba pareciendo "creado/
// editado hoy" según cuándo cayera el último refresh, lo que explica que
// "productos nuevos"/"productos modificados" mostraran resultados erráticos
// (a veces de más, a veces vacío).
//
// El modelo Product no trae created_at/updated_at reales del servidor (el
// RPC get_productos no los expone), así que no hay un valor "verdadero" que
// preservar del lado servidor. Se aproxima con el primer momento en que
// ESTE dispositivo vio el producto localmente: si ya había una fila
// cacheada para ese id (parámetro "anterior"), se conserva su createdAt tal
// cual, y updatedAt solo se mueve a "ahora" si algún campo visible
// (nombre/precio/stock/ubicación/categoría) realmente cambió respecto a esa
// fila. Si es la primera vez que se ve el producto (anterior == null,
// primera carga del dispositivo o producto recién creado), ambos quedan en
// "ahora" — que es lo correcto en ese caso.
fun Product.toEntity(localId: Long, pendienteSync: Boolean = false, anterior: ProductoEntity? = null): ProductoEntity {
    val ahora = LocalDateTime.now().toString()
    val cambioAlgunCampo = anterior == null ||
        anterior.nombre != nombre || anterior.precio != precio || anterior.stock != stock ||
        anterior.ubicacion != ubicacion || anterior.categoria != categoria
    return ProductoEntity(
        id = id, nombre = nombre, precio = precio, stock = stock,
        ubicacion = ubicacion, categoria = categoria, localId = localId,
        createdAt = anterior?.createdAt ?: ahora,
        updatedAt = if (cambioAlgunCampo) ahora else (anterior?.updatedAt ?: ahora),
        pendienteSync = pendienteSync
    )
}
