package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.luisito.gestor360.data.models.Devolucion

/**
 * Caché de get_devoluciones: un registro por local con el JSON completo de
 * la lista (mismo patrón que LocalEntity). Solo cubre lectura: solicitar y
 * resolver una devolución siguen necesitando conexión (ver DevolucionRepository).
 */
@Entity(tableName = "devoluciones_cache")
data class DevolucionCacheEntity(
    @PrimaryKey val localId: Long,
    val json: String
)

private val jsonParser = Json { ignoreUnknownKeys = true }
private val listaSerializer = ListSerializer(Devolucion.serializer())

fun List<Devolucion>.toEntity(localId: Long): DevolucionCacheEntity =
    DevolucionCacheEntity(localId = localId, json = jsonParser.encodeToString(listaSerializer, this))

fun DevolucionCacheEntity.toModel(): List<Devolucion> =
    jsonParser.decodeFromString(listaSerializer, json)
