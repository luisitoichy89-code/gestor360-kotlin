package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json
import org.luisito.gestor360.data.models.Local

/**
 * Caché de la lista de "locales" (get_locales), para que el selector de local
 * funcione sin conexión. Se guarda el JSON completo de cada fila en vez de
 * columna por columna: este caché no necesita filtrar/ordenar por ningún
 * campo propio de Local (a diferencia de Producto/Venta/etc, que sí filtran
 * por localId), así que alcanza con poder reconstruir tal cual la fila que
 * devolvió el servidor la última vez que hubo internet.
 */
@Entity(tableName = "locales_cache")
data class LocalEntity(
    @PrimaryKey val id: Long,
    val json: String
)

private val jsonParser = Json { ignoreUnknownKeys = true }

fun Local.toEntity(): LocalEntity = LocalEntity(id = id, json = jsonParser.encodeToString(Local.serializer(), this))

fun LocalEntity.toModel(): Local = jsonParser.decodeFromString(Local.serializer(), json)
